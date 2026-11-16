import Combine
import Foundation
import UIKit

struct UpdateManifest {
  let version: String
  let publishedAt: String
  let notes: [String]
  let iosUrl: String
  let pageUrl: String
  let iosSourceUrl: String
}

@MainActor
final class UpdaterService: ObservableObject {
  static let shared = UpdaterService()

  private static let repo = "cat-clever/xiaobaozi-native"
  static let releasePageUrl = "https://github.com/\(repo)/releases/latest"
  static let manifestUrl = "https://github.com/\(repo)/releases/latest/download/latest.json"
  static let sideStoreSourceUrl = "https://github.com/\(repo)/releases/latest/download/xiaobaozi.json"

  enum Phase {
    case idle, checking, latest, available, error
  }

  @Published var currentVersion: String = ""
  @Published var phase: Phase = .idle
  @Published var manifest: UpdateManifest?
  @Published var errorMessage: String?

  private init() {
    currentVersion = (Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String) ?? ""
  }

  func checkForUpdate() async {
    if phase == .checking { return }
    phase = .checking
    errorMessage = nil

    guard !currentVersion.isEmpty else {
      phase = .error
      errorMessage = "拿不到本机版本号，没法比较"
      return
    }

    do {
      let url = "\(Self.manifestUrl)?t=\(Int64(Date().timeIntervalSince1970 * 1000))"
      let resp = try await HttpClient.request(url: url, timeoutSec: 10)
      guard resp.status >= 200 && resp.status < 300,
            let data = resp.text.data(using: .utf8),
            let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
            let parsed = Self.parseManifest(json) else {
        phase = .error
        errorMessage = "更新清单不可用（解析失败）"
        return
      }
      manifest = parsed
      phase = Self.compareVersions(parsed.version, currentVersion) > 0 ? .available : .latest
    } catch {
      phase = .error
      errorMessage = error.localizedDescription
    }
  }

  func openExternal(_ urlString: String) {
    guard let url = URL(string: urlString) else { return }
    UIApplication.shared.open(url)
  }

  static func parseManifest(_ o: [String: Any]) -> UpdateManifest? {
    let version = (o["version"] as? String ?? "").trimmingCharacters(in: .whitespaces)
    guard !version.isEmpty,
          version.range(of: "^\\d+(?:\\.\\d+)*$", options: .regularExpression) != nil else {
      return nil
    }
    let ios = (o["ios"] as? [String: Any]) ?? [:]
    let notes = ((o["notes"] as? [String]) ?? []).map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
    return UpdateManifest(
      version: version,
      publishedAt: (o["publishedAt"] as? String ?? "").trimmingCharacters(in: .whitespaces),
      notes: notes,
      iosUrl: (ios["url"] as? String ?? "").trimmingCharacters(in: .whitespaces),
      pageUrl: (o["page"] as? String ?? "").trimmingCharacters(in: .whitespaces),
      iosSourceUrl: (ios["source"] as? String ?? "").trimmingCharacters(in: .whitespaces)
    )
  }

  static func compareVersions(_ a: String, _ b: String) -> Int {
    let pa = a.split(separator: ".").map { Int($0) ?? 0 }
    let pb = b.split(separator: ".").map { Int($0) ?? 0 }
    let count = max(pa.count, pb.count)
    for i in 0..<count {
      let na = i < pa.count ? pa[i] : 0
      let nb = i < pb.count ? pb[i] : 0
      if na != nb { return na > nb ? 1 : -1 }
    }
    return 0
  }
}
