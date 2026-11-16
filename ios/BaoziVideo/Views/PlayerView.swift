import AVKit
import SwiftUI

struct PlayerView: View {
  let item: VodItem
  let initialEpisodeIndex: Int
  let onClose: () -> Void

  @State private var detail: VodDetail?
  @State private var currentVodId: String = ""
  @State private var episodeIndex: Int = 0
  @State private var lines: [PlayLine] = []
  @State private var selectedLine: PlayLine?
  @State private var qualities: [QualityOption] = []
  @State private var selectedQuality: Int?
  @State private var s2Candidates: [[String: Any]] = []
  @State private var s2SegmentIndex: Int = 0

  @State private var stream: ResolvedMediaStream?
  @State private var player: AVPlayer?
  @State private var playbackRate: Float = 1.0
  @State private var loading = true
  @State private var error: String?
  @State private var showCastSheet = false

  private let speeds: [Float] = [0.5, 0.75, 1.0, 1.25, 1.5, 2.0]

  var body: some View {
    VStack(spacing: 0) {
      // 视频区
      ZStack {
        Color.black
        if let player {
          VideoPlayer(player: player)
            .onDisappear {
              saveProgress()
            }
        }
        if loading {
          ProgressView("正在解析播放地址…")
            .tint(.white)
            .foregroundColor(.white)
            .padding()
            .background(Color.black.opacity(0.6), in: RoundedRectangle(cornerRadius: 12))
        }
      }
      .frame(height: 240)
      .overlay(alignment: .topLeading) {
        HStack {
          Button {
            cleanup()
            onClose()
          } label: {
            Image(systemName: "chevron.left")
              .font(.system(size: 16, weight: .bold))
              .foregroundColor(.white)
              .padding(10)
              .background(Color.black.opacity(0.5), in: Circle())
          }
          Spacer()
          HStack(spacing: 12) {
            AirPlayButtonView(tintColor: .white)
              .frame(width: 28, height: 28)
            Button {
              showCastSheet = true
            } label: {
              Image(systemName: "tv")
                .font(.system(size: 15, weight: .semibold))
                .foregroundColor(.white)
                .padding(8)
                .background(Color.black.opacity(0.5), in: Circle())
            }
          }
        }
        .padding(10)
      }

      // 详情与选集区
      ScrollView {
        VStack(alignment: .leading, spacing: 16) {
          if let error {
            Text(error)
              .font(.caption)
              .foregroundColor(.red)
              .padding(10)
              .frame(maxWidth: .infinity, alignment: .leading)
              .background(Color.red.opacity(0.1), in: RoundedRectangle(cornerRadius: 10))
          }

          // 标题 & 线路信息
          VStack(alignment: .leading, spacing: 6) {
            HStack {
              Text(detail?.name ?? item.title)
                .font(.title3.bold())
              Spacer()
              Text(item.sourceId.label)
                .font(.caption.bold())
                .foregroundColor(item.sourceId == .s2 ? AppColors.badgeS2Text : .white)
                .padding(.horizontal, 8)
                .padding(.vertical, 3)
                .background(item.sourceId == .s2 ? AppColors.badgeS2Bg : AppColors.badgeS1Bg)
                .clipShape(Capsule())
            }
            if let lineName = stream?.lineName {
              Text("当前线路：\(lineName)")
                .font(.caption)
                .foregroundColor(.secondary)
            }
          }

          // 倍速控制
          ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
              Text("倍速")
                .font(.caption)
                .foregroundColor(.secondary)
              ForEach(speeds, id: \.self) { sp in
                let active = abs(playbackRate - sp) < 0.01
                Button {
                  playbackRate = sp
                  player?.rate = sp
                } label: {
                  Text(sp == 1.0 ? "1.0x" : "\(String(format: "%g", sp))x")
                    .font(.caption.bold())
                    .foregroundColor(active ? .black : .primary)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(active ? AppColors.accent : Color.secondary.opacity(0.15))
                    .clipShape(Capsule())
                }
              }
            }
          }

          // 季切换 (源2)
          if let seasons = detail?.seasons, seasons.count > 1 {
            VStack(alignment: .leading, spacing: 6) {
              Text("分季")
                .font(.subheadline.bold())
              ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                  ForEach(seasons) { s in
                    let active = s.sid == currentVodId
                    Button {
                      Task { await loadDetailAndPlay(vodId: s.sid, epIdx: 0) }
                    } label: {
                      Text(s.title)
                        .font(.caption)
                        .foregroundColor(active ? .black : .primary)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(active ? AppColors.accent : Color.secondary.opacity(0.15))
                        .clipShape(Capsule())
                    }
                  }
                }
              }
            }
          }

