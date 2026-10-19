import 'dart:async';
import 'dart:convert';
import 'dart:math';
import 'dart:ui' show FontFeature;

import 'package:flutter/material.dart';
import 'package:home_widget/home_widget.dart';
import 'package:shared_preferences/shared_preferences.dart';

// ─────────────────────────── Entry point ───────────────────────────

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final store = TrackerStore();
  await store.load();
  runApp(GoalTrackerApp(store: store));
}

/// Must match the App Group enabled for Runner and the GoalWidget extension.
const widgetAppGroup = 'group.com.example.goalTracker';

// ─────────────────────────── Helpers ───────────────────────────

const _monthsShort = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const _monthsLong = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December'
];

String isoDate(DateTime d) =>
    '${d.year}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

String todayPlus(int days) => isoDate(DateTime.now().add(Duration(days: days)));

DateTime? parseDate(String s) => s.isEmpty ? null : DateTime.tryParse(s);

DateTime endOfDay(String s) {
  final d = DateTime.parse(s);
  return DateTime(d.year, d.month, d.day, 23, 59, 59);
}

String fmtShort(String s) {
  final d = parseDate(s);
  return d == null ? 'No date' : '${_monthsShort[d.month - 1]} ${d.day}, ${d.year}';
}

String fmtLong(String s) {
  final d = parseDate(s);
  return d == null ? 'Not set' : '${_monthsLong[d.month - 1]} ${d.day}, ${d.year}';
}

final _rng = Random();
String newId() => List.generate(7, (_) => _rng.nextInt(36).toRadixString(36)).join();

// ─────────────────────────── Palette ───────────────────────────

class Pal {
  final Color bg, card, ink, mute, line, acc, bad, ok, soft;
  const Pal({
    required this.bg, required this.card, required this.ink, required this.mute,
    required this.line, required this.acc, required this.bad, required this.ok,
    required this.soft,
  });

  static const light = Pal(
    bg: Color(0xFFE6F3FD), card: Colors.white, ink: Color(0xFF0F2335),
    mute: Color(0xFF52697A), line: Color(0xFFCDE2F2), acc: Color(0xFF0284C7),
    bad: Color(0xFFC2410C), ok: Color(0xFF15803D), soft: Color(0xFFD9ECFA),
  );
  static const dark = Pal(
    bg: Color(0xFF0C1A26), card: Color(0xFF132634), ink: Color(0xFFE6F2FB),
    mute: Color(0xFF8BA7BA), line: Color(0xFF24415A), acc: Color(0xFF38BDF8),
    bad: Color(0xFFFB923C), ok: Color(0xFF4ADE80), soft: Color(0xFF17354A),
  );

  static Pal of(BuildContext c) =>
      Theme.of(c).brightness == Brightness.dark ? dark : light;
}

// ─────────────────────────── Models ───────────────────────────

class Task {
  String id, name, desc, start, due, priority;
  int progress;
  bool done;

  Task({
    required this.id,
    required this.name,
    this.desc = '',
    this.start = '',
    this.due = '',
    this.priority = 'medium',
    this.progress = 0,
    this.done = false,
  });

  factory Task.fromJson(Map<String, dynamic> j) => Task(
        id: (j['id'] ?? newId()) as String,
        name: (j['name'] ?? '') as String,
        desc: (j['desc'] ?? '') as String,
        start: (j['start'] ?? '') as String,
        due: (j['due'] ?? '') as String,
        priority: (j['priority'] ?? 'medium') as String,
        progress: (j['progress'] as num?)?.toInt() ?? 0,
        done: (j['done'] ?? false) as bool,
      );

  Map<String, dynamic> toJson() => {
        'id': id, 'name': name, 'desc': desc, 'start': start, 'due': due,
        'priority': priority, 'progress': progress, 'done': done,
      };

  int get effectiveProgress => done ? 100 : progress;
  bool get overdue => !done && due.isNotEmpty && endOfDay(due).isBefore(DateTime.now());
}

class TaskList {
  String id, name;
  List<Task> tasks;
  TaskList({required this.id, required this.name, List<Task>? tasks}) : tasks = tasks ?? [];

  factory TaskList.fromJson(Map<String, dynamic> j) => TaskList(
        id: (j['id'] ?? newId()) as String,
        name: (j['name'] ?? '') as String,
        tasks: ((j['tasks'] ?? []) as List)
            .map((e) => Task.fromJson(Map<String, dynamic>.from(e as Map)))
            .toList(),
      );

