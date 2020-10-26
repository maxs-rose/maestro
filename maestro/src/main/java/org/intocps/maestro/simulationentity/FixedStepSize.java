package org.intocps.maestro.simulationentity;

public class FixedStepSize implements StepSize {
    final Double stepSize;

    public FixedStepSize(Double stepSize) {
        this.stepSize = stepSize;
    }

    public Double getStepSize() {
        return stepSize;
    }
}
