import Flutter
import SafariServices
import UIKit

private let keyURL = "url"
private let keyOption = "safariVCOption"
private let keyUrlsToClose = "urlsToClose"

public class CustomTabsPlugin: NSObject, FlutterPlugin, SFSafariViewControllerDelegate, FlutterStreamHandler {
    private var eventSink: FlutterEventSink?
    var urlsToClose : [String]?=nil
    var safariDisplayed = false
    
    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(
            name: "plugins.flutter.droibit.github.io/custom_tabs",
            binaryMessenger: registrar.messenger()
        )
        let eventChannel = FlutterEventChannel(
            name: "plugins.flutter.dhyash-simform.github.io/custom_tabs_status",
            binaryMessenger: registrar.messenger()
        )
        let instance = CustomTabsPlugin()
        registrar.addMethodCallDelegate(instance, channel: channel)
        registrar.addApplicationDelegate(instance)
        eventChannel.setStreamHandler(instance)
    }
    
    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "launch":
            let arguments = call.arguments as! [String: Any]
            let url = arguments[keyURL] as! String
            let option = arguments[keyOption] as! [String: Any]
            urlsToClose = arguments[keyUrlsToClose] as? [String]
            
            present(withURL: url, option: option, result: result)
        default:
            result(FlutterMethodNotImplemented)
        }
    }
    
    private func present(withURL url: String, option: [String: Any], result: @escaping FlutterResult) {
        if #available(iOS 9.0, *) {
            if let topViewController = UIWindow.keyWindow?.topViewController() {
                let safariViewController = SFSafariViewController.make(url: URL(string: url)!, option: option)
                safariViewController.delegate = self
                safariDisplayed = true
                topViewController.present(safariViewController, animated: true) {
                    result(nil)
                }
            }
        } else {
            result(FlutterMethodNotImplemented)
        }
    }
    
    public func safariViewControllerDidFinish(_ controller: SFSafariViewController) {
        self.eventSink?("CUSTOM_TAB_CLOSED");
        self.safariDisplayed = false
    }
    
    public func application(
        _ application: UIApplication,
        open url: URL,
        options: [UIApplication.OpenURLOptionsKey : Any] = [:]
    ) -> Bool {
        if (safariDisplayed && urlsToClose != nil){
            for urlToClose in urlsToClose!{
                if (url.absoluteString.contains(urlToClose)){
                    UIWindow.keyWindow?.topViewController()?.navigationController?.popViewController(animated: true)
                    UIWindow.keyWindow?.topViewController()?.dismiss(animated: true, completion: nil)
                    return false
                }
            }
        }
        return false
    }

    // FlutterStreamHandler methods
    public func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
        self.eventSink = events
        return nil
    }

    public func onCancel(withArguments arguments: Any?) -> FlutterError? {
        self.eventSink = nil
        return nil
    }
}

private extension UIWindow {
    static var keyWindow: UIWindow? {
        if #available(iOS 13, *) {
            return UIApplication.shared.windows.first(where: { $0.isKeyWindow })
        } else {
            return UIApplication.shared.keyWindow
        }
    }
    
    func topViewController() -> UIViewController? {
        var topViewController: UIViewController? = rootViewController
        while true {
            if let navigationController = topViewController as? UINavigationController {
                topViewController = navigationController.visibleViewController
                continue
            } else if let tabBarController = topViewController as? UITabBarController,
                      let selected = tabBarController.selectedViewController {
                topViewController = selected
                continue
            } else if let presentedViewController = topViewController?.presentedViewController {
                topViewController = presentedViewController
            } else {
                break
            }
        }
        return topViewController
    }
}
