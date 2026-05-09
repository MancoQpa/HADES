package com.harmonicmonitor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * Fábrica centralizada de hilos daemon para la aplicación HADES.
 *
 * <p>Reduce el boilerplate de crear {@code ThreadFactory} con hilo daemon
 * y nombre explícito. Cada módulo sigue creando su propio pool (ciclo de vida
 * independiente por feeder / componente); este utilitario solo elimina la
 * lambda repetida de 5 líneas.
 *
 * <p>Adicionalmente expone un {@code ioPool} compartido de 2 hilos para
 * tareas fire-and-forget que no justifican un executor propio (evita crear
 * pools anónimos que nunca se cierran).
 */
public final class AppExecutors {

    // Pool compartido para tareas IO de corta duración (fire-and-forget)
    private static final ExecutorService IO_POOL =
            Executors.newFixedThreadPool(2, daemonFactory("hades-io-pool"));

    private AppExecutors() {}

    /**
     * Crea una {@link ThreadFactory} que produce hilos daemon con el nombre dado.
     *
     * <pre>{@code
     * scheduler = Executors.newSingleThreadScheduledExecutor(
     *     AppExecutors.daemonFactory("Poller-" + feederId));
     * }</pre>
     */
    public static ThreadFactory daemonFactory(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }

    /**
     * Atajo para {@code Executors.newSingleThreadScheduledExecutor(daemonFactory(name))}.
     */
    public static ScheduledExecutorService newDaemonScheduler(String name) {
        return Executors.newSingleThreadScheduledExecutor(daemonFactory(name));
    }

    /**
     * Atajo para {@code Executors.newSingleThreadExecutor(daemonFactory(name))}.
     */
    public static ExecutorService newDaemonExecutor(String name) {
        return Executors.newSingleThreadExecutor(daemonFactory(name));
    }

    /**
     * Pool compartido para tareas IO breves (máx. 2 hilos concurrentes).
     * No llamar a {@code shutdown()} directamente; usar {@link #shutdownAll()}.
     */
    public static ExecutorService ioPool() {
        return IO_POOL;
    }

    /**
     * Cierra el pool compartido. Debe llamarse desde {@code HarmonicMonitorApp.stop()}.
     */
    public static void shutdownAll() {
        IO_POOL.shutdownNow();
    }
}
