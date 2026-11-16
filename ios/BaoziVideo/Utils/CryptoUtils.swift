import CommonCrypto
import CryptoKit
import Foundation

enum CryptoUtils {
  static func md5Hex(_ string: String) -> String {
    md5Hex(Data(string.utf8))
  }

  static func md5Hex(_ data: Data) -> String {
    let digest = Insecure.MD5.hash(data: data)
    return digest.map { String(format: "%02x", $0) }.joined()
  }

  static func md5HexUpper(_ string: String) -> String {
    md5Hex(string).uppercased()
  }

  static func aesCbcDecrypt(key: Data, iv: Data, data: Data) throws -> Data {
    try crypt(operation: CCOperation(kCCDecrypt), key: key, iv: iv, data: data)
  }

  static func aesCbcEncrypt(key: Data, iv: Data, data: Data) throws -> Data {
    try crypt(operation: CCOperation(kCCEncrypt), key: key, iv: iv, data: data)
  }

  private static func crypt(operation: CCOperation, key: Data, iv: Data, data: Data) throws -> Data {
    let outLength = data.count + kCCBlockSizeAES128
    var outData = Data(count: outLength)
    var numBytesEncrypted = 0

    let status = outData.withUnsafeMutableBytes { outBytes in
      data.withUnsafeBytes { dataBytes in
        iv.withUnsafeBytes { ivBytes in
          key.withUnsafeBytes { keyBytes in
            CCCrypt(
              operation,
              CCAlgorithm(kCCAlgorithmAES),
              CCOptions(kCCOptionPKCS7Padding),
              keyBytes.baseAddress,
              key.count,
              ivBytes.baseAddress,
              dataBytes.baseAddress,
              data.count,
              outBytes.baseAddress,
              outLength,
              &numBytesEncrypted
            )
          }
        }
      }
    }

    guard status == kCCSuccess else {
      throw NSError(domain: "CryptoUtils", code: Int(status), userInfo: [NSLocalizedDescriptionKey: "AES 失败: \(status)"])
    }
    outData.removeSubrange(numBytesEncrypted..<outData.count)
    return outData
  }

  static func base64DecodeFlexible(_ string: String) -> Data? {
    var s = string.trimmingCharacters(in: .whitespacesAndNewlines)
      .replacingOccurrences(of: "-", with: "+")
      .replacingOccurrences(of: "_", with: "/")
    let rem = s.count % 4
    if rem != 0 {
      s += String(repeating: "=", count: 4 - rem)
    }
    return Data(base64Encoded: s, options: .ignoreUnknownCharacters)
  }

  static func base64UrlEncode(_ data: Data) -> String {
    data.base64EncodedString()
      .replacingOccurrences(of: "+", with: "-")
      .replacingOccurrences(of: "/", with: "_")
      .replacingOccurrences(of: "=", with: "")
  }

  static func episodeTitle(_ raw: Any?, index: Int) -> String {
    let text = String(describing: raw ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
    if raw == nil || text.isEmpty || text == "<null>" {
      return String(index + 1)
    }
    if let n = Int(text), text.allSatisfy(\.isNumber) {
      return String(n)
    }
    return text
  }
}
