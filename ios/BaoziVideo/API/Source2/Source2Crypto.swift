import Foundation

enum Source2Crypto {
  static func deriveKeyIv(deviceUid: String, ts: Int64?, material: String = Source2Constants.materialDec) -> (Data, Data) {
    let src = ts == nil ? deviceUid : "\(deviceUid)\(ts!)"
    let did = CryptoUtils.md5Hex(src)
    let h = CryptoUtils.md5Hex(did + material)
    let idx = h.index(h.startIndex, offsetBy: 16)
    let key = Data(String(h[..<idx]).utf8)
    let iv = Data(String(h[idx...]).utf8)
    return (key, iv)
  }

  static func decrypt(dataB64: String, deviceUid: String, ts: Int64?) throws -> String {
    guard let raw = Data(base64Encoded: dataB64, options: .ignoreUnknownCharacters) else {
      throw AppApiError.api("Base64 解码失败")
    }
    let (key, iv) = deriveKeyIv(deviceUid: deviceUid, ts: ts)
    let plain = try CryptoUtils.aesCbcDecrypt(key: key, iv: iv, data: raw)
    return String(data: plain, encoding: .utf8) ?? ""
  }

  static func makeSign(_ kv: [String: String]) -> String {
    let body = kv.keys.sorted(by: >).map { "&\($0)=\(kv[$0] ?? "")" }.joined()
    return CryptoUtils.md5Hex(body + "&" + Source2Constants.materialSign)
  }

  static func makeUk(_ deviceUid: String) -> String {
    let key = Data(Source2Constants.ukKey.utf8)
    let iv = Data(Source2Constants.ukIv.utf8)
    let enc = (try? CryptoUtils.aesCbcEncrypt(key: key, iv: iv, data: Data(deviceUid.utf8))) ?? Data()
    return enc.base64EncodedString()
  }

  static func aesD(_ text: String, deviceUid: String) -> String {
    let h = CryptoUtils.md5Hex(deviceUid)
    let idx = h.index(h.startIndex, offsetBy: 16)
    let key = Data(String(h[..<idx]).utf8)
    let iv = Data(String(h[idx...]).utf8)
    let enc = (try? CryptoUtils.aesCbcEncrypt(key: key, iv: iv, data: Data(text.utf8))) ?? Data()
    return enc.base64EncodedString()
  }

  static func makeSignHeader(_ deviceUid: String, stampMs: Int64? = nil) -> String {
    let ts = stampMs ?? Int64(Date().timeIntervalSince1970 * 1000)
    let payload: [String: Any] = ["uid": deviceUid, "ts": ts]
    guard let data = try? JSONSerialization.data(withJSONObject: payload),
          let json = String(data: data, encoding: .utf8) else {
      return ""
    }
    return aesD(json, deviceUid: deviceUid)
  }

  static func randUid(_ chars: String, length: Int = 20) -> String {
    let arr = Array(chars)
    return String((0..<length).map { _ in arr.randomElement()! })
  }
}
