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
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * SPIM 코어로 만든 명령줄 어셈블러 hcs-asm(docs/hcs-asm.md)을 별도 프로세스로 실행한다. SPIM(BSD) 코드는
 * 이 jar에 들어오지 않는다(CLAUDE.md 규칙 2.5).
 *
 * <p>찾는 순서: 시스템 속성 {@code hcs.asm}, 환경 변수 {@code HCS_ASM}, 이 jar와 같은 폴더의
 * {@code hcs-asm}(Windows는 {@code hcs-asm.exe}).
 */
final class HcsAsm {
    static final long TIMEOUT_SECONDS = 30;

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

    private HcsAsm() {
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
        return jarDirectory(HcsAsm.class.getResource("HcsAsm.class"));
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
}
