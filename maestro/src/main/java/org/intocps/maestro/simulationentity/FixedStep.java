package org.intocps.maestro.simulationentity;

import static org.intocps.maestro.simulationentity.SimulationEntity.indent;

public class FixedStep extends IStepAlgorithm {
    final double size;

    public FixedStep(double size) {
        this.size = size;
        this.configuredMaxSize = size;
    }

    @Override
    public String toString() {
        return "StepAlgorithm - Fixed - max size " + this.getMaxSize() + "\n";
    }

    @Override
    public String print(int indentCount) {
        return indent(indentCount) + "StepAlgorithm - Fixed - max size " + this.getMaxSize() + "\n";
    }
}