  Map<String, dynamic> toJson() =>
      {'id': id, 'name': name, 'tasks': tasks.map((t) => t.toJson()).toList()};
}

class Goal {
  String id, title, deadline, created;
  List<TaskList> lists;
  Goal({
    required this.id,
    required this.title,
    required this.deadline,
    required this.created,
    List<TaskList>? lists,
  }) : lists = lists ?? [];

  factory Goal.fromJson(Map<String, dynamic> j) => Goal(
        id: (j['id'] ?? newId()) as String,
        title: (j['title'] ?? '') as String,
        deadline: (j['deadline'] ?? '') as String,
        created: (j['created'] ?? isoDate(DateTime.now())) as String,
        lists: ((j['lists'] ?? []) as List)
            .map((e) => TaskList.fromJson(Map<String, dynamic>.from(e as Map)))
            .toList(),
      );

  Map<String, dynamic> toJson() => {
        'id': id, 'title': title, 'deadline': deadline, 'created': created,
        'lists': lists.map((l) => l.toJson()).toList(),
      };

  Iterable<Task> get allTasks => lists.expand((l) => l.tasks);

  int get percent {
    final t = allTasks.toList();
    if (t.isEmpty) return 0;
    return (t.fold<int>(0, (a, x) => a + x.effectiveProgress) / t.length).round();
  }
}

// ─────────────────────────── Store (auto-saving) ───────────────────────────

class TrackerStore extends ChangeNotifier {
  static const _key = 'goal-tracker-v1';

  List<Goal> goals = [];
  String? activeId;
  SharedPreferences? _prefs;

  Goal? get active {
    for (final g in goals) {
      if (g.id == activeId) return g;
    }
    return goals.isEmpty ? null : goals.first;
  }

  Future<void> load() async {
    _prefs = await SharedPreferences.getInstance();
    try {
      await HomeWidget.setAppGroupId(widgetAppGroup); // iOS only
    } catch (_) {}
    try {
      final raw = _prefs!.getString(_key);
      if (raw != null) {
        final j = jsonDecode(raw) as Map<String, dynamic>;
        goals = ((j['goals'] ?? []) as List)
            .map((e) => Goal.fromJson(Map<String, dynamic>.from(e as Map)))
            .toList();
        activeId = j['active'] as String?;
        return;
      }
    } catch (_) {/* fall through to seed data */}
    _seed();
    await _persist();
  }

  void _seed() {
    Task t(String name, String desc, int off, String pri, int prog, bool done) => Task(
          id: newId(), name: name, desc: desc, start: todayPlus(0),
          due: todayPlus(off), priority: pri, progress: prog, done: done,
        );
    final g = Goal(
      id: newId(),
      title: 'Build an Android App',
      deadline: todayPlus(85),
      created: todayPlus(0),
      lists: [
        TaskList(id: newId(), name: 'Design', tasks: [
          t('Sketch main screens', 'Wireframes for the home and detail screens', 7, 'high', 100, true),
          t('Pick colors and fonts', '', 14, 'medium', 40, false),
        ]),
        TaskList(id: newId(), name: 'Build', tasks: [
          t('Set up project', 'Install tools, create repo', 10, 'high', 60, false),
          t('Build the home screen', '', 30, 'medium', 0, false),
        ]),
      ],
    );
    goals = [g];
    activeId = g.id;
  }

  Future<void> _persist() async {
    try {
      _prefs ??= await SharedPreferences.getInstance();
      await _prefs!.setString(
        _key,
        jsonEncode({'goals': goals.map((g) => g.toJson()).toList(), 'active': activeId}),
      );
    } catch (_) {}
    await _syncWidget();
  }

  /// Pushes the active goal's name, deadline and list count to the
  /// Android home-screen widget (days are computed natively so they stay
  /// correct as the date changes).
  Future<void> _syncWidget() async {
    try {
      final g = active;
      await HomeWidget.saveWidgetData<String>(
          'goal_name', g == null ? '' : (g.title.isEmpty ? 'Untitled' : g.title));
      await HomeWidget.saveWidgetData<String>('goal_deadline', g?.deadline ?? '');
      await HomeWidget.saveWidgetData<int>('goal_lists', g?.lists.length ?? 0);
      await HomeWidget.updateWidget(
          androidName: 'GoalWidgetProvider', iOSName: 'GoalWidget');
    } catch (_) {/* widget unsupported on this platform */}
  }

