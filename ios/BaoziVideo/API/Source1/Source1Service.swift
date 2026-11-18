import Foundation

struct Source1CategoryTree {
  struct Dim {
    let type: [String]
    let tagType: [[String]]
    let area: [String]
    let year: [String]
    let sort: [String]
  }
  let categories: [(label: String, value: Int)]
  let dims: [Int: Dim]
}

actor Source1Service {
  static let shared = Source1Service()

  private var sharedClient: Source1Client?
  private var playClients: [MahaoId: Source1Client] = [:]

  static func absolutePic(_ raw: Any?) -> String {
    let url = String(describing: raw ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
    if url.isEmpty || url == "<null>" { return "" }
    if url.lowercased().hasPrefix("http://") || url.lowercased().hasPrefix("https://") {
      return url
    }
    let clean = url.replacingOccurrences(of: "^/+", with: "", options: .regularExpression)
    return Source1Constants.imageBase + clean
  }

  static func toVodItem(_ raw: [String: Any]) -> VodItem {
    VodItem(
      id: "s1_\(String(describing: raw["id"] ?? ""))",
      sourceId: .s1,
      title: String(describing: raw["vod_name"] ?? ""),
      coverUrl: absolutePic(raw["vod_pic"]),
      rating: String(describing: raw["vod_douban_score"] ?? ""),
      category: String(describing: raw["vod_tag"] ?? ""),
      year: String(describing: raw["vod_year"] ?? ""),
      area: String(describing: raw["vod_area"] ?? ""),
      remarks: String(describing: raw["remarks"] ?? ""),
      intro: String(describing: raw["vod_blurb"] ?? "")
    )
  }

  private func getClient() async throws -> Source1Client {
    if let existing = sharedClient { return existing }
    var errors: [String] = []
    for mahao in Source1Constants.autoChain {
      let client = Source1Client(mahao: mahao)
      do {
        try await client.initialize()
        sharedClient = client
        playClients[mahao] = client
        return client
      } catch {
        errors.append("\(client.entry.label): \(error.localizedDescription)")
      }
    }
    throw AppApiError.allDown("\(Source1Constants.allDownHint)（\(errors.joined(separator: "；"))）")
  }

  func homeFeed(page: Int = 1) async throws -> [HomeBlock] {
    let client = try await getClient()
    let list = try await client.screen(pn: page, sort: "日榜")
    let items = list.map(Self.toVodItem).filter { !$0.title.trimmingCharacters(in: .whitespaces).isEmpty }
    return items.isEmpty ? [] : [HomeBlock(title: "热播推荐", items: items)]
  }

  func listByCategory(query: CategoryQuery) async throws -> [VodItem] {
    let client = try await getClient()
    let list = try await client.screen(
      pn: query.page,
      typeId: query.typeId,
      typeValue: query.typeValue,
      tagType: query.tagType,
      area: query.area,
      year: query.year,
      sort: query.sort
    )
    return list.map(Self.toVodItem).filter { !$0.title.trimmingCharacters(in: .whitespaces).isEmpty }
  }

  func categoryTree() async throws -> Source1CategoryTree {
    let client = try await getClient()
    let list = try await client.types()

    var categories: [(label: String, value: Int)] = []
    var dims: [Int: Source1CategoryTree.Dim] = [:]

    for item in list {
      let id = (item["id"] as? NSNumber)?.intValue ?? (item["type_id"] as? NSNumber)?.intValue ?? 0
      if id == 0 { continue }
      let label = String(describing: item["name"] ?? item["type_name"] ?? "分类\(id)")
      categories.append((label: label, value: id))

      var typeArr: [String] = []
      var tagTypeArr: [[String]] = []
      var areaArr: [String] = []
      var yearArr: [String] = []
      var sortArr: [String] = []

      if let msgs = item["msg"] as? [[String: Any]] {
        for msg in msgs {
          let name = String(describing: msg["name"] ?? "")
          switch name {
          case "type": typeArr = (msg["data"] as? [String]) ?? []
          case "tag_type": tagTypeArr = (msg["data"] as? [[String]]) ?? []
          case "area": areaArr = (msg["data"] as? [String]) ?? []
          case "year": yearArr = (msg["data"] as? [String]) ?? []
          case "sort": sortArr = (msg["data"] as? [String]) ?? []
          default: break
          }
        }
      }
      dims[id] = Source1CategoryTree.Dim(type: typeArr, tagType: tagTypeArr, area: areaArr, year: yearArr, sort: sortArr)
    }

    return Source1CategoryTree(categories: categories, dims: dims)
  }

  nonisolated static func filterRowsFor(tree: Source1CategoryTree, typeId: Int, genre: String) -> [FilterRow] {
    guard let dim = tree.dims[typeId] else { return [] }
    let noneLabels: Set<String> = ["全部", "排序"]
    func toOptions(_ values: [String]) -> [FilterOption] {
      values.filter { !$0.isEmpty }.map { v in
        FilterOption(label: v, value: noneLabels.contains(v) ? "" : v)
      }
    }

    var rows: [FilterRow] = []
    func push(_ title: String, _ values: [String]) {
      let opts = toOptions(values)
      if !opts.isEmpty { rows.append(FilterRow(title: title, options: opts)) }
    }

    push("题材", dim.type)
    push("地区", dim.area)
    push("年份", dim.year)
    push("排序", dim.sort)

    if let idx = dim.type.firstIndex(of: genre), idx < dim.tagType.count {
      push("标签", dim.tagType[idx])
    }
    return rows
  }

  func search(keyword: String, page: Int = 1) async throws -> [VodItem] {
    let client = try await getClient()
    let list = try await client.search(keyword: keyword, pn: page)
    return list.map(Self.toVodItem).filter { !$0.title.trimmingCharacters(in: .whitespaces).isEmpty }
  }

  func hotSearch() async -> [String] {
    do {
      let client = try await getClient()
      let list = try await client.hotSearch()
      return list.compactMap { row in
        let s = String(describing: row["name"] ?? row["word"] ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        return s.isEmpty || s == "<null>" ? nil : s
      }
    } catch {
      return []
    }
  }

  func detail(vodId: String) async throws -> VodDetail {
    let client = try await getClient()
    let idNum = Int(vodId.replacingOccurrences(of: "s1_", with: "")) ?? 0

    var raw = try await client.detail(vodId: idNum)
    var collection: [String: Any] = [:]
    if let col = try? await client.collectionList(vodId: idNum) {
      collection = col
    }
    if raw.isEmpty && !collection.isEmpty {
      raw = collection
    }

    let eps = Source1Play.mergeDurations(
      eps: Source1Play.episodes(raw),
      lengths: Source1Play.episodes(collection)
    )

    return VodDetail(
      vodId: String(idNum),
      sourceId: .s1,
      name: String(describing: raw["vod_name"] ?? ""),
      pic: Self.absolutePic(raw["vod_pic"] ?? raw["coverUrl"]),
      actor: String(describing: raw["vod_actor"] ?? ""),
      director: String(describing: raw["vod_director"] ?? ""),
      year: String(describing: raw["vod_year"] ?? ""),
      area: String(describing: raw["vod_area"] ?? ""),
      blurb: String(describing: raw["vod_blurb"] ?? ""),
      episodes: eps,
      qualities: [],
      seasons: []
    )
  }

  private func episodeBeanFor(client: Source1Client, vodId: Int, episode: Episode) async -> [String: Any]? {
    let wanted = episode.episodeId ?? ""
    func pick(_ list: [[String: Any]]) -> [String: Any]? {
      if !wanted.isEmpty {
        return list.first { String(describing: $0["id"] ?? "") == wanted }
      }
      return episode.episodeIndex < list.count ? list[episode.episodeIndex] : nil
    }

    var bean: [String: Any]?
    if let d = try? await client.detail(vodId: vodId) {
      bean = pick(Source1Play.episodes(d))
    }
    if let b = bean, let vt = b["vod_token"] as? String, !vt.isEmpty {
      return b
    }
    if let cl = try? await client.collectionList(vodId: vodId),
       var alt = pick(Source1Play.episodes(cl)) {
      if let b = bean {
        if (alt["vod_token"] as? String ?? "").isEmpty { alt["vod_token"] = b["vod_token"] }
        if (alt["cur_time"] as? String ?? "").isEmpty { alt["cur_time"] = b["cur_time"] }
      }
      bean = alt
    }
    return bean
  }

  func playLinesFor(vodId: String, episode: Episode) async throws -> [PlayLine] {
    let idNum = Int(vodId.replacingOccurrences(of: "s1_", with: "")) ?? 0
    guard let collectionId = Int(episode.episodeId ?? ""), collectionId > 0 else {
      throw AppApiError.api("这一集没有拿到上游选集 id，无法取线路（详情需要重新加载）")
    }

    var errors: [String] = []
    for mahao in Source1Constants.playChain {
      let client: Source1Client
      let cached = playClients[mahao]
      if let cached {
        client = cached
      } else {
        client = Source1Client(mahao: mahao)
      }
      do {
        if cached == nil {
          try await client.initialize()
          playClients[mahao] = client
        }
        guard let bean = await episodeBeanFor(client: client, vodId: idNum, episode: episode),
              let token = bean["vod_token"] as? String, !token.isEmpty else {
          playClients.removeValue(forKey: mahao)
          errors.append("\(client.entry.label)：这个身份签不出 vod_token")
          continue
        }
        let colResp = try await client.collection(
          vodId: idNum,
          collectionId: collectionId,
          vodToken: token,
          curTime: String(describing: bean["cur_time"] ?? "")
        )
        let lines = Source1Play.playLines(bean: colResp, secDomain: client.secDomain, wsKey: client.wsKey)
        if Source1Play.hasPlayableLine(lines) { return lines }
        errors.append("\(client.entry.label)：\(lines.isEmpty ? "上游没回线路" : "仅下发占位或不可播线路")")
      } catch {
        playClients.removeValue(forKey: mahao)
        errors.append("\(client.entry.label)：\(error.localizedDescription)")
      }
    }
    throw AppApiError.allDown("取不到线路：\(errors.joined(separator: "；"))")
  }

  func resolveOfficialLine(_ line: PlayLine) async throws -> String {
    guard let prefix = line.parsePrefix, !prefix.isEmpty else {
      throw AppApiError.api("这条官方线路没带解析服务")
    }
    let client = try await getClient()
    return try await client.resolve(prefix: prefix, target: line.url)
  }
}
