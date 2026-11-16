import Foundation

enum AppApiError: LocalizedError {
  case api(String)
  case serverBusy(String)
  case allDown(String)
  case network(String)

  var errorDescription: String? {
    switch self {
    case .api(let msg), .serverBusy(let msg), .allDown(let msg), .network(let msg):
      return msg
    }
  }
}

struct HttpResponse {
  let status: Int
  let text: String
}

enum HttpClient {
  private static let session: URLSession = {
    let config = URLSessionConfiguration.ephemeral
    config.requestCachePolicy = .reloadIgnoringLocalCacheData
    config.urlCache = nil
    return URLSession(configuration: config)
  }()

  static func request(
    url urlString: String,
    method: String = "GET",
    headers: [String: String] = [:],
    body: Data? = nil,
    timeoutSec: TimeInterval = 15
  ) async throws -> HttpResponse {
    guard let url = URL(string: urlString) else {
      throw AppApiError.network("无效的 URL: \(urlString)")
    }
    var req = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: timeoutSec)
    req.httpMethod = method
    for (k, v) in headers {
      req.setValue(v, forHTTPHeaderField: k)
    }
    req.httpBody = body

    do {
      let (data, response) = try await session.data(for: req)
      let http = response as? HTTPURLResponse
      let text = String(data: data, encoding: .utf8) ?? ""
      return HttpResponse(status: http?.statusCode ?? 0, text: text)
    } catch let err as URLError where err.code == .timedOut {
      throw AppApiError.network("请求超时（\(Int(timeoutSec * 1000))ms）：\(urlString)")
    } catch {
      throw AppApiError.network("请求失败：\(error.localizedDescription)")
    }
  }
}
