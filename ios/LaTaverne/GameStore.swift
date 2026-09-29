import Foundation
import Security

typealias Row = [String: Any]
func value(_ row: Row, _ key: String) -> String { row[key] as? String ?? "" }
func amount(_ row: Row, _ key: String) -> Int { (row[key] as? NSNumber)?.intValue ?? 0 }

private enum Vault {
    static let name = "rufus-online-refresh"
    static func read() -> String? {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                kSecAttrAccount as String: name,
                                kSecReturnData as String: true,
                                kSecMatchLimit as String: kSecMatchLimitOne]
        var result: CFTypeRef?
        guard SecItemCopyMatching(q as CFDictionary, &result) == errSecSuccess,
              let bytes = result as? Data else { return nil }
        return String(data: bytes, encoding: .utf8)
    }
    static func clear() {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                kSecAttrAccount as String: name]
        SecItemDelete(q as CFDictionary)
    }
    static func save(_ token: String) {
        clear()
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                kSecAttrAccount as String: name,
                                kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                                kSecValueData as String: Data(token.utf8)]
        SecItemAdd(q as CFDictionary, nil)
    }
}

@MainActor final class GameStore: ObservableObject {
    @Published var authenticated = false, busy = false
    @Published var error: String?, notice: String?
    @Published var pseudo = ""
    @Published var profiles: [Row] = [], characters: [Row] = [], contracts: [Row] = []
    @Published var slots: [Row] = [], participants: [Row] = [], votes: [Row] = [], messages: [Row] = []
    @Published var catalog: [Row] = [], rumours: [Row] = [], campaignEntries: [Row] = [], names: [Row] = []
    private(set) var userId = ""
    private var token = ""
    private let base = "https://dgvvocfpxflmfqaaakoe.supabase.co"
    private let key = "sb_publishable_BW_VQsD-ymcyjgUblfUkDw_bkadPgIu"

    func player(_ id: String) -> String { profiles.first { value($0,"id") == id }.map { value($0,"pseudo") } ?? "MJ" }
    func character(_ id: String) -> String { names.first { value($0,"id") == id }.map { value($0,"name") } ?? "Personnage" }
    func owner(_ id: String) -> String { names.first { value($0,"id") == id }.map { value($0,"owner_id") } ?? "" }

