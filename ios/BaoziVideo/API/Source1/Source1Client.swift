import Foundation

final class Source1Client {
  let mahao: MahaoId
  let entry: MahaoEntry
  let wsKey: String
  var deviceId: String
  var token: String
  var baseUrl: String
  var secDomain: String = Source1Constants.secDomainFallback

  private static var memoBaseUrl = ""
  private static let memoLock = NSLock()

  static func randomDeviceId() -> String {
    let chars = Array("0123456789abcdef")
    return String((0..<16).map { _ in chars.randomElement()! })
  }

  init(mahao: MahaoId) {
    self.mahao = mahao
    self.entry = Source1Constants.mahaoTable[mahao]!
    self.wsKey = entry.wsKey
    Self.memoLock.lock()
    let memo = Self.memoBaseUrl
    Self.memoLock.unlock()
    let rawBase = memo.isEmpty ? Source1Constants.defaultBase : memo
    self.baseUrl = rawBase.replacingOccurrences(of: "/+$", with: "", options: .regularExpression)

    if mahao == .tianji {
      self.deviceId = Source1Constants.tianjiDeviceId
      self.token = Source1Constants.tianjiToken
    } else {
      self.deviceId = Self.randomDeviceId()
      self.token = ""
    }
  }

  func api(_ key: String) -> String {
    switch entry.api {
    case .new: return Source1Constants.apiPathsNew[key] ?? ""
    case .old: return Source1Constants.apiPathsOld[key] ?? ""
    }
  }

  func candidates() -> [String] {
    var out = [baseUrl]
    for base in [Source1Constants.defaultBase] + Source1Constants.fallbackBases {
      let trimmed = base.replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
      if !out.contains(trimmed) { out.append(trimmed) }
    }
    return out
  }

