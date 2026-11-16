import SwiftUI

struct HomeView: View {
  let onOpen: (VodItem) -> Void
  let onOpenSettings: () -> Void

  @State private var s1Blocks: [HomeBlock] = []
  @State private var s2Blocks: [HomeBlock] = []
  @State private var errors: [String] = []
  @State private var loading = false

  var body: some View {
    GeometryReader { geo in
      let cols: CGFloat = geo.size.width > 600 ? 6 : 3
      let cardWidth = (geo.size.width - 32 - 12 * (cols - 1)) / cols
      let rowHeight = cardWidth * 1.42 + 26

      ScrollView {
        VStack(alignment: .leading, spacing: 20) {
          HStack {
            Text("小包子影视")
              .font(.system(size: 26, weight: .bold))
            Spacer()
            Button(action: onOpenSettings) {
              Image(systemName: "gearshape.fill")
                .font(.system(size: 18))
                .padding(8)
            }
            .glassCard(cornerRadius: 20)
          }
          .padding(.horizontal, 16)
          .padding(.top, 8)

          ForEach(errors, id: \.self) { err in
            Text(err)
              .font(.caption)
              .foregroundColor(.red)
              .padding(.horizontal, 16)
          }

          let allBlocks = s1Blocks + s2Blocks
          if allBlocks.isEmpty && loading {
            VStack(spacing: 16) {
              ProgressView("正在加载双源推荐…")
                .padding(.top, 80)
            }
            .frame(maxWidth: .infinity)
          } else {
            ForEach(allBlocks) { block in
              VStack(alignment: .leading, spacing: 10) {
                Text(block.title)
                  .font(.system(size: 17, weight: .bold))
                  .padding(.horizontal, 16)

                let rows = [
                  GridItem(.fixed(rowHeight), spacing: 12),
                  GridItem(.fixed(rowHeight), spacing: 12),
                ]
                ScrollView(.horizontal, showsIndicators: false) {
                  LazyHGrid(rows: rows, spacing: 12) {
                    ForEach(block.items) { item in
                      MediaCardView(item: item, width: cardWidth, onTap: onOpen)
                    }
                  }
                  .padding(.horizontal, 16)
                }
              }
            }
          }
        }
        .padding(.bottom, 24)
      }
      .refreshable {
        await load(force: true)
      }
      .task {
        await load(force: false)
      }
    }
  }

  private func load(force: Bool) async {
    if !force {
      if let c1 = await Aggregator.shared.peekHomeBlocks(sourceId: .s1) { s1Blocks = c1 }
      if let c2 = await Aggregator.shared.peekHomeBlocks(sourceId: .s2) { s2Blocks = c2 }
      if !s1Blocks.isEmpty && !s2Blocks.isEmpty { return }
    } else {
      await Aggregator.shared.clearHomeCache()
    }

    loading = true
    errors = []

    await withTaskGroup(of: Void.self) { group in
      group.addTask {
        do {
          let b = try await Aggregator.shared.homeBlocksOf(sourceId: .s1, force: force)
          await MainActor.run { s1Blocks = b }
        } catch {
          await MainActor.run { errors.append("全网源：\(error.localizedDescription)") }
        }
      }
      group.addTask {
        do {
          let b = try await Aggregator.shared.homeBlocksOf(sourceId: .s2, force: force)
          await MainActor.run { s2Blocks = b }
        } catch {
          await MainActor.run { errors.append("精选源：\(error.localizedDescription)") }
        }
      }
    }
    loading = false
  }
}
