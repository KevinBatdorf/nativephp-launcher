import Foundation

// iOS apps cannot be the home screen, so every function reports that plainly.
enum LauncherFunctions {

    class Apps: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            return BridgeResponse.success(data: ["apps": []])
        }
    }

    class Open: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            return BridgeResponse.error(code: "UNSUPPORTED", message: "iOS does not let an app open another")
        }
    }

    class IsDefaultHome: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            return BridgeResponse.success(data: ["default": false, "current": ""])
        }
    }

    class OpenHomeSettings: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            return BridgeResponse.error(code: "UNSUPPORTED", message: "iOS has no home screen setting")
        }
    }
}
