package com.harmonicmonitor.simulator;

/**
 * Data class representing one simulation load profile for {@link IonSimServer}.
 *
 * Previously a {@code static} inner class of {@link IonSimServer};
 * extracted to its own file (refactor F16-001).
 */
class Profile {
    String name;
    float phVL1, phVL2, phVL3;
    float aL1, aL2, aL3;
    float totW, totVAr, totVA, totPF, hz;
    float thdAL1, thdAL2, thdAL3;
    float thdPpvL12, thdPpvL23, thdPpvL31;
    float hKfL1, hKfL2, hKfL3;
    float thdOddA, thdEvnA;
    float[] harA = new float[50];
    float[] harB = new float[50];
    float[] harC = new float[50];
    float seqAPos, seqANeg, seqAZero, seqVPos, seqVNeg;
    long  totWh, totVAh, totVArh, supWh, supVArh;
    float avW, maxW, minW, avVAr, avVA;
}
