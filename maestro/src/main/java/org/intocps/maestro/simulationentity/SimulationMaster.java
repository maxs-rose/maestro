package org.intocps.maestro.simulationentity;

import java.util.List;
import java.util.stream.Collectors;

public abstract class SimulationMaster implements SimulationEntity {
    protected List<SimulationEntity> simulationEntities;
    protected IStepAlgorithm stepAlgorithm;

    public List<SimulationEntity> getSimulationEntities() {
        return simulationEntities;
    }

    public void setSimulationEntities(List<SimulationEntity> simulationEntities) {
        this.simulationEntities = simulationEntities;
    }

    public IStepAlgorithm getStepAlgorithm() {
        return stepAlgorithm;
    }

    public void setStepAlgorithm(IStepAlgorithm stepAlgorithm) {
        this.stepAlgorithm = stepAlgorithm;
    }

    @Override
    public SimulationEntity.SimulationEntityType getSimulationEntityType() {
        return SimulationEntityType.SIMULATIONMASTER;
    }

    public void forceMaxStep(double maxSize) {
        stepAlgorithm.forceMaxSize(maxSize);
    }

    /**
     * This propagates the forced max step to its simulation entities
     */
    public void configureMaxStep() {
        double maxStep = stepAlgorithm.getMaxSize();

        for (SimulationEntity simulationEntity : simulationEntities) {
            if (simulationEntity.getSimulationEntityType() == SimulationEntityType.SIMULATIONMASTER) {
                ((SimulationMaster) simulationEntity).forceMaxStep(maxStep);
            }
        }
    }

    @Override
    public String toString() {
        return stepAlgorithm + String.join("\n", this.simulationEntities.stream().map(x -> x.toString()).collect(Collectors.toList()));
    }

    @Override
    public String print(int i) {
        return stepAlgorithm.print(i) + String.join("\n", this.simulationEntities.stream().map(x -> x.print(i)).collect(Collectors.toList()));
    }
}
