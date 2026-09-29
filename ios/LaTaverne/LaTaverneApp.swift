import SwiftUI

@main
struct LaTaverneApp: App {
    @StateObject private var game = GameStore()
    var body: some Scene {
        WindowGroup {
            Group { if game.authenticated { MainTabs() } else { LoginScreen() } }
                .environmentObject(game)
                .tint(Color(red: 0.55, green: 0.40, blue: 0.20))
                .task { await game.restore() }
        }
    }
}

struct MainTabs: View {
    @EnvironmentObject var game: GameStore
    var body: some View {
        TabView {
            NavigationStack { HomeScreen() }.tabItem { Label("Taverne", systemImage: "house.fill") }
            NavigationStack { ContractsScreen() }.tabItem { Label("Contrats", systemImage: "scroll.fill") }
            NavigationStack { CharactersScreen() }.tabItem { Label("Personnages", systemImage: "person.2.fill") }
            NavigationStack { CampaignScreen() }.tabItem { Label("Campagne", systemImage: "book.fill") }
            NavigationStack { MoreScreen() }.tabItem { Label("Plus", systemImage: "ellipsis.circle.fill") }
        }
        .task { await game.load() }
    }
}

struct LoginScreen: View {
    @EnvironmentObject var game: GameStore
    @State private var email = "", password = "", pseudo = ""
    @State private var registering = false
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    Image("Tavern").resizable().scaledToFill().frame(height: 215).clipped()
                        .clipShape(RoundedRectangle(cornerRadius: 18))
                    Text("La Taverne du père Rufus").font(.largeTitle.bold()).multilineTextAlignment(.center)
                    VStack(spacing: 14) {
                        TextField("Adresse e-mail", text: $email).textContentType(.emailAddress)
                            .keyboardType(.emailAddress).textInputAutocapitalization(.never).autocorrectionDisabled()
                        SecureField("Mot de passe", text: $password)
                        if registering { TextField("Pseudo visible", text: $pseudo) }
                    }.textFieldStyle(.roundedBorder)
                    Button(registering ? "Créer mon compte" : "Se connecter") {
                        Task { if registering { await game.signUp(email: email, password: password, pseudo: pseudo) }
                               else { await game.signIn(email: email, password: password) } }
                    }.buttonStyle(.borderedProminent).disabled(game.busy || !email.contains("@") || password.count < 8)
                    Button(registering ? "J’ai déjà un compte" : "Créer un compte") { registering.toggle() }
                    if let notice = game.notice { Text(notice).foregroundStyle(.secondary) }
                    if let error = game.error { Text(error).foregroundStyle(.red) }
                }.padding()
            }.background(Color(red: 0.94, green: 0.90, blue: 0.81))
        }
    }
}

struct HomeScreen: View {
    @EnvironmentObject var game: GameStore
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                Image("Tavern").resizable().scaledToFill().frame(height: 470).clipped()
                    .clipShape(RoundedRectangle(cornerRadius: 18))
                Text("Bienvenue, \(game.pseudo)").font(.title2.bold())
                Text("Les contrats et les personnages sont partagés avec la compagnie.")
                    .foregroundStyle(.secondary)
                if let error = game.error { Text(error).foregroundStyle(.red) }
                Button("Actualiser la compagnie") { Task { await game.load() } }
            }.padding()
        }.navigationTitle("La Taverne")
    }
}
