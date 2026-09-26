/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.keys;

import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javax.swing.InputMap;
import javax.swing.KeyStroke;

import kr.ac.hallym.hcs.app.Settings;

/**
 * 바꿀 수 있는 단축키(E-09): 명령마다 기본 키와 사용자가 바꾼 키(앱 환경설정 {@code keys.bind.<명령>}). ? 표와 설정
 * 창이 이 표를 함께 쓰고, 창에 단 키(InputMap)는 바꾸면 곧바로 다시 단다. 원조 메뉴의 단축키(Ctrl+Z·C·V·D, Delete 등),
 * 화살표, Ctrl+2~9 도구, 마우스 동작은 고정이다(바꿀 수 있는 키와 겹치지 않게 막는다). .circ에는 저장하지 않는다.
 */
public final class KeyBindings {
    /** 명령 하나. */
    public static final class Command {
        public final String id;
        /** 설명 문구 키(messages). */
        public final String descKey;
        final List<KeyStroke> defaults;
        /** Shift를 더하면 반대 방향(회전, 영향 경로). */
        public final boolean shiftReverses;

        Command(String id, String descKey, boolean shiftReverses, KeyStroke... defaults) {
            this.id = id;
            this.descKey = descKey;
            this.shiftReverses = shiftReverses;
            this.defaults = Collections.unmodifiableList(java.util.Arrays.asList(defaults));
        }
    }

    static final String PREFIX = "keys.bind.";
    static final int KEY_MODS = InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK
            | InputEvent.META_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK;
    static final int MENU = menuMask();
    private static final Map<String, Command> COMMANDS = new LinkedHashMap<>();

    /** 메뉴 단축키 수식(Ctrl, macOS는 Cmd). 화면 없는 환경(테스트)에서도 쓸 수 있게. */
    static int menuMask() {
        try {
            return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        } catch (UnsupportedOperationException e) { // HeadlessException 포함
            return System.getProperty("os.name", "").startsWith("Mac") ? InputEvent.META_DOWN_MASK
                    : InputEvent.CTRL_DOWN_MASK;
        }
    }

    static KeyStroke k(int code, int mods) {
        return KeyStroke.getKeyStroke(code, mods);
    }

    private static void add(Command c) {
        COMMANDS.put(c.id, c);
    }

    static {
        add(new Command("rotate", "keys.rotate", true, k(KeyEvent.VK_R, 0)));
        add(new Command("label", "keys.label", false, k(KeyEvent.VK_F2, 0)));
        add(new Command("redo", "keys.redo", false, k(KeyEvent.VK_Y, MENU),
                k(KeyEvent.VK_Z, MENU | InputEvent.SHIFT_DOWN_MASK)));
        add(new Command("zoomIn", "keys.zoomIn", false, k(KeyEvent.VK_EQUALS, MENU), k(KeyEvent.VK_PLUS, MENU),
                k(KeyEvent.VK_ADD, MENU), k(KeyEvent.VK_EQUALS, MENU | InputEvent.SHIFT_DOWN_MASK)));
        add(new Command("zoomOut", "keys.zoomOut", false, k(KeyEvent.VK_MINUS, MENU), k(KeyEvent.VK_SUBTRACT, MENU)));
        add(new Command("zoomFit", "keys.zoomFitOnly", false, k(KeyEvent.VK_0, MENU), k(KeyEvent.VK_NUMPAD0, MENU)));
        add(new Command("zoom100", "keys.zoom100", false, k(KeyEvent.VK_1, MENU), k(KeyEvent.VK_NUMPAD1, MENU)));
        add(new Command("zoomSel", "keys.zoomSel", false, k(KeyEvent.VK_F, 0)));
        add(new Command("influence", "keys.influence", true, k(KeyEvent.VK_I, 0)));
        add(new Command("influenceLess", "keys.influenceLess", false, k(KeyEvent.VK_OPEN_BRACKET, 0)));
        add(new Command("influenceMore", "keys.influenceMore", false, k(KeyEvent.VK_CLOSE_BRACKET, 0)));
        add(new Command("flowToggle", "keys.flowToggle", false,
                k(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)));
        add(new Command("find", "keys.find", false, k(KeyEvent.VK_F, MENU)));
        add(new Command("palette", "keys.palette", false, k(KeyEvent.VK_K, MENU)));
    }

    /** 바꿀 수 없는 키(원조 메뉴·화살표·도구). 새 키가 이것과 겹치면 받지 않는다. */
    static final List<KeyStroke> FIXED = new ArrayList<>();

