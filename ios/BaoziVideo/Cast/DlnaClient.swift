import Foundation

enum DlnaClient {
  private static let defaultService = "urn:schemas-upnp-org:service:AVTransport:1"

  static func discoverDevices(timeoutMs: Double = 4000) async -> [CastDevice] {
    let hits: [SsdpHit] = await withCheckedContinuation { cont in
      DispatchQueue.global(qos: .userInitiated).async {
        cont.resume(returning: SsdpDiscovery.run(timeoutMs: timeoutMs))
      }
    }
    if hits.isEmpty { return [] }

    var byLoc: [String: SsdpHit] = [:]
    for hit in hits where byLoc[hit.location] == nil {
      byLoc[hit.location] = hit
    }
    let unique = Array(byLoc.values)
    func looksLikeRenderer(_ h: SsdpHit) -> Bool {
      "\(h.st) \(h.usn) \(h.server)".lowercased().contains("mediarenderer")
    }

    var found = await describeAll(unique.filter(looksLikeRenderer))
    if found.isEmpty {
      found = await describeAll(unique.filter { !looksLikeRenderer($0) })
    }
    return found.sorted { $0.name < $1.name }
  }

  private static func describeAll(_ hits: [SsdpHit]) async -> [CastDevice] {
    await withTaskGroup(of: CastDevice?.self) { group in
      for hit in hits {
        group.addTask { await describe(hit) }
      }
      var out: [CastDevice] = []
      for await d in group {
        if let d { out.append(d) }
      }
      return out
    }
  }

  private static func describe(_ hit: SsdpHit) async -> CastDevice? {
    guard let resp = try? await HttpClient.request(
      url: hit.location,
      headers: ["Accept": "text/xml, application/xml, */*"],
      timeoutSec: 2.5
    ), !resp.text.isEmpty,
          let parsed = DlnaParse.parseDescription(xml: resp.text, descriptionUrl: hit.location) else {
      return nil
    }
    let host = URL(string: hit.location)?.host ?? hit.location
    let id = !parsed.udn.isEmpty ? parsed.udn : (!hit.usn.isEmpty ? hit.usn : host)
    let name = !parsed.friendlyName.isEmpty ? parsed.friendlyName : (!parsed.modelName.isEmpty ? parsed.modelName : host)
    return CastDevice(
      id: id,
      name: name,
      model: parsed.modelName,
      address: hit.address.isEmpty ? host : hit.address,
      controlUrl: parsed.controlUrls[0],
      controlUrls: parsed.controlUrls,
      serviceType: parsed.serviceType
    )
  }

  static func cast(device: CastDevice, url: String, title: String) async throws {
    do {
      try await soap(device: device, action: "SetAVTransportURI", args: [
        ("InstanceID", "0"),
        ("CurrentURI", url),
        ("CurrentURIMetaData", DlnaParse.buildMetadata(url: url, title: title)),
      ])
    } catch {
      try await soap(device: device, action: "SetAVTransportURI", args: [
        ("InstanceID", "0"),
        ("CurrentURI", url),
        ("CurrentURIMetaData", ""),
      ])
    }
    try await play(device: device)
  }

  static func play(device: CastDevice) async throws {
    _ = try await soap(device: device, action: "Play", args: [("InstanceID", "0"), ("Speed", "1")])
  }

  static func pause(device: CastDevice) async throws {
    _ = try await soap(device: device, action: "Pause", args: [("InstanceID", "0")])
  }

  static func stop(device: CastDevice) async throws {
    _ = try await soap(device: device, action: "Stop", args: [("InstanceID", "0")])
  }

  static func seek(device: CastDevice, seconds: Double) async throws {
    let safe = max(0, Int(seconds))
    let h = safe / 3600
    let m = (safe % 3600) / 60
    let s = safe % 60
    let target = String(format: "%d:%02d:%02d", h, m, s)
    _ = try await soap(device: device, action: "Seek", args: [("InstanceID", "0"), ("Unit", "REL_TIME"), ("Target", target)])
  }

  static func status(device: CastDevice) async -> CastTransportState {
    var state: CastTransportState.State = .unknown
    var pos: Double?
    var dur: Double?

    if let info = try? await soap(device: device, action: "GetTransportInfo", args: [("InstanceID", "0")]) {
      state = DlnaParse.normalizeState(DlnaParse.tagText(info, tag: "CurrentTransportState"))
    }
    if let info = try? await soap(device: device, action: "GetPositionInfo", args: [("InstanceID", "0")]) {
      pos = parseRelTime(DlnaParse.tagText(info, tag: "RelTime"))
      dur = parseRelTime(DlnaParse.tagText(info, tag: "TrackDuration"))
    }
    return CastTransportState(state: state, positionSec: pos, durationSec: dur)
  }

  private static func parseRelTime(_ value: String) -> Double? {
    let parts = value.trimmingCharacters(in: .whitespacesAndNewlines).split(separator: ":")
    guard parts.count == 3,
          let h = Double(parts[0]),
          let m = Double(parts[1]),
          let s = Double(parts[2]) else {
      return nil
    }
    return h * 3600 + m * 60 + s
  }

  @discardableResult
  private static func soap(device: CastDevice, action: String, args: [(String, String)]) async throws -> String {
    let urls = device.controlUrls.isEmpty ? [device.controlUrl] : device.controlUrls
    let serviceType = device.serviceType.isEmpty ? defaultService : device.serviceType
    let body = DlnaParse.buildEnvelope(serviceType: serviceType, action: action, args: args)

    var lastErr: Error?
    for url in urls where !url.isEmpty {
      for header in ["\"\(serviceType)#\(action)\"", "\(serviceType)#\(action)"] {
        do {
          let resp = try await HttpClient.request(
            url: url,
            method: "POST",
            headers: [
              "Content-Type": "text/xml; charset=\"utf-8\"",
              "SOAPACTION": header,
              "Accept": "text/xml, application/xml, */*",
            ],
            body: Data(body.utf8),
            timeoutSec: 8
          )
          if resp.status == 404 {
            lastErr = AppApiError.network("控制地址不存在（404）")
            break
          }
          let fault = DlnaParse.faultOf(resp.text)
          if !fault.isEmpty {
            throw AppApiError.api(fault)
          }
          return resp.text
        } catch {
          lastErr = error
        }
      }
    }
    throw lastErr ?? AppApiError.network("\(action) 调用失败")
  }
}
