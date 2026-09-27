/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import kr.ac.hallym.hcs.mips.image.ExecutableImage;

/**
 * 전환용. Hallym MIPS 실행 이미지 내보내기가 나오면 지운다 (#373).
 *
 * <p>.s 불러오기에만 쓰는 코드는 모두 이 클래스 하나에 있다(D-126): hcs-asm(SPIM 코어로 만든 명령줄 어셈블러,
 * docs/hcs-asm.md)을 별도 프로세스로 {@code -exception}(Hallym MIPS 기본 배치: 시작 코드 9워드가
 * {@code 0x00400000}~{@code 0x00400020}, main은 {@code 0x00400024})으로 돌리고, 그 JSON을 실행 이미지 모델
 * ({@link ExecutableImage})로 바꾼다. 그 뒤는 .hmx와 같은 길이다({@link ProgramLoader}). SPIM(BSD) 코드는 이 jar에
 * 들어오지 않는다(CLAUDE.md 규칙 2.5).
 *
 * <p>hcs-asm 찾는 순서: 시스템 속성 {@code hcs.asm}, 환경 변수 {@code HCS_ASM}, 이 jar와 같은 폴더의
 * {@code hcs-asm}(Windows는 {@code hcs-asm.exe}).
 */
final class AssemblyTransition {
    /** Hallym MIPS 기본 배치(D-126). hcs-asm 명령줄의 기본값(예외 처리기 없음)은 그대로 두고 여기서 켠다. */
    static final List<String> FLAGS = Collections.unmodifiableList(Arrays.asList("-exception"));
    static final long TIMEOUT_SECONDS = 30;

    private AssemblyTransition() {
    }

    /** .s(또는 .asm)인가. */
    static boolean accepts(File f) {
        String n = f.getName().toLowerCase();
        return n.endsWith(".s") || n.endsWith(".asm");
    }

    /** 파일 고르기 창의 전환용 거르개. */
    static FileNameExtensionFilter fileFilter() {
        return new FileNameExtensionFilter(Text.name("MIPS assembly, transition (*.s, *.asm)").get(), "s", "asm");
    }

    /**
     * 파일 고르기 창에 전환용 거르개를 더한다: .hmx와 .s를 함께 보이는 거르개(기본으로 고름)와 .s 거르개. 전환 기간의
     * 학생 폴더에는 아직 .s만 있어서 .hmx만 보이면 빈 목록이 된다.
     */
    static void addFilters(JFileChooser chooser, FileNameExtensionFilter hmx) {
        FileNameExtensionFilter both = new FileNameExtensionFilter(
                Text.name("Executable image or .s (*.hmx, *.s, *.asm)").get(), "hmx", "s", "asm");
        chooser.addChoosableFileFilter(fileFilter());
        chooser.addChoosableFileFilter(both);
        chooser.setFileFilter(both);
    }

    /** .s를 hcs-asm -exception으로 어셈블해 실행 이미지로 읽는다. 실패하면 오류만 있는 결과. */
    static ProgramLoader.Loaded read(File source) {
        ProgramLoader.Loaded out = new ProgramLoader.Loaded(source);
        File exe = locate();
        if (exe == null) {
            out.errors.add(Text.of("Cannot find hcs-asm. Put " + executableName()
                    + " in the same folder as hcs-mips.jar. Executable images (.hmx) load without it.",
                    "hcs-asm 프로그램을 찾을 수 없습니다. hcs-mips.jar 파일과 같은 폴더에 " + executableName()
                            + " 파일을 두세요. .hmx 실행 이미지는 hcs-asm 없이 불러옵니다.").get());
            return out;
        }
        Program program;
        try {
            Run run = run(exe, source, FLAGS);
            if (run.exit == 2) {
                out.errors.add(run.stderr.trim());
                return out;
            }
            program = Program.fromJson(run.stdout);
        } catch (Exception ex) {
            out.errors.add(String.valueOf(ex.getMessage()));
            return out;
        }
        if (!program.errors.isEmpty()) {
            out.errors.add(Text.of("Assembly errors in the file " + source.getName() + ":",
                    "어셈블 오류가 있습니다. File: " + source.getName()).get());
            for (Message m : program.errors) {
                out.errors.add(m.toString());
            }
            return out;
        }
        out.image = toImage(program, source.getName());
        out.transition = true;
        out.notes.add(Text.of(".s temporary support (hcs-asm -exception, Hallym MIPS layout): " + source.getName(),
                ".s 임시 지원(hcs-asm -exception, Hallym MIPS 배치). File: " + source.getName()).get());
        for (Message w : program.warnings) {
            out.notes.add(Text.name("hcs-asm: ").get() + w);
        }
        return out;
    }

