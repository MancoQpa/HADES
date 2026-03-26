package com.harmonicmonitor.simulator;

import java.util.logging.*;

/**
 * Punto de entrada del simulador de escritorio ION 7400.
 *
 * Uso:
 *   java -cp <classpath> com.harmonicmonitor.simulator.SimulatorMain [opciones]
 *
 * Opciones:
 *   --cid      <ruta>    Ruta al archivo CID  (default: simulator/ion7400sim.cid)
 *   --port     <n>       Puerto MMS           (default: 10102)
 *   --ied      <nombre>  Nombre del IED       (default: SIM1)
 *   --ld       <inst>    Instancia LD         (default: LD0)
 *   --prefix   <pref>    Prefijo MMXU         (default: M03_)
 *   --profile  <nombre>  Perfil de carga      (default: crypto_mining)
 *   --noise    <0..1>    Nivel de ruido       (default: 0.03 = 3%)
 *   --interval <ms>      Intervalo update ms  (default: 5000)
 *
 * Perfiles disponibles (en simulator/templates/):
 *   crypto_mining, data_center, electronic_light, industrial,
 *   lighting, linear_load, mixed_electronic, normal_load
 *
 * Ejemplos:
 *   -- Un solo simulador en localhost:10102
 *      SimulatorMain --ied SIM1 --port 10102 --profile crypto_mining
 *
 *   -- Dos instancias simultáneas (ejecutar en consolas separadas):
 *      SimulatorMain --ied SIM1 --port 10102 --profile crypto_mining
 *      SimulatorMain --ied SIM2 --port 10103 --profile linear_load
 *
 *   En HarmonicMonitor configurar:
 *      Feeder 1: host=127.0.0.1  port=10102  iedName=SIM1  ldInst=LD0  prefix=M03_
 *      Feeder 2: host=127.0.0.1  port=10103  iedName=SIM2  ldInst=LD0  prefix=M03_
 */
public class SimulatorMain {

    public static void main(String[] args) throws Exception {
        // Redirigir logs de iec61850bean a la consola en formato legible
        Logger rootLog = Logger.getLogger("");
        for (Handler h : rootLog.getHandlers()) rootLog.removeHandler(h);
        ConsoleHandler ch = new ConsoleHandler();
        ch.setFormatter(new SimpleFormatter());
        ch.setLevel(Level.INFO);
        rootLog.addHandler(ch);
        rootLog.setLevel(Level.INFO);

        // Valores por defecto
        String cidPath   = "simulator/ion7400sim.cid";
        int    port      = 10102;
        String iedName   = "SIM1";
        String ldInst    = "LD0";
        String prefix    = "M03_";
        String profile   = "crypto_mining";
        float  noise     = 0.03f;
        int    interval  = 5000;

        // Parseo de argumentos
        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--cid":      cidPath  = args[++i]; break;
                case "--port":     port     = Integer.parseInt(args[++i]); break;
                case "--ied":      iedName  = args[++i]; break;
                case "--ld":       ldInst   = args[++i]; break;
                case "--prefix":   prefix   = args[++i]; break;
                case "--profile":  profile  = args[++i]; break;
                case "--noise":    noise    = Float.parseFloat(args[++i]); break;
                case "--interval": interval = Integer.parseInt(args[++i]); break;
            }
        }

        // Banner
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║     ION 7400 Desktop Simulator — HarmonicMonitor  ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.printf("  IED: %-10s  LD: %-6s  MMXU prefix: %s%n", iedName, ldInst, prefix);
        System.out.printf("  Puerto: %-6d  Perfil: %-20s  Ruido: %.0f%%%n",
                port, profile, noise * 100);
        System.out.printf("  Intervalo actualización: %d ms%n", interval);
        System.out.println("  HarmonicMonitor → host=127.0.0.1  port=" + port +
                "  iedName=" + iedName + "  ldInst=" + ldInst + "  prefix=" + prefix);
        System.out.println("──────────────────────────────────────────────────────");

        IonSimServer server = new IonSimServer();
        server.start(cidPath, port, iedName, ldInst, prefix, profile, noise, interval);

        System.out.println("Servidor activo. Presione Ctrl+C para detener.");

        final String finalIedName = iedName;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nDeteniendo simulador " + finalIedName + "...");
            server.stop();
        }));

        // Mantener el hilo principal vivo
        Thread.currentThread().join();
    }
}
