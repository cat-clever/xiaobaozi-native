import Foundation

enum Source1Crypto {
  private static let base64Regex = try! NSRegularExpression(pattern: "^[A-Za-z0-9+/=\\s]+$")

  static func buildHeaders(
    entry: MahaoEntry,
    deviceId: String,
    token: String,
    curTimeMs: Int64
  ) -> [String: String] {
    let raw = Source1Constants.signSalt + deviceId + String(curTimeMs)
    return [
      "app_id": entry.appId,
      "package_name": entry.packageName,
      "version": entry.version,
      "sys_platform": Source1Constants.sysPlatform,
      "mob_mfr": "xiaomi",
      "mobmodel": "MI 9",
      "sysrelease": "10",
      "device_id": deviceId,
      "channel_code": entry.channelCode,
      "cur_time": String(curTimeMs),
      "token": token,
      "sign": CryptoUtils.md5HexUpper(raw),
      "User-Agent": Source1Constants.apiUA,
    ]
  }

  static func decodeBody(_ text: String) throws -> [String: Any] {
    let body = text.trimmingCharacters(in: .whitespacesAndNewlines)
    if body.isEmpty {
      throw AppApiError.serverBusy("空响应")
    }

    if body.hasPrefix("{") || body.hasPrefix("[") {
      if let data = body.data(using: .utf8),
         let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
        return obj
      }
    }

    let range = NSRange(body.startIndex..<body.endIndex, in: body)
    if base64Regex.firstMatch(in: body, range: range) == nil {
      throw AppApiError.serverBusy(String(body.prefix(120)))
    }

    guard let raw = CryptoUtils.base64DecodeFlexible(body) else {
      throw AppApiError.serverBusy(String(body.prefix(120)))
    }
    let keyData = Data(Source1Constants.aesKey.utf8)
    let ivData = Data(Source1Constants.aesIv.utf8)
    let plainData = try CryptoUtils.aesCbcDecrypt(key: keyData, iv: ivData, data: raw)
    guard let obj = try? JSONSerialization.jsonObject(with: plainData) as? [String: Any] else {
      let plain = String(data: plainData, encoding: .utf8) ?? ""
      throw AppApiError.api("解密后不是 JSON: \(plain.prefix(200))")
    }
    return obj
  }

  static func unwrap(_ data: [String: Any]) throws -> Any {
    let code = (data["code"] as? NSNumber)?.intValue ?? Int(String(describing: data["code"] ?? "0")) ?? 0
    if code != Source1Constants.successCode {
      let msg = String(describing: data["message"] ?? "")
      throw AppApiError.api("接口返回 code=\(code) message=\(msg)")
    }
    return data["result"] ?? data["data"] ?? [:]
  }

  static func parseCk(_ bean: [String: Any]) -> String {
    guard let ck = bean["ck"] as? String, !ck.isEmpty,
          let data = CryptoUtils.base64DecodeFlexible(ck),
          let str = String(data: data, encoding: .utf8) else {
      return ""
    }
    return str
  }
}
