import SwiftUI

struct CategoryView: View {
  let onOpen: (VodItem) -> Void

  @State private var source: SourceId = .s1
  @State private var tree1: Source1CategoryTree?
  @State private var tree2: Source2Client.CateResult?

  // S1 filters
  @State private var s1TypeId: Int = 0
  @State private var s1Genre: String = ""
  @State private var s1Tag: String = ""
  @State private var s1Area: String = ""
  @State private var s1Year: String = ""
  @State private var s1Sort: String = ""

  // S2 filters
  @State private var s2Stype: Int? = nil
  @State private var s2Cid: Int? = nil
  @State private var s2Year: String = ""
  @State private var s2Sort: String = ""

  @State private var items: [VodItem] = []
  @State private var page = 1
  @State private var loading = false
  @State private var loadingMore = false
  @State private var reachedEnd = false
  @State private var error: String?

  var body: some View {
    GeometryReader { geo in
      let colsCount = geo.size.width > 600 ? 6 : 3
      let cardWidth = (geo.size.width - 32 - 12 * CGFloat(colsCount - 1)) / CGFloat(colsCount)
      let columns = Array(repeating: GridItem(.fixed(cardWidth), spacing: 12), count: colsCount)

      VStack(spacing: 0) {
        // 源切换
        Picker("片源", selection: $source) {
          Text("全网源").tag(SourceId.s1)
          Text("精选源").tag(SourceId.s2)
        }
        .pickerStyle(.segmented)
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .onChange(of: source) { _ in
          Task { await reloadFromScratch() }
        }

        ScrollView {
          VStack(alignment: .leading, spacing: 10) {
            filterSection
              .padding(.horizontal, 16)

            if let error {
              Text(error)
                .font(.caption)
                .foregroundColor(.red)
                .padding(.horizontal, 16)
            }

            if loading && items.isEmpty {
              ProgressView("加载中…")
                .frame(maxWidth: .infinity)
                .padding(.top, 40)
            } else {
              LazyVGrid(columns: columns, spacing: 14) {
                ForEach(items) { item in
                  MediaCardView(item: item, width: cardWidth, onTap: onOpen)
                    .onAppear {
                      if item.id == items.last?.id && !reachedEnd && !loadingMore {
                        Task { await loadNextPage() }
                      }
                    }
                }
              }
              .padding(.horizontal, 16)
              .padding(.top, 8)

              if loadingMore {
                ProgressView()
                  .frame(maxWidth: .infinity)
                  .padding(.vertical, 16)
              }
            }
          }
          .padding(.bottom, 24)
        }
        .refreshable {
          await reloadFromScratch()
        }
      }
      .task {
        await loadTreesIfNeeded()
        if items.isEmpty {
          await reloadFromScratch()
        }
      }
    }
  }

  @ViewBuilder
  private var filterSection: some View {
    if source == .s1, let tree1 {
      VStack(alignment: .leading, spacing: 8) {
        chipRow(title: "频道", items: tree1.categories.map { ($0.label, String($0.value)) }, selected: String(s1TypeId)) { val in
          s1TypeId = Int(val) ?? 0
          s1Genre = ""
          s1Tag = ""
          Task { await fetchPage(1) }
        }
        let rows = Source1Service.filterRowsFor(tree: tree1, typeId: s1TypeId, genre: s1Genre)
        ForEach(rows) { row in
          chipRow(
            title: row.title,
            items: row.options.map { ($0.label, $0.value) },
            selected: selectedS1Value(for: row.title)
          ) { val in
            updateS1Filter(title: row.title, value: val)
            Task { await fetchPage(1) }
          }
        }
      }
    } else if source == .s2, let tree2 {
      VStack(alignment: .leading, spacing: 8) {
        if !tree2.groups.isEmpty {
          chipRow(title: "分组", items: tree2.groups.map { ($0.label, String($0.value)) }, selected: s2Stype.map(String.init) ?? "") { val in
            s2Stype = Int(val)
            s2Cid = nil
            Task { await fetchPage(1) }
          }
          if let activeGroup = tree2.groups.first(where: { $0.value == s2Stype }) ?? tree2.groups.first {
            chipRow(title: "分类", items: activeGroup.cates.map { ($0.label, $0.value) }, selected: s2Cid.map(String.init) ?? "") { val in
              s2Cid = Int(val)
              Task { await fetchPage(1) }
            }
          }
        }
        if !tree2.years.isEmpty {
          chipRow(title: "年份", items: tree2.years.map { ($0.label, $0.value) }, selected: s2Year) { val in
            s2Year = val
            Task { await fetchPage(1) }
          }
        }
        if !tree2.sorts.isEmpty {
          chipRow(title: "排序", items: tree2.sorts.map { ($0.label, $0.value) }, selected: s2Sort) { val in
            s2Sort = val
            Task { await fetchPage(1) }
          }
        }
      }
    }
  }

