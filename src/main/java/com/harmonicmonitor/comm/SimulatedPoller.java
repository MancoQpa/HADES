package com.harmonicmonitor.comm;

import com.harmonicmonitor.AppExecutors;
import com.harmonicmonitor.model.FeederConfig;
import com.harmonicmonitor.model.FeederMeasurement;
import com.harmonicmonitor.model.SimProfile;
import com.harmonicmonitor.analysis.HarmonicAnalyzer;
import com.harmonicmonitor.analysis.ElectronicLoadDetector;
import com.harmonicmonitor.analysis.ResonanceAnalyzer;
import com.harmonicmonitor.analysis.LoadStabilityAnalyzer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

/**
 * Poller simulado para demostración sin IED real.
 *
 * Soporta múltiples perfiles de carga:
 *   CRYPTO_MINER         — SMPS sin PFC, H5/H7 dominantes, THD ~45%
 *   DATACENTER           — PFC activo, H3 reducido, THD ~18%
 *   ARC_FURNACE          — Horno de arco, H2/H3, flicker, alta reactiva
 *   VARIABLE_SPEED_DRIVE — 6-pulsos, H5/H7/H11/H13, THD ~25%
 *   INDUSTRIAL_LINEAR    — Motores directos, THD <5%, FP 0.82-0.88
 *   MIXED_COMMERCIAL     — Mezcla comercial, variación diurna
 */
public class SimulatedPoller extends MeasurementPoller {

    private static final Random RND = new Random();

    private final FeederConfig cfg;
    private final HarmonicAnalyzer      harmonicAnalyzer  = new HarmonicAnalyzer();
    private final ElectronicLoadDetector loadDetector     = new ElectronicLoadDetector();
    private final ResonanceAnalyzer      resonanceAnalyzer = new ResonanceAnalyzer();
    private final LoadStabilityAnalyzer  stabilityAnalyzer = new LoadStabilityAnalyzer();

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?>       task;
    private volatile boolean         running = false;

    private final List<MeasurementPoller.MeasurementListener> simListeners = new CopyOnWriteArrayList<>();
    private final List<Double> currentHistory = new ArrayList<>();

    private double timeSeconds = 0;

    // Arc furnace state (random walk)
    private double arcLoad   = 0.5;
    private double arcQvar   = 0.0;
    // VFD state
    private double vfdSpeed  = 0.5;
    private int    vfdDir    = 1;

    public SimulatedPoller(FeederConfig cfg) {
        super(cfg, null);
        this.cfg = cfg;
    }

    @Override public void addListener(MeasurementPoller.MeasurementListener l) { simListeners.add(l); }
    @Override public void start() {
        if (running) return;
        running   = true;
        scheduler = AppExecutors.newDaemonScheduler("SimPoller-" + cfg.getFeederId());
        task = scheduler.scheduleAtFixedRate(this::simulatePoll, 0, cfg.getPollIntervalMs(), TimeUnit.MILLISECONDS);
    }
    @Override public void stop() {
        running = false;
        if (task      != null) task.cancel(false);
        if (scheduler != null) scheduler.shutdown();
    }
    @Override public void setInterval(int ms) {
        boolean was = running;
        if (was) stop();
        cfg.setPollIntervalMs(ms);
        if (was) start();
    }
    @Override public boolean isRunning() { return running; }

    // ── Dispatch ──────────────────────────────────────────────────────────────

    private void simulatePoll() {
        timeSeconds += cfg.getPollIntervalMs() / 1000.0;
        SimProfile profile = cfg.getSimProfile() != null ? cfg.getSimProfile() : SimProfile.CRYPTO_MINER;

        FeederMeasurement m;
        switch (profile) {
            case CRYPTO_MINER:         m = simulateCryptoMiner();   break;
            case DATACENTER:           m = simulateDatacenter();     break;
            case ARC_FURNACE:          m = simulateArcFurnace();     break;
            case VARIABLE_SPEED_DRIVE: m = simulateVFD();            break;
            case INDUSTRIAL_LINEAR:    m = simulateLinear();         break;
            case MIXED_COMMERCIAL:     m = simulateMixed();          break;
            default:                   m = simulateCryptoMiner();    break;
        }

        harmonicAnalyzer.calculateHarmonicRatios(m);
        currentHistory.add(m.getCurrentL1());
        if (currentHistory.size() > 60) currentHistory.remove(0);
        m.setCvCurrent(stabilityAnalyzer.calculateCV(currentHistory));
        loadDetector.classify(m, cfg);
        resonanceAnalyzer.analyze(m, cfg);
        m.setDataValid(true);
        m.setQualityFlag("SIMULATED");

        for (MeasurementPoller.MeasurementListener l : simListeners) {
            try { l.onMeasurement(m); } catch (Exception ignored) {}
        }
    }

