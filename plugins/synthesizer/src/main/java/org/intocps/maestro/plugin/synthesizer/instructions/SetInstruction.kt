package org.intocps.maestro.plugin.synthesizer.instructions

import org.intocps.maestro.framework.fmi2.api.mabl.PortFmi2Api
import org.intocps.maestro.framework.fmi2.api.mabl.variables.ComponentVariableFmi2Api

class SetInstruction(fmu: ComponentVariableFmi2Api, private val port: PortFmi2Api) : FMUCoSimInstruction(fmu) {
    override fun Perform() {
        FMU.setLinked(port.name)
    }
}