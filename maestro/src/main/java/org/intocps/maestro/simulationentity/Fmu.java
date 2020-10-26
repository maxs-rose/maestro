package org.intocps.maestro.simulationentity;

public class Fmu extends SimulationUnit {
    final String id;

    public Fmu(String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "FMU\n";
    }

    @Override
    public String print(int indentCount) {
        return SimulationEntity.indent(indentCount) + "FMU";
    }
}
