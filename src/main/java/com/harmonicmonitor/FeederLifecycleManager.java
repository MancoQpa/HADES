package com.harmonicmonitor;

import com.harmonicmonitor.alarm.AlarmEngine;
import com.harmonicmonitor.comm.IEC61850Communicator;
import com.harmonicmonitor.comm.MeasurementPoller;
import com.harmonicmonitor.comm.SimulatedPoller;
import com.harmonicmonitor.comtrade.ComtradeTriggerEngine;
import com.harmonicmonitor.model.AlarmEvent;
import com.harmonicmonitor.model.FeederConfig;
import com.harmonicmonitor.model.FeederMeasurement;
import com.harmonicmonitor.model.SimProfile;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the lifecycle of feeders: creation, polling, reconnection, and removal.
 *
 * Holds the four feeder-related collections previously in {@link HarmonicMonitorApp}
 * and provides all lifecycle operations. UI notifications are delivered via
 * package-private methods/fields on the parent {@code HarmonicMonitorApp}.
 *
 * Extracted from {@link HarmonicMonitorApp} (refactor F11-001).
 */
class FeederLifecycleManager {

    private final HarmonicMonitorApp      app;
    private final com.harmonicmonitor.storage.DataStorage dataStorage;
    private final ComtradeTriggerEngine   comtradeTrigger;

    // ── Collections (package-private for direct access from HarmonicMonitorApp) ─

    final List<FeederConfig>         feederConfigs  = new ArrayList<>();
    final List<IEC61850Communicator> communicators  = new ArrayList<>();
    final List<MeasurementPoller>    pollers        = new ArrayList<>();
    final ConcurrentHashMap<String, FeederMeasurement> latestMeasurements
        = new ConcurrentHashMap<>();

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    FeederLifecycleManager(HarmonicMonitorApp app,
                           com.harmonicmonitor.storage.DataStorage dataStorage,
                           ComtradeTriggerEngine comtradeTrigger) {
        this.app            = app;
        this.dataStorage    = dataStorage;
        this.comtradeTrigger = comtradeTrigger;
    }

    // ── Add feeders ───────────────────────────────────────────────────────────

    /**
     * Adds a pre-configured demo feeder driven by a {@link SimulatedPoller}.
     */
    void addDemoFeeder() {
        int n = feederConfigs.size() + 1;
        FeederConfig cfg = new FeederConfig("AL-DEMO-" + n, "127.0.0.1");
        cfg.setFeederName("Alimentador Demo " + n);
        cfg.setIedName("IED_DEMO_" + n);
        cfg.setNominalVoltageKv(23.0);
        cfg.setNominalCurrentA(150.0 + (n - 1) * 25);
        cfg.setShortCircuitMva(80.0);
        cfg.setFeederCapacitanceMicroF(3.5);
        feederConfigs.add(cfg);
        comtradeTrigger.prepareFeederDir(cfg.getFeederId());
        startSimulatedPoller(cfg);
        app.updateFeedersIndicator();
        if (app.multiFeederPanel != null) app.multiFeederPanel.refreshFeeders();
        if (app.trendsPanel      != null) app.trendsPanel.refreshFeederSelector();
        app.setStatusMessage("Feeder demo agregado: " + cfg.getFeederName());
    }

    /**
     * Adds a feeder driven by a {@link SimulatedPoller} using the profile in
     * {@code cfg.getSimProfile()}.
     */
    void addSimulatedFeeder(FeederConfig cfg) {
        String id = cfg.getFeederId();
        boolean dup = feederConfigs.stream().anyMatch(c -> c.getFeederId().equals(id));
        if (dup) cfg.setFeederId(id + "-" + (feederConfigs.size() + 1));

        feederConfigs.add(cfg);
        comtradeTrigger.prepareFeederDir(cfg.getFeederId());
        startSimulatedPoller(cfg);
        app.updateFeedersIndicator();
        if (app.multiFeederPanel != null) app.multiFeederPanel.refreshFeeders();
        if (app.trendsPanel      != null) app.trendsPanel.refreshFeederSelector();
        if (app.alarmsPanel      != null) app.alarmsPanel.refreshFeederFilter();
        String profileName = cfg.getSimProfile() != null ? cfg.getSimProfile().displayName : "Demo";
        app.setStatusMessage("Feeder simulado: " + cfg.getFeederName() + "  [" + profileName + "]");
    }

