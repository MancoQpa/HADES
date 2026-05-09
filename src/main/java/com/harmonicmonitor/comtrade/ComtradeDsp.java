package com.harmonicmonitor.comtrade;

import java.util.Arrays;

/**
 * DSP utilities for COMTRADE signal analysis.
 * Pure static methods — no GUI dependencies.
 */
public final class ComtradeDsp {

    private ComtradeDsp() {}

    /**
     * Returns magnitude[maxOrder] (RMS amplitudes for harmonics H1..HmaxOrder).
     */
    public static double[] calculateFFTMagnitude(double[] samples, double fs, double f0, int maxOrder) {
        double[][] c = calculateComplexSpectrum(samples, fs, f0, maxOrder);
        double[] mag = new double[maxOrder];
        for (int i = 0; i < maxOrder; i++) mag[i] = c[i][0];
        return mag;
    }

    /**
     * Returns [maxOrder][2] where [h][0] = magnitude_rms, [h][1] = phase_radians.
     */
    public static double[][] calculateComplexSpectrum(double[] samples, double fs, double f0, int maxOrder) {
        double[][] result = new double[maxOrder][2];
        if (samples == null || samples.length < 4 || fs <= 0 || f0 <= 0) return result;

        int sampPerCycle = (int) Math.round(fs / f0);
        int numCycles    = Math.max(1, samples.length / sampPerCycle);
        int N            = Math.min(numCycles * sampPerCycle, samples.length);

        int fftSize = 1;
        while (fftSize < N) fftSize <<= 1;

        double[] re = new double[fftSize];
        double[] im = new double[fftSize];
        for (int i = 0; i < N; i++) {
            double w = 0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / Math.max(1, N - 1)));
            re[i] = samples[i] * w;
        }
        fft(re, im, fftSize);

        // Hann window coherent gain = 0.5 (sum of coefficients ≈ N/2).
        // Correct peak amplitude: A_peak = |X[bin]| * 4 / N  (not 2/N).
        // Convert to RMS: A_rms = A_peak / sqrt(2).
        double freqRes = fs / fftSize;
        for (int h = 1; h <= maxOrder; h++) {
            int bin = (int) Math.round(h * f0 / freqRes);
            if (bin < fftSize / 2) {
                double mag   = Math.sqrt(re[bin] * re[bin] + im[bin] * im[bin]) * 4.0 / N;
                result[h-1][0] = mag / Math.sqrt(2.0);        // RMS
                result[h-1][1] = Math.atan2(im[bin], re[bin]); // phase
            }
        }
        return result;
    }

    /** Cooley-Tukey in-place FFT (n must be power of 2). */
    public static void fft(double[] re, double[] im, int n) {
        int j = 0;
        for (int i = 1; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                double tr = re[i]; re[i] = re[j]; re[j] = tr;
                double ti = im[i]; im[i] = im[j]; im[j] = ti;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2.0 * Math.PI / len;
            double wR  = Math.cos(ang), wI = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double cR = 1.0, cI = 0.0;
                for (int k = 0; k < len / 2; k++) {
                    double uR = re[i+k], uI = im[i+k];
                    double vR = re[i+k+len/2]*cR - im[i+k+len/2]*cI;
                    double vI = re[i+k+len/2]*cI + im[i+k+len/2]*cR;
                    re[i+k]         = uR + vR; im[i+k]         = uI + vI;
                    re[i+k+len/2]   = uR - vR; im[i+k+len/2]   = uI - vI;
                    double nR = cR*wR - cI*wI;
                    double nI = cR*wI + cI*wR;
                    cR = nR; cI = nI;
                }
            }
        }
    }

    /**
     * Returns the sub-array of signal within the analysis window [winStart, winEnd).
     * winEnd < 0 means "use the entire signal".
     */
    public static double[] extractWindow(double[] signal, int winStart, int winEnd) {
        if (signal == null) return new double[0];
        if (winEnd < 0) return signal;
        int s = Math.max(0, winStart);
        int e = Math.min(signal.length, winEnd);
        if (s >= e) return signal;
        return Arrays.copyOfRange(signal, s, e);
    }
}
