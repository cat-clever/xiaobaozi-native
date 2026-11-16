import Foundation

final class Source2Client {
  let deviceUid: String
  let randomUid: Bool

  init(deviceUid: String? = nil, randomUid: Bool = true) {
    self.deviceUid = (deviceUid?.isEmpty == false) ? deviceUid! : Source2Constants.defaultDeviceUid
    self.randomUid = randomUid
  }

  func pickUid() -> String {
    randomUid ? Source2Crypto.randUid(Source2Constants.randChars) : deviceUid
  }

  func udidOf(_ uid: String) -> String {
    CryptoUtils.md5Hex(uid)
  }

  func headers(uid: String) -> [String: String] {
    [
      "vc": Source2Constants.vc,
      "vn": Source2Constants.vn,
      "ch": Source2Constants.ch,
      "app": Source2Constants.app,
      "User-Agent": Source2Constants.ua,
      "said": Source2Constants.said,
      "auth-token": "",
      "auth-uid": "",
      "sign": Source2Crypto.makeSignHeader(uid),
      "uk": Source2Crypto.makeUk(uid),
    ]
  }

  static func stripHtml(_ s: Any?) -> String {
    let str = String(describing: s ?? "")
    if s == nil || str == "<null>" { return "" }
    return str.replacingOccurrences(of: "<[^>]*>", with: "", options: .regularExpression)
      .trimmingCharacters(in: .whitespacesAndNewlines)
  }

  static func seriesBrief(_ s: [String: Any]) -> VodItem {
    let image = (s["image"] as? [String: Any]) ?? [:]
    let cate = (s["relatedCateInfo"] as? [String: Any]) ?? [:]
    let published = (s["publishTime"] as? NSNumber)?.doubleValue ?? 0
    let yearStr: String
    if published > 0 {
      let date = Date(timeIntervalSince1970: published / 1000.0)
      yearStr = String(Calendar.current.component(.year, from: date))
    } else {
      yearStr = String(describing: s["year"] ?? "")
    }

    return VodItem(
      id: "s2_\(String(describing: s["sid"] ?? ""))",
      sourceId: .s2,
      title: String(describing: s["name"] ?? s["title"] ?? ""),
      coverUrl: String(describing: image["thumb"] ?? image["poster"] ?? ""),
      rating: String(describing: s["score"] ?? ""),
      category: String(describing: cate["name"] ?? ""),
      year: yearStr == "<null>" ? "" : yearStr,
      area: String(describing: s["area"] ?? ""),
      remarks: String(describing: s["conerMemo"] ?? s["detailMemo"] ?? s["memo"] ?? ""),
      intro: stripHtml(s["shorthand"] ?? s["intro"])
    )
  }

