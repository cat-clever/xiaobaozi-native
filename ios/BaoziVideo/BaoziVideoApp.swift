import AVFoundation
import SwiftUI

@main
struct BaoziVideoApp: App {
  @StateObject private var theme = ThemeManager.shared

  init() {
    try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback)
    try? AVAudioSession.sharedInstance().setActive(true)
  }

  var body: some Scene {
    WindowGroup {
      MainTabView()
        .environmentObject(theme)
        .preferredColorScheme(theme.colorScheme)
    }
  }
}
