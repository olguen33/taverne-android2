import SwiftUI

struct CampaignScreen: View {
    @EnvironmentObject var game: GameStore
    @State private var title = ""
    @State private var bodyText = ""
    var body: some View {
        List {
            Section("Mon personnage") {
                ForEach(game.characters,id:\.rowID) { c in
                    NavigationLink { CharacterCampaign(id:value(c,"id")) }
                        label: { Text(value(c,"name")) }
                }
            }
            Section("Parchemin de la compagnie") {
                TextField("Titre",text:$title)
                TextField("Information à partager",text:$bodyText,axis:.vertical).lineLimit(3...8)
                Button("Publier") {
                    let heading=title.trimmingCharacters(in:.whitespaces),message=bodyText.trimmingCharacters(in:.whitespaces)
                    guard !heading.isEmpty, !message.isEmpty else { return }
                    Task { await game.perform {
                        try await game.insert("campaign_entries",["author_id":game.userId,"title":heading,"body":message])
                    }; if game.error == nil { title="";bodyText="" } }
                }
            }
            ForEach(game.campaignEntries,id:\.rowID) { entry in
                Section(value(entry,"title")) {
                    Text(value(entry,"body"))
                    Text("Publié par \(game.player(value(entry,"author_id")))").font(.caption)
                    if value(entry,"author_id") == game.userId {
                        Button("Supprimer",role:.destructive) {
                            Task { await game.perform { try await game.remove("campaign_entries",id:value(entry,"id")) } }
                        }
                    }
                }
            }
            if let error = game.error { Text(error).foregroundStyle(.red) }
        }.navigationTitle("Campagne").refreshable { await game.load() }
    }
}

struct CharacterCampaign: View {
    @EnvironmentObject var game: GameStore
    let id: String
    @State private var inventory = ""
    @State private var notes = ""
    var body: some View {
        let c=game.characters.first { value($0,"id") == id } ?? [:]
        List {
            Section("Fiche") {
                CharacterStats(role:value(c,"archetype"),race:value(c,"race"),origin:value(c,"origin"))
                Text(value(c,"sheet"))
            }
            Section("Inventaire · \(amount(c,"gold")) po") {
                TextEditor(text:$inventory).frame(minHeight:130)
                Button("Enregistrer l’inventaire") { update("inventory",inventory) }
            }
            Section("Notes") {
                TextEditor(text:$notes).frame(minHeight:130)
                Button("Enregistrer la note") { update("campaign_notes",notes) }
            }
            Section { NavigationLink("Lancer les dés") { DiceScreen() } }
        }
        .navigationTitle(value(c,"name"))
        .onAppear { inventory=value(c,"inventory");notes=value(c,"campaign_notes") }
    }
    private func update(_ field:String,_ text:String) {
        Task { await game.perform { _ = try await game.rpc("update_character_part",[
            "p_id":id,"p_field":field,"p_value":text
        ]) } }
    }
}

struct MoreScreen: View {
    @EnvironmentObject var game: GameStore
    var body: some View {
        List {
            Section("La compagnie") {
                NavigationLink("Boutique") { ShopScreen() }
                NavigationLink("Rumeurs") { RumoursScreen() }
                NavigationLink("Carte") { GameMapScreen() }
                NavigationLink("Espace MJ") { MasterScreen() }
                NavigationLink("Archétypes") { ArchetypesScreen() }
                NavigationLink("Dés") { DiceScreen() }
            }
            Section("Compte · \(game.pseudo)") {
                Button("Actualiser") { Task { await game.load() } }
                Button("Se déconnecter",role:.destructive) { game.signOut() }
            }
            if let error = game.error { Text(error).foregroundStyle(.red) }
        }.navigationTitle("Plus")
    }
}

struct ShopScreen: View {
    @EnvironmentObject var game: GameStore
    @State private var characterID = ""
    var body: some View {
        List {
            if game.characters.isEmpty { Text("Crée un personnage avant d’acheter.") }
            else {
                Picker("Personnage",selection:$characterID) {
                    ForEach(game.characters,id:\.rowID) { c in Text(value(c,"name")).tag(value(c,"id")) }
                }
                ForEach(game.catalog,id:\.rowID) { item in
                    Section(value(item,"name")) {
                        Text(value(item,"description"))
                        Text("\(amount(item,"price")) pièces d’or")
                        Button("Acheter") {
                            guard !characterID.isEmpty else { return }
                            Task { await game.perform { _ = try await game.rpc("buy_consumable",[
                                "p_character":characterID,"p_item":value(item,"id")
                            ]) } }
                        }
                    }
                }
            }
            if let error = game.error { Text(error).foregroundStyle(.red) }
        }.navigationTitle("Boutique")
            .onAppear { if characterID.isEmpty { characterID=game.characters.first.map { value($0,"id") } ?? "" } }
    }
}

