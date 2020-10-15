package org.intocps.maestro.plugin.verificationsuite.graph;

import guru.nidi.graphviz.attribute.Color;
import guru.nidi.graphviz.attribute.Style;
import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.model.MutableGraph;
import org.intocps.maestro.framework.fmi2.FmiSimulationEnvironment;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import static guru.nidi.graphviz.model.Factory.mutGraph;
import static guru.nidi.graphviz.model.Factory.mutNode;

public class GraphDrawer {
    private String getInstanceName(FmiSimulationEnvironment.Variable o) {
        return o.scalarVariable.instance.getText() + "." + o.scalarVariable.scalarVariable.getName();
    }

    public void plotGraph(Set<? extends FmiSimulationEnvironment.Relation> relations, String name, String filepath) throws IOException {
        MutableGraph g = mutGraph(name).setDirected(true);
        var connections = relations.stream().filter(o -> o.getDirection() == FmiSimulationEnvironment.Relation.Direction.OutputToInput)
                .collect(Collectors.toList());
        for (FmiSimulationEnvironment.Relation rel : connections) {
            var targets = rel.getTargets().values().stream().map(o -> mutNode(getInstanceName(o)).add(Color.BLACK)).collect(Collectors.toList());
            var source = mutNode(getInstanceName(rel.getSource())).add(Color.BLACK);

            if (rel.getOrigin() == FmiSimulationEnvironment.Relation.InternalOrExternal.Internal) {
                targets.forEach(t -> {
                    g.add(t.addLink(source));
                    g.nodes().stream().filter(o -> o == t).forEach(o -> o.links().forEach(r -> r.attrs().add(Style.DASHED)));
                });
            } else {
                targets.forEach(t -> g.add(source.addLink(t)));
            }
        }


        Graphviz.fromGraph(g).height(500).render(Format.PNG).toFile(new File(filepath + ".png"));
    }
}