  /// Save and rebuild the UI.
  void changed() {
    notifyListeners();
    _persist();
  }

  /// Save only (for text fields that already show the latest text).
  void saveQuietly() => _persist();

  // Goals
  void selectGoal(String id) { activeId = id; changed(); }

  void addGoal() {
    final g = Goal(
      id: newId(), title: 'New goal', deadline: todayPlus(30),
      created: isoDate(DateTime.now()),
      lists: [TaskList(id: newId(), name: 'To do')],
    );
    goals.add(g);
    activeId = g.id;
    changed();
  }

  void deleteGoal(Goal g) {
    goals.remove(g);
    activeId = goals.isEmpty ? null : goals.first.id;
    changed();
  }

  // Lists
  void addList(Goal g) { g.lists.add(TaskList(id: newId(), name: 'New list')); changed(); }
  void deleteList(Goal g, TaskList l) { g.lists.remove(l); changed(); }

  // Tasks
  void upsertTask(TaskList l, Task data) {
    final i = l.tasks.indexWhere((t) => t.id == data.id);
    if (i >= 0) { l.tasks[i] = data; } else { l.tasks.add(data); }
    changed();
  }

  void deleteTask(TaskList l, Task t) { l.tasks.remove(t); changed(); }

  void toggleTask(Task t, bool v) {
    t.done = v;
    t.progress = v ? 100 : 0;
    changed();
  }

  void moveTask(TaskList l, int i, int delta) {
    final j = i + delta;
    if (j < 0 || j >= l.tasks.length) return;
    final t = l.tasks.removeAt(i);
    l.tasks.insert(j, t);
    changed();
  }
}

// ─────────────────────────── App ───────────────────────────

class GoalTrackerApp extends StatelessWidget {
  final TrackerStore store;
  const GoalTrackerApp({super.key, required this.store});

  ThemeData _theme(Brightness b) {
    final p = b == Brightness.dark ? Pal.dark : Pal.light;
    return ThemeData(
      useMaterial3: true,
      brightness: b,
      scaffoldBackgroundColor: p.bg,
      colorScheme: ColorScheme.fromSeed(seedColor: p.acc, brightness: b)
          .copyWith(primary: p.acc, surface: p.card, onSurface: p.ink),
      textTheme: ThemeData(brightness: b).textTheme.apply(bodyColor: p.ink, displayColor: p.ink),
      dividerColor: p.line,
      checkboxTheme: CheckboxThemeData(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(5)),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Goal & Deadline Tracker',
      debugShowCheckedModeBanner: false,
      theme: _theme(Brightness.light),
      darkTheme: _theme(Brightness.dark),
      themeMode: ThemeMode.system,
      home: TrackerPage(store: store),
    );
  }
}

class TrackerPage extends StatelessWidget {
  final TrackerStore store;
  const TrackerPage({super.key, required this.store});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: ListenableBuilder(
          listenable: store,
          builder: (context, _) {
            final g = store.active;
            return Center(
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 900),
                child: ListView(
                  padding: const EdgeInsets.all(14),
                  children: [
                    _GoalTabs(store: store),
                    const SizedBox(height: 12),
                    if (g == null)
                      const _Empty(
                        title: 'No goals yet',
                        message: 'Add your first goal to start tracking a deadline.',
                      )
                    else
                      ..._goalView(context, g),
                  ],
                ),
              ),
            );
          },
        ),
      ),
    );
  }

  List<Widget> _goalView(BuildContext context, Goal g) {
    final p = Pal.of(context);
    return [
      _Hero(key: ValueKey('hero-${g.id}'), store: store, goal: g),
      Padding(
        padding: const EdgeInsets.fromLTRB(0, 22, 0, 10),
        child: Row(
          children: [
            const Expanded(
              child: Text('Task lists', style: TextStyle(fontSize: 21, fontWeight: FontWeight.w800)),
            ),
            FilledButton(
              onPressed: () => store.addList(g),
              style: FilledButton.styleFrom(
                backgroundColor: p.acc,
                foregroundColor: Colors.white,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
              ),
              child: const Text('+ New list'),
            ),
          ],
        ),
      ),
      if (g.lists.isEmpty)
        const _Empty(message: 'No lists yet. Create one to start adding tasks.')
      else
        for (final l in g.lists) _ListCard(key: ValueKey(l.id), store: store, goal: g, list: l),
    ];
  }
}

