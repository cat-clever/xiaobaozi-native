import AVKit
import SwiftUI

struct AirPlayButtonView: UIViewRepresentable {
  var tintColor: UIColor = .white

  func makeUIView(context: Context) -> AVRoutePickerView {
    let view = AVRoutePickerView()
    view.tintColor = tintColor
    view.activeTintColor = .systemOrange
    view.prioritizesVideoDevices = true
    return view
  }

  func updateUIView(_ uiView: AVRoutePickerView, context: Context) {
    uiView.tintColor = tintColor
  }
}
