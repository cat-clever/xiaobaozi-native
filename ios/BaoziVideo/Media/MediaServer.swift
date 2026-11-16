import CryptoKit
import Foundation
import Network

struct SignRule {
  let key: String
  let extra: String
}

final class MediaServer {
  static let shared = MediaServer()

  private static let routePrefix = "m"
  private static let copyChunk = 64 * 1024
  private static let maxHeadBytes = 8192
  private static let probeBytes = 64 * 1024

  final class Session {
    let headers: [String: String]
    let sign: SignRule?
    let inheritQuery: Bool
    let forCast: Bool
    var remuxer: TsFmp4Remuxer?
    var remuxInit: Data?
    var probed: (target: String, data: Data)?

    init(headers: [String: String], sign: SignRule?, inheritQuery: Bool, forCast: Bool) {
      self.headers = headers
      self.sign = sign
      self.inheritQuery = inheritQuery
      self.forCast = forCast
    }
  }

  private var listener: NWListener?
  private(set) var port: UInt16 = 0
  private(set) var token = ""
  private(set) var lanHost = ""

  private let lock = NSLock()
  private var sessions: [String: Session] = [:]

  private static let probeSession: URLSession = {
    let config = URLSessionConfiguration.ephemeral
    config.timeoutIntervalForRequest = 30
    return URLSession(configuration: config)
  }()

  private init() {}

  static func lanAddress() -> String {
    var preferred = ""
    var fallback = ""
    var head: UnsafeMutablePointer<ifaddrs>?
    guard getifaddrs(&head) == 0, let first = head else { return "" }
    defer { freeifaddrs(head) }
    var cursor: UnsafeMutablePointer<ifaddrs>? = first
    while let current = cursor {
      let entry = current.pointee
      if let addr = entry.ifa_addr, addr.pointee.sa_family == sa_family_t(AF_INET) {
        let name = String(cString: entry.ifa_name)
        var host = [CChar](repeating: 0, count: Int(NI_MAXHOST))
        if getnameinfo(addr, socklen_t(addr.pointee.sa_len), &host, socklen_t(host.count), nil, 0, NI_NUMERICHOST) == 0 {
          let ip = String(cString: host)
          if !ip.hasPrefix("127.") && !ip.hasPrefix("169.254.") {
            if name.hasPrefix("en") || name.hasPrefix("bridge") {
              if preferred.isEmpty { preferred = ip }
            } else if !name.hasPrefix("utun") && !name.hasPrefix("lo") && fallback.isEmpty {
              fallback = ip
            }
          }
        }
      }
      cursor = entry.ifa_next
    }
    return preferred.isEmpty ? fallback : preferred
  }

  func ensureStarted() throws -> (port: UInt16, token: String, lanHost: String) {
    lock.lock()
    if listener != nil, port != 0 {
      let res = (port, token, Self.lanAddress())
      lanHost = res.2
      lock.unlock()
      return res
    }
    lock.unlock()

    let params = NWParameters.tcp
    let lan = Self.lanAddress()
    if lan.isEmpty {
      params.requiredLocalEndpoint = NWEndpoint.hostPort(host: .ipv4(.loopback), port: .any)
    }
    params.allowLocalEndpointReuse = true

    let newListener = try NWListener(using: params)
    let ready = DispatchSemaphore(value: 0)
    var startError: Error?

    newListener.stateUpdateHandler = { [weak self] state in
      switch state {
      case .ready:
        self?.port = newListener.port?.rawValue ?? 0
        ready.signal()
      case .failed(let error):
        startError = error
        ready.signal()
      default:
        break
      }
    }
    newListener.newConnectionHandler = { [weak self] connection in
      self?.handle(connection)
    }
    newListener.start(queue: DispatchQueue.global(qos: .userInitiated))

    if ready.wait(timeout: .now() + 5) == .timedOut || startError != nil || port == 0 {
      newListener.cancel()
      throw startError ?? AppApiError.network("本地媒体服务启动失败")
    }

    lock.lock()
    self.listener = newListener
    self.token = UUID().uuidString.replacingOccurrences(of: "-", with: "")
    self.lanHost = lan
    let res = (self.port, self.token, self.lanHost)
    lock.unlock()
    return res
  }

  func stop() {
    lock.lock()
    listener?.cancel()
    listener = nil
    port = 0
    sessions.removeAll()
    lock.unlock()
  }

  func openSession(headers: [String: String], sign: SignRule?, inheritQuery: Bool, forCast: Bool) throws -> String {
    _ = try ensureStarted()
    let sid = UUID().uuidString.replacingOccurrences(of: "-", with: "").prefix(16).lowercased()
    let id = String(sid)
    lock.lock()
    sessions[id] = Session(headers: headers, sign: sign, inheritQuery: inheritQuery, forCast: forCast)
    lock.unlock()
    return id
  }

