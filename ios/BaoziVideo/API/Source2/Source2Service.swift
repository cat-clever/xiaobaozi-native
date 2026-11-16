import Foundation

actor Source2Service {
  static let shared = Source2Service()

  private var cachedUid: String?

  private func deviceUid() -> String {
    if let u = cachedUid { return u }
    let u = StorageManager.shared.getDeviceUid()
    cachedUid = u
    return u
  }

  private func makeClient() -> Source2Client {
    Source2Client(deviceUid: deviceUid(), randomUid: false)
  }

  func homeFeed(page: Int = 1) async throws -> [HomeBlock] {
    try await makeClient().recommendBlocks(page: page)
  }

  func listByCategory(query: CategoryQuery) async throws -> [VodItem] {
    let res = try await makeClient().catePage(
      stype: query.stype,
      cid: query.cid,
      year: query.year,
      sort: query.sort,
      page: query.page
    )
    return res.items
  }

  func categoryTree() async throws -> Source2Client.CateResult {
    try await makeClient().catePage()
  }

  func search(keyword: String) async throws -> [VodItem] {
    try await makeClient().searchPage(keyword: keyword)
  }

  func detail(sid: String) async throws -> VodDetail {
    try await makeClient().detailPage(sid: sid.replacingOccurrences(of: "s2_", with: ""))
  }

  func playFor(sid: String, episode: Episode, quality: Int? = nil) async throws -> [[String: Any]] {
    let client = makeClient()
    let pid = episode.pid ?? ""
    if pid.isEmpty { return [] }

    let items = try await client.playUrl(pid: pid, scid: nil, sq: quality)
    if !items.isEmpty {
      StorageManager.shared.notePlayNode(hasHttps: Source2Client.hasHttpsPlay(items))
    }
    if Source2Client.hasHttpsPlay(items) { return items }

    var best: [[String: Any]]? = items.isEmpty ? nil : items
    for uid in Source2Client.sampleUids(Source2Constants.goodUidTries, exclude: client.deviceUid) {
      let alt = Source2Client(deviceUid: uid, randomUid: false)
      if let t = try? await alt.playUrl(pid: pid, scid: nil, sq: quality) {
        if Source2Client.hasHttpsPlay(t) { return t }
        if best == nil && !t.isEmpty { best = t }
      }
    }
    return best ?? items
  }
}