    static {
        for (int code : new int[] {KeyEvent.VK_Z, KeyEvent.VK_X, KeyEvent.VK_C, KeyEvent.VK_V, KeyEvent.VK_A,
            KeyEvent.VK_D, KeyEvent.VK_S, KeyEvent.VK_O, KeyEvent.VK_N, KeyEvent.VK_W, KeyEvent.VK_Q, KeyEvent.VK_P,
            KeyEvent.VK_E, KeyEvent.VK_T}) {
            FIXED.add(k(code, MENU));
        }
        for (int i = 2; i <= 9; i++) {
            FIXED.add(k(KeyEvent.VK_0 + i, MENU));
        }
        for (int code : new int[] {KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_UP, KeyEvent.VK_DOWN,
            KeyEvent.VK_DELETE, KeyEvent.VK_BACK_SPACE, KeyEvent.VK_ESCAPE, KeyEvent.VK_ENTER, KeyEvent.VK_TAB,
            KeyEvent.VK_SPACE}) {
            FIXED.add(k(code, 0));
        }
    }

    private static final Map<InputMap, Map<String, String>> INSTALLED = new WeakHashMap<>();
    /** 대표 키는 메뉴 항목의 단축키가 맡는 설치(다시 실행): 나머지 키만 단다. */
    private static final java.util.Set<String> SKIP_PRIMARY = new java.util.HashSet<>();
    private static final List<Runnable> LISTENERS = new ArrayList<>();

    private KeyBindings() {
    }

    public static List<Command> commands() {
        return new ArrayList<>(COMMANDS.values());
    }

    public static Command command(String id) {
        return COMMANDS.get(id);
    }

    /** 지금 키들(사용자가 바꿨으면 그 하나, 아니면 기본 키들). */
    public static List<KeyStroke> strokes(String id) {
        Command c = COMMANDS.get(id);
        String saved = Settings.get().getString(PREFIX + id, null);
        if (saved != null) {
            KeyStroke ks = KeyStroke.getKeyStroke(saved);
            if (ks != null) {
                return Collections.singletonList(ks);
            }
        }
        return c == null ? Collections.<KeyStroke>emptyList() : c.defaults;
    }

    /** 대표 키(표와 메뉴에 보이는 것). */
    public static KeyStroke primary(String id) {
        List<KeyStroke> s = strokes(id);
        return s.isEmpty() ? null : s.get(0);
    }

    /** 사용자가 바꾼 키인가. */
    public static boolean customized(String id) {
        return Settings.get().getString(PREFIX + id, null) != null;
    }

    /** 키 누름이 이 명령인가. Shift로 방향을 바꾸는 명령은 Shift를 더해도 맞다. */
    public static boolean matches(String id, KeyEvent e) {
        if (e.getID() != KeyEvent.KEY_PRESSED) {
            return false;
        }
        int mods = e.getModifiersEx();
        Command c = COMMANDS.get(id);
        for (KeyStroke ks : strokes(id)) {
            if (ks.getKeyCode() != e.getKeyCode()) {
                continue;
            }
            // KeyStroke는 옛 방식과 새 방식 수식 비트를 함께 가진다: 새 방식(_DOWN_MASK)만 비교한다
            int want = ks.getModifiers() & KEY_MODS;
            int got = mods & KEY_MODS;
            if (got == want || c != null && c.shiftReverses && got == (want | InputEvent.SHIFT_DOWN_MASK)) {
                return true;
            }
        }
        return false;
    }