    // ── Perfil: Cripto-Miner / SMPS sin PFC ──────────────────────────────────

    private FeederMeasurement simulateCryptoMiner() {
        // Ciclo largo: el cripto miner oscila entre carga alta y baja
        double cycle      = Math.sin(timeSeconds / 300.0 * Math.PI);
        double cryptoLoad = 0.6 + 0.3 * Math.max(0, cycle);
        if (timeSeconds > 120 && timeSeconds < 150) cryptoLoad = 0.2; // baja temporal

        double vn   = nominalPhaseV();
        double iBase   = cfg.getNominalCurrentA() * 0.25 * (1 + 0.1 * g());
        double iCrypto = cfg.getNominalCurrentA() * cryptoLoad * (1 + 0.005 * g());
        double iTotal  = iBase + iCrypto;

        double pf   = (0.87 * iBase + 0.985 * iCrypto) / Math.max(1, iTotal);
        double thdI = (45.0 + 5 * g()) * iCrypto / Math.max(1, iTotal) + (5 + g()) * iBase / Math.max(1, iTotal);
        double thdV = thdI * 0.12 + 0.5 * Math.abs(g());

        double[] iSpec = buildSpectrum(iTotal, thdI, new double[]{0, 0.03, 0, 0.35, 0, 0.22, 0, 0.08, 0, 0.07, 0, 0.05, 0, 0.03});
        double[] vSpec = buildSpectrum(vn,    thdV, new double[]{0, 0,    0, 0.40, 0, 0.25, 0, 0,    0, 0.10, 0, 0.08});

        return buildMeasurement(vn, iTotal, pf, thdI, thdV, iSpec, vSpec);
    }

    // ── Perfil: Datacenter con PFC activo ─────────────────────────────────────

    private FeederMeasurement simulateDatacenter() {
        // Muy estable, carga casi constante con pequeños escalones
        double load  = 0.75 + 0.05 * Math.sin(timeSeconds / 3600.0 * Math.PI);
        double noise = 0.003 * g();

        double vn     = nominalPhaseV();
        double iTotal = cfg.getNominalCurrentA() * load * (1 + noise);
        double pf     = 0.87 + 0.02 * g();   // PFC parcial: PF < 0.92 → DATA_CENTER (no CRYPTO)
        double thdI   = 22.0 + 1.5 * g(); // PFC activo: H3 suprimido, H5/H7 residuales; 22% → H5/H1≈0.179>0.15, H7/H1≈0.115>0.10
        double thdV   = thdI * 0.08 + 0.3 * Math.abs(g());

        // Con PFC activo: H3 pequeño, H5 dominante, H7 secundario
        double[] iSpec = buildSpectrum(iTotal, thdI, new double[]{0, 0.05, 0, 0.28, 0, 0.18, 0, 0.06, 0, 0.04});
        double[] vSpec = buildSpectrum(vn,    thdV, new double[]{0, 0,    0, 0.40, 0, 0.25});

        return buildMeasurement(vn, iTotal, pf, thdI, thdV, iSpec, vSpec);
    }

    // ── Perfil: Horno de Arco Eléctrico ──────────────────────────────────────

    private FeederMeasurement simulateArcFurnace() {
        // Random walk para simular naturaleza caótica del arco
        arcLoad += 0.15 * g();
        arcLoad  = Math.max(0.2, Math.min(1.0, arcLoad));
        arcQvar += 0.2  * g();
        arcQvar  = Math.max(-0.3, Math.min(0.3, arcQvar));

        double vn     = nominalPhaseV() * (1 + 0.02 * g()); // flicker de tensión
        double iTotal = cfg.getNominalCurrentA() * arcLoad * (1 + 0.08 * g());
        double pf     = 0.77 + arcQvar * 0.08 + 0.03 * g();
        pf = Math.max(0.65, Math.min(0.88, pf));

        double thdI = 22.0 + 12 * Math.abs(g());   // muy variable
        double thdV = thdI * 0.25 + 2.0 * Math.abs(g());

        // Arco produce H2 (asimétrico), H3, H4, H5 mezclados
        double h2 = 0.12 + 0.06 * Math.abs(g()); // componente par característica
        double h3 = 0.15 + 0.08 * g();
        double h4 = 0.07 + 0.04 * Math.abs(g());
        double h5 = 0.10 + 0.05 * g();
        double[] iSpec = buildSpectrum(iTotal, thdI, new double[]{h2, h3, h4, h5, 0, 0.04, 0, 0.03});
        double[] vSpec = buildSpectrum(vn,    thdV, new double[]{h2*0.5, h3*0.5, h4*0.3, h5*0.4});

        return buildMeasurement(vn, iTotal, pf, thdI, thdV, iSpec, vSpec);
    }