          // 线路切换 (源1)
          if !lines.isEmpty {
            VStack(alignment: .leading, spacing: 6) {
              Text("线路")
                .font(.subheadline.bold())
              ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                  ForEach(lines) { line in
                    let active = line.id == selectedLine?.id
                    Button {
                      if line.playable {
                        Task { await switchS1Line(line) }
                      }
                    } label: {
                      Text(line.name)
                        .font(.caption)
                        .foregroundColor(active ? .black : (line.playable ? .primary : .secondary))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(active ? AppColors.accent : Color.secondary.opacity(0.15))
                        .clipShape(Capsule())
                    }
                    .disabled(!line.playable)
                  }
                }
              }
            }
          }

          // 清晰度 (源2)
          if !qualities.isEmpty {
            VStack(alignment: .leading, spacing: 6) {
              Text("清晰度")
                .font(.subheadline.bold())
              ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                  ForEach(qualities) { q in
                    let active = selectedQuality == q.value
                    Button {
                      selectedQuality = q.value
                      if let ep = detail?.episodes[safe: episodeIndex] {
                        Task { await playEpisode(ep, quality: q.value) }
                      }
                    } label: {
                      Text(q.label)
                        .font(.caption)
                        .foregroundColor(active ? .black : .primary)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(active ? AppColors.accent : Color.secondary.opacity(0.15))
                        .clipShape(Capsule())
                    }
                  }
                }
              }
            }
          }

          // 选集
          if let episodes = detail?.episodes, !episodes.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
              Text("选集（\(episodes.count)）")
                .font(.subheadline.bold())
              let cols = Array(repeating: GridItem(.flexible(), spacing: 8), count: 4)
              LazyVGrid(columns: cols, spacing: 8) {
                ForEach(episodes) { ep in
                  let active = ep.episodeIndex == episodeIndex
                  Button {
                    Task { await playEpisode(ep, quality: selectedQuality) }
                  } label: {
                    Text(ep.title)
                      .font(.caption)
                      .lineLimit(1)
                      .foregroundColor(active ? .black : .primary)
                      .frame(maxWidth: .infinity)
                      .padding(.vertical, 9)
                      .background(active ? AppColors.accent : Color.secondary.opacity(0.14))
                      .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                  }
                }
              }
            }
          }

          // 简介
          if let d = detail {
            VStack(alignment: .leading, spacing: 6) {
              Text("简介")
                .font(.subheadline.bold())
              if !d.actor.isEmpty {
                Text("主演：\(d.actor)")
                  .font(.caption)
                  .foregroundColor(.secondary)
              }
              if !d.director.isEmpty {
                Text("导演：\(d.director)")
                  .font(.caption)
                  .foregroundColor(.secondary)
              }
              if !d.blurb.isEmpty {
                Text(d.blurb)
                  .font(.caption)
                  .foregroundColor(.secondary)
              }
            }
          }
        }
        .padding(16)
      }
    }
    .sheet(isPresented: $showCastSheet) {
      CastSheetView(
        title: "\(detail?.name ?? item.title) - 第\(episodeIndex + 1)集",
        prepareCastSource: {
          if item.sourceId == .s1 {
            return try await StreamResolver.castSourceForSource1(lines: lines, preferred: selectedLine)
          } else {
            guard let cand = s2Candidates[safe: s2SegmentIndex] else {
              throw AppApiError.api("当前没有可投屏的播放地址")
            }
            return try StreamResolver.castSourceForSource2Candidate(cand)
          }
        }
      )
    }
    .task {
      episodeIndex = initialEpisodeIndex
      currentVodId = item.id
      await loadDetailAndPlay(vodId: item.id, epIdx: initialEpisodeIndex)
    }
    .onDisappear {
      cleanup()
    }
  }

  private func loadDetailAndPlay(vodId: String, epIdx: Int) async {
    loading = true
    error = nil
    currentVodId = vodId
    do {
      let d = try await Aggregator.shared.detail(sourceId: item.sourceId, id: vodId)
      detail = d
      qualities = d.qualities
      let targetIdx = min(epIdx, max(0, d.episodes.count - 1))
      if let ep = d.episodes[safe: targetIdx] {
        await playEpisode(ep, quality: selectedQuality)
      } else {
        error = "没有可播放的分集"
        loading = false
      }
    } catch {
      self.error = error.localizedDescription
      loading = false
    }
  }

  private func playEpisode(_ ep: Episode, quality: Int?) async {
    loading = true
    error = nil
    episodeIndex = ep.episodeIndex
    stream?.dispose()
    stream = nil

    do {
      if item.sourceId == .s1 {
        let fetchedLines = try await Source1Service.shared.playLinesFor(vodId: currentVodId, episode: ep)
        lines = fetchedLines
        let picked = StreamResolver.pickPlayableLine(fetchedLines)
        selectedLine = picked
        let resolved = try await StreamResolver.resolveSource1(lines: fetchedLines, preferred: picked)
        startPlayback(resolved)
      } else {
        let cands = try await Source2Service.shared.playFor(sid: currentVodId, episode: ep, quality: quality)
        s2Candidates = cands
        s2SegmentIndex = 0
        guard let first = cands.first else {
          throw AppApiError.api("没有拿到播放地址（该集可能受限或上游抖动）")
        }
        let resolved = try await StreamResolver.resolveSource2Candidate(first)
        startPlayback(resolved)
      }
    } catch {
      self.error = error.localizedDescription
    }
    loading = false
  }

  private func switchS1Line(_ line: PlayLine) async {
    loading = true
    error = nil
    selectedLine = line
    stream?.dispose()
    do {
      let resolved = try await StreamResolver.resolveSource1(lines: lines, preferred: line)
      startPlayback(resolved)
    } catch {
      self.error = error.localizedDescription
    }
    loading = false
  }

  private func startPlayback(_ resolved: ResolvedMediaStream) {
    stream = resolved
    guard let url = URL(string: resolved.streamUrl) else {
      error = "播放地址无效"
      return
    }
    let asset = AVURLAsset(url: url)
    let playerItem = AVPlayerItem(asset: asset)
    let p = AVPlayer(playerItem: playerItem)
    p.rate = playbackRate
    player = p
    p.play()
  }

  private func saveProgress() {
    guard let p = player else { return }
    let sec = CMTimeGetSeconds(p.currentTime())
    if sec.isFinite && sec > 0 {
      StorageManager.shared.saveWatchRecord(WatchRecord(
        id: item.id,
        sourceId: item.sourceId,
        title: detail?.name ?? item.title,
        cover: detail?.pic ?? item.coverUrl,
        episodeIndex: episodeIndex,
        positionSec: sec,
        updatedAt: Date().timeIntervalSince1970
      ))
    }
  }

  private func cleanup() {
    saveProgress()
    player?.pause()
    player = nil
    stream?.dispose()
    stream = nil
  }
}

private extension Array {
  subscript(safe index: Int) -> Element? {
    indices.contains(index) ? self[index] : nil
  }
}
