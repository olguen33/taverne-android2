import SwiftUI

private let races = ["Humain","Orc","Nain","Elfe"]
private let origins = ["Citadin","Reclu","Vie sauvage"]
private let archetypes = ["Guerrier","Mage","Archer","Roublard","Barbare","Enquêteur","Explorateur","Paladin","Assassin","Chasseur"]
private let statLabels = ["CT · Capacité de tir","CC · Corps à corps","Endurance","Agilité","Force","Sociabilité","Force mentale","Perception","Intelligence"]
private let baseStats: [String:[Int]] = [
    "Guerrier":[30,70,50,50,40,40,50,40,40],"Mage":[70,10,30,40,20,50,70,50,70],
    "Archer":[70,30,40,70,20,40,30,60,50],"Roublard":[50,50,30,60,30,70,20,60,40],
    "Barbare":[60,40,70,60,70,20,70,60,10],"Enquêteur":[50,30,40,50,20,70,60,70,70],
    "Explorateur":[50,40,60,60,40,30,50,70,50],"Paladin":[20,70,60,30,60,20,60,20,50],
    "Assassin":[70,50,30,70,30,20,40,50,50],"Chasseur":[60,30,40,70,40,30,50,70,40]
]

struct CharacterStats: View {
    let role: String, race: String, origin: String
    var body: some View {
        let raceIndex = ["Humain":8,"Orc":4,"Nain":2,"Elfe":3][race]
        let originIndex = ["Citadin":5,"Reclu":6,"Vie sauvage":7][origin]
        let base = baseStats[role] ?? []
        ForEach(Array(base.enumerated()),id:\.offset) { index, score in
            LabeledContent(statLabels[index], value:"\(score + (raceIndex == index ? 10 : 0) + (originIndex == index ? 10 : 0))")
        }
    }
}

struct CharactersScreen: View {
    @EnvironmentObject var game: GameStore
    @State private var creating = false
    var body: some View {
        List {
            Section {
                Button("Créer un personnage",systemImage:"plus") { creating = true }
                NavigationLink("Voir les archétypes") { ArchetypesScreen() }
            }
            Section("Mes personnages") {
                if game.characters.isEmpty { Text("Aucun personnage.").foregroundStyle(.secondary) }
                ForEach(game.characters,id:\.rowID) { c in
                    NavigationLink {
                        CharacterDetail(characterID:value(c,"id"))
                    } label: {
                        VStack(alignment:.leading) {
                            Text(value(c,"name")).font(.headline)
                            Text("\(value(c,"race")) · \(value(c,"archetype")) · \(value(c,"origin"))")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
            }
        }
        .navigationTitle("Personnages")
        .sheet(isPresented:$creating) { NavigationStack { CharacterEditor(id:nil) } }
        .refreshable { await game.load() }
    }
}

extension Dictionary where Key == String, Value == Any {
    var rowID: String { value(self,"id") }
}

struct CharacterDetail: View {
    @EnvironmentObject var game: GameStore
    @Environment(\.dismiss) var dismiss
    let characterID: String
    @State private var editing = false, deleting = false
    @State private var gold = ""
    var body: some View {
        let c = game.characters.first { value($0,"id") == characterID } ?? [:]
        List {
            Section("Caractéristiques") {
                CharacterStats(role:value(c,"archetype"),race:value(c,"race"),origin:value(c,"origin"))
            }
            Section("Fiche") { Text(value(c,"sheet").isEmpty ? "Aucune note." : value(c,"sheet")) }
            Section("Histoire") { Text(value(c,"lore").isEmpty ? "Aucune histoire." : value(c,"lore")) }
            Section("Inventaire") { Text(value(c,"inventory").isEmpty ? "Vide" : value(c,"inventory")) }
            Section("Notes de campagne") { Text(value(c,"campaign_notes").isEmpty ? "Aucune note." : value(c,"campaign_notes")) }
            Section("Bourse") {
                Text("\(amount(c,"gold")) pièces d’or")
                TextField("Modifier la bourse",text:$gold).keyboardType(.numberPad)
                Button("Enregistrer la bourse") {
                    guard let number = Int(gold), number >= 0 else { return }
                    Task { await game.perform { _ = try await game.rpc("set_character_gold",["p_id":characterID,"p_gold":number]) } }
                }
            }
            Section {
                Button("Modifier le personnage") { editing = true }
                Button("Supprimer le personnage",role:.destructive) { deleting = true }
            }
        }
        .navigationTitle(value(c,"name"))
        .sheet(isPresented:$editing) { NavigationStack { CharacterEditor(id:characterID) } }
        .confirmationDialog("Supprimer ce personnage ?",isPresented:$deleting) {
            Button("Supprimer",role:.destructive) {
                Task { await game.perform { try await game.remove("characters",id:characterID) }; dismiss() }
            }
        }
        .onAppear { gold = "\(amount(c,"gold"))" }
    }
}

struct CharacterEditor: View {
    @EnvironmentObject var game: GameStore
    @Environment(\.dismiss) var dismiss
    let id: String?
    @State private var name = "", race = "Humain", role = "Guerrier", origin = "Citadin"
    @State private var sheet = "", lore = "", inventory = "", notes = ""
    var body: some View {
        Form {
            Section("Identité") {
                TextField("Nom",text:$name)
                Picker("Archétype",selection:$role) { ForEach(archetypes,id:\.self) { Text($0) } }
                Picker("Race",selection:$race) { ForEach(races,id:\.self) { Text($0) } }
                Picker("Origine",selection:$origin) { ForEach(origins,id:\.self) { Text($0) } }
            }
            Section("Caractéristiques") { CharacterStats(role:role,race:race,origin:origin) }
            Section("Fiche et notes") { TextEditor(text:$sheet).frame(minHeight:110) }
            Section("Histoire") { TextEditor(text:$lore).frame(minHeight:110) }
            Section("Inventaire") { TextEditor(text:$inventory).frame(minHeight:90) }
            Section("Notes de campagne") { TextEditor(text:$notes).frame(minHeight:90) }
        }
        .navigationTitle(id == nil ? "Nouveau personnage" : "Modifier")
        .toolbar {
            ToolbarItem(placement:.cancellationAction) { Button("Annuler") { dismiss() } }
            ToolbarItem(placement:.confirmationAction) {
                Button("Enregistrer") {
                    Task {
                        await game.perform {
                            _ = try await game.rpc("save_character",[
                                "p_id":id as Any? ?? NSNull(),"p_name":name,"p_race":race,
                                "p_archetype":role,"p_origin":origin,"p_sheet":sheet,
                                "p_lore":lore,"p_inventory":inventory,"p_notes":notes
                            ])
                        }
                        if game.error == nil { dismiss() }
                    }
                }.disabled(name.trimmingCharacters(in:.whitespaces).isEmpty)
            }
        }
        .onAppear {
            guard let id, let c = game.characters.first(where:{ value($0,"id") == id }) else { return }
            name=value(c,"name");race=value(c,"race");role=value(c,"archetype");origin=value(c,"origin")
            sheet=value(c,"sheet");lore=value(c,"lore");inventory=value(c,"inventory");notes=value(c,"campaign_notes")
        }
    }
}

struct ArchetypesScreen: View {
    var body: some View {
        List(archetypes,id:\.self) { role in
            Section(role) { CharacterStats(role:role,race:"",origin:"") }
        }.navigationTitle("Archétypes")
    }
}
