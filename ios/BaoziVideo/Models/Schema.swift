import Foundation

enum SourceId: String, Codable, Hashable {
  case s1
  case s2

  var label: String {
    switch self {
    case .s1: return "全网源"
    case .s2: return "精选源"
    }
  }
}

struct VodItem: Identifiable, Hashable, Codable {
  let id: String
  let sourceId: SourceId
  let title: String
  let coverUrl: String
  let rating: String
  let category: String
  let year: String
  let area: String
  let remarks: String
  let intro: String
}

struct Episode: Identifiable, Hashable {
  var id: String { "\(episodeIndex)_\(episodeId ?? pid ?? title)" }
  let episodeIndex: Int
  let episodeId: String?
  let title: String
  let vodToken: String?
  let curTime: String?
  let duration: String
  let durationSec: Int
  let pid: String?
  let scid: String?
}

enum LineKind: String {
  case signed
  case mirror
  case official
  case m3u8
  case backup
}

struct PlayLine: Identifiable, Hashable {
  var id: String { "\(type)_\(kind.rawValue)_\(name)_\(url)" }
  let type: Int
  let kind: LineKind
  let name: String
  let url: String
  let parsePrefix: String?
  let ck: String?
  let wsKey: String?
  let playable: Bool
  let note: String
}

enum StreamFormat: String {
  case hls
  case mp4
}

struct ResolvedMediaStream {
  let streamUrl: String
  let format: StreamFormat
  let headers: [String: String]
  let lineName: String
  let dispose: () -> Void
}

struct QualityOption: Identifiable, Hashable {
  var id: Int { value }
  let label: String
  let value: Int
}

struct SeasonOption: Identifiable, Hashable {
  var id: String { sid }
  let sid: String
  let title: String
}

struct VodDetail {
  let vodId: String
  let sourceId: SourceId
  let name: String
  let pic: String
  let actor: String
  let director: String
  let year: String
  let area: String
  let blurb: String
  let episodes: [Episode]
  let qualities: [QualityOption]
  let seasons: [SeasonOption]
}

struct HomeBlock: Identifiable {
  var id: String { title }
  let title: String
  let items: [VodItem]
}

struct FilterOption: Identifiable, Hashable {
  var id: String { "\(label)_\(value)" }
  let label: String
  let value: String
}

struct FilterRow: Identifiable {
  var id: String { title }
  let title: String
  let options: [FilterOption]
}

struct CategoryQuery {
  var page: Int = 1
  var typeId: Int = 0
  var typeValue: String = ""
  var tagType: String = ""
  var area: String = ""
  var stype: Int? = nil
  var cid: Int? = nil
  var year: String = ""
  var sort: String = ""
}

struct WatchRecord: Codable, Identifiable {
  let id: String
  let sourceId: SourceId
  let title: String
  let cover: String
  let episodeIndex: Int
  let positionSec: Double
  let updatedAt: Double
}
