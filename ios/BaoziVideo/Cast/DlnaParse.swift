import Foundation

struct CastDevice: Identifiable, Hashable {
  let id: String
  let name: String
  let model: String
  let address: String
  let controlUrl: String
  let controlUrls: [String]
  let serviceType: String
}

struct CastTransportState {
  enum State: String {
    case playing, paused, stopped, transitioning, unknown
  }
  let state: State
  let positionSec: Double?
  let durationSec: Double?
}

enum DlnaParse {
  static let avTransport = "avtransport"
  private static let guessedPaths = [
    "/AVTransport/control",
    "/upnp/control/AVTransport1",
    "/upnp/control/AVTransport",
    "/AVTransport/ctrl",
  ]
  private static let fallbackPaths = ["/control"] + guessedPaths

  static func tagText(_ xml: String, tag: String) -> String {
    let pattern = "<(?:[\\w.-]+:)?\(tag)\\b[^>]*>([\\s\\S]*?)</(?:[\\w.-]+:)?\(tag)\\s*>"
    guard let regex = try? NSRegularExpression(pattern: pattern, options: .caseInsensitive) else { return "" }
    let range = NSRange(xml.startIndex..<xml.endIndex, in: xml)
    guard let match = regex.firstMatch(in: xml, range: range),
          let innerRange = Range(match.range(at: 1), in: xml) else {
      return ""
    }
    return String(xml[innerRange])
      .replacingOccurrences(of: "<[^>]*>", with: "", options: .regularExpression)
      .trimmingCharacters(in: .whitespacesAndNewlines)
  }

  static func allBlocks(_ xml: String, tag: String) -> [String] {
    let pattern = "<(?:[\\w.-]+:)?\(tag)\\b[^>]*>[\\s\\S]*?</(?:[\\w.-]+:)?\(tag)\\s*>"
    guard let regex = try? NSRegularExpression(pattern: pattern, options: .caseInsensitive) else { return [] }
    let range = NSRange(xml.startIndex..<xml.endIndex, in: xml)
    return regex.matches(in: xml, range: range).compactMap {
      Range($0.range, in: xml).map { String(xml[$0]) }
    }
  }

  static func parseDescription(xml: String, descriptionUrl: String) -> (udn: String, friendlyName: String, modelName: String, controlUrls: [String], serviceType: String)? {
    let rawBase = tagText(xml, tag: "URLBase")
    let base = normalizeBase(raw: rawBase, fallback: descriptionUrl)

    var controlUrls: [String] = []
    var serviceType = ""

    for block in allBlocks(xml, tag: "service") {
      let type = tagText(block, tag: "serviceType")
      let control = tagText(block, tag: "controlURL")
      if control.isEmpty { continue }
      if type.lowercased().contains(avTransport) {
        controlUrls = controlCandidates(base: base, rawBase: rawBase, descriptionUrl: descriptionUrl, control: control)
        serviceType = type
        break
      }
      if controlUrls.isEmpty && control.lowercased().contains(avTransport) {
        controlUrls = controlCandidates(base: base, rawBase: rawBase, descriptionUrl: descriptionUrl, control: control)
        serviceType = type.isEmpty ? "urn:schemas-upnp-org:service:AVTransport:1" : type
      }
    }

    if controlUrls.isEmpty && xml.range(of: "<\\s*(?:[\\w.-]+:)?(?:root|device)\\b", options: [.regularExpression, .caseInsensitive]) != nil {
      let root = rootOf(base)
      if !root.isEmpty { controlUrls = ["\(root)\(guessedPaths[0])"] }
    }
    if controlUrls.isEmpty { return nil }

    let model = tagText(xml, tag: "modelName")
    return (
      udn: tagText(xml, tag: "UDN"),
      friendlyName: tagText(xml, tag: "friendlyName"),
      modelName: model.isEmpty ? tagText(xml, tag: "manufacturer") : model,
      controlUrls: controlUrls,
      serviceType: serviceType.isEmpty ? "urn:schemas-upnp-org:service:AVTransport:1" : serviceType
    )
  }

