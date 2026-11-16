import Foundation

struct CastSource {
  let url: String
  let format: StreamFormat
  let direct: Bool
  let lineName: String
  let dispose: () -> Void
}

enum StreamResolver {
  private static let s1Headers: [String: String] = [
    "User-Agent": Source1Constants.mediaUA,
    "Accept": "*/*",
  ]

  private static let s1SignedHeaders: [String: String] = [
    "User-Agent": Source1Constants.mediaUA,
    "Accept": "*/*",
    "Badci": Source1Constants.badci,
  ]

  private static let s2Headers: [String: String] = [
    "User-Agent": Source2Constants.playUA,
  ]

  static func formatOf(_ url: String) -> StreamFormat {
    url.range(of: "\\.mp4(\\?|$)", options: [.regularExpression, .caseInsensitive]) != nil ? .mp4 : .hls
  }

  static func pickPlayableLine(_ lines: [PlayLine]) -> PlayLine? {
    let usable = lines.filter { $0.playable && $0.note.isEmpty }
    return usable.first { $0.kind == .signed }
      ?? usable.first { $0.kind == .mirror }
      ?? usable.first { $0.kind == .m3u8 }
      ?? usable.first { $0.kind == .official }
  }

  private static func openLocalStream(
    target: String,
    headers: [String: String],
    sign: SignRule?,
    inheritQuery: Bool,
    format: StreamFormat,
    lineName: String
  ) async -> ResolvedMediaStream {
    do {
      let sid = try MediaServer.shared.openSession(headers: headers, sign: sign, inheritQuery: inheritQuery, forCast: false)
      if format == .hls {
        await MediaServer.shared.prepareRemux(sid: sid, playlistUrl: target)
      }
      let localUrl = MediaServer.shared.streamUrl(sid: sid, upstreamUrl: target, format: format, forCast: false)
      return ResolvedMediaStream(
        streamUrl: localUrl,
        format: format,
        headers: [:],
        lineName: lineName,
        dispose: { MediaServer.shared.closeSession(sid) }
      )
    } catch {
      return ResolvedMediaStream(
        streamUrl: target,
        format: format,
        headers: headers,
        lineName: lineName,
        dispose: {}
      )
    }
  }

  static func resolveSource1(lines: [PlayLine], preferred: PlayLine? = nil) async throws -> ResolvedMediaStream {
    guard let line = preferred ?? pickPlayableLine(lines) else {
      throw AppApiError.api("这些线路当前都播不了：主线路要接口下发真实路径，备用线路上也没有对应文件。")
    }
    let target = line.kind == .official ? try await Source1Service.shared.resolveOfficialLine(line) : line.url
    let format = formatOf(target)
    let headers = line.kind == .signed ? s1SignedHeaders : s1Headers
    let sign = (line.kind == .signed && !(line.wsKey ?? "").isEmpty) ? SignRule(key: line.wsKey!, extra: line.ck ?? "") : nil
    return await openLocalStream(
      target: target,
      headers: headers,
      sign: sign,
      inheritQuery: false,
      format: format,
      lineName: line.name
    )
  }

  static func resolveSource2Candidate(_ item: [String: Any]) async throws -> ResolvedMediaStream {
    let url = String(describing: item["playUrl"] ?? "")
    guard !url.isEmpty, url != "<null>" else {
      throw AppApiError.api("这条清晰度没有播放地址")
    }
    let format = formatOf(url)
    let lineName = String(describing: item["qualityName"] ?? item["src"] ?? "默认线路")
    return await openLocalStream(
      target: url,
      headers: s2Headers,
      sign: nil,
      inheritQuery: format == .hls,
      format: format,
      lineName: lineName
    )
  }

  static func castSourceForSource1(lines: [PlayLine], preferred: PlayLine? = nil) async throws -> CastSource {
    guard let line = preferred ?? pickPlayableLine(lines) else {
      throw AppApiError.api("这些线路当前都播不了，换一条再投。")
    }
    let target = line.kind == .official ? try await Source1Service.shared.resolveOfficialLine(line) : line.url
    let format = formatOf(target)
    if line.kind == .m3u8 || line.kind == .official {
      return CastSource(url: target, format: format, direct: true, lineName: line.name, dispose: {})
    }
    let headers = line.kind == .signed ? s1SignedHeaders : s1Headers
    let sign = (line.kind == .signed && !(line.wsKey ?? "").isEmpty) ? SignRule(key: line.wsKey!, extra: line.ck ?? "") : nil
    return try openCastProxy(target: target, headers: headers, sign: sign, inheritQuery: false, format: format, lineName: line.name)
  }

  static func castSourceForSource2Candidate(_ item: [String: Any]) throws -> CastSource {
    let url = String(describing: item["playUrl"] ?? "")
    guard !url.isEmpty, url != "<null>" else {
      throw AppApiError.api("这条清晰度没有播放地址")
    }
    let format = formatOf(url)
    let lineName = String(describing: item["qualityName"] ?? item["src"] ?? "默认线路")
    return try openCastProxy(target: url, headers: s2Headers, sign: nil, inheritQuery: format == .hls, format: format, lineName: lineName)
  }

  private static func openCastProxy(
    target: String,
    headers: [String: String],
    sign: SignRule?,
    inheritQuery: Bool,
    format: StreamFormat,
    lineName: String
  ) throws -> CastSource {
    let lan = MediaServer.lanAddress()
    guard !lan.isEmpty else {
      throw AppApiError.network("这台设备现在没有局域网地址（多半是没连 Wi-Fi），电视取不到转发的流。\n请让手机和电视连同一个 Wi-Fi 再试。")
    }
    let sid = try MediaServer.shared.openSession(headers: headers, sign: sign, inheritQuery: inheritQuery, forCast: true)
    let castUrl = MediaServer.shared.streamUrl(sid: sid, upstreamUrl: target, format: format, forCast: true)
    return CastSource(url: castUrl, format: format, direct: false, lineName: lineName, dispose: {
      MediaServer.shared.closeSession(sid)
    })
  }
}
