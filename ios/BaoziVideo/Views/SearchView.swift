import SwiftUI

struct SearchView: View {
  let onOpen: (VodItem) -> Void

  @State private var keyword = ""
  @State private var items: [VodItem] = []
  @State private var errors: [String] = []
  @State private var history: [String] = []
  @State private var hot: [String] = []
  @State private var loading = false

  var body: some View {
    GeometryReader { geo in
      let colsCount = geo.size.width > 600 ? 6 : 3
      let cardWidth = (geo.size.width - 32 - 12 * CGFloat(colsCount - 1)) / CGFloat(colsCount)
      let columns = Array(repeating: GridItem(.fixed(cardWidth), spacing: 12), count: colsCount)

      VStack(spacing: 12) {
        HStack(spacing: 10) {
          HStack {
            Image(systemName: "magnifyingglass")
              .foregroundColor(.secondary)
            TextField("搜剧名、演员…", text: $keyword)
              .submitLabel(.search)
              .onSubmit {
                Task { await runSearch(keyword) }
              }
            if !keyword.isEmpty {
              Button {
                keyword = ""
                items = []
                errors = []
              } label: {
                Image(systemName: "xmark.circle.fill")
                  .foregroundColor(.secondary)
              }
            }
          }
          .padding(.horizontal, 12)
          .padding(.vertical, 10)
          .glassCard(cornerRadius: 12)

          Button("搜索") {
            Task { await runSearch(keyword) }
          }
          .font(.system(size: 14, weight: .semibold))
          .foregroundColor(.black)
          .padding(.horizontal, 16)
          .padding(.vertical, 10)
          .background(AppColors.accent)
          .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)

        ForEach(errors, id: \.self) { err in
          Text(err)
            .font(.caption)
            .foregroundColor(.red)
            .padding(.horizontal, 16)
        }

        if loading {
          Spacer()
          ProgressView("正在双源搜索…")
          Spacer()
        } else if items.isEmpty {
          ScrollView {
            VStack(alignment: .leading, spacing: 16) {
              if !history.isEmpty {
                HStack {
                  Text("最近搜索")
                    .font(.headline)
                  Spacer()
                  Button("清空") {
                    StorageManager.shared.clearSearchHistory()
                    history = []
                  }
                  .font(.caption)
                  .foregroundColor(.secondary)
                }
                FlowChipsView(items: history) { kw in
                  keyword = kw
                  Task { await runSearch(kw) }
                }
              }

              if !hot.isEmpty {
                Text("热搜")
                  .font(.headline)
                FlowChipsView(items: Array(hot.prefix(20))) { kw in
                  keyword = kw
                  Task { await runSearch(kw) }
                }
              }
            }
            .padding(16)
          }
        } else {
          ScrollView {
            LazyVGrid(columns: columns, spacing: 14) {
              ForEach(items) { item in
                MediaCardView(item: item, width: cardWidth, onTap: onOpen)
              }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 24)
          }
        }
      }
      .task {
        history = StorageManager.shared.getSearchHistory()
        if hot.isEmpty {
          hot = await Source1Service.shared.hotSearch()
        }
      }
    }
  }

  private func runSearch(_ kw: String) async {
    let trimmed = kw.trimmingCharacters(in: .whitespacesAndNewlines)
    if trimmed.isEmpty { return }
    loading = true
    errors = []
    history = StorageManager.shared.pushSearchHistory(trimmed)
    let res = await Aggregator.shared.searchAll(keyword: trimmed)
    items = res.items
    errors = res.errors
    loading = false
  }
}

private struct FlowChipsView: View {
  let items: [String]
  let onTap: (String) -> Void

  var body: some View {
    ScrollView(.horizontal, showsIndicators: false) {
      HStack(spacing: 8) {
        ForEach(items, id: \.self) { item in
          Button {
            onTap(item)
          } label: {
            Text(item)
              .font(.system(size: 13))
              .padding(.horizontal, 12)
              .padding(.vertical, 7)
              .glassCard(cornerRadius: 16)
          }
          .buttonStyle(.plain)
        }
      }
    }
  }
}