    /** hcs-asm 결과를 실행 이미지로. 이어진 주소는 한 구간이고, .data 워드는 리틀 엔디언 바이트로 푼다. */
    static ExecutableImage toImage(Program p, String sourceName) {
        ExecutableImage.Builder b = new ExecutableImage.Builder();
        b.header(ExecutableImage.SOURCE, sourceName);
        b.header(ExecutableImage.PRODUCED_BY, (p.tool == null ? "hcs-asm" : p.tool) + " -exception");
        b.endian(ExecutableImage.Endian.LITTLE);
        b.entry(p.entry);
        for (Map.Entry<String, Long> e : p.labels.entrySet()) {
            b.symbol(e.getKey(), e.getValue());
        }
        TreeMap<Long, Integer> text = new TreeMap<Long, Integer>();
        for (Word w : p.text) {
            text.put(w.addr, w.word);
        }
        for (Map.Entry<Long, int[]> run : runs(text).entrySet()) {
            b.text(run.getKey(), run.getValue());
        }
        for (Map.Entry<Long, int[]> run : runs(p.data).entrySet()) {
            int[] words = run.getValue();
            int[] bytes = new int[words.length * 4];
            for (int i = 0; i < words.length; i += 1) {
                for (int k = 0; k < 4; k += 1) {
                    bytes[4 * i + k] = (words[i] >>> (8 * k)) & 0xff;
                }
            }
            b.data(run.getKey(), bytes);
        }
        return b.build();
    }

    /** 주소 → 워드를 이어진 구간(시작 주소 → 워드들)으로. */
    private static Map<Long, int[]> runs(Map<Long, Integer> words) {
        Map<Long, int[]> out = new LinkedHashMap<Long, int[]>();
        long start = -1;
        long next = -1;
        List<Integer> cur = new ArrayList<Integer>();
        for (Map.Entry<Long, Integer> e : new TreeMap<Long, Integer>(words).entrySet()) {
            if (e.getKey() != next && !cur.isEmpty()) {
                out.put(start, toArray(cur));
                cur.clear();
            }
            if (cur.isEmpty()) {
                start = e.getKey();
            }
            cur.add(e.getValue());
            next = e.getKey() + 4;
        }
        if (!cur.isEmpty()) {
            out.put(start, toArray(cur));
        }
        return out;
    }

    private static int[] toArray(List<Integer> list) {
        int[] a = new int[list.size()];
        for (int i = 0; i < a.length; i += 1) {
            a[i] = list.get(i);
        }
        return a;
    }

    // ---- hcs-asm 프로세스 ----

    static final class Run {
        final int exit;
        final String stdout;
        final String stderr;

        Run(int exit, String stdout, String stderr) {
            this.exit = exit;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }

    static String executableName() {
        return System.getProperty("os.name", "").toLowerCase().startsWith("windows") ? "hcs-asm.exe" : "hcs-asm";
    }

    static File locate() {
        String prop = System.getProperty("hcs.asm");
        if (prop != null && new File(prop).canExecute()) {
            return new File(prop);
        }
        String env = System.getenv("HCS_ASM");
        if (env != null && new File(env).canExecute()) {
            return new File(env);
        }
        File dir = jarDirectory();
        if (dir != null) {
            File exe = new File(dir, executableName());
            if (exe.canExecute()) {
                return exe;
            }
        }
        return null;
    }