  func closeSession(_ sid: String) {
    lock.lock()
    sessions.removeValue(forKey: sid)
    let empty = sessions.isEmpty
    lock.unlock()
    if empty {
      stop()
    }
  }

  func streamUrl(sid: String, upstreamUrl: String, format: StreamFormat, forCast: Bool) -> String {
    let host = forCast ? (lanHost.isEmpty ? Self.lanAddress() : lanHost) : "127.0.0.1"
    lock.lock()
    let session = sessions[sid]
    let remuxing = session?.remuxer != nil
    let p = port
    let t = token
    lock.unlock()

    let encoded = CryptoUtils.base64UrlEncode(Data(upstreamUrl.utf8))
    let name: String
    if format == .mp4 {
      name = PlaylistRewriter.basenameOf(upstreamUrl)
    } else if upstreamUrl.components(separatedBy: "?").first?.lowercased().hasSuffix(".m3u8") == true {
      name = PlaylistRewriter.basenameOf(upstreamUrl)
    } else {
      name = PlaylistRewriter.localSegmentName(upstreamUrl: upstreamUrl, remuxing: remuxing, forCast: forCast)
    }
    return "http://\(host):\(p)/\(Self.routePrefix)/\(t)/\(sid)/\(encoded)/\(name)"
  }

  func prepareRemux(sid: String, playlistUrl: String) async {
    lock.lock()
    let session = sessions[sid]
    lock.unlock()
    guard let session,
          playlistUrl.components(separatedBy: "?").first?.lowercased().hasSuffix(".m3u8") == true,
          let req = makeUpstreamRequest(url: playlistUrl, session: session) else {
      return
    }

    guard let (data, resp) = try? await Self.probeSession.data(for: req),
          let http = resp as? HTTPURLResponse,
          (200..<300).contains(http.statusCode),
          let text = String(data: data, encoding: .utf8),
          let firstSeg = PlaylistRewriter.firstMediaSegment(text: text, playlistUrl: playlistUrl, inheritQuery: session.inheritQuery) else {
      return
    }

    guard let segReq = makeUpstreamRequest(url: firstSeg, session: session, range: "bytes=0-\(Self.probeBytes - 1)"),
          let (segData, segResp) = try? await Self.probeSession.data(for: segReq),
          let segHttp = segResp as? HTTPURLResponse,
          (200..<300).contains(segHttp.statusCode),
          TsFmp4Remuxer.videoStreamType(inTs: segData) == 0x24 else {
      return
    }

    var fullData = segData
    let status = segHttp.statusCode
    if status == 206, let fullReq = makeUpstreamRequest(url: firstSeg, session: session),
       let (d, r) = try? await Self.probeSession.data(for: fullReq),
       let h = r as? HTTPURLResponse, (200..<300).contains(h.statusCode) {
      fullData = d
    }

    let remuxer = TsFmp4Remuxer()
    guard let frag = try? remuxer.remux(segment: fullData),
          let initSeg = try? remuxer.initSegment() else {
      return
    }

    lock.lock()
    session.remuxer = remuxer
    session.remuxInit = initSeg
    session.probed = (target: firstSeg, data: frag)
    lock.unlock()
  }

  // MARK: - HTTP Server

  private func handle(_ connection: NWConnection) {
    connection.start(queue: DispatchQueue.global(qos: .userInitiated))
    readHead(from: connection, buffer: Data())
  }

  private func readHead(from connection: NWConnection, buffer: Data) {
    connection.receive(minimumIncompleteLength: 1, maximumLength: Self.copyChunk) { [weak self] data, _, isComplete, error in
      guard let self else { connection.cancel(); return }
      if error != nil { connection.cancel(); return }
      var acc = buffer
      if let data { acc.append(data) }
      if let range = acc.range(of: Data("\r\n\r\n".utf8)) {
        let headData = acc.subdata(in: 0..<range.lowerBound)
        if let head = String(data: headData, encoding: .utf8) {
          self.dispatch(head: head, connection: connection)
        } else {
          self.sendStatus(connection, code: 400, text: "Bad Request")
        }
        return
      }
      if isComplete || acc.count >= Self.maxHeadBytes {
        self.sendStatus(connection, code: 400, text: "Bad Request")
        return
      }
      self.readHead(from: connection, buffer: acc)
    }
  }