    /** 사람이 읽는 키 표기: {@code Ctrl+Shift+F}. */
    public static String display(KeyStroke ks) {
        if (ks == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int m = ks.getModifiers();
        if ((m & InputEvent.CTRL_DOWN_MASK) != 0) {
            sb.append("Ctrl+");
        }
        if ((m & InputEvent.META_DOWN_MASK) != 0) {
            sb.append("Cmd+");
        }
        if ((m & InputEvent.ALT_DOWN_MASK) != 0) {
            sb.append("Alt+");
        }
        if ((m & InputEvent.SHIFT_DOWN_MASK) != 0) {
            sb.append("Shift+");
        }
        return sb.append(keyName(ks.getKeyCode())).toString();
    }

    /** 키 이름(기호는 기호로). 로캘과 상관없이 영어 표기(D-049: 키 이름은 이름이다). */
    static String keyName(int code) {
        switch (code) {
        case KeyEvent.VK_EQUALS:
            return "=";
        case KeyEvent.VK_MINUS:
            return "-";
        case KeyEvent.VK_PLUS:
            return "+";
        case KeyEvent.VK_OPEN_BRACKET:
            return "[";
        case KeyEvent.VK_CLOSE_BRACKET:
            return "]";
        case KeyEvent.VK_SLASH:
            return "/";
        case KeyEvent.VK_BACK_SLASH:
            return "\\";
        case KeyEvent.VK_COMMA:
            return ",";
        case KeyEvent.VK_PERIOD:
            return ".";
        case KeyEvent.VK_SEMICOLON:
            return ";";
        case KeyEvent.VK_QUOTE:
            return "'";
        case KeyEvent.VK_BACK_QUOTE:
            return "`";
        case KeyEvent.VK_ADD:
            return "Num+";
        case KeyEvent.VK_SUBTRACT:
            return "Num-";
        default:
            if (code >= KeyEvent.VK_NUMPAD0 && code <= KeyEvent.VK_NUMPAD9) {
                return "Num" + (code - KeyEvent.VK_NUMPAD0);
            }
            if (code >= KeyEvent.VK_F1 && code <= KeyEvent.VK_F12) {
                return "F" + (code - KeyEvent.VK_F1 + 1);
            }
            if (code >= KeyEvent.VK_A && code <= KeyEvent.VK_Z || code >= KeyEvent.VK_0 && code <= KeyEvent.VK_9) {
                return String.valueOf((char) code);
            }
            return KeyEvent.getKeyText(code);
        }
    }

    /** 명령의 표기(Shift로 반대 방향이면 {@code R / Shift+R}). */
    public static String display(String id) {
        Command c = COMMANDS.get(id);
        KeyStroke ks = primary(id);
        String d = display(ks);
        if (c != null && c.shiftReverses && ks != null && (ks.getModifiers() & InputEvent.SHIFT_DOWN_MASK) == 0) {
            d = d + " / " + display(KeyStroke.getKeyStroke(ks.getKeyCode(),
                    ks.getModifiers() | InputEvent.SHIFT_DOWN_MASK));
        }
        return d;
    }

    /**
     * 이 키를 id에 줄 때 겹치는 것: 다른 명령 id, 고정 키이면 {@code "fixed"}, 없으면 null. Shift로 반대 방향이 되는
     * 명령은 Shift를 더한 키도 본다.
     */
    public static String conflict(String id, KeyStroke ks) {
        List<KeyStroke> check = new ArrayList<>();
        check.add(ks);
        Command me = COMMANDS.get(id);
        if (me != null && me.shiftReverses) {
            check.add(KeyStroke.getKeyStroke(ks.getKeyCode(), ks.getModifiers() | InputEvent.SHIFT_DOWN_MASK));
        }
        for (KeyStroke x : check) {
            if (FIXED.contains(x)) {
                return "fixed";
            }
            for (Command c : COMMANDS.values()) {
                if (c.id.equals(id)) {
                    continue;
                }
                List<KeyStroke> theirs = new ArrayList<>(strokes(c.id));
                if (c.shiftReverses) {
                    for (KeyStroke t : strokes(c.id)) {
                        theirs.add(KeyStroke.getKeyStroke(t.getKeyCode(), t.getModifiers() | InputEvent.SHIFT_DOWN_MASK));
                    }
                }
                if (theirs.contains(x)) {
                    return c.id;
                }
            }
        }
        return null;
    }

    /** 키를 바꾼다(겹치면 바꾸지 않고 겹치는 것을 돌려준다). */
    public static String set(String id, KeyStroke ks) {
        String clash = conflict(id, ks);
        if (clash != null) {
            return clash;
        }
        Settings.get().set(PREFIX + id, ks.toString());
        save();
        changed();
        return null;
    }

    /** 기본 키로. */
    public static void reset(String id) {
        Settings.get().set(PREFIX + id, null);
        save();
        changed();
    }

    public static void resetAll() {
        for (String id : COMMANDS.keySet()) {
            Settings.get().set(PREFIX + id, null);
        }
        save();
        changed();
    }

    private static void save() {
        try {
            Settings.get().save();
        } catch (java.io.IOException e) {
            // 환경설정을 못 써도 이번 실행에는 바뀐다
        }
    }

    /** 창에 명령 id의 키를 actionKey로 단다. 키를 바꾸면 곧바로 다시 단다. */
    public static synchronized void install(InputMap im, String id, String actionKey) {
        INSTALLED.computeIfAbsent(im, k -> new LinkedHashMap<>()).put(actionKey, id);
        bind(im, id, actionKey);
    }

    /** 대표 키는 메뉴 항목이 맡을 때: 나머지 키만 단다(같은 키가 두 번 동작하지 않게). */
    public static synchronized void installAlternates(InputMap im, String id, String actionKey) {
        SKIP_PRIMARY.add(actionKey);
        install(im, id, actionKey);
    }

    private static void bind(InputMap im, String id, String actionKey) {
        if (im.keys() != null) {
            for (KeyStroke old : im.keys()) {
                if (actionKey.equals(im.get(old))) {
                    im.remove(old);
                }
            }
        }
        List<KeyStroke> all = strokes(id);
        for (int i = SKIP_PRIMARY.contains(actionKey) ? 1 : 0; i < all.size(); i++) {
            im.put(all.get(i), actionKey);
        }
    }

    public static synchronized void addListener(Runnable r) {
        LISTENERS.add(r);
    }

    private static void changed() {
        List<Runnable> ls;
        synchronized (KeyBindings.class) {
            for (Map.Entry<InputMap, Map<String, String>> e : INSTALLED.entrySet()) {
                for (Map.Entry<String, String> b : e.getValue().entrySet()) {
                    bind(e.getKey(), b.getValue(), b.getKey());
                }
            }
            ls = new ArrayList<>(LISTENERS);
        }
        for (Runnable r : ls) {
            r.run();
        }
    }
}