    /** 이 클래스가 들어 있는 jar의 폴더. 원조 2.7.1의 ZipClassLoader는 jar:file: URL을 준다. */
    static File jarDirectory() {
        return jarDirectory(AssemblyTransition.class.getResource("AssemblyTransition.class"));
    }

    /** {@code jar:file:/path/hcs-mips.jar!/kr/…} → {@code /path}. jar가 아니면 null. */
    static File jarDirectory(URL url) {
        if (url == null || !"jar".equals(url.getProtocol())) {
            return null;
        }
        String s = url.toString();
        int bang = s.indexOf("!/");
        try {
            return new File(new URI(s.substring(4, bang))).getParentFile();
        } catch (Exception e) {
            return null;
        }
    }

    static Run run(File exe, File source, List<String> flags) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<String>();
        cmd.add(exe.getPath());
        cmd.addAll(flags);
        cmd.add(source.getPath());
        Process p = new ProcessBuilder(cmd).start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Thread a = drain(p.getInputStream(), out);
        Thread b = drain(p.getErrorStream(), err);
        if (!p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IOException("hcs-asm did not finish in " + TIMEOUT_SECONDS + "s");
        }
        a.join();
        b.join();
        return new Run(p.exitValue(), new String(out.toByteArray(), StandardCharsets.UTF_8),
                new String(err.toByteArray(), StandardCharsets.UTF_8));
    }