  func post(path: String, params: [String: Any] = [:], overrideBase: String? = nil) async throws -> [String: Any] {
    let root = (overrideBase ?? baseUrl).replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
    let url = root + path

    let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "-._~"))
    let bodyString = params
      .compactMap { k, v -> String? in
        let val = String(describing: v)
        guard let ek = k.addingPercentEncoding(withAllowedCharacters: allowed),
              let ev = val.addingPercentEncoding(withAllowedCharacters: allowed) else { return nil }
        return "\(ek)=\(ev)"
      }
      .joined(separator: "&")
    let bodyData = Data(bodyString.utf8)

    var last = ""
    var busy = 0

    for attempt in 0..<Source1Constants.retries {
      var headers = Source1Crypto.buildHeaders(
        entry: entry,
        deviceId: deviceId,
        token: token,
        curTimeMs: Int64(Date().timeIntervalSince1970 * 1000)
      )
      headers["Content-Type"] = "application/x-www-form-urlencoded"

      let text: String
      do {
        let resp = try await HttpClient.request(
          url: url,
          method: "POST",
          headers: headers,
          body: bodyData,
          timeoutSec: Source1Constants.apiTimeoutSec
        )
        text = resp.text
      } catch {
        last = error.localizedDescription
        if attempt < Source1Constants.retries - 1 {
          let ms = Double.random(in: Source1Constants.retrySleepRangeMs.0...Source1Constants.retrySleepRangeMs.1)
          try? await Task.sleep(nanoseconds: UInt64(ms * 1_000_000))
        }
        continue
      }

      let data: [String: Any]
      do {
        data = try Source1Crypto.decodeBody(text)
      } catch AppApiError.serverBusy(let msg) {
        busy += 1
        last = "服务端返回错误提示：\(msg)"
        if attempt < Source1Constants.retries - 1 {
          let ms = Double.random(in: Source1Constants.retrySleepRangeMs.0...Source1Constants.retrySleepRangeMs.1)
          try? await Task.sleep(nanoseconds: UInt64(ms * 1_000_000))
        }
        continue
      } catch {
        last = error.localizedDescription
        if attempt < Source1Constants.retries - 1 {
          let ms = Double.random(in: Source1Constants.retrySleepRangeMs.0...Source1Constants.retrySleepRangeMs.1)
          try? await Task.sleep(nanoseconds: UInt64(ms * 1_000_000))
        }
        continue
      }

      let code = (data["code"] as? NSNumber)?.intValue ?? Int(String(describing: data["code"] ?? "0")) ?? 0
      if code != Source1Constants.successCode {
        throw AppApiError.api("接口返回 code=\(code) message=\(String(describing: data["message"] ?? ""))")
      }
      return data
    }

    if busy == Source1Constants.retries {
      throw AppApiError.serverBusy(last)
    }
    throw AppApiError.api("请求失败（已重试 \(Source1Constants.retries) 次）: \(last.prefix(200))")
  }

  func initialize() async throws {
    var errors: [String] = []
    for base in candidates() {
      do {
        let payload = try await post(
          path: api("init"),
          params: ["invited_by": "", "is_install": "0", "ua": "0"],
          overrideBase: base
        )
        applyInit(payload: payload, usedBase: base)
        return
      } catch {
        errors.append("\(base): \(error.localizedDescription)")
      }
    }
    throw AppApiError.network("init 在所有候选域名上都失败：" + errors.joined(separator: " | "))
  }

  private func applyInit(payload: [String: Any], usedBase: String) {
    baseUrl = usedBase
    Self.memoLock.lock()
    Self.memoBaseUrl = usedBase
    Self.memoLock.unlock()

    let result = (payload["result"] as? [String: Any]) ?? (payload["data"] as? [String: Any]) ?? [:]
    let apiConf = (result["api_conf"] as? [String: Any]) ?? [:]
    let nextUrl = String(describing: apiConf["api_url2"] ?? apiConf["api_url"] ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
    if nextUrl.hasPrefix("http") {
      baseUrl = nextUrl.replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
      Self.memoLock.lock()
      Self.memoBaseUrl = baseUrl
      Self.memoLock.unlock()
    }

    if let userInfo = result["user_info"] as? [String: Any] {
      let issued = String(describing: userInfo["token"] ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
      if !issued.isEmpty, issued != "<null>" {
        token = issued
      }
    }

    let sysConf = (result["sys_conf"] as? [String: Any]) ?? [:]
    let p2p = (sysConf["p2p_config"] as? [String: Any]) ?? [:]
    let sec = String(describing: p2p["sec_domain"] ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
    if !sec.isEmpty, sec != "<null>" {
      secDomain = sec
    }
    if !entry.secDomain.isEmpty {
      secDomain = entry.secDomain
    }
  }

  func postWithFailover(path: String, params: [String: Any] = [:]) async throws -> [String: Any] {
    var errors: [String] = []
    for base in candidates() {
      do {
        let result = try await post(path: path, params: params, overrideBase: base)
        if base != baseUrl {
          baseUrl = base
          Self.memoLock.lock()
          Self.memoBaseUrl = base
          Self.memoLock.unlock()
        }
        return result
      } catch AppApiError.serverBusy(let msg) {
        throw AppApiError.serverBusy(msg)
      } catch {
        errors.append("\(base): \(error.localizedDescription)")
      }
    }
    throw AppApiError.network("所有候选域名都失败：" + errors.joined(separator: " | "))
  }

  private func callDict(_ path: String, params: [String: Any] = [:]) async throws -> [String: Any] {
    let raw = try Source1Crypto.unwrap(await postWithFailover(path: path, params: params))
    return (raw as? [String: Any]) ?? [:]
  }

  private func callArray(_ path: String, params: [String: Any] = [:]) async throws -> [[String: Any]] {
    let raw = try Source1Crypto.unwrap(await postWithFailover(path: path, params: params))
    return (raw as? [[String: Any]]) ?? []
  }

  func types() async throws -> [[String: Any]] {
    try await callArray(api("types"))
  }

  func screen(
    pn: Int = 1,
    typeId: Int = 0,
    typeValue: String = "",
    tagType: String = "",
    area: String = "",
    year: String = "",
    sort: String = ""
  ) async throws -> [[String: Any]] {
    try await callArray(
      api("screen"),
      params: [
        "pn": pn,
        "type_id": typeId,
        "type_value": typeValue,
        "tag_type": tagType,
        "area": area,
        "year": year,
        "sort": sort,
      ]
    )
  }

  func search(keyword: String, pn: Int = 1) async throws -> [[String: Any]] {
    try await callArray(api("search"), params: ["kw": keyword, "pn": pn])
  }

  func detail(vodId: Int) async throws -> [String: Any] {
    try await callDict(api("detail"), params: ["vod_id": vodId])
  }

  func collection(vodId: Int, collectionId: Int, vodToken: String, curTime: String) async throws -> [String: Any] {
    try await callDict(
      api("collection"),
      params: [
        "vod_id": vodId,
        "collection_id": collectionId,
        "vod_token": vodToken,
        "cur_time": curTime,
      ]
    )
  }

  func collectionList(vodId: Int) async throws -> [String: Any] {
    try await callDict(api("collection_list"), params: ["vod_id": vodId])
  }

  func hotSearch() async throws -> [[String: Any]] {
    try await callArray(api("hot_search"))
  }

  func resolve(prefix: String, target: String) async throws -> String {
    guard !prefix.isEmpty, !target.isEmpty else {
      throw AppApiError.api("缺少解析前缀或线路地址")
    }
    let headers = Source1Crypto.buildHeaders(
      entry: entry,
      deviceId: deviceId,
      token: token,
      curTimeMs: Int64(Date().timeIntervalSince1970 * 1000)
    )
    let resp = try await HttpClient.request(
      url: prefix + target,
      headers: headers,
      timeoutSec: Source1Constants.apiTimeoutSec
    )
    guard let data = resp.text.data(using: .utf8),
          let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
      throw AppApiError.api("解析返回的不是 JSON: \(resp.text.prefix(200))")
    }
    let code = (json["code"] as? NSNumber)?.intValue ?? 0
    if code != 200 {
      throw AppApiError.api("解析失败 code=\(code) message=\(String(describing: json["message"] ?? ""))")
    }
    let real = String(describing: json["url"] ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
    if real.isEmpty {
      throw AppApiError.api("解析成功但没返回地址")
    }
    return real
  }
}
