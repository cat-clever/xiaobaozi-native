# 小包子影视（双端纯原生分离版）

端内双源直连的纯原生流媒体客户端：
- **iOS**：100% 纯 Swift + SwiftUI 原生实现（无 React Native / 无第三方依赖）
- **Android**：100% 纯 Java + Media3 ExoPlayer 原生实现（无 React Native / 无 Kotlin）

## 架构特点

- **无服务端**：双源协议与加解密全部在端内完成
- **本地媒体服务**：端内 HTTP 代理，承担 `Mozi` / `Badci` 请求头注入、m3u8 重写、`wsSecret` 现算、Range 透传、iOS HEVC-TS → fMP4 转封装
- **投屏**：DLNA / AirPlay，直投优先，转发兜底
- **GitHub Actions 自动构建**：推送代码自动编译 iOS & Android；推送 tag（`v*`）自动产出 `apk` 与未签名 `ipa`

## 开源协议

本项目采用 [GNU General Public License v3.0 (GPLv3)](./LICENSE) 开源。
