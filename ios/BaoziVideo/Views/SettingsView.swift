import SwiftUI

struct SettingsView: View {
  @EnvironmentObject var theme: ThemeManager
  @ObservedObject private var updater = UpdaterService.shared
  @Environment(\.dismiss) private var dismiss

  var body: some View {
    NavigationStack {
      List {
        Section("外观") {
          ForEach(ThemeMode.allCases, id: \.self) { mode in
            Button {
              theme.mode = mode
            } label: {
              HStack {
                VStack(alignment: .leading, spacing: 2) {
                  Text(mode.label)
                    .foregroundColor(.primary)
                  if !mode.hint.isEmpty {
                    Text(mode.hint)
                      .font(.caption)
                      .foregroundColor(.secondary)
                  }
                }
                Spacer()
                if theme.mode == mode {
                  Image(systemName: "checkmark")
                    .foregroundColor(AppColors.accent)
                }
              }
            }
          }

          Toggle("液态玻璃效果", isOn: $theme.glassEnabled)
            .tint(AppColors.accent)
        }

        Section("版本与更新") {
          HStack {
            Text("当前版本")
            Spacer()
            Text(updater.currentVersion.isEmpty ? "未知" : updater.currentVersion)
              .foregroundColor(.secondary)
          }

          Button {
            Task { await updater.checkForUpdate() }
          } label: {
            HStack {
              Text("检查更新")
              Spacer()
              if updater.phase == .checking {
                ProgressView()
              } else if updater.phase == .latest {
                Text("已是最新")
                  .font(.caption)
                  .foregroundColor(.secondary)
              } else if updater.phase == .available, let m = updater.manifest {
                Text("发现 v\(m.version)")
                  .font(.caption)
                  .foregroundColor(AppColors.accent)
              }
            }
          }

          if let err = updater.errorMessage {
            Text(err)
              .font(.caption)
              .foregroundColor(.red)
          }

          if updater.phase == .available, let m = updater.manifest {
            VStack(alignment: .leading, spacing: 6) {
              Text("v\(m.version) 更新内容")
                .font(.subheadline.bold())
              ForEach(m.notes, id: \.self) { note in
                Text("• \(note)")
                  .font(.caption)
                  .foregroundColor(.secondary)
              }
              HStack(spacing: 12) {
                Button("打开发布页") {
                  updater.openExternal(m.pageUrl.isEmpty ? UpdaterService.releasePageUrl : m.pageUrl)
                }
                .buttonStyle(.borderedProminent)
                .tint(AppColors.accent)

                Button("复制 SideStore 源") {
                  UIPasteboard.general.string = m.iosSourceUrl.isEmpty ? UpdaterService.sideStoreSourceUrl : m.iosSourceUrl
                }
                .buttonStyle(.bordered)
              }
              .padding(.top, 4)
            }
            .padding(.vertical, 4)
          }
        }

        Section("关于") {
          Text("小包子影视 · 纯原生 SwiftUI 版\n端内双源直连，遵循 GPLv3 开源协议。")
            .font(.caption)
            .foregroundColor(.secondary)
        }
      }
      .navigationTitle("设置")
      .toolbar {
        ToolbarItem(placement: .topBarTrailing) {
          Button("完成") { dismiss() }
        }
      }
      .task {
        if updater.phase == .idle {
          await updater.checkForUpdate()
        }
      }
    }
  }
}