class _Empty extends StatelessWidget {
  final String? title;
  final String message;
  const _Empty({this.title, required this.message});

  @override
  Widget build(BuildContext context) {
    final p = Pal.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 24, horizontal: 8),
      child: Column(children: [
        if (title != null)
          Text(title!, style: const TextStyle(fontSize: 21, fontWeight: FontWeight.w800)),
        Text(message, textAlign: TextAlign.center, style: TextStyle(color: p.mute)),
      ]),
    );
  }
}

// ─────────────────────────── Tabs ───────────────────────────

class _GoalTabs extends StatelessWidget {
  final TrackerStore store;
  const _GoalTabs({required this.store});

  @override
  Widget build(BuildContext context) {
    final p = Pal.of(context);
    final activeId = store.active?.id;
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(
        children: [
          for (final g in store.goals)
            Padding(
              padding: const EdgeInsets.only(right: 8),
              child: ChoiceChip(
                label: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 200),
                  child: Text(g.title.isEmpty ? 'Untitled' : g.title, overflow: TextOverflow.ellipsis),
                ),
                selected: g.id == activeId,
                showCheckmark: false,
                selectedColor: p.acc,
                backgroundColor: p.card,
                side: BorderSide(color: g.id == activeId ? p.acc : p.line),
                shape: const StadiumBorder(),
                labelStyle: TextStyle(color: g.id == activeId ? Colors.white : p.ink),
                onSelected: (_) => store.selectGoal(g.id),
              ),
            ),
          ActionChip(
            label: const Text('+ New goal'),
            onPressed: store.addGoal,
            backgroundColor: p.bg,
            side: BorderSide(color: p.mute.withAlpha(120)),
            shape: const StadiumBorder(),
            labelStyle: TextStyle(color: p.mute),
          ),
        ],
      ),
    );
  }
}

// ─────────────────────────── Inline text field ───────────────────────────

class _InlineField extends StatefulWidget {
  final String initial;
  final String hint;
  final TextStyle style;
  final int maxLength;
  final ValueChanged<String> onChanged;
  const _InlineField({
    super.key,
    required this.initial,
    required this.hint,
    required this.style,
    required this.onChanged,
    this.maxLength = 100,
  });

  @override
  State<_InlineField> createState() => _InlineFieldState();
}

class _InlineFieldState extends State<_InlineField> {
  late final TextEditingController _c = TextEditingController(text: widget.initial);

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final p = Pal.of(context);
    return TextField(
      controller: _c,
      style: widget.style,
      maxLength: widget.maxLength,
      maxLines: 1,
      onChanged: widget.onChanged,
      decoration: InputDecoration(
        isDense: true,
        counterText: '',
        hintText: widget.hint,
        hintStyle: TextStyle(color: p.mute),
        border: InputBorder.none,
        enabledBorder: InputBorder.none,
        focusedBorder: UnderlineInputBorder(borderSide: BorderSide(color: p.line, width: 2)),
        contentPadding: const EdgeInsets.symmetric(vertical: 6),
      ),
    );
  }
}

// ─────────────────────────── Hero ───────────────────────────

class _Hero extends StatelessWidget {
  final TrackerStore store;
  final Goal goal;
  const _Hero({super.key, required this.store, required this.goal});

  Future<void> _pickDeadline(BuildContext context) async {
    final now = DateTime.now();
    final init = parseDate(goal.deadline) ?? now;
    final r = await showDatePicker(
      context: context,
      initialDate: init,
      firstDate: DateTime(2000),
      lastDate: DateTime(2100),
    );
    if (r != null) {
      goal.deadline = isoDate(r);
      store.changed();
    }
  }

  Future<void> _confirmDelete(BuildContext context) async {
    final ok = await _confirm(context, 'Delete this goal and all its lists?');
    if (ok) store.deleteGoal(goal);
  }