    /**
     * Adds a real IEC 61850 feeder and initiates async MMS connection.
     * Starts a {@link MeasurementPoller} once the server model is loaded.
     */
    void addFeeder(FeederConfig cfg) {
        boolean dup = feederConfigs.stream().anyMatch(c -> c.getFeederId().equals(cfg.getFeederId()));
        if (dup) {
            app.setStatusMessage("Ya existe un feeder con ID \"" + cfg.getFeederId() + "\" \u2014 ignorado");
            return;
        }
        feederConfigs.add(cfg);
        comtradeTrigger.prepareFeederDir(cfg.getFeederId());
        IEC61850Communicator comm = new IEC61850Communicator(cfg);
        communicators.add(comm);

        comm.addListener(ev -> Platform.runLater(() -> {
            app.setStatusMessage(ev.feederId + ": " + ev.message);

            // Actualizar LED de conexion en pestana MULTI
            if (app.multiFeederPanel != null) {
                IEC61850Communicator.State ledState;
                switch (ev.type) {
                    case CONNECTED:
                    case MODEL_LOADED:  ledState = IEC61850Communicator.State.CONNECTED;    break;
                    case DISCONNECTED:  ledState = IEC61850Communicator.State.DISCONNECTED; break;
                    case CONNECTION_FAILED:
                    case READ_ERROR:    ledState = IEC61850Communicator.State.ERROR;         break;
                    default:            ledState = IEC61850Communicator.State.CONNECTING;   break;
                }
                app.multiFeederPanel.updateConnectionState(cfg.getFeederId(), ledState);
            }

            // Actualizar dashboard con estado de conexion
            if (app.dashboardPanel != null) {
                IEC61850Communicator.State ds;
                switch (ev.type) {
                    case CONNECTED:
                    case MODEL_LOADED:  ds = IEC61850Communicator.State.CONNECTED;    break;
                    case DISCONNECTED:  ds = IEC61850Communicator.State.DISCONNECTED; break;
                    case CONNECTION_FAILED:
                    case READ_ERROR:    ds = IEC61850Communicator.State.ERROR;         break;
                    default:            ds = IEC61850Communicator.State.CONNECTING;   break;
                }
                app.dashboardPanel.updateConnectionState(cfg.getFeederId(), ds);
            }

            // Registrar perdida/recuperacion de conexion como alarma
            if (ev.type == IEC61850Communicator.CommEvent.Type.DISCONNECTED ||
                ev.type == IEC61850Communicator.CommEvent.Type.CONNECTION_FAILED) {
                AlarmEvent connAlarm = new AlarmEvent(
                    AlarmEvent.Level.WARNING, cfg.getFeederId(),
                    "Conexion",
                    "Perdida de conexion [" + cfg.getFeederId() + "]: " + ev.message,
                    0.0, 0.0);
                dataStorage.storeAlarm(connAlarm);
                if (app.alarmsPanel != null) app.alarmsPanel.onAlarm(connAlarm);
            } else if (ev.type == IEC61850Communicator.CommEvent.Type.MODEL_LOADED) {
                AlarmEvent connOk = new AlarmEvent(
                    AlarmEvent.Level.WARNING, cfg.getFeederId(),
                    "Conexion",
                    "Conexion establecida/restablecida [" + cfg.getFeederId() + "]: " + ev.message,
                    1.0, 1.0);
                dataStorage.storeAlarm(connOk);
            }

            if (ev.type == IEC61850Communicator.CommEvent.Type.MODEL_LOADED) {
                // Advertir si el IED no expone el array de armonicos
                if (!comm.isHarmonicArrayInModel()) {
                    Alert warn = new Alert(Alert.AlertType.WARNING);
                    warn.setTitle("Modo degradado \u2014 arm\u00F3nicos no disponibles");
                    warn.setHeaderText("El IED \"" + cfg.getIedName() + "\" no expone el array de arm\u00F3nicos (MHAI.HA)");
                    warn.setContentText(
                        "Sin el array H1\u2013H13, las dimensiones espectrales del clasificador\n" +
                        "(H5/H1, H7/H1, H11/H1) ser\u00E1n ESTIMADAS con un perfil SMPS gen\u00E9rico,\n" +
                        "no medidas desde el IED.\n\n" +
                        "En modo degradado solo THD_I, CV y FP son observables reales.\n" +
                        "Los resultados de clasificaci\u00F3n de tipo de carga tienen validez reducida\n" +
                        "y el espectro mostrado NO proviene del instrumento.\n\n" +
                        "\u00BFDesea continuar de todas formas?");
                    ButtonType btnContinuar = new ButtonType("Continuar en modo degradado", ButtonBar.ButtonData.OK_DONE);
                    ButtonType btnCancelar  = new ButtonType("Cancelar conexi\u00F3n",       ButtonBar.ButtonData.CANCEL_CLOSE);
                    warn.getButtonTypes().setAll(btnContinuar, btnCancelar);
                    Optional<ButtonType> res = warn.showAndWait();
                    if (res.isEmpty() || res.get().getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE) {
                        comm.disconnect();
                        communicators.remove(comm);
                        feederConfigs.remove(cfg);
                        app.setStatusMessage(cfg.getFeederId() + ": conexi\u00F3n cancelada \u2014 el IED no provee arm\u00F3nicos");
                        if (app.multiFeederPanel != null) {
                            app.multiFeederPanel.updateConnectionState(cfg.getFeederId(),
                                IEC61850Communicator.State.DISCONNECTED);
                            app.multiFeederPanel.refreshFeeders();
                        }
                        app.updateFeedersIndicator();
                        return;
                    }
                }
                startPoller(comm, cfg);
                app.updateFeedersIndicator();
                if (app.multiFeederPanel != null) app.multiFeederPanel.refreshFeeders();
                if (app.trendsPanel      != null) app.trendsPanel.refreshFeederSelector();
                if (app.alarmsPanel      != null) app.alarmsPanel.refreshFeederFilter();
            }
            app.updatePollingIndicator();
        }));

        comm.connectAsync();
        if (app.multiFeederPanel != null)
            app.multiFeederPanel.updateConnectionState(cfg.getFeederId(), IEC61850Communicator.State.CONNECTING);
        app.updateFeedersIndicator();
        app.setStatusMessage("Conectando a " + cfg.getFeederName() + " (" + cfg.getIedHost() + ")...");
    }

