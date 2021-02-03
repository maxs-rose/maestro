package org.intocps.maestro.plugin.synthesizer

import core.*
import org.intocps.maestro.framework.core.IRelation
import org.intocps.maestro.framework.core.IRelation.InternalOrExternal
import org.intocps.maestro.framework.fmi2.Fmi2SimulationEnvironment
import org.intocps.maestro.framework.fmi2.api.mabl.PortFmi2Api
import org.intocps.maestro.framework.fmi2.api.mabl.variables.ComponentVariableFmi2Api
import org.intocps.orchestration.coe.modeldefinition.ModelDescription
import scala.collection.immutable.`Map$`
import scala.jdk.javaapi.CollectionConverters
import scala.jdk.javaapi.`CollectionConverters$`

class ScalaModelBuilder {
    fun buildFmuModels(fmuInstances: Map<String, ComponentVariableFmi2Api>, env: Fmi2SimulationEnvironment?,
                       feedthrough: List<ConnectionModel>): Map<String, FmuModel> {
        return fmuInstances.values.map { fmu: ComponentVariableFmi2Api ->
            val inputs = fmu.ports.filter { i: PortFmi2Api -> i.scalarVariable.causality == ModelDescription.Causality.Input }
            val outputs = fmu.ports.filter { i: PortFmi2Api -> i.scalarVariable.causality == ModelDescription.Causality.Output }
            val feedthroughFMU = feedthrough.filter { i: ConnectionModel -> i.srcPort().fmu() == fmu.name }
            val inputMap = inputs.map { x: PortFmi2Api -> (x.name to InputPortModel(Reactivity.delayed())) }.toMap()
            val outputMap = outputs.map { v: PortFmi2Api ->
                val dependencies = CollectionConverters.asScala(feedthroughFMU.filter { i: ConnectionModel -> i.srcPort().port() == v.name }.map { i: ConnectionModel -> i.trgPort().port() }).toList()
                (v.name to OutputPortModel(dependencies, dependencies))
            }.toMap()

            fmu.name to FmuModel(toScalaMap(inputMap), toScalaMap(outputMap), false)
        }.toMap()
    }

    fun <A, B> toScalaMap(javaMap: Map<A, B>?): scala.collection.immutable.Map<A, B>? {
        val mutableScalaMap = `CollectionConverters$`.`MODULE$`.asScala(javaMap)
        return `Map$`.`MODULE$`.from(mutableScalaMap)
    }

    fun createConnections(env: Fmi2SimulationEnvironment, fmuInstance: Map<String, ComponentVariableFmi2Api?>,
                          external: InternalOrExternal): List<ConnectionModel> {
        val relations = env.getRelations(*fmuInstance.keys.toTypedArray()).filter { i -> i.direction == IRelation.Direction.OutputToInput && i.origin == external }
        return relations
                .flatMap { i -> i.targets.values.map { o -> relationToConnectionModel(i.source, o) } }
    }

    private fun relationToConnectionModel(source: Fmi2SimulationEnvironment.Variable, target: Fmi2SimulationEnvironment.Variable): ConnectionModel {
        return ConnectionModel(PortRef(source.getScalarVariable().instance.text,
                source.getScalarVariable().scalarVariable.getName()),
                PortRef(target.getScalarVariable().instance.text, target.getScalarVariable().scalarVariable.getName()))
    }
}
