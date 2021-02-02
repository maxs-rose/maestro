package org.intocps.maestro.plugin.synthesizer.instructions

import org.intocps.maestro.ast.node.PStm
import org.intocps.maestro.framework.fmi2.api.Fmi2Builder
import org.intocps.maestro.framework.fmi2.api.mabl.variables.ComponentVariableFmi2Api

class SaveInstruction(fmu: ComponentVariableFmi2Api, private var fmuStates: List<Fmi2Builder.StateVariable<PStm>>) : FMUCoSimInstruction(fmu) {
    override fun Perform() {
        fmuStates += FMU.state
    }
}