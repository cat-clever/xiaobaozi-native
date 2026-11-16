import SwiftUI

struct CastSheetView: View {
  let title: String
  let prepareCastSource: () async throws -> CastSource
  @Environment(\.dismiss) private var dismiss

  @State private var devices: [CastDevice] = []
  @State private var scanning = false
  @State private var activeDevice: CastDevice?
  @State private var activeSource: CastSource?
  @State private var transportState: CastTransportState?
  @State private var error: String?
  @State private var busy = false

  var body: some View {
    NavigationStack {
      VStack(spacing: 16) {
        if let device = activeDevice, let source = activeSource {
          VStack(spacing: 12) {
            Text("正在投屏到 \(device.name)")
              .font(.headline)
            Text("\(source.direct ? "直投模式（可息屏）" : "转发模式（请保持 App 在前台）") · \(source.lineName)")
              .font(.caption)
              .foregroundColor(.secondary)

            if let ts = transportState {
              Text("状态：\(ts.state.rawValue)")
                .font(.caption)
                .foregroundColor(AppColors.accent)
            }

            HStack(spacing: 24) {
              Button {
                Task { try? await DlnaClient.play(device: device); await refreshStatus() }
              } label: {
                Label("播放", systemImage: "play.fill")
              }
              .buttonStyle(.bordered)

              Button {
                Task { try? await DlnaClient.pause(device: device); await refreshStatus() }
              } label: {
                Label("暂停", systemImage: "pause.fill")
              }
              .buttonStyle(.bordered)

              Button(role: .destructive) {
                Task { await stopCast() }
              } label: {
                Label("退出投屏", systemImage: "stop.fill")
              }
              .buttonStyle(.borderedProminent)
            }
          }
          .padding()
          .glassCard()
          .padding(.horizontal)
        }

        if let error {
          Text(error)
            .font(.caption)
            .foregroundColor(.red)
            .padding(.horizontal)
        }

        List {
          Section {
            if scanning && devices.isEmpty {
              HStack {
                ProgressView()
                Text("正在搜索局域网 DLNA 设备…")
                  .foregroundColor(.secondary)
              }
            } else if devices.isEmpty {
              Text("未发现 DLNA 电视或盒子，请确认与手机在同一 Wi-Fi。")
                .foregroundColor(.secondary)
            } else {
              ForEach(devices) { dev in
                Button {
                  Task { await startCast(to: dev) }
                } label: {
                  HStack {
                    VStack(alignment: .leading) {
                      Text(dev.name).foregroundColor(.primary)
                      Text([dev.model, dev.address].filter { !$0.isEmpty }.joined(separator: " · "))
                        .font(.caption)
                        .foregroundColor(.secondary)
                    }
                    Spacer()
                    if busy && activeDevice?.id == dev.id {
                      ProgressView()
                    } else {
                      Image(systemName: "tv")
                        .foregroundColor(AppColors.accent)
                    }
                  }
                }
              }
            }
          } header: {
            HStack {
              Text("DLNA 设备")
              Spacer()
              Button("重新扫描") {
                Task { await scan() }
              }
              .font(.caption)
            }
          }
        }
      }
      .navigationTitle("投屏")
      .toolbar {
        ToolbarItem(placement: .topBarTrailing) {
          Button("关闭") { dismiss() }
        }
      }
      .task {
        await scan()
      }
    }
  }

  private func scan() async {
    scanning = true
    error = nil
    devices = await DlnaClient.discoverDevices()
    scanning = false
  }

  private func startCast(to device: CastDevice) async {
    busy = true
    error = nil
    do {
      activeSource?.dispose()
      let source = try await prepareCastSource()
      try await DlnaClient.cast(device: device, url: source.url, title: title)
      activeDevice = device
      activeSource = source
      await refreshStatus()
    } catch {
      self.error = error.localizedDescription
    }
    busy = false
  }

  private func stopCast() async {
    if let dev = activeDevice {
      try? await DlnaClient.stop(device: dev)
    }
    activeSource?.dispose()
    activeSource = nil
    activeDevice = nil
    transportState = nil
  }

  private func refreshStatus() async {
    guard let dev = activeDevice else { return }
    transportState = await DlnaClient.status(device: dev)
  }
}