    private static Thread drain(final InputStream in, final ByteArrayOutputStream out) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] buf = new byte[8192];
                try {
                    for (int n; (n = in.read(buf)) > 0;) {
                        out.write(buf, 0, n);
                    }
                } catch (IOException e) {
                    // 프로세스가 끝나면 닫힌다.
                }
            }
        });
        t.setDaemon(true);
        t.start();
        return t;
    }

    // ---- hcs-asm의 JSON 출력(docs/hcs-asm.md) ----

    static final class Word {
        final long addr;
        final int word;
        final int line; // 0: 모름(예외 처리기 시작 코드)
        final String source;

        Word(long addr, int word, int line, String source) {
            this.addr = addr;
            this.word = word;
            this.line = line;
            this.source = source;
        }
    }

    static final class Message {
        final int line; // 0: 줄과 무관
        final String text;
        final String context;

        Message(int line, String text, String context) {
            this.line = line;
            this.text = text;
            this.context = context;
        }

        @Override
        public String toString() {
            return (line > 0 ? line + ": " : "") + text + (context != null ? "  (" + context + ")" : "");
        }
    }

    static final class Program {
        final String tool;
        final Map<String, Object> settings;
        final Long entry;
        final List<Word> text;
        final Map<Long, Integer> data;
        final Map<String, Long> labels;
        final List<Message> errors;
        final List<Message> warnings;

        private Program(String tool, Map<String, Object> settings, Long entry, List<Word> text,
                Map<Long, Integer> data, Map<String, Long> labels, List<Message> errors, List<Message> warnings) {
            this.tool = tool;
            this.settings = settings;
            this.entry = entry;
            this.text = text;
            this.data = data;
            this.labels = labels;
            this.errors = errors;
            this.warnings = warnings;
        }

        @SuppressWarnings("unchecked")
        static Program fromJson(String json) {
            Map<String, Object> root = (Map<String, Object>) Json.parse(json);
            List<Word> text = new ArrayList<Word>();
            for (Object o : (List<Object>) root.get("text")) {
                Map<String, Object> w = (Map<String, Object>) o;
                Object line = w.get("line");
                text.add(new Word(hex(w.get("addr")), (int) hex(w.get("word")),
                        line == null ? 0 : ((Long) line).intValue(), (String) w.get("source")));
            }
            Map<Long, Integer> data = new TreeMap<Long, Integer>();
            for (Object o : (List<Object>) root.get("data")) {
                Map<String, Object> d = (Map<String, Object>) o;
                data.put(hex(d.get("addr")), (int) hex(d.get("word")));
            }
            Map<String, Long> labels = new LinkedHashMap<String, Long>();
            for (Map.Entry<String, Object> e : ((Map<String, Object>) root.get("labels")).entrySet()) {
                labels.put(e.getKey(), hex(e.getValue()));
            }
            Object entry = root.get("entry");
            return new Program((String) root.get("tool"), (Map<String, Object>) root.get("settings"),
                    entry == null ? null : hex(entry), text, data, labels,
                    messages(root.get("errors")), messages(root.get("warnings")));
        }

        @SuppressWarnings("unchecked")
        private static List<Message> messages(Object list) {
            List<Message> out = new ArrayList<Message>();
            for (Object o : (List<Object>) list) {
                Map<String, Object> m = (Map<String, Object>) o;
                Object line = m.get("line");
                out.add(new Message(line == null ? 0 : ((Long) line).intValue(),
                        (String) m.get("message"), (String) m.get("context")));
            }
            return Collections.unmodifiableList(out);
        }

        private static long hex(Object s) {
            String t = (String) s;
            return Long.parseLong(t.startsWith("0x") ? t.substring(2) : t, 16);
        }
    }

    /**
     * hcs-asm 출력만 읽으면 되는 작은 JSON 파서. lib-mips는 외부 의존성 없는 단일 jar여야 해서 직접 짰다
     * (CLAUDE.md 5절). 값은 Map(순서 유지), List, String, Long 또는 Double, Boolean, null이다.
     */
    static final class Json {
        private final String s;
        private int i;

        private Json(String s) {
            this.s = s;
        }

        static Object parse(String text) {
            Json p = new Json(text);
            p.space();
            Object v = p.value();
            p.space();
            if (p.i != p.s.length()) {
                throw p.error("trailing characters");
            }
            return v;
        }

        private IllegalArgumentException error(String what) {
            return new IllegalArgumentException("JSON: " + what + " at " + i);
        }

        private void space() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i += 1;
            }
        }

        private void expect(char c) {
            if (i >= s.length() || s.charAt(i) != c) {
                throw error("expected '" + c + "'");
            }
            i += 1;
        }

        private Object value() {
            if (i >= s.length()) {
                throw error("unexpected end");
            }
            char c = s.charAt(i);
            if (c == '{') {
                return object();
            } else if (c == '[') {
                return array();
            } else if (c == '"') {
                return string();
            } else if (s.startsWith("true", i)) {
                i += 4;
                return Boolean.TRUE;
            } else if (s.startsWith("false", i)) {
                i += 5;
                return Boolean.FALSE;
            } else if (s.startsWith("null", i)) {
                i += 4;
                return null;
            }
            return number();
        }

        private Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            expect('{');
            space();
            if (i < s.length() && s.charAt(i) == '}') {
                i += 1;
                return m;
            }
            while (true) {
                space();
                String key = string();
                space();
                expect(':');
                space();
                m.put(key, value());
                space();
                if (i < s.length() && s.charAt(i) == ',') {
                    i += 1;
                } else {
                    expect('}');
                    return m;
                }
            }
        }

        private List<Object> array() {
            List<Object> list = new ArrayList<Object>();
            expect('[');
            space();
            if (i < s.length() && s.charAt(i) == ']') {
                i += 1;
                return list;
            }
            while (true) {
                space();
                list.add(value());
                space();
                if (i < s.length() && s.charAt(i) == ',') {
                    i += 1;
                } else {
                    expect(']');
                    return list;
                }
            }
        }

        private String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (i >= s.length()) {
                    throw error("unterminated string");
                }
                char c = s.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'u':
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default: sb.append(e);
                }
            }
        }

        private Object number() {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
                i += 1;
            }
            String n = s.substring(start, i);
            if (n.isEmpty()) {
                throw error("unexpected character");
            }
            if (n.indexOf('.') >= 0 || n.indexOf('e') >= 0 || n.indexOf('E') >= 0) {
                return Double.valueOf(n);
            }
            return Long.valueOf(n);
        }
    }
}
