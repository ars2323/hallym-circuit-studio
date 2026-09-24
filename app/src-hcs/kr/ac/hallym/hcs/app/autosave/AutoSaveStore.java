/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.autosave;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 자동 저장 파일의 이름·위치·정리(#70, PLAN.md 11.13). 앱 설정 폴더의 {@code autosave/}에 파일마다
 * {@code <키>.circ}(내용)와 {@code <키>.properties}(원래 경로, 시각)를 둔다. 원본 .circ 옆에는 아무것도 만들지 않는다.
 * GUI와 Logisim을 모르는 부분이라 GUI 없이 테스트한다.
 */
public final class AutoSaveStore {
    /** 복구할 수 있는 자동 저장 하나. original은 저장한 적 없는 새 파일이면 null. */
    public static final class Entry {
        public final File circ;
        public final File original;
        public final String title;
        public final long savedAt;

        Entry(File circ, File original, String title, long savedAt) {
            this.circ = circ;
            this.original = original;
            this.title = title;
            this.savedAt = savedAt;
        }
    }

    private final File dir;

    public AutoSaveStore(File dir) {
        this.dir = dir;
    }

    public File dir() {
        return dir;
    }

    /** 원래 파일(없으면 창마다 다른 이름)에 대한 자동 저장 키. 같은 파일은 늘 같은 키다. */
    public static String key(File original, String fallback) {
        String basis = original == null ? "untitled:" + fallback : original.getAbsolutePath();
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(basis.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", d[i]));
            }
            String name = original == null ? "untitled" : original.getName().replaceAll("\\.circ$", "");
            return name.replaceAll("[^A-Za-z0-9_.-]", "_") + "-" + sb;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 자동 저장 내용을 쓴다. xml은 원조 writer가 원래 파일 위치 기준으로 쓴 것이고, 라이브러리 상대 경로를
     * 원래 폴더 기준 절대 경로로 바꿔 둔다(자동 저장 폴더에서 열어도 같은 라이브러리를 찾게).
     */
    public Entry write(String key, File original, String title, byte[] xml, long now) throws IOException {
        Files.createDirectories(dir.toPath());
        File circ = new File(dir, key + ".circ");
        File meta = new File(dir, key + ".properties");
        String text = new String(xml, StandardCharsets.UTF_8);
        if (original != null && original.getAbsoluteFile().getParentFile() != null) {
            text = absoluteLibraries(text, original.getAbsoluteFile().getParentFile());
        }
        atomicWrite(circ, text.getBytes(StandardCharsets.UTF_8));
        Properties p = new Properties();
        if (original != null) {
            p.setProperty("original", original.getAbsolutePath());
        }
        p.setProperty("title", title);
        p.setProperty("savedAt", Long.toString(now));
        File tmp = File.createTempFile("hcs-" + key, ".tmp", dir);
        try {
            try (OutputStream out = Files.newOutputStream(tmp.toPath())) {
                p.store(out, "Hallym Circuit Studio autosave");
            }
            Files.move(tmp.toPath(), meta.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp.toPath());
        }
        return new Entry(circ, original, title, now);
    }

    /** 정상 저장·닫기 뒤에는 지운다. */
    public void delete(String key) throws IOException {
        Files.deleteIfExists(new File(dir, key + ".circ").toPath());
        Files.deleteIfExists(new File(dir, key + ".properties").toPath());
    }

    /** 남아 있는 자동 저장(최근 것부터). 짝이 안 맞거나 깨진 것은 건너뛴다. */
    public List<Entry> list() {
        List<Entry> ret = new ArrayList<>();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".properties"));
        if (files == null) {
            return ret;
        }
        for (File meta : files) {
            String key = meta.getName().substring(0, meta.getName().length() - ".properties".length());
            File circ = new File(dir, key + ".circ");
            if (!circ.isFile()) {
                continue;
            }
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(meta.toPath())) {
                p.load(in);
                String orig = p.getProperty("original");
                ret.add(new Entry(circ, orig == null ? null : new File(orig), p.getProperty("title", key),
                        Long.parseLong(p.getProperty("savedAt", "0"))));
            } catch (IOException | NumberFormatException e) {
                // 깨진 기록은 무시
            }
        }
        ret.sort(Comparator.comparingLong((Entry e) -> e.savedAt).reversed());
        return ret;
    }

    /** 이 파일이 자동 저장 폴더 안의 것인가(복구한 창에서 저장할 때 원래 자리로 돌리려고). */
    public boolean contains(File f) {
        if (f == null) {
            return false;
        }
        File parent = f.getAbsoluteFile().getParentFile();
        return parent != null && parent.equals(dir.getAbsoluteFile());
    }

    private static final Pattern LIB = Pattern.compile("(<lib desc=\")(file|jar)#([^\"#]+)");

    /** {@code file#rel}, {@code jar#rel#cls}의 상대 경로를 base 기준 절대 경로로. 이미 절대면 그대로. */
    static String absoluteLibraries(String xml, File base) {
        Matcher m = LIB.matcher(xml);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String path = unescape(m.group(3));
            File f = new File(path);
            String abs = f.isAbsolute() ? path : new File(base, path).getAbsolutePath();
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + m.group(2) + "#" + escape(abs)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String unescape(String s) {
        return s.replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private void atomicWrite(File f, byte[] data) throws IOException {
        File tmp = File.createTempFile("hcs-" + f.getName(), ".tmp", dir);
        try {
            Files.write(tmp.toPath(), data);
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp.toPath());
        }
    }
}
