package org.intocps.maestro.simulationentity;

public class GaussSeidel extends SimulationMaster {

    @Override
    public String toString() {
        return "Gauss-Seidel Start\n" + super.toString();
    }

    @Override
    public String print(int indentCount) {
        return SimulationEntity.indent(indentCount) + "Gauss-Seidel\n" + super.print(++indentCount) + SimulationEntity.indent(indentCount);
    }
}