    func restore() async {
        guard !authenticated, let refresh = Vault.read() else { return }
        do { try await refreshSession(refresh); await load() }
        catch { self.error = error.localizedDescription }
    }
    func signUp(email: String, password: String, pseudo: String) async {
        guard (3...32).contains(pseudo.count) else { error = "Pseudo de 3 à 32 caractères requis."; return }
        busy = true; defer { busy = false }
        do {
            let result = try await request("POST","/auth/v1/signup",body:["email":email,"password":password],auth:false) as? Row ?? [:]
            if let access = result["access_token"] as? String {
                saveSession(result, access)
                _ = try await rpc("set_pseudo", ["p_pseudo":pseudo]); await load()
            } else {
                UserDefaults.standard.set(pseudo,forKey:"pending_pseudo")
                notice = "Compte créé. Confirme l’e-mail reçu, puis connecte-toi."
            }
        } catch { self.error = error.localizedDescription }
    }
    func signIn(email: String, password: String) async {
        busy = true; defer { busy = false }
        do {
            let result = try await request("POST","/auth/v1/token?grant_type=password",body:["email":email,"password":password],auth:false) as? Row ?? [:]
            guard let access = result["access_token"] as? String else { throw GameError.reason("Connexion refusée") }
            saveSession(result, access)
            if let pending = UserDefaults.standard.string(forKey:"pending_pseudo") {
                _ = try await rpc("set_pseudo", ["p_pseudo":pending]); UserDefaults.standard.removeObject(forKey:"pending_pseudo")
            }
            await load()
        } catch { self.error = error.localizedDescription }
    }
    func signOut() {
        Vault.clear(); token = ""; userId = ""; authenticated = false; error = nil; notice = nil
        profiles = []; characters = []; contracts = []; slots = []; participants = []; votes = []
        messages = []; catalog = []; rumours = []; campaignEntries = []; names = []
    }
    private func saveSession(_ result: Row, _ access: String) {
        token = access; userId = value(result["user"] as? Row ?? [:], "id")
        if let refresh = result["refresh_token"] as? String { Vault.save(refresh) }
        authenticated = true; error = nil
    }
    private func refreshSession(_ refresh: String) async throws {
        let result = try await request("POST","/auth/v1/token?grant_type=refresh_token",body:["refresh_token":refresh],auth:false) as? Row ?? [:]
        guard let access = result["access_token"] as? String else { throw GameError.reason("Session expirée") }
        saveSession(result, access)
    }
    func load() async {
        guard authenticated else { return }
        do {
            profiles = try await table("profiles","select=id,pseudo")
            characters = try await table("characters")
            contracts = try await table("contracts","select=*&order=id.desc")
            slots = try await table("slots")
            participants = try await table("participants")
            votes = try await table("votes")
            messages = try await table("messages","select=*&order=id.asc")
            catalog = try await table("catalog","select=*&active=eq.true")
            rumours = try await table("rumours")
            campaignEntries = try await table("campaign_entries","select=*&order=created_at.desc")
            names = try await rpc("shared_character_names",[:]) as? [Row] ?? []
            pseudo = player(userId); error = nil
        } catch { self.error = error.localizedDescription }
    }
    func perform(_ action: () async throws -> Void) async {
        guard !busy else { return }; busy = true; error = nil
        do { try await action(); busy = false; await load() }
        catch { busy = false; self.error = error.localizedDescription }
    }
    func table(_ name: String, _ query: String = "select=*") async throws -> [Row] {
        return try await request("GET","/rest/v1/\(name)?\(query)") as? [Row] ?? []
    }
    func rpc(_ name: String, _ parameters: Row) async throws -> Any {
        return try await request("POST","/rest/v1/rpc/\(name)",body:parameters)
    }
    func insert(_ name: String, _ row: Row) async throws {
        _ = try await request("POST","/rest/v1/\(name)",body:row)
    }
    func remove(_ name: String, id: String) async throws {
        _ = try await request("DELETE","/rest/v1/\(name)?id=eq.\(id)")
    }
    private func request(_ method: String, _ path: String, body: Row? = nil, auth: Bool = true, retried: Bool = false) async throws -> Any {
        guard let url = URL(string: base + path) else { throw GameError.reason("Adresse invalide") }
        var req = URLRequest(url:url); req.httpMethod = method; req.timeoutInterval = 20
        req.setValue(key,forHTTPHeaderField:"apikey")
        if auth { req.setValue("Bearer \(token)",forHTTPHeaderField:"Authorization") }
        if let body {
            req.setValue("application/json",forHTTPHeaderField:"Content-Type")
            req.httpBody = try JSONSerialization.data(withJSONObject:body,options:[.fragmentsAllowed])
        }
        let (bytes,response) = try await URLSession.shared.data(for:req)
        guard let http = response as? HTTPURLResponse else { throw GameError.reason("Réponse invalide") }
        if http.statusCode == 401, auth, !retried, let refresh = Vault.read() {
            try await refreshSession(refresh)
            return try await request(method,path,body:body,auth:true,retried:true)
        }
        if http.statusCode >= 400 {
            let result = (try? JSONSerialization.jsonObject(with:bytes)) as? Row ?? [:]
            let message = [value(result,"msg"),value(result,"message"),value(result,"error_description")].first { !$0.isEmpty }
            throw GameError.reason(message ?? "Serveur : \(http.statusCode)")
        }
        return bytes.isEmpty ? NSNull() : try JSONSerialization.jsonObject(with:bytes,options:[.fragmentsAllowed])
    }
}

private enum GameError: LocalizedError {
    case reason(String)
    var errorDescription: String? { if case let .reason(s) = self { return s }; return nil }
}
