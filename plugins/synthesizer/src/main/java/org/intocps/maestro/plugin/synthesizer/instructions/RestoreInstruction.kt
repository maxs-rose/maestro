package org.intocps.maestro.plugin.synthesizer.instructions

import org.intocps.maestro.ast.node.PStm
import org.intocps.maestro.framework.fmi2.api.Fmi2Builder.StateVariable
import org.intocps.maestro.framework.fmi2.api.mabl.variables.ComponentVariableFmi2Api


class RestoreInstruction(fmu: ComponentVariableFmi2Api, private val fmuStates: List<StateVariable<PStm>>) : FMUCoSimInstruction(fmu) {

    override fun Perform() {
        fmuStates.find { i -> i.name == FMU.name }!!.set()
    }
}