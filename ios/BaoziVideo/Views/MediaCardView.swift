import SwiftUI

final class RemoteImageLoader: ObservableObject {
  @Published var image: UIImage?
  private static let cache = NSCache<NSString, UIImage>()
  private var task: URLSessionDataTask?

  func load(urlString: String) {
    guard !urlString.isEmpty, let url = URL(string: urlString) else { return }
    if let cached = Self.cache.object(forKey: urlString as NSString) {
      self.image = cached
      return
    }
    task?.cancel()
    var req = URLRequest(url: url, timeoutInterval: 15)
    req.setValue(Source1Constants.imageUA, forHTTPHeaderField: "User-Agent")
    task = URLSession.shared.dataTask(with: req) { [weak self] data, _, _ in
      guard let data, let img = UIImage(data: data) else { return }
      Self.cache.setObject(img, forKey: urlString as NSString)
      DispatchQueue.main.async {
        self?.image = img
      }
    }
    task?.resume()
  }

  deinit {
    task?.cancel()
  }
}

struct MediaCardView: View {
  let item: VodItem
  let width: CGFloat
  let onTap: (VodItem) -> Void

  @StateObject private var loader = RemoteImageLoader()

  var body: some View {
    Button {
      onTap(item)
    } label: {
      VStack(alignment: .leading, spacing: 6) {
        ZStack(alignment: .topLeading) {
          Group {
            if let img = loader.image {
              Image(uiImage: img)
                .resizable()
                .aspectRatio(contentMode: .fill)
            } else {
              Rectangle()
                .fill(Color.secondary.opacity(0.18))
                .overlay(
                  Image(systemName: "film")
                    .foregroundColor(.secondary.opacity(0.5))
                )
            }
          }
          .frame(width: width, height: width * 1.42)
          .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

          // 来源角标
          Text(item.sourceId.label)
            .font(.system(size: 10, weight: .semibold))
            .foregroundColor(item.sourceId == .s2 ? AppColors.badgeS2Text : .white)
            .padding(.horizontal, 6)
            .padding(.vertical, 3)
            .background(item.sourceId == .s2 ? AppColors.badgeS2Bg : AppColors.badgeS1Bg)
            .clipShape(Capsule())
            .padding(6)

          // 右上角评分
          if !item.rating.isEmpty && item.rating != "0" && item.rating != "0.0" {
            HStack {
              Spacer()
              Text(item.rating)
                .font(.system(size: 10, weight: .bold))
                .foregroundColor(AppColors.accent)
                .padding(.horizontal, 5)
                .padding(.vertical, 2)
                .background(Color.black.opacity(0.72))
                .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
                .padding(6)
            }
          }

          // 底部备注
          if !item.remarks.isEmpty {
            VStack {
              Spacer()
              Text(item.remarks)
                .font(.system(size: 10, weight: .medium))
                .foregroundColor(.white)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 6)
                .padding(.vertical, 4)
                .background(
                  LinearGradient(
                    colors: [.clear, .black.opacity(0.78)],
                    startPoint: .top,
                    endPoint: .bottom
                  )
                )
            }
            .frame(width: width, height: width * 1.42)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
          }
        }

        Text(item.title)
          .font(.system(size: 13, weight: .medium))
          .foregroundColor(.primary)
          .lineLimit(1)
          .frame(width: width, alignment: .leading)
      }
    }
    .buttonStyle(.plain)
    .onAppear {
      loader.load(urlString: item.coverUrl)
    }
  }
}