  @override
  Widget build(BuildContext context) {
    final p = Pal.of(context);
    final tasks = goal.allTasks.toList();
    final done = tasks.where((t) => t.done).length;
    final pct = goal.percent;

    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: p.card,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: p.line),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _InlineField(
            key: ValueKey('title-${goal.id}'),
            initial: goal.title,
            hint: 'Goal or project name',
            style: const TextStyle(fontSize: 26, fontWeight: FontWeight.w800, height: 1.15),
            onChanged: (v) {
              goal.title = v;
              store.changed(); // updates the tab label too
            },
          ),
          const SizedBox(height: 8),
          Wrap(
            spacing: 10,
            runSpacing: 4,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              Text('Deadline', style: TextStyle(color: p.mute)),
              OutlinedButton.icon(
                onPressed: () => _pickDeadline(context),
                icon: const Icon(Icons.event, size: 18),
                label: Text(goal.deadline.isEmpty ? 'Set date' : fmtShort(goal.deadline)),
                style: OutlinedButton.styleFrom(
                  foregroundColor: p.ink,
                  side: BorderSide(color: p.line),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                ),
              ),
              TextButton(
                onPressed: () => _confirmDelete(context),
                style: TextButton.styleFrom(foregroundColor: p.bad),
                child: const Text('Delete goal'),
              ),
            ],
          ),
          const SizedBox(height: 12),
          _StatsGrid(goal: goal),
          const SizedBox(height: 18),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text('Progress', style: TextStyle(fontWeight: FontWeight.w600)),
              Flexible(
                child: Text('$pct% · $done/${tasks.length} tasks done',
                    style: const TextStyle(fontWeight: FontWeight.w600)),
              ),
            ],
          ),
          const SizedBox(height: 6),
          ClipRRect(
            borderRadius: BorderRadius.circular(99),
            child: LinearProgressIndicator(
              value: pct / 100,
              minHeight: 14,
              backgroundColor: p.line,
              valueColor: AlwaysStoppedAnimation(p.acc),
            ),
          ),
        ],
      ),
    );
  }
}

class _StatsGrid extends StatefulWidget {
  final Goal goal;
  const _StatsGrid({required this.goal});

  @override
  State<_StatsGrid> createState() => _StatsGridState();
}

class _StatsGridState extends State<_StatsGrid> {
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _timer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (mounted) setState(() {});
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Widget _stat(Pal p, double w, String label, Widget child) => SizedBox(
        width: w,
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          decoration: BoxDecoration(color: p.soft, borderRadius: BorderRadius.circular(14)),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(label, style: TextStyle(color: p.mute, fontSize: 13)),
              const SizedBox(height: 2),
              child,
            ],
          ),
        ),
      );

  @override
  Widget build(BuildContext context) {
    final p = Pal.of(context);
    final g = widget.goal;
    final hasDeadline = g.deadline.isNotEmpty;

    String big = '–';
    bool passed = false;
    Widget clock;
    if (!hasDeadline) {
      big = '–';
      clock = Text('Set a deadline', style: TextStyle(color: p.mute));
    } else {
      final ms = endOfDay(g.deadline).difference(DateTime.now()).inMilliseconds;
      if (ms <= 0) {
        passed = true;
        big = 'Passed';
        clock = Text('Deadline reached', style: TextStyle(color: p.bad, fontSize: 13));
      } else {
        final d = ms ~/ 86400000;
        final h = (ms ~/ 3600000) % 24;
        final m = (ms ~/ 60000) % 60;
        final s = (ms ~/ 1000) % 60;
        big = '$d ${d == 1 ? 'day' : 'days'} left';
        Widget cell(int v, String u) => Expanded(
              child: Container(
                margin: const EdgeInsets.only(right: 6),
                padding: const EdgeInsets.symmetric(vertical: 6),
                decoration: BoxDecoration(color: p.card, borderRadius: BorderRadius.circular(10)),
                child: Column(children: [
                  Text(v.toString().padLeft(2, '0'),
                      style: const TextStyle(
                          fontWeight: FontWeight.w700,
                          fontSize: 19,
                          fontFeatures: [FontFeature.tabularFigures()])),
                  Text(u, style: TextStyle(fontSize: 11, color: p.mute)),
                ]),
              ),
            );
        clock = Row(children: [cell(d, 'd'), cell(h, 'h'), cell(m, 'm'), cell(s, 's')]);
      }
    }

    final total = !hasDeadline
        ? null
        : (DateTime.parse(g.deadline).difference(DateTime.parse(g.created)).inHours / 24)
            .round()
            .clamp(0, 100000);

    return LayoutBuilder(builder: (context, c) {
      const gap = 12.0;
      final half = (c.maxWidth - gap) / 2;
      final wide = c.maxWidth >= 640;
      final w = wide ? (c.maxWidth - gap * 3) / 4 : half;
      final full = c.maxWidth;
      return Wrap(
        spacing: gap,
        runSpacing: gap,
        children: [
          _stat(
            p, w, 'Days remaining',
            Text(big,
                style: TextStyle(
                    fontSize: big.length > 8 ? 22 : 30,
                    fontWeight: FontWeight.w800,
                    height: 1.1,
                    color: passed ? p.bad : p.acc)),
          ),
          _stat(p, wide ? w : half, 'Deadline date',
              Text(fmtLong(g.deadline), style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16))),
          _stat(p, wide ? w : half, 'Total days (from ${fmtShort(g.created)})',
              Text(total == null ? '–' : '$total days',
                  style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16))),
          _stat(p, wide ? w : full, 'Time left', clock),
        ],
      );
    });
  }
}

