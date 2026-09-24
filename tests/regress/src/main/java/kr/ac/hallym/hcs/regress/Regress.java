/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.regress;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 엔진 회귀 명령.
 * <pre>
 *   generate          tests/circ/의 회로를 원조 2.7.1 API로 다시 만든다
 *   update            표준 2.7.1 jar의 -tty table 결과를 &lt;name&gt;.expected로 쓴다
 *   check [jar]       jar(기본: 표준 2.7.1)의 결과가 .expected와 같은지 본다
 * </pre>
 */
public final class Regress {
    private Regress() {
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(System.getProperty("hcs.circDir"));
        File reference = new File(System.getProperty("hcs.logisimJar"));
        String cmd = args.length > 0 ? args[0] : "check";
        if (cmd.equals("generate")) {
            Circuits.generateAll(dir);
            System.out.println("generated " + Engine.circuits(dir));
        } else if (cmd.equals("update")) {
            Engine engine = Engine.current(reference);
            for (String name : Engine.circuits(dir)) {
                String out = engine.run(dir, name);
                Files.write(new File(dir, name + ".expected").toPath(), out.getBytes(StandardCharsets.UTF_8));
                System.out.println(name + ": " + (out.split("\n").length - 1) + " rows");
            }
        } else if (cmd.equals("check")) {
            Engine engine = Engine.current(args.length > 1 ? new File(args[1]) : reference);
            int failures = 0;
            for (String name : Engine.circuits(dir)) {
                String expected = new String(Files.readAllBytes(new File(dir, name + ".expected").toPath()),
                        StandardCharsets.UTF_8);
                String actual = engine.run(dir, name);
                if (!expected.equals(actual)) {
                    failures += 1;
                    System.out.println(name + ": differs\n--- expected\n" + expected + "--- actual\n" + actual);
                }
            }
            System.out.println(failures == 0 ? "engine regression OK" : failures + " circuit(s) differ");
            System.exit(failures == 0 ? 0 : 1);
        } else {
            System.err.println("usage: Regress generate|update|check [jar]");
            System.exit(2);
        }
    }
}
