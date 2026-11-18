import Foundation

enum Source1Play {
  static func mirrorUrl(vodUrl: String, secDomain: String) -> String {
    guard !vodUrl.isEmpty, !secDomain.isEmpty,
          let parts = URL(string: vodUrl),
          var root = URL(string: secDomain) else {
      return ""
    }
    if root.host?.lowercased() == parts.host?.lowercased() {
      guard let fallback = URL(string: Source1Constants.secDomainFallback),
            fallback.host?.lowercased() != parts.host?.lowercased() else {
        return ""
      }
      root = fallback
    }
    var comps = URLComponents()
    comps.scheme = root.scheme
    comps.host = root.host
    comps.port = root.port
    comps.percentEncodedPath = parts.path
    comps.percentEncodedQuery = parts.query
    return comps.string ?? ""
  }

  static func isPlaceholderUrl(_ vodUrl: String) -> Bool {
    guard let host = URL(string: vodUrl)?.host?.lowercased() else { return false }
    return host == Source1Constants.placeholderHost
  }

  static func asLineList(_ value: Any?) -> [[String: Any]] {
    if let arr = value as? [[String: Any]] { return arr }
    guard let str = value as? String, !str.isEmpty,
          let data = str.data(using: .utf8),
          let arr = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
      return []
    }
    return arr
  }

  static func parseDuration(_ text: String) -> Int {
    guard !text.isEmpty else { return 0 }
    var seconds = 0.0
    for part in text.split(separator: ":") {
      let trimmed = part.trimmingCharacters(in: .whitespaces)
      guard !trimmed.isEmpty, let value = Double(trimmed), value.isFinite else { return 0 }
      seconds = seconds * 60.0 + value
    }
    return Int(seconds)
  }

  static func playLines(bean: [String: Any], secDomain: String = "", wsKey: String = "") -> [PlayLine] {
    var lines: [PlayLine] = []
    let vodUrl = String(describing: bean["vod_url"] ?? "")
    let placeholder = isPlaceholderUrl(vodUrl)
    let placeholderNote = "接口这次下发的是占位路径（该账号没有真实路径配额），直接播只会拿到一段 11 秒的警告片。"

    if vodUrl.hasPrefix("http") {
      lines.append(PlayLine(
        type: Source1Constants.lineSigned,
        kind: .signed,
        name: "主线路",
        url: vodUrl,
        parsePrefix: nil,
        ck: Source1Crypto.parseCk(bean),
        wsKey: wsKey,
        playable: !placeholder,
        note: placeholder ? placeholderNote : ""
      ))
    }

    if !placeholder {
      let mirrored = mirrorUrl(vodUrl: vodUrl, secDomain: secDomain)
      if !mirrored.isEmpty {
        lines.append(PlayLine(
          type: Source1Constants.lineMirror,
          kind: .mirror,
          name: "备用线路",
          url: mirrored,
          parsePrefix: nil,
          ck: nil,
          wsKey: nil,
          playable: true,
          note: ""
        ))
      }
    }

    let jx = asLineList(bean["jx_json"])
    let prefix = jx.first.flatMap { $0["url"] as? String } ?? ""

    for item in asLineList(bean["official_json"]) {
      let target = String(describing: item["url"] ?? "")
      if target.isEmpty || target == "<null>" { continue }
      let name = String(describing: item["zname"] ?? item["name"] ?? "官方线路")
      lines.append(PlayLine(
        type: Source1Constants.lineOfficial,
        kind: .official,
        name: name,
        url: target,
        parsePrefix: prefix,
        ck: nil,
        wsKey: nil,
        playable: !prefix.isEmpty,
        note: prefix.isEmpty ? "这条官方线路没带解析服务，播不了" : ""
      ))
    }

    if vodUrl.hasPrefix("http") {
      lines.append(PlayLine(
        type: Source1Constants.lineBackup,
        kind: .backup,
        name: "备用高清",
        url: vodUrl,
        parsePrefix: nil,
        ck: nil,
        wsKey: nil,
        playable: !placeholder,
        note: placeholder ? placeholderNote : ""
      ))
    }

    for item in asLineList(bean["m3u8_json"]) {
      let url = String(describing: item["url"] ?? "")
      if !url.hasPrefix("http") { continue }
      let m3u8Placeholder = isPlaceholderUrl(url)
      let name = String(describing: item["zname"] ?? item["name"] ?? "m3u8 线路")
      lines.append(PlayLine(
        type: Source1Constants.lineM3u8,
        kind: .m3u8,
        name: name,
        url: url,
        parsePrefix: nil,
        ck: nil,
        wsKey: nil,
        playable: !m3u8Placeholder,
        note: m3u8Placeholder ? placeholderNote : ""
      ))
    }

    return lines
  }

  static func hasPlayableLine(_ lines: [PlayLine]) -> Bool {
    lines.contains { $0.playable && $0.note.isEmpty && $0.kind != .backup }
  }

  static func episodes(_ detail: [String: Any]) -> [[String: Any]] {
    (detail["vod_collection"] as? [[String: Any]]) ?? []
  }

  static func mergeDurations(eps: [[String: Any]], lengths: [[String: Any]]) -> [Episode] {
    var byId: [String: String] = [:]
    for row in lengths {
      let id = String(describing: row["id"] ?? "")
      let dur = String(describing: row["duration"] ?? "")
      if !id.isEmpty { byId[id] = dur }
    }
    return eps.enumerated().map { index, bean in
      let id = String(describing: bean["id"] ?? "")
      let rawDur = String(describing: bean["duration"] ?? "")
      let duration = (byId[id]?.isEmpty == false ? byId[id]! : rawDur)
      return Episode(
        episodeIndex: index,
        episodeId: id,
        title: CryptoUtils.episodeTitle(bean["title"] ?? bean["collection_new_title"] ?? bean["collection"], index: index),
        vodToken: String(describing: bean["vod_token"] ?? ""),
        curTime: String(describing: bean["cur_time"] ?? ""),
        duration: duration,
        durationSec: parseDuration(duration),
        pid: nil,
        scid: nil
      )
    }
  }
}