    // ── Remove / reconnect ────────────────────────────────────────────────────

    void removeFeeder(String feederId) {
        List<MeasurementPoller> toRemove = new ArrayList<>();
        for (MeasurementPoller p : pollers) {
            if (p.getConfig().getFeederId().equals(feederId)) {
                try { p.stop(); } catch (Exception ignored) {}
                toRemove.add(p);
            }
        }
        pollers.removeAll(toRemove);

        communicators.stream()
            .filter(c -> c.getConfig().getFeederId().equals(feederId))
            .findFirst()
            .ifPresent(c -> { try { c.disconnect(); } catch (Exception ignored) {} });
        communicators.removeIf(c -> c.getConfig().getFeederId().equals(feederId));

        feederConfigs.removeIf(c -> c.getFeederId().equals(feederId));
        latestMeasurements.remove(feederId);
        app.updateFeedersIndicator();
        if (app.multiFeederPanel != null) app.multiFeederPanel.refreshFeeders();
        if (app.trendsPanel      != null) app.trendsPanel.refreshFeederSelector();
        if (app.alarmsPanel      != null) app.alarmsPanel.refreshFeederFilter();
        app.setStatusMessage("Feeder eliminado: " + feederId);
    }

    /** Fuerza la reconexion de un feeder real desconectado o en error. */
    void reconnectFeeder(String feederId) {
        IEC61850Communicator comm = null;
        for (IEC61850Communicator c : communicators) {
            if (c.getConfig().getFeederId().equals(feederId)) { comm = c; break; }
        }
        if (comm == null) {
            app.setStatusMessage(feederId + ": es simulado, no requiere reconexion");
            return;
        }
        IEC61850Communicator.State st = comm.getState();
        if (st == IEC61850Communicator.State.CONNECTED || st == IEC61850Communicator.State.CONNECTING) {
            app.setStatusMessage(feederId + ": ya esta conectado o conectandose");
            return;
        }
        List<MeasurementPoller> toStop = new ArrayList<>();
        for (MeasurementPoller p : pollers) {
            if (p.getConfig().getFeederId().equals(feederId)) toStop.add(p);
        }
        for (MeasurementPoller p : toStop) { try { p.stop(); } catch (Exception ignored) {} }
        pollers.removeAll(toStop);

        comm.connectAsync();
        if (app.multiFeederPanel != null)
            app.multiFeederPanel.updateConnectionState(feederId, IEC61850Communicator.State.CONNECTING);
        app.setStatusMessage("Reconectando " + feederId + "...");
        app.updatePollingIndicator();
    }

    // ── Polling ───────────────────────────────────────────────────────────────

    void setPollingInterval(int ms) {
        for (MeasurementPoller p : pollers) p.setInterval(ms);
        app.setStatusMessage("Intervalo de polling: " + ms / 1000 + " seg");
    }

    private void startPoller(IEC61850Communicator comm, FeederConfig cfg) {
        List<MeasurementPoller> existing = new ArrayList<>();
        for (MeasurementPoller px : pollers) {
            if (px.getConfig().getFeederId().equals(cfg.getFeederId())) existing.add(px);
        }
        for (MeasurementPoller px : existing) { try { px.stop(); } catch (Exception ignored) {} }
        pollers.removeAll(existing);

        MeasurementPoller p = new MeasurementPoller(cfg, comm);
        pollers.add(p);
        p.addListener(m -> app.onMeasurement(m, cfg));
        p.start();
    }

    private void startSimulatedPoller(FeederConfig cfg) {
        SimulatedPoller p = new SimulatedPoller(cfg);
        pollers.add(p);
        p.addListener(m -> app.onMeasurement(m, cfg));
        p.start();
    }

    // ── State queries ─────────────────────────────────────────────────────────

    /** Returns the connection state of the communicator for a feeder, or null if simulated. */
    IEC61850Communicator.State getCommunicatorState(String feederId) {
        return communicators.stream()
            .filter(c -> c.getConfig().getFeederId().equals(feederId))
            .findFirst()
            .map(IEC61850Communicator::getState)
            .orElse(null);
    }

    /** True if the feeder runs on a {@link SimulatedPoller} (no real IEC 61850 communicator). */
    boolean isSimulatedFeeder(String feederId) {
        return communicators.stream().noneMatch(c -> c.getConfig().getFeederId().equals(feederId));
    }
}
