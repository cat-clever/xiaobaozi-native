import AVFoundation
import Foundation
import MediaPlayer
import UIKit

final class NowPlayingController {
  private weak var player: AVPlayer?
  private var timeObserver: Any?
  private var baseInfo: [String: Any] = [:]
  private var artworkTask: URLSessionDataTask?
  private var commandsRegistered = false

  func attach(player: AVPlayer, title: String, episodeTitle: String, artworkUrl: String) {
    detachObserverOnly()
    self.player = player

    if !commandsRegistered {
      registerCommands()
      commandsRegistered = true
    }

    baseInfo = [
      MPMediaItemPropertyTitle: title,
      MPMediaItemPropertyArtist: episodeTitle,
      MPMediaItemPropertyAlbumTitle: "小包子影视",
      MPNowPlayingInfoPropertyMediaType: MPNowPlayingInfoMediaType.video.rawValue,
    ]
    MPNowPlayingInfoCenter.default().nowPlayingInfo = baseInfo
    loadArtwork(urlString: artworkUrl)

    let interval = CMTime(seconds: 1.0, preferredTimescale: 600)
    timeObserver = player.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] _ in
      self?.syncPlaybackInfo()
    }
    syncPlaybackInfo()
  }

  func syncPlaybackInfo() {
    guard let player else { return }
    var info = baseInfo
    let elapsed = CMTimeGetSeconds(player.currentTime())
    if elapsed.isFinite && elapsed >= 0 {
      info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = elapsed
    }
    if let item = player.currentItem {
      let dur = CMTimeGetSeconds(item.duration)
      if dur.isFinite && dur > 0 {
        info[MPMediaItemPropertyPlaybackDuration] = dur
      }
    }
    info[MPNowPlayingInfoPropertyPlaybackRate] = player.rate
    MPNowPlayingInfoCenter.default().nowPlayingInfo = info
  }

  func clear() {
    detachObserverOnly()
    artworkTask?.cancel()
    artworkTask = nil
    player = nil
    baseInfo = [:]
    MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
  }

  private func detachObserverOnly() {
    if let obs = timeObserver, let p = player {
      p.removeTimeObserver(obs)
    }
    timeObserver = nil
  }

  private func loadArtwork(urlString: String) {
    artworkTask?.cancel()
    guard !urlString.isEmpty, let url = URL(string: urlString) else { return }
    var req = URLRequest(url: url, timeoutInterval: 15)
    req.setValue(Source1Constants.imageUA, forHTTPHeaderField: "User-Agent")
    artworkTask = URLSession.shared.dataTask(with: req) { [weak self] data, _, _ in
      guard let data, let image = UIImage(data: data) else { return }
      let artwork = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
      DispatchQueue.main.async {
        guard let self, self.player != nil else { return }
        self.baseInfo[MPMediaItemPropertyArtwork] = artwork
        self.syncPlaybackInfo()
      }
    }
    artworkTask?.resume()
  }

  private func registerCommands() {
    let center = MPRemoteCommandCenter.shared()
    center.playCommand.isEnabled = true
    center.playCommand.addTarget { [weak self] _ in
      guard let p = self?.player else { return .noActionableNowPlayingItem }
      p.play()
      self?.syncPlaybackInfo()
      return .success
    }

    center.pauseCommand.isEnabled = true
    center.pauseCommand.addTarget { [weak self] _ in
      guard let p = self?.player else { return .noActionableNowPlayingItem }
      p.pause()
      self?.syncPlaybackInfo()
      return .success
    }

    center.togglePlayPauseCommand.isEnabled = true
    center.togglePlayPauseCommand.addTarget { [weak self] _ in
      guard let p = self?.player else { return .noActionableNowPlayingItem }
      if p.rate > 0 { p.pause() } else { p.play() }
      self?.syncPlaybackInfo()
      return .success
    }

    center.changePlaybackPositionCommand.isEnabled = true
    center.changePlaybackPositionCommand.addTarget { [weak self] event in
      guard let p = self?.player,
            let posEvent = event as? MPChangePlaybackPositionCommandEvent else {
        return .noActionableNowPlayingItem
      }
      let target = CMTime(seconds: posEvent.positionTime, preferredTimescale: 600)
      p.seek(to: target) { _ in
        self?.syncPlaybackInfo()
      }
      return .success
    }

    center.skipForwardCommand.isEnabled = true
    center.skipForwardCommand.preferredIntervals = [15]
    center.skipForwardCommand.addTarget { [weak self] _ in
      guard let p = self?.player else { return .noActionableNowPlayingItem }
      let cur = CMTimeGetSeconds(p.currentTime())
      let target = CMTime(seconds: max(0, cur + 15), preferredTimescale: 600)
      p.seek(to: target) { _ in self?.syncPlaybackInfo() }
      return .success
    }

    center.skipBackwardCommand.isEnabled = true
    center.skipBackwardCommand.preferredIntervals = [15]
    center.skipBackwardCommand.addTarget { [weak self] _ in
      guard let p = self?.player else { return .noActionableNowPlayingItem }
      let cur = CMTimeGetSeconds(p.currentTime())
      let target = CMTime(seconds: max(0, cur - 15), preferredTimescale: 600)
      p.seek(to: target) { _ in self?.syncPlaybackInfo() }
      return .success
    }
  }
}