// ─────────────────────────── Lists & tasks ───────────────────────────

class _ListCard extends StatelessWidget {
  final TrackerStore store;
  final Goal goal;
  final TaskList list;
  const _ListCard({super.key, required this.store, required this.goal, required this.list});

  Future<void> _openEditor(BuildContext context, [Task? t]) async {
    final result = await showModalBottomSheet<Task>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      showDragHandle: true,
      builder: (_) => TaskForm(initial: t),
    );
    if (result != null) store.upsertTask(list, result);
  }

  @override
  Widget build(BuildContext context) {
    final p = Pal.of(context);
    final n = list.tasks.length;
    final doneCount = list.tasks.where((t) => t.done).length;

    return Container(
      margin: const EdgeInsets.only(bottom: 14),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: p.card,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: p.line),
      ),
      child: Column(
        children: [
          Row(
            children: [
              Expanded(
                child: _InlineField(
                  key: ValueKey('name-${list.id}'),
                  initial: list.name,
                  hint: 'List name',
                  maxLength: 60,
                  style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w700),
                  onChanged: (v) {
                    list.name = v;
                    store.saveQuietly();
                  },
                ),
              ),
              Text('$doneCount/$n', style: TextStyle(color: p.mute, fontSize: 13)),
              const SizedBox(width: 6),
              OutlinedButton(
                onPressed: () => _openEditor(context),
                style: OutlinedButton.styleFrom(
                  foregroundColor: p.ink,
                  side: BorderSide(color: p.line),
                  visualDensity: VisualDensity.compact,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                ),
                child: const Text('+ Task'),
              ),
              IconButton(
                tooltip: 'Delete list',
                icon: Icon(Icons.delete_outline, color: p.mute),
                onPressed: () async {
                  if (await _confirm(context, 'Delete this list and its tasks?')) {
                    store.deleteList(goal, list);
                  }
                },
              ),
            ],
          ),
          if (n == 0)
            Padding(
              padding: const EdgeInsets.all(12),
              child: Text('No tasks yet. Tap “+ Task” to add one.', style: TextStyle(color: p.mute)),
            )
          else
            for (var i = 0; i < n; i++)
              _TaskTile(
                key: ValueKey(list.tasks[i].id),
                store: store,
                list: list,
                task: list.tasks[i],
                index: i,
                count: n,
                onEdit: () => _openEditor(context, list.tasks[i]),
              ),
        ],
      ),
    );
  }
}

class _TaskTile extends StatelessWidget {
  final TrackerStore store;
  final TaskList list;
  final Task task;
  final int index, count;
  final VoidCallback onEdit;
  const _TaskTile({
    super.key,
    required this.store,
    required this.list,
    required this.task,
    required this.index,
    required this.count,
    required this.onEdit,
  });

  static const _pillColors = {
    'high': [Color(0xFFFDE2D3), Color(0xFF9A3412)],
    'medium': [Color(0xFFFDF0C4), Color(0xFF7A5B00)],
    'low': [Color(0xFFD9F2E3), Color(0xFF166534)],
  };

  Widget _icon(IconData i, String tip, VoidCallback? f, Pal p) => SizedBox(
        width: 34,
        height: 34,
        child: IconButton(
          padding: EdgeInsets.zero,
          iconSize: 18,
          tooltip: tip,
          color: p.mute,
          icon: Icon(i),
          onPressed: f,
        ),
      );

