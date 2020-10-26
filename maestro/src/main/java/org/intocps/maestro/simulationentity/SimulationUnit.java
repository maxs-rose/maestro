package org.intocps.maestro.simulationentity;

public abstract class SimulationUnit implements SimulationEntity {

    @Override
    public SimulationEntityType getSimulationEntityType() {
        return SimulationEntityType.SIMULATIONUNIT;
    }
}
