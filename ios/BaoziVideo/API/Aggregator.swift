import Foundation

actor Aggregator {
  static let shared = Aggregator()

  private var homeCache: [String: [HomeBlock]] = [:]

  func peekHomeBlocks(sourceId: SourceId, page: Int = 1) -> [HomeBlock]? {
    homeCache["\(sourceId.rawValue)|\(page)"]
  }

  func clearHomeCache() {
    homeCache.removeAll()
  }

  func homeBlocksOf(sourceId: SourceId, page: Int = 1, force: Bool = false) async throws -> [HomeBlock] {
    let key = "\(sourceId.rawValue)|\(page)"
    if !force, let hit = homeCache[key] {
      return hit
    }

    let blocks: [HomeBlock]
    switch sourceId {
    case .s1: blocks = try await Source1Service.shared.homeFeed(page: page)
    case .s2: blocks = try await Source2Service.shared.homeFeed(page: page)
    }

    let normalized = blocks.map { HomeBlock(title: "\(sourceId.label) · \($0.title)", items: $0.items) }
    if !normalized.isEmpty {
      homeCache[key] = normalized
    }
    return normalized
  }

  func searchAll(keyword: String) async -> (items: [VodItem], errors: [String]) {
    async let r1: Result<[VodItem], Error> = {
      do { return .success(try await Source1Service.shared.search(keyword: keyword)) }
      catch { return .failure(error) }
    }()
    async let r2: Result<[VodItem], Error> = {
      do { return .success(try await Source2Service.shared.search(keyword: keyword)) }
      catch { return .failure(error) }
    }()

    let (res1, res2) = await (r1, r2)
    var items: [VodItem] = []
    var errors: [String] = []

    switch res1 {
    case .success(let list): items.append(contentsOf: list)
    case .failure(let err): errors.append("全网源：\(err.localizedDescription)")
    }
    switch res2 {
    case .success(let list): items.append(contentsOf: list)
    case .failure(let err): errors.append("精选源：\(err.localizedDescription)")
    }

    return (items.filter { !$0.title.trimmingCharacters(in: .whitespaces).isEmpty }, errors)
  }

  func listByCategory(sourceId: SourceId, query: CategoryQuery) async throws -> [VodItem] {
    switch sourceId {
    case .s1: return try await Source1Service.shared.listByCategory(query: query)
    case .s2: return try await Source2Service.shared.listByCategory(query: query)
    }
  }

  func detail(sourceId: SourceId, id: String) async throws -> VodDetail {
    switch sourceId {
    case .s1: return try await Source1Service.shared.detail(vodId: id)
    case .s2: return try await Source2Service.shared.detail(sid: id)
    }
  }
}
