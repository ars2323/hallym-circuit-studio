/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips.image;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 원본 .s 대조(Z-01): "원본 .s를 고쳤는데 다시 내보내지 않음"을 잡는다. .hmx의 {@code source}(.hmx 폴더 기준 상대
 * 경로)로, 없으면 .hmx 옆의 같은 이름으로 원본을 찾아 SHA-256을 {@code source-sha256}과 비교한다. 사실만 알린다.
 */
public final class SourceCheck {
    public enum Status {
        /** 내보낸 때와 같다. */
        SAME,
        /** 내보낸 뒤 바뀌었다(노란 사실 줄). */
        CHANGED,
        /** 원본 .s를 찾지 못했다. */
        NOT_FOUND,
        /** 이미지에 source-sha256이 없다. */
        NO_HASH
    }

    public final Status status;
    /** 찾은 원본. 없으면 null. */
    public final File file;
    /** 사용자에게 보일 원본 이름(예: {@code lab04.s}). 모르면 null. */
    public final String name;

    private SourceCheck(Status status, File file, String name) {
        this.status = status;
        this.file = file;
        this.name = name;
    }

    /** hmx 파일 옆에서 image의 원본을 찾아 비교한다. */
    public static SourceCheck check(File hmx, ExecutableImage image) {
        String source = image.source();
        String name = source == null ? null : new File(source.replace('\\', '/')).getName();
        String want = image.sourceSha256();
        if (want == null) {
            return new SourceCheck(Status.NO_HASH, null, name);
        }
        File found = find(hmx, source);
        if (found == null) {
            return new SourceCheck(Status.NOT_FOUND, null, name);
        }
        String got;
        try {
            got = sha256(found);
        } catch (IOException e) {
            return new SourceCheck(Status.NOT_FOUND, null, name);
        }
        return new SourceCheck(got.equalsIgnoreCase(want) ? Status.SAME : Status.CHANGED, found,
                name == null ? found.getName() : name);
    }

    /**
     * 찾는 순서: (1) {@code source}를 .hmx 폴더 기준으로(절대 경로면 그대로), (2) .hmx 옆의 {@code source} 파일 이름,
     * (3) {@code source}가 없을 때만 .hmx와 같은 이름의 .s. 없으면 null.
     */
    static File find(File hmx, String source) {
        File dir = hmx.getAbsoluteFile().getParentFile();
        if (source != null) {
            String s = source.replace('\\', '/');
            File direct = new File(s);
            File first = direct.isAbsolute() ? direct : new File(dir, s);
            if (first.isFile()) {
                return first;
            }
            File beside = new File(dir, direct.getName());
            return beside.isFile() ? beside : null;
        }
        String base = hmx.getName();
        int dot = base.lastIndexOf('.');
        File same = new File(dir, (dot > 0 ? base.substring(0, dot) : base) + ".s");
        return same.isFile() ? same : null;
    }

    /** 사용자에게 보일 한 줄. SAME이면 보통 줄, CHANGED면 노란 줄로 보인다({@link #warns()}). */
    public Msg message() {
        String n = name == null ? "(source)" : name;
        switch (status) {
            case SAME:
                return Msg.of("Source file " + n + ": same as when exported.",
                        "원본 파일 " + n + ": 내보낸 때와 같음.");
            case CHANGED:
                return Msg.of("Source file " + n + ": changed after export. Export it again from Hallym MIPS.",
                        "원본 파일 " + n + ": 내보낸 뒤 바뀜. Hallym MIPS에서 다시 내보내세요.");
            case NOT_FOUND:
                return Msg.of("The source .s file was not found, so it was not compared.",
                        "원본 .s 파일을 찾지 못해 비교하지 않았습니다.");
            default:
                return Msg.of("The file has no source-sha256 line, so the source was not compared.",
                        "source-sha256 줄이 없어 원본과 비교하지 않았습니다.");
        }
    }

    /** 노란 사실 줄로 보일 결과인가. */
    public boolean warns() {
        return status == Status.CHANGED;
    }

    /** 파일의 SHA-256(소문자 16진수 64자). */
    public static String sha256(File f) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        InputStream in = new FileInputStream(f);
        try {
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0;) {
                md.update(buf, 0, n);
            }
        } finally {
            in.close();
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