  static func controlCandidates(base: String, rawBase: String, descriptionUrl: String, control: String) -> [String] {
    var out: [String] = []
    func push(_ u: String) {
      if !u.isEmpty && !out.contains(u) { out.append(u) }
    }
    push(resolveUrl(base: base, relative: control))
    if !rawBase.trimmingCharacters(in: .whitespaces).isEmpty {
      push(resolveUrl(base: rawBase.trimmingCharacters(in: .whitespaces), relative: control))
    }
    push(resolveUrl(base: descriptionUrl, relative: control))
    let root = rootOf(base).isEmpty ? rootOf(descriptionUrl) : rootOf(base)
    if !root.isEmpty {
      for path in fallbackPaths { push("\(root)\(path)") }
    }
    return out
  }

  static func normalizeBase(raw: String, fallback: String) -> String {
    let base = raw.trimmingCharacters(in: .whitespacesAndNewlines)
    let target = base.isEmpty ? fallback : base
    guard let url = URL(string: target) else { return target }
    let path = url.path
    let trimmed = path.hasSuffix("/") ? String(path.dropLast()) : path
    if trimmed.isEmpty { return target }
    let last = trimmed.split(separator: "/").last.map(String.init) ?? ""
    if !last.contains(".") { return target }
    let dir = trimmed[..<trimmed.lastIndex(of: "/")!] + "/"
    let portStr = url.port.map { ":\($0)" } ?? ""
    return "\(url.scheme ?? "http")://\(url.host ?? "")\(portStr)\(dir)"
  }

  static func resolveUrl(base: String, relative: String) -> String {
    let rel = relative.trimmingCharacters(in: .whitespacesAndNewlines)
    if rel.isEmpty { return "" }
    if rel.lowercased().hasPrefix("http://") || rel.lowercased().hasPrefix("https://") { return rel }
    guard let baseUrl = URL(string: base),
          let resolved = URL(string: rel, relativeTo: baseUrl)?.absoluteURL else {
      return rel
    }
    return resolved.absoluteString
  }

  static func rootOf(_ urlString: String) -> String {
    guard let url = URL(string: urlString), let scheme = url.scheme, let host = url.host else { return "" }
    let portStr = url.port.map { ":\($0)" } ?? ""
    return "\(scheme)://\(host)\(portStr)"
  }

  static func normalizeState(_ raw: String) -> CastTransportState.State {
    switch raw.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
    case "PLAYING": return .playing
    case "PAUSED_PLAYBACK", "PAUSED_RECORDING", "PAUSED": return .paused
    case "STOPPED", "NO_MEDIA_PRESENT": return .stopped
    case "TRANSITIONING": return .transitioning
    default: return .unknown
    }
  }

  static func faultOf(_ xml: String) -> String {
    if xml.range(of: "fault", options: .caseInsensitive) == nil { return "" }
    let reason = [tagText(xml, tag: "faultstring"), tagText(xml, tag: "errorDescription"), tagText(xml, tag: "errorCode")]
      .first { !$0.isEmpty }
    return reason ?? "设备返回了 SOAP Fault"
  }

  static func escapeXml(_ input: String) -> String {
    input
      .replacingOccurrences(of: "&", with: "&amp;")
      .replacingOccurrences(of: "<", with: "&lt;")
      .replacingOccurrences(of: ">", with: "&gt;")
      .replacingOccurrences(of: "\"", with: "&quot;")
      .replacingOccurrences(of: "'", with: "&apos;")
  }

  static func buildEnvelope(serviceType: String, action: String, args: [(String, String)]) -> String {
    let inner = args.map { "<\($0.0)>\(escapeXml($0.1))</\($0.0)>" }.joined()
    return "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
      "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
      "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
      "<s:Body><u:\(action) xmlns:u=\"\(serviceType)\">\(inner)</u:\(action)></s:Body></s:Envelope>"
  }

  static func buildMetadata(url: String, title: String) -> String {
    let safeTitle = escapeXml(title.isEmpty ? "Video" : title)
    return "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" " +
      "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " +
      "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
      "<item id=\"0\" parentID=\"-1\" restricted=\"1\">" +
      "<dc:title>\(safeTitle)</dc:title>" +
      "<upnp:class>object.item.videoItem</upnp:class>" +
      "<res protocolInfo=\"http-get:*:video/*:*\">\(escapeXml(url))</res>" +
      "</item></DIDL-Lite>"
  }
}