  private func dispatch(head: String, connection: NWConnection) {
    let lines = head.components(separatedBy: "\r\n")
    guard let requestLine = lines.first else {
      sendStatus(connection, code: 400, text: "Bad Request")
      return
    }
    let parts = requestLine.split(separator: " ")
    guard parts.count >= 2 else {
      sendStatus(connection, code: 400, text: "Bad Request")
      return
    }
    let method = String(parts[0]).uppercased()
    guard method == "GET" || method == "HEAD" else {
      sendStatus(connection, code: 405, text: "Method Not Allowed")
      return
    }

    let rawPath = String(parts[1]).components(separatedBy: "?").first ?? ""
    let segs = rawPath.split(separator: "/").map(String.init)
    // /m/<token>/<sid>/<b64url>/<filename>
    guard segs.count >= 5, segs[0] == Self.routePrefix, segs[1] == token else {
      sendStatus(connection, code: 403, text: "Forbidden")
      return
    }
    let sid = segs[2]
    let encodedTarget = segs[3]
    let filename = segs[4]

    lock.lock()
    let session = sessions[sid]
    lock.unlock()
    guard let session else {
      sendStatus(connection, code: 404, text: "Session Not Found")
      return
    }

    if filename == "init.mp4" {
      lock.lock()
      let initData = session.remuxInit
      lock.unlock()
      if let initData {
        sendData(connection, status: 200, contentType: "video/mp4", data: method == "HEAD" ? Data() : initData)
      } else {
        sendStatus(connection, code: 404, text: "No Init Segment")
      }
      return
    }

    guard let targetData = CryptoUtils.base64DecodeFlexible(encodedTarget),
          let targetUrl = String(data: targetData, encoding: .utf8),
          targetUrl.hasPrefix("http") else {
      sendStatus(connection, code: 400, text: "Bad Target")
      return
    }

    var rangeHeader: String?
    var reqHost = "127.0.0.1:\(port)"
    for line in lines.dropFirst() {
      if let idx = line.firstIndex(of: ":") {
        let k = line[..<idx].trimmingCharacters(in: .whitespaces).lowercased()
        let v = line[line.index(after: idx)...].trimmingCharacters(in: .whitespaces)
        if k == "range" { rangeHeader = v }
        if k == "host" { reqHost = v }
      }
    }

    if targetUrl.components(separatedBy: "?").first?.lowercased().hasSuffix(".m3u8") == true {
      servePlaylist(targetUrl: targetUrl, sid: sid, session: session, reqHost: reqHost, headOnly: method == "HEAD", connection: connection)
      return
    }

    // Check probed cache for remux
    lock.lock()
    let probed = session.probed
    let remuxer = session.remuxer
    if probed?.target == targetUrl { session.probed = nil }
    lock.unlock()

    if let probed, probed.target == targetUrl {
      sendData(connection, status: 200, contentType: "video/iso.segment", data: method == "HEAD" ? Data() : probed.data)
      return
    }

    if let remuxer {
      serveRemuxedSegment(targetUrl: targetUrl, session: session, remuxer: remuxer, headOnly: method == "HEAD", connection: connection)
      return
    }

    proxyRaw(targetUrl: targetUrl, session: session, range: rangeHeader, headOnly: method == "HEAD", connection: connection)
  }

  private func servePlaylist(targetUrl: String, sid: String, session: Session, reqHost: String, headOnly: Bool, connection: NWConnection) {
    guard let req = makeUpstreamRequest(url: targetUrl, session: session) else {
      sendStatus(connection, code: 400, text: "Bad URL")
      return
    }
    Self.probeSession.dataTask(with: req) { [weak self] data, resp, err in
      guard let self else { connection.cancel(); return }
      guard err == nil, let http = resp as? HTTPURLResponse, (200..<300).contains(http.statusCode),
            let data, let text = String(data: data, encoding: .utf8) else {
        self.sendStatus(connection, code: 502, text: "Upstream Playlist Error")
        return
      }

      self.lock.lock()
      let remuxing = session.remuxer != nil
      let t = self.token
      self.lock.unlock()

      let basePrefix = "http://\(reqHost)/\(Self.routePrefix)/\(t)/\(sid)"
      let mapUri = remuxing ? "\(basePrefix)/init/init.mp4" : nil
      let rewritten = PlaylistRewriter.rewrite(
        text: text,
        playlistUrl: targetUrl,
        inheritQuery: session.inheritQuery,
        mapUri: mapUri
      ) { absUrl in
        let b64 = CryptoUtils.base64UrlEncode(Data(absUrl.utf8))
        let segName = PlaylistRewriter.localSegmentName(upstreamUrl: absUrl, remuxing: remuxing, forCast: session.forCast)
        return "\(basePrefix)/\(b64)/\(segName)"
      }
      let outData = Data(rewritten.utf8)
      self.sendData(connection, status: 200, contentType: "application/vnd.apple.mpegurl", data: headOnly ? Data() : outData)
    }.resume()
  }