  private func chipRow(title: String, items: [(String, String)], selected: String, onSelect: @escaping (String) -> Void) -> some View {
    ScrollView(.horizontal, showsIndicators: false) {
      HStack(spacing: 8) {
        Text(title)
          .font(.caption)
          .foregroundColor(.secondary)
          .frame(width: 34, alignment: .leading)
        ForEach(items, id: \.0) { label, val in
          let active = val == selected
          Button {
            onSelect(val)
          } label: {
            Text(label)
              .font(.system(size: 12, weight: active ? .semibold : .regular))
              .foregroundColor(active ? .black : .primary)
              .padding(.horizontal, 10)
              .padding(.vertical, 5)
              .background(active ? AppColors.accent : Color.secondary.opacity(0.14))
              .clipShape(Capsule())
          }
          .buttonStyle(.plain)
        }
      }
    }
  }

  private func selectedS1Value(for title: String) -> String {
    switch title {
    case "题材": return s1Genre
    case "标签": return s1Tag
    case "地区": return s1Area
    case "年份": return s1Year
    case "排序": return s1Sort
    default: return ""
    }
  }

  private func updateS1Filter(title: String, value: String) {
    switch title {
    case "题材":
      s1Genre = value
      s1Tag = ""
    case "标签": s1Tag = value
    case "地区": s1Area = value
    case "年份": s1Year = value
    case "排序": s1Sort = value
    default: break
    }
  }

  private func loadTreesIfNeeded() async {
    if tree1 == nil {
      if let t1 = try? await Source1Service.shared.categoryTree() {
        tree1 = t1
        if s1TypeId == 0, let first = t1.categories.first {
          s1TypeId = first.value
        }
      }
    }
    if tree2 == nil {
      if let t2 = try? await Source2Service.shared.categoryTree() {
        tree2 = t2
        if s2Stype == nil, let first = t2.groups.first {
          s2Stype = first.value
        }
      }
    }
  }

  private func reloadFromScratch() async {
    await loadTreesIfNeeded()
    await fetchPage(1)
  }

  private func fetchPage(_ targetPage: Int) async {
    if targetPage == 1 {
      loading = true
      reachedEnd = false
    } else {
      loadingMore = true
    }
    error = nil
    page = targetPage

    var q = CategoryQuery(page: targetPage)
    if source == .s1 {
      q.typeId = s1TypeId
      q.typeValue = s1Genre
      q.tagType = s1Tag
      q.area = s1Area
      q.year = s1Year
      q.sort = s1Sort
    } else {
      q.stype = s2Stype
      q.cid = s2Cid
      q.year = s2Year
      q.sort = s2Sort
    }

    do {
      let list = try await Aggregator.shared.listByCategory(sourceId: source, query: q)
      if targetPage == 1 {
        items = list
      } else {
        let existingIds = Set(items.map(\.id))
        items.append(contentsOf: list.filter { !existingIds.contains($0.id) })
      }
      if list.isEmpty { reachedEnd = true }
    } catch {
      self.error = error.localizedDescription
    }
    loading = false
    loadingMore = false
  }

  private func loadNextPage() async {
    await fetchPage(page + 1)
  }
}