  @override
  Widget build(BuildContext context) {
    final p = Pal.of(context);
    final t = task;
    final pr = t.effectiveProgress;
    final pill = _pillColors[t.priority] ?? _pillColors['medium']!;
    final dates = [
      if (t.start.isNotEmpty) fmtShort(t.start),
      if (t.due.isNotEmpty) fmtShort(t.due) + (t.overdue ? ' (overdue)' : ''),
    ];

    return Container(
      decoration: BoxDecoration(border: Border(top: BorderSide(color: p.line))),
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Checkbox(
            value: t.done,
            activeColor: p.acc,
            onChanged: (v) => store.toggleTask(t, v ?? false),
          ),
          Expanded(
            child: Padding(
              padding: const EdgeInsets.only(top: 10),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    t.name,
                    style: TextStyle(
                      fontWeight: FontWeight.w600,
                      color: t.done ? p.mute : p.ink,
                      decoration: t.done ? TextDecoration.lineThrough : null,
                    ),
                  ),
                  if (t.desc.isNotEmpty)
                    Text(t.desc, style: TextStyle(color: p.mute, fontSize: 14)),
                  const SizedBox(height: 4),
                  Wrap(
                    spacing: 10,
                    runSpacing: 4,
                    crossAxisAlignment: WrapCrossAlignment.center,
                    children: [
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 1),
                        decoration: BoxDecoration(color: pill[0], borderRadius: BorderRadius.circular(99)),
                        child: Text(t.priority,
                            style: TextStyle(color: pill[1], fontSize: 12.5, fontWeight: FontWeight.w600)),
                      ),
                      if (dates.isNotEmpty)
                        Text(dates.join(' → '),
                            style: TextStyle(fontSize: 13, color: t.overdue ? p.bad : p.mute)),
                      Row(mainAxisSize: MainAxisSize.min, children: [
                        SizedBox(
                          width: 70,
                          child: ClipRRect(
                            borderRadius: BorderRadius.circular(9),
                            child: LinearProgressIndicator(
                              value: pr / 100,
                              minHeight: 6,
                              backgroundColor: p.line,
                              valueColor: AlwaysStoppedAnimation(p.acc),
                            ),
                          ),
                        ),
                        const SizedBox(width: 6),
                        Text('$pr%', style: TextStyle(fontSize: 13, color: p.mute)),
                      ]),
                    ],
                  ),
                ],
              ),
            ),
          ),
          Column(children: [
            Row(children: [
              _icon(Icons.arrow_upward, 'Move up',
                  index == 0 ? null : () => store.moveTask(list, index, -1), p),
              _icon(Icons.edit_outlined, 'Edit task', onEdit, p),
            ]),
            Row(children: [
              _icon(Icons.arrow_downward, 'Move down',
                  index == count - 1 ? null : () => store.moveTask(list, index, 1), p),
              _icon(Icons.delete_outline, 'Delete task', () => store.deleteTask(list, t), p),
            ]),
          ]),
        ],
      ),
    );
  }
}

// ─────────────────────────── Task form (bottom sheet) ───────────────────────────

class TaskForm extends StatefulWidget {
  final Task? initial;
  const TaskForm({super.key, this.initial});

  @override
  State<TaskForm> createState() => _TaskFormState();
}