  func get(path: String, params: [String: Any] = [:], uid: String? = nil) async throws -> (json: [String: Any], uid: String) {
    let u = uid ?? pickUid()
    let cleanPath = path.replacingOccurrences(of: "^/+", with: "", options: .regularExpression)
    var comps = URLComponents(string: "\(Source2Constants.base)/\(cleanPath)")!
    if !params.isEmpty {
      comps.queryItems = params.compactMap { k, v in
        let s = String(describing: v)
        if s.isEmpty || s == "<null>" { return nil }
        return URLQueryItem(name: k, value: s)
      }
    }
    let resp = try await HttpClient.request(url: comps.url!.absoluteString, headers: headers(uid: u), timeoutSec: 15)
    if resp.status < 200 || resp.status >= 300 {
      throw AppApiError.network("上游 HTTP \(resp.status)")
    }
    if resp.text.isEmpty {
      throw AppApiError.api("上游返回空（路径可能不存在）")
    }
    guard let data = resp.text.data(using: .utf8),
          let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
      throw AppApiError.api("上游返回的不是 JSON: \(resp.text.prefix(200))")
    }
    return (obj, u)
  }

  func post(path: String, payload: Any, uid: String? = nil) async throws -> (json: [String: Any], uid: String) {
    let u = uid ?? pickUid()
    let cleanPath = path.replacingOccurrences(of: "^/+", with: "", options: .regularExpression)
    var hdrs = headers(uid: u)
    hdrs["Content-Type"] = "application/json"
    let body = try JSONSerialization.data(withJSONObject: payload)
    let resp = try await HttpClient.request(
      url: "\(Source2Constants.base)/\(cleanPath)",
      method: "POST",
      headers: hdrs,
      body: body,
      timeoutSec: 15
    )
    if resp.status < 200 || resp.status >= 300 {
      throw AppApiError.network("上游 HTTP \(resp.status)")
    }
    guard let data = resp.text.data(using: .utf8),
          let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
      throw AppApiError.api("上游返回的不是 JSON: \(resp.text.prefix(200))")
    }
    return (obj, u)
  }

  func getDecrypted(path: String, params: [String: Any] = [:]) async throws -> [String: Any] {
    let (resp, u) = try await get(path: path, params: params)
    if let dataStr = resp["data"] as? String, !dataStr.isEmpty {
      let ts = (resp["ts"] as? NSNumber)?.int64Value
      if let plain = try? Source2Crypto.decrypt(dataB64: dataStr, deviceUid: u, ts: ts),
         let pData = plain.data(using: .utf8),
         let obj = try? JSONSerialization.jsonObject(with: pData) as? [String: Any] {
        return obj
      }
    }
    return resp
  }

  func recommendBlocks(page: Int = 1) async throws -> [HomeBlock] {
    let d = try await getDecrypted(path: "api/index/recommend_v5", params: ["page": page])
    let blocks = (d["mediaBlocks"] as? [[String: Any]]) ?? []
    var out: [HomeBlock] = []
    for blk in blocks {
      let series = (blk["seriesList"] as? [[String: Any]]) ?? []
      let items = series.filter { $0["sid"] != nil }.map(Self.seriesBrief)
      if !items.isEmpty {
        out.append(HomeBlock(title: String(describing: blk["title"] ?? ""), items: items))
      }
    }
    return out
  }

  struct CateResult {
    let groups: [(label: String, value: Int, cates: [FilterOption])]
    let sorts: [FilterOption]
    let years: [FilterOption]
    let items: [VodItem]
  }

  func catePage(stype: Int? = nil, cid: Int? = nil, year: String? = nil, sort: String? = nil, page: Int = 1) async throws -> CateResult {
    var p: [String: Any] = ["page": page]
    if let stype = stype { p["stype"] = stype }
    if let cid = cid { p["cid"] = cid }
    if let year = year, !year.isEmpty { p["year"] = year }
    if let sort = sort, !sort.isEmpty { p["sort"] = sort }

    let d = try await getDecrypted(path: "api/series2/arrange/cate", params: p)

    func toOpts(_ rows: [[String: Any]]) -> [FilterOption] {
      rows.compactMap { o in
        let label = String(describing: o["name"] ?? "")
        let val = String(describing: o["value"] ?? "")
        return label.isEmpty ? nil : FilterOption(label: label, value: val)
      }
    }

    let groups = ((d["groups"] as? [[String: Any]]) ?? []).map { g in
      let label = String(describing: g["name"] ?? "")
      let value = (g["stype"] as? NSNumber)?.intValue ?? 0
      let cates = toOpts((g["cates"] as? [[String: Any]]) ?? [])
      return (label: label, value: value, cates: cates)
    }

    let sorts = toOpts((d["sorts"] as? [[String: Any]]) ?? [])
    let years = toOpts((d["years"] as? [[String: Any]]) ?? [])
    let items = ((d["seriesList"] as? [[String: Any]]) ?? []).filter { $0["sid"] != nil }.map(Self.seriesBrief)

    return CateResult(groups: groups, sorts: sorts, years: years, items: items)
  }

  func searchPage(keyword: String) async throws -> [VodItem] {
    let d = try await getDecrypted(path: "api/search/s4", params: ["k": keyword, "srefer": "search"])
    return ((d["seriesList"] as? [[String: Any]]) ?? []).filter { $0["sid"] != nil }.map(Self.seriesBrief)
  }

  func detailNormal(sid: String) async throws -> [String: Any] {
    try await getDecrypted(path: "api/series2/detail/normal", params: ["sid": sid])
  }

  func detailPage(sid: String) async throws -> VodDetail {
    let d = try await detailNormal(sid: sid)
    let s = (d["series"] as? [String: Any]) ?? [:]
    let img = (s["image"] as? [String: Any]) ?? [:]

    let playItems = ((d["playItems"] as? [[String: Any]]) ?? []).filter { $0["pid"] != nil }
    let episodes: [Episode] = playItems.enumerated().map { index, it in
      Episode(
        episodeIndex: index,
        episodeId: nil,
        title: CryptoUtils.episodeTitle(it["title"], index: index),
        vodToken: nil,
        curTime: nil,
        duration: String(describing: it["duration"] ?? ""),
        durationSec: 0,
        pid: String(describing: it["pid"] ?? ""),
        scid: nil
      )
    }

    let rawQualities = (s["scopeQualities"] as? [[String: Any]]) ?? []
    let freeQualities = rawQualities.filter { (($0["vtype"] as? NSNumber)?.intValue ?? 0) != 1 }
    let qSource = freeQualities.isEmpty ? rawQualities : freeQualities
    let qualities: [QualityOption] = qSource.compactMap { q in
      let label = [String(describing: q["name"] ?? ""), String(describing: q["resolution"] ?? "")]
        .filter { !$0.isEmpty && $0 != "<null>" }
        .joined(separator: " ")
      let val = (q["value"] as? NSNumber)?.intValue ?? 0
      return label.isEmpty ? nil : QualityOption(label: label, value: val)
    }

    let seasons: [SeasonOption] = ((d["buckets"] as? [[String: Any]]) ?? [])
      .filter { $0["sid"] != nil }
      .map { b in
        SeasonOption(sid: String(describing: b["sid"] ?? ""), title: String(describing: b["title"] ?? ""))
      }

    return VodDetail(
      vodId: String(describing: s["sid"] ?? sid),
      sourceId: .s2,
      name: String(describing: s["name"] ?? ""),
      pic: String(describing: img["thumb"] ?? img["poster"] ?? ""),
      actor: String(describing: s["crew"] ?? ""),
      director: "",
      year: "",
      area: "",
      blurb: Self.stripHtml(s["intro"]),
      episodes: episodes,
      qualities: qualities,
      seasons: seasons
    )
  }

  func pickSource(pid: String) async throws -> String? {
    let d = try await getDecrypted(path: "api/series2/episode/detail", params: ["pid": pid, "refer": ""])
    let pi = (d["playItem"] as? [String: Any]) ?? [:]
    for s in (pi["sources"] as? [[String: Any]]) ?? [] {
      if let scid = s["scid"] {
        let str = String(describing: scid)
        if !str.isEmpty, str != "<null>" { return str }
      }
    }
    return nil
  }

  func resolveV4(pid: String, scid: String, uid: String? = nil, sq: Int? = nil, ttk: String? = nil) async throws -> (json: [String: Any], uid: String) {
    let t = String(Int64(Date().timeIntervalSince1970))
    let u = uid ?? pickUid()
    var kv: [String: String] = [
      "t": t,
      "dt": "android",
      "version": Source2Constants.vn,
      "uuid": Source2Crypto.randUid("0123456789abcdef", length: 32),
      "udid": udidOf(u),
      "pid": pid,
      "scid": scid,
      "re": "0",
    ]
    if let sq = sq { kv["sq"] = String(sq) }
    if let ttk = ttk, !ttk.isEmpty { kv["ttk"] = ttk }

    var params: [String: Any] = [:]
    for (k, v) in kv where k != "udid" { params[k] = v }
    params["sign"] = Source2Crypto.makeSign(kv)
    return try await get(path: "api/series/rslvV4", params: params, uid: u)
  }

  func rewardToken(pid: String, scene: String = Source2Constants.sceneReward, uid: String) async throws -> String? {
    let (r1, _) = try await get(path: "api/carp/reward/v2", params: ["scene": scene], uid: uid)
    guard let d1 = r1["data"] as? String, !d1.isEmpty else { return nil }
    let ts1 = (r1["ts"] as? NSNumber)?.int64Value
    let plain1 = try Source2Crypto.decrypt(dataB64: d1, deviceUid: uid, ts: ts1)
    guard let obj1 = try? JSONSerialization.jsonObject(with: Data(plain1.utf8)) as? [String: Any],
          let traceId = obj1["traceId"] as? String, !traceId.isEmpty else {
      return nil
    }

    let bodyObj: [String: Any] = ["pid": pid, "scene": scene, "traceId": traceId]
    let bodyJson = String(data: try JSONSerialization.data(withJSONObject: bodyObj), encoding: .utf8) ?? "{}"
    let (r2, _) = try await post(path: "api/carp/reward/rp/v2", payload: ["data": Source2Crypto.aesD(bodyJson, deviceUid: uid)], uid: uid)
    guard let d2 = r2["data"] as? String, !d2.isEmpty else { return nil }
    let ts2 = (r2["ts"] as? NSNumber)?.int64Value
    let plain2 = try Source2Crypto.decrypt(dataB64: d2, deviceUid: uid, ts: ts2)
    guard let obj2 = try? JSONSerialization.jsonObject(with: Data(plain2.utf8)) as? [String: Any],
          let info = obj2["rewardTokenInfo"] as? [String: Any] else {
      return nil
    }
    return info["token"] as? String
  }

  func playUrl(pid: String, scid: String?, sq: Int? = nil) async throws -> [[String: Any]] {
    let source: String
    if let s = scid, !s.isEmpty {
      source = s
    } else if let picked = try await pickSource(pid: pid) {
      source = picked
    } else {
      return []
    }

    var (resp, usedUid) = try await resolveV4(pid: pid, scid: source, sq: sq)
    let rescode = (resp["rescode"] as? NSNumber)?.intValue ?? 0
    if rescode == 10401 {
      let uid = pickUid()
      if let ttk = try? await rewardToken(pid: pid, uid: uid), !ttk.isEmpty {
        (resp, usedUid) = try await resolveV4(pid: pid, scid: source, uid: uid, sq: sq, ttk: ttk)
      }
    }

    var out: [[String: Any]] = []
    for item in (resp["datas"] as? [[String: Any]]) ?? [] {
      guard let dataB64 = item["data"] as? String, !dataB64.isEmpty else { continue }
      let plain = try Source2Crypto.decrypt(dataB64: dataB64, deviceUid: usedUid, ts: nil)
      if var info = try? JSONSerialization.jsonObject(with: Data(plain.utf8)) as? [String: Any] {
        info["qualityName"] = resp["qualityName"]
        out.append(info)
      }
    }
    return out
  }

  static func hasHttpsPlay(_ items: [[String: Any]]) -> Bool {
    items.contains { String(describing: $0["playUrl"] ?? "").lowercased().hasPrefix("https://") }
  }

  static func sampleUids(_ count: Int, exclude: String) -> [String] {
    Array(Source2Constants.goodUids.filter { $0 != exclude }.shuffled().prefix(count))
  }
}
