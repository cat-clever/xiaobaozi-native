import Foundation

enum MahaoId: Int, CaseIterable {
  case dieying = 0
  case tianji = 1
  case chsp = 2
  case chspOld = 3
}

enum ApiStyle {
  case new
  case old
}

struct MahaoEntry {
  let label: String
  let appId: String
  let packageName: String
  let version: String
  let channelCode: String
  let wsKey: String
  let secDomain: String
  let lineMode: Int
  let api: ApiStyle
}

enum Source1Constants {
  static let mahaoTable: [MahaoId: MahaoEntry] = [
    .dieying: MahaoEntry(
      label: "叠影视频",
      appId: "dieyingshipin",
      packageName: "com.pathrecall.gj",
      version: "50000",
      channelCode: "dysp_sp01",
      wsKey: "6Jh7hrLCXBrutmJEYpMpvbU3LDEHwUZY",
      secDomain: "",
      lineMode: 0,
      api: .new
    ),
    .tianji: MahaoEntry(
      label: "天际视频",
      appId: "xingkongshipin",
      packageName: "com.lineone.connecter",
      version: "50100",
      channelCode: "xk_sp01",
      wsKey: "vT1RQRz8YzlzTgN26pIXNJ7Mi65juwSP",
      secDomain: "",
      lineMode: 0,
      api: .new
    ),
    .chsp: MahaoEntry(
      label: "彩虹视频（新接口）",
      appId: "caihongshipin",
      packageName: "com.hjmore.hdcre",
      version: "43000",
      channelCode: "sp01",
      wsKey: "af2tu1aCoXwDIUpAFBv7i8sDhpi3cSrr",
      secDomain: "",
      lineMode: 0,
      api: .new
    ),
    .chspOld: MahaoEntry(
      label: "彩虹视频（老接口）",
      appId: "caihongshipin",
      packageName: "com.hjmore.hdcre",
      version: "43000",
      channelCode: "sp01",
      wsKey: "af2tu1aCoXwDIUpAFBv7i8sDhpi3cSrr",
      secDomain: "",
      lineMode: 0,
      api: .old
    ),
  ]

  static let autoChain: [MahaoId] = [.dieying, .chsp, .chspOld, .tianji]
  static let playChain: [MahaoId] = [.dieying, .chsp, .chspOld, .tianji]

  static let tianjiDeviceId = "96ab557f6826febf"
  static let tianjiToken = "gAAAAABqyeanWUJ6JutPAslzwyrJdqtIZthjnfZrAfgFmbZhOdDlJYtI4XCW3TfbAFIRrDoecPmA32TWPizIq5tewpE0QHRYpGZCUPGL0HV14wFuwQo-4qf8ON1pMBiFF3Odq7p2oyw7K5szpSE-nwiGUYU5_5zA5bb6p8Q_23gYcanLA1XXLJp1RX8Iz645_e_aaRibg25KSTyJKOErmhgiiAJ-nlp6TA=="

  static let defaultBase = "https://u.gslock.com"
  static let fallbackBases = [
    "https://u.yyxdmn.com",
    "https://u.jsyysm.com",
    "https://u.skzsz.com",
    "https://u.uccao.cn",
  ]

  static let signSalt = "zD9[bM4~sF4~uY2)"
  static let aesKey = "aZ9$kU5%qI7=yC2=zH2#gM0@pX7^wF3a"
  static let aesIv = "hY2&tN3]kF7,dL7="
  static let sysPlatform = "2"
  static let successCode = 10000
  static let apiTimeoutSec: TimeInterval = 12

  static let mediaUA = "Mozi"
  static let imageBase = "http://wim.xzjykj.com/"
  static let imageUA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
  static let apiUA = "okhttp/4.9.0"
  static let badci = "723baba511fd6e9378e4f9eec2a97ca3"
  static let secDomainFallback = "http://chw.ycblhs.com"
  static let placeholderHost = "ydsp.skzsz.com"

  static let retries = 2
  static let retrySleepRangeMs: (Double, Double) = (3000, 6000)

  static let lineMirror = 4
  static let lineOfficial = 1
  static let lineM3u8 = 2
  static let lineBackup = 3
  static let lineSigned = 5

  static let apiPathsNew: [String: String] = [
    "init": "/api/new_public/init_v2",
    "types": "/api/new_type/list_v2",
    "screen": "/api/new_search/screen_v2",
    "search": "/api/new_search/result_v2",
    "recommend": "/api/new_search/recommend_v2",
    "detail": "/api/new_video/result_v2",
    "collection": "/api/new_video/collection_v2",
    "collection_list": "/api/new_video/collection_list_v2",
    "channels": "/api/new_channel/get_list_v2",
    "hot_search": "/api/new_search/hot_search_v2",
    "suggest": "/api/new_search/suggest_v2",
  ]

  static let apiPathsOld: [String: String] = [
    "init": "/api/public/init",
    "types": "/api/type/get_list",
    "screen": "/api/search/screen",
    "search": "/api/search/result",
    "recommend": "/api/search/recommend",
    "detail": "/api/video/result",
    "collection": "/api/video/collection",
    "collection_list": "/api/video/collection_list",
    "channels": "/api/channel/get_list",
    "hot_search": "/api/search/hot_search",
    "suggest": "/api/search/suggest",
  ]

  static let allDownHint = "所有接口都不可用"
}
