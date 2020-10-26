package org.intocps.maestro.simulationentity;

public class Jacobi extends SimulationMaster {
    @Override
    public String print(int indentCount) {
        return SimulationEntity.indent(indentCount) + "Jacobi\n" + super.print(++indentCount) + SimulationEntity.indent(indentCount);
    }
}
