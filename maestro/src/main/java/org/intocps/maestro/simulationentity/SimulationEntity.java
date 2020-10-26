package org.intocps.maestro.simulationentity;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

public interface SimulationEntity {
    static String indent(int indentionCount) {
        return IntStream.range(0, indentionCount).mapToObj(i -> "\t").collect(Collectors.joining());
    }

    SimulationEntity.SimulationEntityType getSimulationEntityType();


    String print(int indentCount);

    enum SimulationEntityType {
        SIMULATIONUNIT,
        SIMULATIONMASTER
    }

}