    // ── Perfil: Variador de Velocidad (VFD) ──────────────────────────────────

    private FeederMeasurement simulateVFD() {
        // Ciclos de aceleración/desaceleración
        vfdSpeed += vfdDir * 0.003 * (1 + 0.2 * Math.abs(g()));
        if (vfdSpeed >= 1.0) { vfdSpeed = 1.0; vfdDir = -1; }
        if (vfdSpeed <= 0.1) { vfdSpeed = 0.1; vfdDir =  1; }

        double vn     = nominalPhaseV();
        double iTotal = cfg.getNominalCurrentA() * (0.3 + 0.65 * vfdSpeed) * (1 + 0.02 * g());
        double pf     = 0.93 + 0.02 * vfdSpeed + 0.01 * g();
        double thdI   = 28.0 - 5 * vfdSpeed + 3 * g(); // THD mayor a baja velocidad

        // Patrón 6-pulsos: 5°, 7°, 11°, 13°, 17°, 19°, 23°, 25° (6k±1)
        // buildSpectrum: relAmpl[n] → spec[n+1] = H(n+2), así H5=relAmpl[3], H7=relAmpl[5], etc.
        double[] relAmpl = new double[24];
        relAmpl[3]  = 0.30 + 0.04 * g(); // H5
        relAmpl[5]  = 0.20 + 0.03 * g(); // H7
        relAmpl[9]  = 0.10 + 0.02 * g(); // H11
        relAmpl[11] = 0.08 + 0.02 * g(); // H13
        relAmpl[15] = 0.05;              // H17
        relAmpl[17] = 0.04;              // H19
        relAmpl[21] = 0.03;              // H23
        relAmpl[23] = 0.02;              // H25 (idx=24 → out of bounds; use 23)

        double thdV = thdI * 0.10 + 0.5 * Math.abs(g());
        double[] iSpec = buildSpectrum(iTotal, thdI, relAmpl);
        double[] vSpec = buildSpectrum(vn, thdV, new double[]{0, 0, 0, 0.40, 0, 0.25, 0, 0, 0, 0.12, 0, 0.08});

        return buildMeasurement(vn, iTotal, pf, thdI, thdV, iSpec, vSpec);
    }

    // ── Perfil: Industrial Lineal ─────────────────────────────────────────────

    private FeederMeasurement simulateLinear() {
        // Carga lentamente variable, casi sinusoidal
        double load = 0.45 + 0.40 * Math.abs(Math.sin(timeSeconds / 7200.0 * Math.PI));
        load += 0.05 * g();
        load  = Math.max(0.1, Math.min(0.95, load));

        double vn     = nominalPhaseV();
        double iTotal = cfg.getNominalCurrentA() * load * (1 + 0.03 * g());
        double pf     = 0.83 + 0.05 * load + 0.01 * g();
        double thdI   = 4.0  + 1.5 * g();
        double thdV   = thdI * 0.06 + 0.2 * Math.abs(g());

        // Casi sin armónicos: pequeñas perturbaciones en H5/H7
        double[] iSpec = buildSpectrum(iTotal, thdI, new double[]{0, 0.05, 0, 0.15, 0, 0.08, 0, 0.04});
        double[] vSpec = buildSpectrum(vn,    thdV, new double[]{0, 0.05, 0, 0.20, 0, 0.10});

        return buildMeasurement(vn, iTotal, pf, thdI, thdV, iSpec, vSpec);
    }

    // ── Perfil: Carga Mixta Comercial ─────────────────────────────────────────