class _TaskFormState extends State<TaskForm> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _name;
  late final TextEditingController _desc;
  late String _start, _due, _priority;
  late bool _done;
  late double _progress;

  @override
  void initState() {
    super.initState();
    final t = widget.initial;
    _name = TextEditingController(text: t?.name ?? '');
    _desc = TextEditingController(text: t?.desc ?? '');
    _start = t?.start ?? isoDate(DateTime.now());
    _due = t?.due ?? '';
    _priority = t?.priority ?? 'medium';
    _done = t?.done ?? false;
    _progress = (t?.effectiveProgress ?? 0).toDouble();
  }

  @override
  void dispose() {
    _name.dispose();
    _desc.dispose();
    super.dispose();
  }

  Future<void> _pick(bool isStart) async {
    final init = parseDate(isStart ? _start : _due) ?? DateTime.now();
    final r = await showDatePicker(
      context: context,
      initialDate: init,
      firstDate: DateTime(2000),
      lastDate: DateTime(2100),
    );
    if (r != null) setState(() => isStart ? _start = isoDate(r) : _due = isoDate(r));
  }

  void _save() {
    if (!_formKey.currentState!.validate()) return;
    Navigator.pop(
      context,
      Task(
        id: widget.initial?.id ?? newId(),
        name: _name.text.trim(),
        desc: _desc.text.trim(),
        start: _start,
        due: _due,
        priority: _priority,
        done: _done,
        progress: _done ? 100 : _progress.round(),
      ),
    );
  }

  Widget _dateField(String label, String value, bool isStart) {
    final p = Pal.of(context);
    return InkWell(
      borderRadius: BorderRadius.circular(10),
      onTap: () => _pick(isStart),
      child: InputDecorator(
        decoration: InputDecoration(
          labelText: label,
          border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
          isDense: true,
          suffixIcon: (!isStart && value.isNotEmpty)
              ? IconButton(
                  icon: const Icon(Icons.close, size: 18),
                  onPressed: () => setState(() => _due = ''),
                )
              : const Icon(Icons.event, size: 18),
        ),
        child: Text(value.isEmpty ? 'None' : fmtShort(value),
            style: TextStyle(color: value.isEmpty ? p.mute : p.ink)),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final p = Pal.of(context);
    final editing = widget.initial != null;
    return Padding(
      padding: EdgeInsets.fromLTRB(20, 0, 20, MediaQuery.of(context).viewInsets.bottom + 20),
      child: SingleChildScrollView(
        child: Form(
          key: _formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(editing ? 'Edit task' : 'Add task',
                  style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800)),
              const SizedBox(height: 14),
              TextFormField(
                controller: _name,
                autofocus: !editing,
                maxLength: 120,
                textCapitalization: TextCapitalization.sentences,
                decoration: InputDecoration(
                  labelText: 'Task name',
                  border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
                ),
                validator: (v) => (v == null || v.trim().isEmpty) ? 'Please enter a name' : null,
              ),
              const SizedBox(height: 8),
              TextFormField(
                controller: _desc,
                maxLines: 2,
                textCapitalization: TextCapitalization.sentences,
                decoration: InputDecoration(
                  labelText: 'Description',
                  border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
                ),
              ),
              const SizedBox(height: 14),
              Row(children: [
                Expanded(child: _dateField('Start date', _start, true)),
                const SizedBox(width: 10),
                Expanded(child: _dateField('Due date', _due, false)),
              ]),
              const SizedBox(height: 16),
              const Text('Priority', style: TextStyle(fontWeight: FontWeight.w600)),
              const SizedBox(height: 6),
              SizedBox(
                width: double.infinity,
                child: SegmentedButton<String>(
                  showSelectedIcon: false,
                  segments: const [
                    ButtonSegment(value: 'high', label: Text('High')),
                    ButtonSegment(value: 'medium', label: Text('Medium')),
                    ButtonSegment(value: 'low', label: Text('Low')),
                  ],
                  selected: {_priority},
                  onSelectionChanged: (s) => setState(() => _priority = s.first),
                ),
              ),
              const SizedBox(height: 14),
              const Text('Status', style: TextStyle(fontWeight: FontWeight.w600)),
              const SizedBox(height: 6),
              SizedBox(
                width: double.infinity,
                child: SegmentedButton<bool>(
                  showSelectedIcon: false,
                  segments: const [
                    ButtonSegment(value: false, label: Text('Incomplete')),
                    ButtonSegment(value: true, label: Text('Completed')),
                  ],
                  selected: {_done},
                  onSelectionChanged: (s) => setState(() {
                    _done = s.first;
                    if (_done) _progress = 100;
                  }),
                ),
              ),
              const SizedBox(height: 14),
              Text('Progress: ${_progress.round()}%',
                  style: const TextStyle(fontWeight: FontWeight.w600)),
              Slider(
                value: _progress,
                min: 0,
                max: 100,
                divisions: 20,
                activeColor: p.acc,
                label: '${_progress.round()}%',
                onChanged: (v) => setState(() {
                  _progress = v;
                  _done = v >= 100;
                }),
              ),
              const SizedBox(height: 8),
              Row(mainAxisAlignment: MainAxisAlignment.end, children: [
                TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
                const SizedBox(width: 8),
                FilledButton(
                  onPressed: _save,
                  style: FilledButton.styleFrom(backgroundColor: p.acc, foregroundColor: Colors.white),
                  child: const Text('Save task'),
                ),
              ]),
            ],
          ),
        ),
      ),
    );
  }
}

// ─────────────────────────── Confirm dialog ───────────────────────────

Future<bool> _confirm(BuildContext context, String message) async {
  final r = await showDialog<bool>(
    context: context,
    builder: (ctx) => AlertDialog(
      content: Text(message),
      actions: [
        TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
        TextButton(
          onPressed: () => Navigator.pop(ctx, true),
          child: Text('Delete', style: TextStyle(color: Pal.of(ctx).bad)),
        ),
      ],
    ),
  );
  return r ?? false;
}
