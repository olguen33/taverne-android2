import SwiftUI

struct ContractEditor: View {
    @EnvironmentObject var game: GameStore
    @Environment(\.dismiss) var dismiss
    let id: String?
    @State private var title = ""
    @State private var description = ""
    @State private var reward = ""
    @State private var danger = 1
    @State private var places = 4
    @State private var days: Set<Int> = []
    @State private var start = Calendar.current.date(from:DateComponents(hour:19)) ?? Date()
    @State private var end = Calendar.current.date(from:DateComponents(hour:23)) ?? Date()
    @State private var x: CGFloat = -1
    @State private var y: CGFloat = -1

    var body: some View {
        Form {
            Section("Contrat") {
                TextField("Titre",text:$title)
                TextField("Description",text:$description,axis:.vertical).lineLimit(4...8)
                TextField("Récompense en pièces d’or",text:$reward).keyboardType(.numberPad)
                Stepper("Danger : \(danger)/5",value:$danger,in:1...5)
                Stepper("Places : \(places)",value:$places,in:1...12)
            }
            Section("Disponibilités du MJ") {
                ForEach(0..<7,id:\.self) { i in
                    Button { if days.contains(i) { days.remove(i) } else { days.insert(i) } } label: {
                        Label(["Lundi","Mardi","Mercredi","Jeudi","Vendredi","Samedi","Dimanche"][i],systemImage:days.contains(i) ? "checkmark.square.fill" : "square")
                    }
                }
                DatePicker("Début",selection:$start,displayedComponents:.hourAndMinute)
                DatePicker("Fin",selection:$end,displayedComponents:.hourAndMinute)
            }
            Section("Lieu sur la carte") {
                MapPicker(x:$x,y:$y)
                Text("Touche la carte pour placer le contrat.").font(.caption)
            }
            if let error = game.error { Text(error).foregroundStyle(.red) }
        }
        .navigationTitle(id == nil ? "Proposer un contrat" : "Modifier le contrat")
        .toolbar {
            ToolbarItem(placement:.cancellationAction) { Button("Annuler") { dismiss() } }
            ToolbarItem(placement:.confirmationAction) {
                Button("Enregistrer") {
                    guard let gold = Int(reward), gold > 0, !days.isEmpty, x >= 0, y >= 0 else { return }
                    let cal = Calendar.current
                    let first = cal.component(.hour,from:start)*60+cal.component(.minute,from:start)
                    let last = cal.component(.hour,from:end)*60+cal.component(.minute,from:end)
                    guard first != last else { return }
                    Task {
                        await game.perform {
                            _ = try await game.rpc(id == nil ? "publish_contract_once" : "edit_contract",[
                                "p_id":id ?? UUID().uuidString,"p_title":title,"p_description":description,
                                "p_reward":gold,"p_danger":danger,"p_places":places,
                                "p_x":Double(x),"p_y":Double(y),"p_days":Array(days).sorted(),
                                "p_start":first,"p_end":last
                            ])
                        }
                        if game.error == nil { dismiss() }
                    }
                }.disabled(title.isEmpty || description.isEmpty || reward.isEmpty || days.isEmpty || x < 0)
            }
        }
        .onAppear {
            guard let id, let c = game.contracts.first(where:{ value($0,"id") == id }) else { return }
            title=value(c,"title"); description=value(c,"description")
            reward="\(amount(c,"reward_gold"))";danger=amount(c,"danger");places=amount(c,"places")
            x=CGFloat((c["map_x"] as? NSNumber)?.doubleValue ?? -1)
            y=CGFloat((c["map_y"] as? NSNumber)?.doubleValue ?? -1)
            let slots = game.slots.filter { value($0,"contract_id") == id }
            days=Set(slots.map { amount($0,"weekday") })
            if let first = slots.first {
                let a=amount(first,"start_minute"),b=amount(first,"end_minute")
                start=Calendar.current.date(from:DateComponents(hour:a/60,minute:a%60)) ?? start
                end=Calendar.current.date(from:DateComponents(hour:b/60,minute:b%60)) ?? end
            }
        }
    }
}

struct MapPicker: View {
    @Binding var x: CGFloat
    @Binding var y: CGFloat
    var body: some View {
        GeometryReader { geo in
            Image("Map").resizable().scaledToFill().frame(width:geo.size.width,height:geo.size.height).clipped()
                .overlay(alignment:.topLeading) {
                    if x >= 0, y >= 0 {
                        Image(systemName:"mappin.circle.fill")
                            .foregroundStyle(.red).font(.title)
                            .position(x:x*geo.size.width,y:y*geo.size.height)
                    }
                }
                .contentShape(Rectangle())
                .gesture(SpatialTapGesture().onEnded { event in
                    x=min(1,max(0,event.location.x/geo.size.width))
                    y=min(1,max(0,event.location.y/geo.size.height))
                })
        }.frame(height:360).clipShape(RoundedRectangle(cornerRadius:12))
    }
}
