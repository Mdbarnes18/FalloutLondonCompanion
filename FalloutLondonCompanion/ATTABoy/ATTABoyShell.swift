import SwiftUI

struct ATTABoyShell<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View {
        GeometryReader { geo in
            ZStack {
                RoundedRectangle(cornerRadius: min(geo.size.width, geo.size.height) * 0.07)
                    .fill(.black.gradient)
                    .overlay(RoundedRectangle(cornerRadius: 24).stroke(.white.opacity(0.12), lineWidth: 2))
                    .shadow(radius: 24)
                content.padding(geo.size.width * 0.075)
            }
            .padding(geo.size.width * 0.025)
        }
    }
}
