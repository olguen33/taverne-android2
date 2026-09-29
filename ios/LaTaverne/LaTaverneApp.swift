import SwiftUI
import WebKit

@main
struct LaTaverneApp: App {
    var body: some Scene {
        WindowGroup {
            TavernScreen()
        }
    }
}

private enum TavernURL {
    static let home = URL(string: "https://taverne-pere-rufus.olguen33.chatgpt.site")!
}

@MainActor
final class TavernBrowser: ObservableObject {
    @Published var isLoading = true
    @Published var errorMessage: String?
    weak var webView: WKWebView?

    func reload() {
        errorMessage = nil
        isLoading = true
        guard let webView else { return }
        if webView.url == nil {
            webView.load(URLRequest(url: TavernURL.home))
        } else {
            webView.reload()
        }
    }
}

private struct TavernScreen: View {
    @StateObject private var browser = TavernBrowser()

    var body: some View {
        ZStack {
            Color(red: 20 / 255, green: 35 / 255, blue: 31 / 255)
                .ignoresSafeArea()
            TavernWebView(browser: browser)
                .ignoresSafeArea(edges: .bottom)

            if let message = browser.errorMessage {
                VStack(spacing: 16) {
                    Image(systemName: "wifi.exclamationmark")
                        .font(.system(size: 36))
                    Text("Connexion à la Taverne impossible")
                        .font(.headline)
                    Text(message)
                        .font(.subheadline)
                        .multilineTextAlignment(.center)
                    Button("Réessayer") { browser.reload() }
                        .buttonStyle(.borderedProminent)
                        .tint(Color(red: 40 / 255, green: 84 / 255, blue: 64 / 255))
                }
                .padding(28)
                .frame(maxWidth: 340)
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 20))
            } else if browser.isLoading {
                ProgressView("Ouverture de la Taverne…")
                    .tint(.white)
                    .foregroundStyle(.white)
            }
        }
        .preferredColorScheme(.dark)
    }
}

private struct TavernWebView: UIViewRepresentable {
    @ObservedObject var browser: TavernBrowser

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .default()
        configuration.defaultWebpagePreferences.allowsContentJavaScript = true
        let view = WKWebView(frame: .zero, configuration: configuration)
        view.navigationDelegate = context.coordinator
        view.isOpaque = false
        view.backgroundColor = UIColor(red: 20 / 255, green: 35 / 255, blue: 31 / 255, alpha: 1)
        view.scrollView.contentInsetAdjustmentBehavior = .never
        browser.webView = view
        view.load(URLRequest(url: TavernURL.home))
        return view
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(browser: browser) }

    final class Coordinator: NSObject, WKNavigationDelegate {
        private let browser: TavernBrowser
        init(browser: TavernBrowser) { self.browser = browser }

        func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
            browser.isLoading = true
            browser.errorMessage = nil
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            browser.isLoading = false
            browser.errorMessage = nil
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            browser.isLoading = false
            browser.errorMessage = error.localizedDescription
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            browser.isLoading = false
            browser.errorMessage = error.localizedDescription
        }

        func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            guard let url = navigationAction.request.url else {
                decisionHandler(.cancel)
                return
            }
            let host = url.host?.lowercased() ?? ""
            if host == TavernURL.home.host || host == "dgvvocfpxflmfqaaakoe.supabase.co" || url.scheme == "about" {
                decisionHandler(.allow)
            } else if navigationAction.navigationType == .linkActivated {
                UIApplication.shared.open(url)
                decisionHandler(.cancel)
            } else {
                decisionHandler(.cancel)
            }
        }
    }
}