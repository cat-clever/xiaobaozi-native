import Foundation

enum ThemeMode: String, CaseIterable {
  case system
  case dark
  case light

  var label: String {
    switch self {
    case .system: return "跟随系统"
    case .dark: return "深色"
    case .light: return "亮色"
    }
  }

  var hint: String {
    switch self {
    case .system: return "系统切深色它就切"
    case .dark: return "默认"
    case .light: return ""
    }
  }
}

final class StorageManager {
  static let shared = StorageManager()

  private let defaults = UserDefaults.standard
  private let keyDeviceUid = "baozivideo.deviceUid"
  private let keyUidSlowHits = "baozivideo.deviceUid.slowHits"
  private let keySearchHistory = "baozivideo.searchHistory"
  private let keyWatchHistory = "baozivideo.watchHistory"
  private let keyThemeMode = "baozivideo.theme.mode"
  private let keyThemeGlass = "baozivideo.theme.glass"

  static let slowHitsBeforeRotate = 2
  private static let uidRegex = try! NSRegularExpression(pattern: "^[0-9A-Za-z]{20}$")

  private init() {}

  func shouldRotateDeviceUid(saved: String?, slowHits: Int) -> Bool {
    guard let saved = saved, !saved.isEmpty else { return true }
    let range = NSRange(saved.startIndex..<saved.endIndex, in: saved)
    if Self.uidRegex.firstMatch(in: saved, range: range) == nil {
      return true
    }
    return slowHits >= Self.slowHitsBeforeRotate
  }

  func getDeviceUid() -> String {
    let saved = defaults.string(forKey: keyDeviceUid)
    let slowHits = defaults.integer(forKey: keyUidSlowHits)
    if !shouldRotateDeviceUid(saved: saved, slowHits: slowHits), let valid = saved {
      return valid
    }
    let fresh = Source2Crypto.randUid(Source2Constants.randChars)
    defaults.set(fresh, forKey: keyDeviceUid)
    defaults.set(0, forKey: keyUidSlowHits)
    return fresh
  }

  func notePlayNode(hasHttps: Bool) {
    if hasHttps {
      defaults.set(0, forKey: keyUidSlowHits)
      return
    }
    let hits = defaults.integer(forKey: keyUidSlowHits) + 1
    defaults.set(hits, forKey: keyUidSlowHits)
  }

  func getThemeMode() -> ThemeMode {
    guard let raw = defaults.string(forKey: keyThemeMode),
          let mode = ThemeMode(rawValue: raw) else {
      return .system
    }
    return mode
  }

  func saveThemeMode(_ mode: ThemeMode) {
    defaults.set(mode.rawValue, forKey: keyThemeMode)
  }

  func getGlassEnabled() -> Bool {
    if defaults.object(forKey: keyThemeGlass) == nil { return true }
    return defaults.bool(forKey: keyThemeGlass)
  }

  func saveGlassEnabled(_ enabled: Bool) {
    defaults.set(enabled, forKey: keyThemeGlass)
  }

  func getSearchHistory() -> [String] {
    defaults.stringArray(forKey: keySearchHistory) ?? []
  }

  @discardableResult
  func pushSearchHistory(_ keyword: String) -> [String] {
    let list = getSearchHistory().filter { $0 != keyword }
    let next = Array(([keyword] + list).prefix(20))
    defaults.set(next, forKey: keySearchHistory)
    return next
  }

  func clearSearchHistory() {
    defaults.removeObject(forKey: keySearchHistory)
  }

  func getWatchHistory() -> [WatchRecord] {
    guard let data = defaults.data(forKey: keyWatchHistory),
          let list = try? JSONDecoder().decode([WatchRecord].self, from: data) else {
      return []
    }
    return list
  }

  func saveWatchRecord(_ record: WatchRecord) {
    let list = getWatchHistory().filter { $0.id != record.id }
    let next = Array(([record] + list).prefix(100))
    if let data = try? JSONEncoder().encode(next) {
      defaults.set(data, forKey: keyWatchHistory)
    }
  }
}
