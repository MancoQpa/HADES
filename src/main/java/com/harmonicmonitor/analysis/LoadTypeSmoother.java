package com.harmonicmonitor.analysis;

import com.harmonicmonitor.model.LoadType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;

/**
 * Suavizado temporal de la clasificación de carga: ventana deslizante con
 * histéresis de conmutación.
 *
 * <p><b>Problema que resuelve</b>: el árbol de {@link ElectronicLoadDetector}
 * clasifica muestra a muestra con umbrales duros. Una carga cuyo valor queda
 * clavado en un borde (p. ej. THD ≈ 5.0%) oscila de clase por puro ruido de
 * medición. Evidencia empírica: en {@code docs/estudio_roc.md} el perfil
 * {@code normal_load} (THD = 5.0% exacto) se repartió 50.7% LINEAR /
 * 49.3% MIXED_ELECTRONIC sobre 2 251 muestras de una carga constante.
 *
 * <p><b>Diseño</b>: esta clase envuelve la salida del árbol sin tocar sus
 * umbrales (el árbol sigue siendo determinista y testeable por muestra):
 * <ul>
 *   <li><b>Ventana</b>: se conservan las últimas N clasificaciones crudas
 *       (N = {@code FeederConfig.loadTypeWindowSize}, por defecto 15).</li>
 *   <li><b>Adopción inicial</b>: mientras no hay clase estable (UNKNOWN),
 *       se adopta la mayoría simple de la ventana — el arranque es reactivo.</li>
 *   <li><b>Histéresis de conmutación</b>: una vez adoptada una clase estable,
 *       solo se conmuta cuando otra clase alcanza una supermayoría de
 *       ⌈fracción × N⌉ muestras en la ventana (fracción =
 *       {@code loadTypeSwitchFraction}, por defecto 0.67 → 10 de 15).
 *       El requisito se calcula sobre N (ventana completa), no sobre el
 *       llenado actual: conmutar siempre exige evidencia suficiente.</li>
 * </ul>
 *
 * <p>Consecuencias: una oscilación 50/50 nunca alcanza supermayoría → la
 * primera clase adoptada se mantiene (sin parpadeo). Un cambio de carga real
 * conmuta tras ~⌈0.67·N⌉ muestras (≈ 10 ciclos de polling con los valores por
 * defecto). Un transitorio breve (&lt; supermayoría) no conmuta.
 *
 * <p>La fracción de la ventana que coincide con la clase estable se expone
 * como {@link #stability()} (0–1) para visualización de confianza en GUI y
 * exportación.
 *
 * <p>No es thread-safe: cada poller posee su propia instancia y la usa desde
 * su propio hilo de scheduling.
 */
public class LoadTypeSmoother {

    private final int windowSize;
    private final int switchCount;   // muestras de supermayoría para conmutar
    private final Deque<LoadType> window;

    private LoadType stable = LoadType.UNKNOWN;

    /**
     * @param windowSize      tamaño N de la ventana deslizante (mín. 1)
     * @param switchFraction  fracción de N que debe alcanzar una clase
     *                        distinta para desbancar a la estable (0–1;
     *                        se satura a [0.5, 1.0] para que la supermayoría
     *                        sea al menos mayoría absoluta)
     */
    public LoadTypeSmoother(int windowSize, double switchFraction) {
        this.windowSize  = Math.max(1, windowSize);
        double f = Math.min(1.0, Math.max(0.5, switchFraction));
        this.switchCount = Math.max(1, (int) Math.ceil(f * this.windowSize));
        this.window      = new ArrayDeque<>(this.windowSize);
    }

    /**
     * Ingresa la clasificación cruda de la muestra actual y devuelve la
     * clase estable (suavizada) vigente.
     */
    public LoadType add(LoadType raw) {
        if (raw == null) raw = LoadType.UNKNOWN;
        if (window.size() == windowSize) window.removeFirst();
        window.addLast(raw);

        Map<LoadType, Integer> counts = new EnumMap<>(LoadType.class);
        for (LoadType t : window) counts.merge(t, 1, Integer::sum);

        LoadType mode = raw;      // desempate: la clase más reciente
        int modeCount = 0;
        for (Map.Entry<LoadType, Integer> e : counts.entrySet()) {
            if (e.getValue() > modeCount
                || (e.getValue() == modeCount && e.getKey() == raw)) {
                mode = e.getKey();
                modeCount = e.getValue();
            }
        }

        if (stable == LoadType.UNKNOWN) {
            stable = mode;                          // adopción inicial reactiva
        } else if (mode != stable && modeCount >= switchCount) {
            stable = mode;                          // conmutación con histéresis
        }
        return stable;
    }

    /** Clase estable vigente (UNKNOWN si aún no se ingresó ninguna muestra). */
    public LoadType getStable() { return stable; }

    /**
     * Fracción de la ventana actual que coincide con la clase estable (0–1).
     * 1.0 = clasificación unánime; valores cercanos a 0.5 indican una carga
     * en un borde de umbral o en transición.
     */
    public double stability() {
        if (window.isEmpty()) return 0.0;
        int n = 0;
        for (LoadType t : window) if (t == stable) n++;
        return (double) n / window.size();
    }

    /** Reinicia ventana y clase estable (p. ej. tras reconexión del feeder). */
    public void reset() {
        window.clear();
        stable = LoadType.UNKNOWN;
    }
}
