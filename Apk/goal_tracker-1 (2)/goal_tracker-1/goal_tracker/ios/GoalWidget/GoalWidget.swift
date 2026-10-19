import SwiftUI
import WidgetKit

// Must match `widgetAppGroup` in lib/main.dart and the App Group capability
// enabled on both the Runner and GoalWidget targets.
private let appGroup = "group.com.example.goalTracker"

struct GoalEntry: TimelineEntry {
    let date: Date
    let name: String
    let days: Int?      // nil = no deadline
    let lists: Int
}

struct Provider: TimelineProvider {
    func placeholder(in context: Context) -> GoalEntry {
        GoalEntry(date: Date(), name: "My goal", days: 30, lists: 2)
    }

    func getSnapshot(in context: Context, completion: @escaping (GoalEntry) -> Void) {
        completion(load())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<GoalEntry>) -> Void) {
        // Refresh shortly after midnight so the day count stays correct.
        let cal = Calendar.current
        let midnight = cal.startOfDay(for: cal.date(byAdding: .day, value: 1, to: Date())!)
        let refresh = cal.date(byAdding: .minute, value: 5, to: midnight)!
        completion(Timeline(entries: [load()], policy: .after(refresh)))
    }

    private func load() -> GoalEntry {
        let d = UserDefaults(suiteName: appGroup)
        let name = d?.string(forKey: "goal_name") ?? ""
        let deadline = d?.string(forKey: "goal_deadline") ?? ""
        let lists = d?.integer(forKey: "goal_lists") ?? 0
        return GoalEntry(date: Date(), name: name, days: daysLeft(deadline), lists: lists)
    }

    private func daysLeft(_ deadline: String) -> Int? {
        if deadline.isEmpty { return nil }
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd"
        guard let target = f.date(from: deadline) else { return nil }
        let today = Calendar.current.startOfDay(for: Date())
        return Calendar.current.dateComponents([.day], from: today, to: target).day
    }
}

private let brand = Color(red: 0.008, green: 0.518, blue: 0.780)   // #0284C7

struct GoalWidgetView: View {
    let entry: GoalEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(entry.name.isEmpty ? "No goal yet" : entry.name)
                .font(.system(size: 15, weight: .bold))
                .lineLimit(2)

            HStack(alignment: .lastTextBaseline, spacing: 6) {
                Text(daysText)
                    .font(.system(size: 38, weight: .bold))
                    .minimumScaleFactor(0.6)
                    .lineLimit(1)
                if !labelText.isEmpty {
                    Text(labelText).font(.system(size: 13)).opacity(0.85)
                }
            }

            Text(entry.lists == 1 ? "1 list" : "\(entry.lists) lists")
                .font(.system(size: 13))
                .opacity(0.85)
        }
        .foregroundColor(.white)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .modifier(WidgetBackground())
    }

    private var daysText: String {
        guard let d = entry.days else { return "–" }
        return d < 0 ? "Passed" : String(d)
    }

    private var labelText: String {
        guard let d = entry.days, d >= 0 else { return "" }
        return d == 1 ? "day left" : "days left"
    }
}

struct WidgetBackground: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 17.0, *) {
            content.containerBackground(brand, for: .widget)
        } else {
            content.padding(14).background(brand)
        }
    }
}

@main
struct GoalWidget: Widget {
    let kind = "GoalWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: Provider()) { entry in
            GoalWidgetView(entry: entry)
        }
        .configurationDisplayName("Goal Tracker")
        .description("Goal name, days left and number of lists.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}
