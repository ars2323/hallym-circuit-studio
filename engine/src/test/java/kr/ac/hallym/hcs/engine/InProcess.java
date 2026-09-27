/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import kr.ac.hallym.hcs.engine.prefs.MemoryPreferencesFactory;
import kr.ac.hallym.hcs.engine.rpc.Server;

/** 같은 JVM 안의 엔진: 파이프로 {@link Server}와 {@link Client}를 잇는다. */
final class InProcess implements AutoCloseable {
    final Server server;
    final Engine engine;
    final Client client;
    private final Thread serveThread;
    private volatile String exitReason;

    InProcess() throws IOException {
        if (!MemoryPreferencesFactory.isActive()) {
            throw new IllegalStateException("tests must run with the in-memory preferences factory");
        }
        PipedOutputStream toEngine = new PipedOutputStream();
        PipedInputStream engineIn = new PipedInputStream(toEngine, 1 << 16);
        PipedOutputStream engineOut = new PipedOutputStream();
        PipedInputStream fromEngine = new PipedInputStream(engineOut, 1 << 22);
        server = new Server(engineIn, engineOut, System.err);
        engine = new Engine(server);
        serveThread = new Thread(() -> {
            try {
                exitReason = server.serve();
            } catch (InterruptedException e) {
                exitReason = "interrupted";
            }
        }, "test-serve");
        serveThread.setDaemon(true);
        serveThread.start();
        client = new Client(fromEngine, toEngine);
    }

    /** 엔진 스레드에서 실행하고 결과를 기다린다(내부 상태를 안전하게 본다). */
    <T> T onEngine(Callable<T> task) throws Exception {
        return server.executor().submit(task).get(30, TimeUnit.SECONDS);
    }

    /** serve()가 돌아올 때까지 기다리고 그 이유를 돌려준다(시간이 넘으면 null). */
    String awaitExit(long ms) throws InterruptedException {
        serveThread.join(ms);
        return serveThread.isAlive() ? null : exitReason;
    }

    @Override
    public void close() {
        client.closeInput();
        try {
            serveThread.join(10_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
