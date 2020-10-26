package org.intocps.maestro.simulationentity;

import java.util.Map;

import static org.intocps.maestro.simulationentity.SimulationEntity.indent;

public class VariableStep extends IStepAlgorithm {

    private Map<String, IVarStepConstraint> constraints;
    private double initialSize;
    private double minimumSize;

    public VariableStep() {

    }

    public VariableStep(double initialSize, double minimumSize, double maxSize) {
        this.initialSize = initialSize;
        this.minimumSize = minimumSize;
        this.configuredMaxSize = maxSize;
    }


    public Map<String, IVarStepConstraint> getConstraints() {
        return constraints;
    }

    public void setConstraints(Map<String, IVarStepConstraint> constraints) {
        this.constraints = constraints;
    }

    public double getInitialSize() {
        return initialSize;
    }

    public void setInitialSize(double initialSize) {
        this.initialSize = initialSize;
    }

    public double getMinimumSize() {
        return minimumSize;
    }

    public void setMinimumSize(double minimumSize) {
        this.minimumSize = minimumSize;
    }

    public void setMaxSize(double maxSize) {
        this.configuredMaxSize = maxSize;
    }

    @Override
    public String toString() {
        return "StepAlgorithm - Variable - max size " + this.getMaxSize() + "\n";
    }

    @Override
    public String print(int indentCount) {
        return indent(indentCount) + "StepAlgorithm - Variable - max size " + this.getMaxSize() + "\n";
    }
}
