import Combine
import SwiftUI
import UIKit

final class ThemeManager: ObservableObject {
  static let shared = ThemeManager()

  @Published var mode: ThemeMode {
    didSet { StorageManager.shared.saveThemeMode(mode) }
  }
  let glassEnabled: Bool = true

  private init() {
    self.mode = StorageManager.shared.getThemeMode()
    applyBarAppearance()
  }

  func applyBarAppearance() {
    let tabApp = UITabBarAppearance()
    let navApp = UINavigationBarAppearance()
    tabApp.configureWithDefaultBackground()
    navApp.configureWithDefaultBackground()
    UITabBar.appearance().standardAppearance = tabApp
    UITabBar.appearance().scrollEdgeAppearance = tabApp
    UINavigationBar.appearance().standardAppearance = navApp
    UINavigationBar.appearance().scrollEdgeAppearance = navApp
  }

  var colorScheme: ColorScheme? {
    switch mode {
    case .system: return nil
    case .dark: return .dark
    case .light: return .light
    }
  }
}

enum AppColors {
  static let accent = Color(red: 1.0, green: 0.706, blue: 0.329) // #FFB454
  static let badgeS1Bg = Color.black.opacity(0.78)
  static let badgeS2Bg = Color(red: 1.0, green: 0.706, blue: 0.329)
  static let badgeS2Text = Color(red: 0.141, green: 0.082, blue: 0.012)
}

struct GlassCardModifier: ViewModifier {
  @EnvironmentObject var theme: ThemeManager
  var cornerRadius: CGFloat = 16

  func body(content: Content) -> some View {
    if theme.glassEnabled {
      content
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        .overlay(
          RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
            .stroke(Color.primary.opacity(0.14), lineWidth: 0.8)
        )
    } else {
      content
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        .overlay(
          RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
            .stroke(Color.primary.opacity(0.08), lineWidth: 0.8)
        )
    }
  }
}

extension View {
  func glassCard(cornerRadius: CGFloat = 16) -> some View {
    modifier(GlassCardModifier(cornerRadius: cornerRadius))
  }
}
