import SwiftUI

struct MainTabView: View {
  @EnvironmentObject var theme: ThemeManager
  @State private var selectedTab = 0
  @State private var activePlayerItem: VodItem?
  @State private var showSettings = false

  var body: some View {
    ZStack {
      TabView(selection: $selectedTab) {
        HomeView(
          onOpen: { activePlayerItem = $0 },
          onOpenSettings: { showSettings = true }
        )
        .tabItem {
          Label("首页", systemImage: "house.fill")
        }
        .tag(0)

        CategoryView(onOpen: { activePlayerItem = $0 })
          .tabItem {
            Label("分类", systemImage: "square.grid.2x2.fill")
          }
          .tag(1)

        SearchView(onOpen: { activePlayerItem = $0 })
          .tabItem {
            Label("搜索", systemImage: "magnifyingglass")
          }
          .tag(2)
      }
      .tint(AppColors.accent)
      .onChange(of: theme.glassEnabled) { _ in
        theme.applyBarAppearance()
      }
    }
    .fullScreenCover(item: $activePlayerItem) { item in
      PlayerView(item: item, initialEpisodeIndex: 0) {
        activePlayerItem = nil
      }
    }
    .sheet(isPresented: $showSettings) {
      SettingsView()
    }
  }
}