  private func serveRemuxedSegment(targetUrl: String, session: Session, remuxer: TsFmp4Remuxer, headOnly: Bool, connection: NWConnection) {
    guard let req = makeUpstreamRequest(url: targetUrl, session: session) else {
      sendStatus(connection, code: 400, text: "Bad URL")
      return
    }
    Self.probeSession.dataTask(with: req) { [weak self] data, resp, err in
      guard let self else { connection.cancel(); return }
      guard err == nil, let http = resp as? HTTPURLResponse, (200..<300).contains(http.statusCode), let data else {
        self.sendStatus(connection, code: 502, text: "Upstream Segment Error")
        return
      }
      if let frag = try? remuxer.remux(segment: data) {
        self.sendData(connection, status: 200, contentType: "video/iso.segment", data: headOnly ? Data() : frag)
      } else {
        self.sendData(connection, status: 200, contentType: "video/mp2t", data: headOnly ? Data() : data)
      }
    }.resume()
  }

  private func proxyRaw(targetUrl: String, session: Session, range: String?, headOnly: Bool, connection: NWConnection) {
    guard let req = makeUpstreamRequest(url: targetUrl, session: session, range: range, method: headOnly ? "HEAD" : "GET") else {
      sendStatus(connection, code: 400, text: "Bad URL")
      return
    }
    Self.probeSession.dataTask(with: req) { [weak self] data, resp, err in
      guard let self else { connection.cancel(); return }
      guard err == nil, let http = resp as? HTTPURLResponse else {
        self.sendStatus(connection, code: 502, text: "Upstream Error")
        return
      }
      var hdr = "HTTP/1.1 \(http.statusCode) \(HTTPURLResponse.localizedString(forStatusCode: http.statusCode))\r\n"
      if let ct = http.value(forHTTPHeaderField: "Content-Type") { hdr += "Content-Type: \(ct)\r\n" }
      if let cr = http.value(forHTTPHeaderField: "Content-Range") { hdr += "Content-Range: \(cr)\r\n" }
      if let ar = http.value(forHTTPHeaderField: "Accept-Ranges") { hdr += "Accept-Ranges: \(ar)\r\n" }
      let payload = headOnly ? Data() : (data ?? Data())
      hdr += "Content-Length: \(payload.count)\r\nConnection: close\r\n\r\n"
      var packet = Data(hdr.utf8)
      packet.append(payload)
      connection.send(content: packet, completion: .contentProcessed { _ in connection.cancel() })
    }.resume()
  }

  private func makeUpstreamRequest(url urlString: String, session: Session, range: String? = nil, method: String = "GET") -> URLRequest? {
    let finalUrl = signUrlIfNeeded(urlString, rule: session.sign)
    guard let url = URL(string: finalUrl) else { return nil }
    var req = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 30)
    req.httpMethod = method
    for (k, v) in session.headers {
      req.setValue(v, forHTTPHeaderField: k)
    }
    if let range { req.setValue(range, forHTTPHeaderField: "Range") }
    return req
  }

  private func signUrlIfNeeded(_ urlString: String, rule: SignRule?) -> String {
    guard let rule, !rule.key.isEmpty, var comps = URLComponents(string: urlString) else {
      return urlString
    }
    let ts = UInt32(Date().timeIntervalSince1970)
    let tsHex = String(ts, radix: 16)
    let path = comps.path
    let secret = CryptoUtils.md5Hex(rule.key + path + tsHex)

    var kept: [String] = []
    if let q = comps.percentEncodedQuery, !q.isEmpty {
      for kv in q.split(separator: "&").map(String.init) {
        let k = kv.split(separator: "=").first.map(String.init) ?? ""
        if k != "wsSecret" && k != "wsTime" { kept.append(kv) }
      }
    }
    kept.append("wsSecret=\(secret)&wsTime=\(tsHex)")
    var finalQuery = kept.joined(separator: "&")
    if !rule.extra.isEmpty {
      finalQuery = finalQuery.isEmpty ? rule.extra : "\(finalQuery)&\(rule.extra)"
    }
    comps.percentEncodedQuery = finalQuery
    return comps.string ?? urlString
  }

  private func sendStatus(_ connection: NWConnection, code: Int, text: String) {
    let body = Data(text.utf8)
    let hdr = "HTTP/1.1 \(code) \(text)\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: \(body.count)\r\nConnection: close\r\n\r\n"
    var packet = Data(hdr.utf8)
    packet.append(body)
    connection.send(content: packet, completion: .contentProcessed { _ in connection.cancel() })
  }

  private func sendData(_ connection: NWConnection, status: Int, contentType: String, data: Data) {
    let hdr = "HTTP/1.1 \(status) OK\r\nContent-Type: \(contentType)\r\nContent-Length: \(data.count)\r\nAccept-Ranges: bytes\r\nConnection: close\r\n\r\n"
    var packet = Data(hdr.utf8)
    packet.append(data)
    connection.send(content: packet, completion: .contentProcessed { _ in connection.cancel() })
  }
}