    private FeederMeasurement simulateMixed() {
        // Variación diurna: mañana, mediodía, tarde (ciclo de 1 hora simulada cada 600 s)
        double hour   = (timeSeconds % 600.0) / 600.0 * 24.0; // h simuladas en 10 min
        double dayFactor;
        if      (hour <  6) dayFactor = 0.25;
        else if (hour < 10) dayFactor = 0.25 + 0.60 * (hour - 6) / 4;
        else if (hour < 14) dayFactor = 0.85;
        else if (hour < 16) dayFactor = 0.70;
        else if (hour < 20) dayFactor = 0.90;
        else                dayFactor = 0.55;
        dayFactor += 0.05 * g();

        double vn     = nominalPhaseV();
        double iTotal = cfg.getNominalCurrentA() * dayFactor * (1 + 0.04 * g());
        double pf     = 0.91 + 0.03 * dayFactor + 0.01 * g();
        double thdI   = 14.0 + 4 * dayFactor + 2 * g();
        double thdV   = thdI * 0.11 + 0.5 * Math.abs(g());

        // Mezcla: algo de H3 (residencial), H5/H7 (electrónica)
        double[] iSpec = buildSpectrum(iTotal, thdI, new double[]{0, 0.12, 0, 0.20, 0, 0.14, 0, 0.06, 0, 0.04});
        double[] vSpec = buildSpectrum(vn,    thdV, new double[]{0, 0.10, 0, 0.30, 0, 0.18, 0, 0.06});

        return buildMeasurement(vn, iTotal, pf, thdI, thdV, iSpec, vSpec);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private double nominalPhaseV() {
        return cfg.getNominalVoltageKv() * 1000.0 / Math.sqrt(3);
    }

    /** Gaussiana escalada, desviación ~0.5 */
    private double g() { return RND.nextGaussian() * 0.5; }

    /**
     * Construye espectro armónico de 50 órdenes.
     * relAmpl[0] = H2, relAmpl[1] = H3, ..., relAmpl[n-1] = H(n+1)
     * (relAmpl[4] = H5, relAmpl[6] = H7, etc.)
     * Los amplitudes se normalizan para que el THD coincida con thdPct.
     */
    private double[] buildSpectrum(double fundamental, double thdPct, double[] relAmpl) {
        double[] spec = new double[50];
        spec[0] = fundamental;  // H1

        double sumSq = 0;
        for (double v : relAmpl) sumSq += v * v;
        double norm = (sumSq > 0) ? (thdPct / 100.0) / Math.sqrt(sumSq) : 0;
        for (int i = 0; i < relAmpl.length && i < 49; i++) {
            spec[i + 1] = Math.abs(relAmpl[i] * fundamental * norm);
        }
        return spec;
    }

    private FeederMeasurement buildMeasurement(
            double vn, double iTotal, double pf,
            double thdI, double thdV,
            double[] iSpec, double[] vSpec) {

        double phaseNoise = 0.015;
        double iL1 = iTotal * (1.00 + phaseNoise * g());
        double iL2 = iTotal * (0.98 + phaseNoise * g());
        double iL3 = iTotal * (1.02 + phaseNoise * g());
        double vL1 = vn * (1.00 + 0.004 * g());
        double vL2 = vn * (0.99 + 0.004 * g());
        double vL3 = vn * (1.01 + 0.004 * g());

        // P = 3 * Vfase * I * fp  (potencia trifasica correcta)
        double pTotal = vn * iTotal * 3.0 * Math.abs(pf) / 1000.0;
        double qTotal = vn * iTotal * 3.0 * Math.sqrt(Math.max(0, 1 - pf * pf)) / 1000.0;
        double sTotal = Math.sqrt(pTotal * pTotal + qTotal * qTotal);

        FeederMeasurement m = new FeederMeasurement(cfg.getFeederId(), cfg.getIedName());
        m.setVoltageL1(vL1); m.setVoltageL2(vL2); m.setVoltageL3(vL3);
        m.setCurrentL1(iL1); m.setCurrentL2(iL2); m.setCurrentL3(iL3);
        m.setActivePower(pTotal);
        m.setReactivePower(qTotal);
        m.setApparentPower(sTotal);
        m.setPowerFactor(pf);
        m.setFrequency(50.0 + 0.015 * g());

        m.setThdCurrentL1(thdI + 1.5 * g());
        m.setThdCurrentL2(thdI + 1.5 * g());
        m.setThdCurrentL3(thdI + 1.5 * g());
        m.setThdVoltageL1(thdV + 0.5 * g());
        m.setThdVoltageL2(thdV + 0.5 * g());
        m.setThdVoltageL3(thdV + 0.5 * g());

        m.setHarmonicCurrentL1(iSpec);
        m.setHarmonicCurrentL2(iSpec.clone());
        m.setHarmonicCurrentL3(iSpec.clone());
        m.setHarmonicVoltageL1(vSpec);
        m.setHarmonicVoltageL2(vSpec.clone());
        m.setHarmonicVoltageL3(vSpec.clone());

        // Calcular desbalance de tensión para que AlarmEngine use el valor pre-calculado
        double vAvg = (vL1 + vL2 + vL3) / 3.0;
        double maxDev = Math.max(Math.max(Math.abs(vL1 - vAvg), Math.abs(vL2 - vAvg)), Math.abs(vL3 - vAvg));
        m.setVoltageUnbalancePct(100.0 * maxDev / vAvg);

        return m;
    }
}
