import SwiftUI

private let weekdays = ["Lundi","Mardi","Mercredi","Jeudi","Vendredi","Samedi","Dimanche"]
private struct SlotSelection: Identifiable { let id: String; let row: Row }
private func slotText(_ s: Row) -> String {
    func time(_ n: Int) -> String { String(format:"%02d:%02d",n/60,n%60) }
    let day = weekdays[max(0,min(6,amount(s,"weekday")))]
    return "\(day) · \(time(amount(s,"start_minute"))) – \(time(amount(s,"end_minute")))"
}

struct ContractsScreen: View {
    @EnvironmentObject var game: GameStore
    @State private var completed = false
    @State private var publishing = false
    var body: some View {
        List {
            Picker("Contrats",selection:$completed) {
                Text("En cours").tag(false); Text("Terminés").tag(true)
            }.pickerStyle(.segmented)
            if !completed { Button("Proposer un contrat",systemImage:"plus") { publishing = true } }
            ForEach(game.contracts.filter { (value($0,"status") == "terminé") == completed },id:\.rowID) { c in
                NavigationLink {
                    ContractDetail(contractID:value(c,"id"))
                } label: {
                    VStack(alignment:.leading,spacing:6) {
                        Text(value(c,"title")).font(.headline)
                        Text("\(value(c,"status")) · Danger \(amount(c,"danger"))/5")
                            .font(.caption).foregroundStyle(.secondary)
                        Text(value(c,"description")).lineLimit(3)
                        Text("Proposé par \(game.player(value(c,"proposer_id")))")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
        }
        .navigationTitle("Contrats")
        .sheet(isPresented:$publishing) { NavigationStack { ContractEditor(id:nil) } }
        .refreshable { await game.load() }
    }
}

struct ContractDetail: View {
    @EnvironmentObject var game: GameStore
    @Environment(\.dismiss) var dismiss
    let contractID: String
    @State private var chosenCharacter = ""
    @State private var selectedSlots: Set<String> = []
    @State private var message = ""
    @State private var editing = false
    @State private var deleting = false
    @State private var lockingSlot: SlotSelection?
    @State private var chosenDate = Date()
    var body: some View {
        let c = game.contracts.first { value($0,"id") == contractID } ?? [:]
        let slots = game.slots.filter { value($0,"contract_id") == contractID }
        let members = game.participants.filter { value($0,"contract_id") == contractID }
        List {
            Section {
                Text(value(c,"description"))
                Text("Proposé par \(game.player(value(c,"proposer_id")))")
                LabeledContent("Récompense",value:"\(amount(c,"reward_gold")) po")
                LabeledContent("Danger",value:"\(amount(c,"danger"))/5")
            }
            Section("Disponibilités du MJ") {
                ForEach(slots,id:\.rowID) { s in
                    let voters = game.votes.filter { value($0,"slot_id") == value(s,"id") }
                    VStack(alignment:.leading) {
                        Text(slotText(s)).font(.headline)
                        if value(c,"locked_slot_id") == value(s,"id"), !value(c,"locked_date").isEmpty {
                            Text("Date : \(value(c,"locked_date"))")
                        }
                        Text(voters.isEmpty ? "Aucun votant" : voters.map { game.player(game.owner(value($0,"character_id"))) }.joined(separator:", "))
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
            Section("La compagnie") {
                if members.isEmpty { Text("Aucun personnage inscrit") }
                ForEach(members,id:\.memberID) { m in Text(game.character(value(m,"character_id"))) }
            }
            if value(c,"status") == "ouvert", !game.characters.isEmpty {
                Section("Inscription et votes") {
                    Picker("Ton personnage",selection:$chosenCharacter) {
                        ForEach(game.characters,id:\.rowID) { ch in Text(value(ch,"name")).tag(value(ch,"id")) }
                    }
                    ForEach(slots,id:\.rowID) { s in
                        let slotID = value(s,"id")
                        Button {
                            if selectedSlots.contains(slotID) { selectedSlots.remove(slotID) }
                            else { selectedSlots.insert(slotID) }
                        } label: {
                            Label(slotText(s),systemImage:selectedSlots.contains(slotID) ? "checkmark.square.fill" : "square")
                        }
                    }
                    Button("Inscrire et voter") {
                        guard !chosenCharacter.isEmpty, !selectedSlots.isEmpty || slots.isEmpty else { return }
                        Task { await game.perform {
                            _ = try await game.rpc("join_and_vote",[
                                "p_contract":contractID,"p_character":chosenCharacter,"p_slots":Array(selectedSlots)
                            ])
                        } }
                    }
                }
            }
            Section("À la table") {
                ForEach(game.messages.filter { value($0,"contract_id") == contractID },id:\.messageID) { m in
                    Text("\(game.character(value(m,"character_id"))) : \(value(m,"body"))")
                }
                if !game.characters.isEmpty {
                    TextField("Message",text:$message,axis:.vertical)
                    Button("Envoyer") {
                        let body = message.trimmingCharacters(in:.whitespacesAndNewlines)
                        guard !body.isEmpty, !chosenCharacter.isEmpty else { return }
                        Task { await game.perform {
                            try await game.insert("messages",[
                                "contract_id":contractID,"character_id":chosenCharacter,"body":body
                            ])
                        }; if game.error == nil { message = "" } }
                    }
                }
            }
            if value(c,"proposer_id") == game.userId {
                Section("Gestion MJ") {
                    if value(c,"status") == "ouvert" {
                        Button("Modifier le contrat") { editing = true }
                        ForEach(slots,id:\.rowID) { s in
                            Button("Bloquer une date · \(slotText(s))") { lockingSlot = SlotSelection(id:value(s,"id"),row:s) }
                        }
                        Button("Supprimer le contrat",role:.destructive) { deleting = true }
                    }
                    if value(c,"status") == "planifié" {
                        Button("Terminer et partager la prime") {
                            Task { await game.perform { _ = try await game.rpc("complete_contract",["p_contract":contractID]) } }
                        }
                    }
                }
            }
            if let error = game.error { Section { Text(error).foregroundStyle(.red) } }
        }
        .navigationTitle(value(c,"title"))
        .sheet(isPresented:$editing) { NavigationStack { ContractEditor(id:contractID) } }
        .sheet(item:$lockingSlot) { chosen in
            let s = chosen.row
            NavigationStack {
                Form {
                    DatePicker("Date",selection:$chosenDate,in:Date()...,displayedComponents:.date)
                    Text("Jour requis : \(weekdays[amount(s,"weekday")])")
                }.navigationTitle("Bloquer une date").toolbar {
                    ToolbarItem(placement:.cancellationAction) { Button("Annuler") { lockingSlot = nil } }
                    ToolbarItem(placement:.confirmationAction) {
                        Button("Bloquer") {
                            let weekday = (Calendar.current.component(.weekday,from:chosenDate)+5)%7
                            guard weekday == amount(s,"weekday") else { game.error = "La date ne correspond pas au créneau."; return }
                            let date = ISO8601DateFormatter().string(from:chosenDate).prefix(10)
                            Task { await game.perform { _ = try await game.rpc("lock_contract_date",[
                                "p_contract":contractID,"p_slot":value(s,"id"),"p_date":String(date)
                            ]) }; lockingSlot = nil }
                        }
                    }
                }
            }
        }
        .confirmationDialog("Supprimer le contrat ?",isPresented:$deleting) {
            Button("Supprimer",role:.destructive) {
                Task { await game.perform { try await game.remove("contracts",id:contractID) }; dismiss() }
            }
        }
        .onAppear { if chosenCharacter.isEmpty { chosenCharacter = game.characters.first.map { value($0,"id") } ?? "" } }
        .onChange(of:chosenCharacter) { newValue in
            selectedSlots = Set(game.votes.filter { value($0,"character_id") == newValue }.map { value($0,"slot_id") })
        }
    }
}

extension Dictionary where Key == String, Value == Any {
    var memberID: String { value(self,"contract_id") + value(self,"character_id") }
    var messageID: String { String(amount(self,"id")) }
}
