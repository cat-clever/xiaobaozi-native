import Foundation

enum PlaylistRewriter {
  static func basenameOf(_ urlString: String) -> String {
    guard let url = URL(string: urlString) else { return "seg.ts" }
    let last = url.lastPathComponent
    if !last.isEmpty && last.contains(".") { return last }
    return "\(last.isEmpty ? "index" : last).ts"
  }

  static func localSegmentName(upstreamUrl: String, remuxing: Bool, forCast: Bool) -> String {
    let name = basenameOf(upstreamUrl)
    if remuxing {
      return name.replacingOccurrences(of: "\\.ts$", with: ".m4s", options: [.regularExpression, .caseInsensitive])
    }
    if forCast {
      let stripped = name.replacingOccurrences(of: "\\.ts$", with: "", options: [.regularExpression, .caseInsensitive])
      return stripped.isEmpty ? name : stripped
    }
    return name
  }

  static func absolutize(_ target: String, playlistUrl: String) -> String {
    if target.lowercased().hasPrefix("http://") || target.lowercased().hasPrefix("https://") {
      return target
    }
    guard let base = URL(string: playlistUrl),
          let scheme = base.scheme,
          let host = base.host else {
      return target
    }
    let portStr = base.port.map { ":\($0)" } ?? ""
    let origin = "\(scheme)://\(host)\(portStr)"

    if target.hasPrefix("/") {
      return origin + target
    }
    let path = base.path
    let dir: String
    if let idx = path.lastIndex(of: "/") {
      dir = String(path[...idx])
    } else {
      dir = "/"
    }
    return origin + dir + target
  }

  static func decorateUrl(_ target: String, playlistUrl: String, inheritQuery: Bool) -> String {
    let absolute = absolutize(target, playlistUrl: playlistUrl)
    if !inheritQuery { return absolute }
    guard let query = URL(string: playlistUrl)?.query, !query.isEmpty else {
      return absolute
    }
    if absolute.contains("sign=") { return absolute }
    return absolute + (absolute.contains("?") ? "&" : "?") + query
  }

  static func firstMediaSegment(text: String, playlistUrl: String, inheritQuery: Bool) -> String? {
    for rawLine in text.split(separator: "\n", omittingEmptySubsequences: false) {
      let trimmed = rawLine.trimmingCharacters(in: .whitespacesAndNewlines)
      if trimmed.isEmpty || trimmed.hasPrefix("#") { continue }
      return decorateUrl(trimmed, playlistUrl: playlistUrl, inheritQuery: inheritQuery)
    }
    return nil
  }

  static func rewrite(
    text: String,
    playlistUrl: String,
    inheritQuery: Bool,
    mapUri: String?,
    toLocal: (String) -> String
  ) -> String {
    func decorate(_ target: String) -> String {
      toLocal(decorateUrl(target, playlistUrl: playlistUrl, inheritQuery: inheritQuery))
    }

    let uriRegex = try! NSRegularExpression(pattern: "URI=\"([^\"]+)\"")
    let lines = text.split(separator: "\n", omittingEmptySubsequences: false).map(String.init)
    var mapped: [String] = []
    mapped.reserveCapacity(lines.count)

    for line in lines {
      let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
      if trimmed.isEmpty {
        mapped.append(line)
        continue
      }
      if trimmed.hasPrefix("#") {
        let range = NSRange(line.startIndex..<line.endIndex, in: line)
        if let match = uriRegex.firstMatch(in: line, range: range),
           let uriRange = Range(match.range(at: 1), in: line),
           let fullRange = Range(match.range, in: line) {
          let uri = String(line[uriRange])
          let replacement = "URI=\"\(decorate(uri))\""
          var updated = line
          updated.replaceSubrange(fullRange, with: replacement)
          mapped.append(updated)
        } else {
          mapped.append(line)
        }
      } else {
        mapped.append(decorate(trimmed))
      }
    }

    var body = mapped.joined(separator: "\n")
    if body.range(of: "#EXT-X-ENDLIST", options: .caseInsensitive) == nil {
      body += body.hasSuffix("\n") ? "#EXT-X-ENDLIST\n" : "\n#EXT-X-ENDLIST\n"
    }
    if let mapUri = mapUri {
      return withInitSegment(text: body, mapUri: mapUri)
    }
    return body
  }

  private static func withInitSegment(text: String, mapUri: String) -> String {
    let lines = text.split(separator: "\n", omittingEmptySubsequences: false).map(String.init)
    let hasVersion = lines.contains { $0.trimmingCharacters(in: .whitespaces).uppercased().hasPrefix("#EXT-X-VERSION:") }
    var out: [String] = []
    var insertedMap = false
    var insertedVersion = false

    for line in lines {
      let trimmed = line.trimmingCharacters(in: .whitespaces)
      if trimmed.uppercased().hasPrefix("#EXT-X-VERSION:") {
        out.append("#EXT-X-VERSION:7")
        continue
      }
      if !insertedMap && trimmed.uppercased().hasPrefix("#EXTINF:") {
        out.append("#EXT-X-MAP:URI=\"\(mapUri)\"")
        insertedMap = true
      }
      out.append(line)
      if !hasVersion && !insertedVersion && trimmed.uppercased().hasPrefix("#EXTM3U") {
        out.append("#EXT-X-VERSION:7")
        insertedVersion = true
      }
    }
    return out.joined(separator: "\n")
  }
}