struct RumoursScreen: View {
    @EnvironmentObject var game: GameStore
    var body: some View {
        List {
            ForEach(game.rumours,id:\.rowID) { r in Text(value(r,"description")) }
            if game.rumours.isEmpty { Text("Aucune rumeur pour le moment.") }
        }.navigationTitle("Rumeurs")
    }
}

struct GameMapScreen: View {
    @EnvironmentObject var game: GameStore
    var body: some View {
        ScrollView {
            GeometryReader { geo in
                Image("Map").resizable().scaledToFill().frame(width:geo.size.width,height:geo.size.height).clipped()
                    .overlay(alignment:.topLeading) {
                        ForEach(game.contracts.filter { value($0,"status") != "terminé" && $0["map_x"] != nil },id:\.rowID) { c in
                            NavigationLink {
                                ContractDetail(contractID:value(c,"id"))
                            } label: {
                                Image(systemName:"mappin.circle.fill").font(.title).foregroundStyle(.red)
                            }.position(x:CGFloat((c["map_x"] as? NSNumber)?.doubleValue ?? 0)*geo.size.width,
                                       y:CGFloat((c["map_y"] as? NSNumber)?.doubleValue ?? 0)*geo.size.height)
                        }
                    }
                    .overlay(alignment:.topLeading) {
                        ForEach(game.rumours,id:\.rowID) { r in
                            Image(systemName:"questionmark.circle.fill").font(.title).foregroundStyle(.blue)
                                .position(x:CGFloat((r["map_x"] as? NSNumber)?.doubleValue ?? 0)*geo.size.width,
                                          y:CGFloat((r["map_y"] as? NSNumber)?.doubleValue ?? 0)*geo.size.height)
                        }
                    }
            }.frame(height:550)
        }.navigationTitle("Carte")
    }
}

struct DiceScreen: View {
    @State private var sides=20
    @State private var count=1
    @State private var result=""
    var body: some View {
        Form {
            Picker("Dé",selection:$sides) {
                ForEach([4,6,8,10,12,20,100],id:\.self) { Text("D\($0)").tag($0) }
            }
            Stepper("Quantité : \(count)",value:$count,in:1...100)
            Button("Lancer") {
                let rolls=(0..<count).map { _ in Int.random(in:1...sides) }
                result="\(count)D\(sides) : \(rolls.map(String.init).joined(separator:" + ")) = \(rolls.reduce(0,+))"
            }
            Text(result)
        }.navigationTitle("Dés")
    }
}

struct MasterScreen: View {
    @EnvironmentObject var game: GameStore
    @State private var publishing = false
    @State private var rumour = ""
    @State private var x:CGFloat = -1
    @State private var y:CGFloat = -1
    var body: some View {
        List {
            Section("Contrats") { Button("Publier un contrat") { publishing = true } }
            Section("Placer une rumeur") {
                TextField("Indice",text:$rumour,axis:.vertical).lineLimit(2...5)
                MapPicker(x:$x,y:$y)
                Button("Publier") {
                    guard !rumour.isEmpty, x >= 0, y >= 0 else { return }
                    Task { await game.perform { try await game.insert("rumours",[
                        "id":UUID().uuidString,"owner_id":game.userId,"description":rumour,
                        "map_x":Double(x),"map_y":Double(y)
                    ]) }; if game.error == nil { rumour="";x = -1;y = -1 } }
                }
            }
            Section("Mes rumeurs") {
                ForEach(game.rumours.filter { value($0,"owner_id") == game.userId },id:\.rowID) { r in
                    VStack(alignment:.leading) {
                        Text(value(r,"description"))
                        Button("Supprimer",role:.destructive) {
                            Task { await game.perform { try await game.remove("rumours",id:value(r,"id")) } }
                        }
                    }
                }
            }
            if let error = game.error { Text(error).foregroundStyle(.red) }
        }
        .navigationTitle("Espace MJ")
        .sheet(isPresented:$publishing) { NavigationStack { ContractEditor(id:nil) } }
    }
}
