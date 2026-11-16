import Darwin
import Foundation

struct SsdpHit: Hashable {
  let location: String
  let usn: String
  let st: String
  let server: String
  let address: String
}

enum SsdpDiscovery {
  private static let ssdpGroup = "239.255.255.250"
  private static let ssdpPort: UInt16 = 1900
  private static let recvTimeoutMs = 350
  private static let rounds = 4
  private static let targets = [
    "urn:schemas-upnp-org:device:MediaRenderer:1",
    "urn:schemas-upnp-org:service:AVTransport:1",
    "ssdp:all",
  ]

  static func run(timeoutMs: Double = 4000) -> [SsdpHit] {
    var found: [String: SsdpHit] = [:]
    let unicast = makeUnicastSocket()
    let group = makeGroupSocket()
    defer {
      if unicast >= 0 { close(unicast) }
      if group >= 0 { close(group) }
    }

    let deadline = Date().addingTimeInterval(max(0.8, timeoutMs / 1000.0))
    var round = 0
    var buffer = [UInt8](repeating: 0, count: 4096)

    while Date() < deadline {
      if round < rounds {
        sendSearch(fd: unicast)
        sendSearch(fd: group)
        if round == 0 || round == 2 { sweepUnicast(fd: unicast) }
        round += 1
      }
      drain(fd: unicast, buffer: &buffer, into: &found)
      drain(fd: group, buffer: &buffer, into: &found)
    }
    return Array(found.values)
  }

  private static func makeUnicastSocket() -> Int32 {
    let fd = socket(AF_INET, SOCK_DGRAM, 0)
    guard fd >= 0 else { return -1 }
    setReuse(fd)
    setRecvTimeout(fd)
    var addr = sockaddr_in()
    addr.sin_family = sa_family_t(AF_INET)
    addr.sin_port = 0
    addr.sin_addr.s_addr = INADDR_ANY
    let bound = withUnsafePointer(to: &addr) {
      $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
        bind(fd, $0, socklen_t(MemoryLayout<sockaddr_in>.size))
      }
    }
    if bound < 0 { close(fd); return -1 }
    return fd
  }

  private static func makeGroupSocket() -> Int32 {
    let fd = socket(AF_INET, SOCK_DGRAM, 0)
    guard fd >= 0 else { return -1 }
    setReuse(fd)
    setRecvTimeout(fd)
    var addr = sockaddr_in()
    addr.sin_family = sa_family_t(AF_INET)
    addr.sin_port = ssdpPort.bigEndian
    addr.sin_addr.s_addr = INADDR_ANY
    let bound = withUnsafePointer(to: &addr) {
      $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
        bind(fd, $0, socklen_t(MemoryLayout<sockaddr_in>.size))
      }
    }
    if bound < 0 { close(fd); return -1 }

    var mreq = ip_mreq()
    mreq.imr_multiaddr.s_addr = inet_addr(ssdpGroup)
    mreq.imr_interface.s_addr = INADDR_ANY
    setsockopt(fd, IPPROTO_IP, IP_ADD_MEMBERSHIP, &mreq, socklen_t(MemoryLayout<ip_mreq>.size))
    return fd
  }

  private static func setReuse(_ fd: Int32) {
    var yes: Int32 = 1
    setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &yes, socklen_t(MemoryLayout<Int32>.size))
    setsockopt(fd, SOL_SOCKET, SO_REUSEPORT, &yes, socklen_t(MemoryLayout<Int32>.size))
  }

  private static func setRecvTimeout(_ fd: Int32) {
    var tv = timeval(tv_sec: 0, tv_usec: __darwin_suseconds_t(recvTimeoutMs * 1000))
    setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &tv, socklen_t(MemoryLayout<timeval>.size))
  }

  private static func sendSearch(fd: Int32) {
    guard fd >= 0 else { return }
    var dest = sockaddr_in()
    dest.sin_family = sa_family_t(AF_INET)
    dest.sin_port = ssdpPort.bigEndian
    dest.sin_addr.s_addr = inet_addr(ssdpGroup)

    for st in targets {
      let msg = "M-SEARCH * HTTP/1.1\r\nHOST: \(ssdpGroup):\(ssdpPort)\r\nMAN: \"ssdp:discover\"\r\nMX: 3\r\nST: \(st)\r\n\r\n"
      let bytes = Array(msg.utf8)
      withUnsafePointer(to: &dest) {
        $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
          _ = sendto(fd, bytes, bytes.count, 0, $0, socklen_t(MemoryLayout<sockaddr_in>.size))
        }
      }
    }
  }

  private static func sweepUnicast(fd: Int32) {
    guard fd >= 0 else { return }
    let lan = MediaServer.lanAddress()
    let parts = lan.split(separator: ".")
    guard parts.count == 4 else { return }
    let prefix = "\(parts[0]).\(parts[1]).\(parts[2])."
    let myHost = Int(parts[3]) ?? -1

    let msg = "M-SEARCH * HTTP/1.1\r\nHOST: \(ssdpGroup):\(ssdpPort)\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: \(targets[0])\r\n\r\n"
    let bytes = Array(msg.utf8)
    var dest = sockaddr_in()
    dest.sin_family = sa_family_t(AF_INET)
    dest.sin_port = ssdpPort.bigEndian

    for host in 1...254 where host != myHost {
      dest.sin_addr.s_addr = inet_addr("\(prefix)\(host)")
      withUnsafePointer(to: &dest) {
        $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
          _ = sendto(fd, bytes, bytes.count, 0, $0, socklen_t(MemoryLayout<sockaddr_in>.size))
        }
      }
    }
  }

  private static func drain(fd: Int32, buffer: inout [UInt8], into found: inout [String: SsdpHit]) {
    guard fd >= 0 else { return }
    while true {
      var sender = sockaddr_in()
      var senderLen = socklen_t(MemoryLayout<sockaddr_in>.size)
      let n = withUnsafeMutablePointer(to: &sender) {
        $0.withMemoryRebound(to: sockaddr.self, capacity: 1) {
          recvfrom(fd, &buffer, buffer.count, 0, $0, &senderLen)
        }
      }
      if n <= 0 { break }
      let text = String(decoding: buffer[0..<n], as: UTF8.self)
      var headers: [String: String] = [:]
      for line in text.components(separatedBy: CharacterSet.newlines) {
        if let idx = line.firstIndex(of: ":") {
          let k = line[..<idx].trimmingCharacters(in: .whitespaces).lowercased()
          let v = line[line.index(after: idx)...].trimmingCharacters(in: .whitespaces)
          headers[k] = v
        }
      }
      guard let location = headers["location"], !location.isEmpty else { continue }
      let ip = String(cString: inet_ntoa(sender.sin_addr))
      if found[location] == nil {
        found[location] = SsdpHit(
          location: location,
          usn: headers["usn"] ?? "",
          st: headers["st"] ?? "",
          server: headers["server"] ?? "",
          address: ip
        )
      }
    }
  }
}
