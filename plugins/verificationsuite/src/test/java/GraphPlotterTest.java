import org.intocps.maestro.ast.LexIdentifier;
import org.intocps.maestro.plugin.env.UnitRelationship;
import org.intocps.maestro.plugin.env.UnitRelationship.Variable;
import org.intocps.maestro.plugin.env.fmi2.RelationVariable;
import org.intocps.maestro.plugin.verificationsuite.graph.GraphDrawer;
import org.intocps.maestro.plugin.verificationsuite.vdmcheck.VDMChecker;
import org.intocps.orchestration.coe.modeldefinition.ModelDescription;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.stream.Collectors;

public class GraphPlotterTest {
    InputStream envWaterTankJson = this.getClass().getResourceAsStream("PrologVerifierTest/env.json");
    InputStream envThreeTankJson = this.getClass().getResourceAsStream("PrologVerifierTest/threetank_env.json");
    String path;

    @Before
    public void setUp() {
        path = GraphPlotterTest.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        if (path != null && path.length() > 0 && path.charAt(path.length() - 1) == '/') {
            path = path.substring(0, path.length() - 1);
        }
        path = path.substring(0, path.lastIndexOf("/"));
    }

    @Test
    public void PlotGraphWatertankTest() throws Exception {
        var graphDrawer = new GraphDrawer();
        var unitRelationship = new UnitRelationship(envWaterTankJson);
        var components = Arrays.asList("crtlInstance", "wtInstance");
        var relations = new HashSet<UnitRelationship.Relation>();
        components.forEach(c -> relations.addAll(unitRelationship.getRelations(new LexIdentifier(c, null))));
        String filePath = Path.of(path, "WaterTankGraph").toString();
        graphDrawer.plotGraph(relations, "WaterTankGraph", filePath);
    }

    //@Ignore
    @Test
    public void PlotGraphThreeTankTest() throws Exception {
        var graphDrawer = new GraphDrawer();
        var unitRelationship = new UnitRelationship(envThreeTankJson);
        var components = Arrays.asList("controller", "tank1", "tank2");
        var relations = new HashSet<UnitRelationship.Relation>();
        components.forEach(c -> relations.addAll(unitRelationship.getRelations(new LexIdentifier(c, null))));

        String filePath = Path.of(path, "ThreeTankGraph").toString();
        graphDrawer.plotGraph(relations, "ThreeTankGraph", filePath);
    }

    @Test
    public void PlotGraphWithSimpleLoop() throws Exception {
        var graphDrawer = new GraphDrawer();
        var unitRelationship = new UnitRelationship(envThreeTankJson);
        var relations = new HashSet<UnitRelationship.Relation>();

        var variable1 = createVariable("Ctrl", "Sig", unitRelationship);
        var variable2 = createVariable("Ctrl", "Input", unitRelationship);
        var variable3 = createVariable("CE", "Level", unitRelationship);
        var variable4 = createVariable("Tank", "Level", unitRelationship);

        HashMap<LexIdentifier, Variable> target1 = new HashMap<>();
        target1.put(new LexIdentifier(variable1.scalarVariable.instance.getText(), null), variable1);
        HashMap<LexIdentifier, Variable> target2 = new HashMap<>();
        target2.put(new LexIdentifier(variable2.scalarVariable.instance.getText(), null), variable2);
        HashMap<LexIdentifier, Variable> target3 = new HashMap<>();
        target3.put(new LexIdentifier(variable3.scalarVariable.instance.getText(), null), variable3);
        HashMap<LexIdentifier, Variable> target4 = new HashMap<>();
        target4.put(new LexIdentifier(variable4.scalarVariable.instance.getText(), null), variable4);
        relations.add(new UnitRelationship.Relation.RelationBuilder(variable1, target2).build());
        relations.add(new UnitRelationship.Relation.RelationBuilder(variable3, target2)
                .setInternalOrExternal(UnitRelationship.Relation.InternalOrExternal.Internal).build());
        relations.add(new UnitRelationship.Relation.RelationBuilder(variable3, target1).build());
        relations.add(new UnitRelationship.Relation.RelationBuilder(variable3, target4).build());

        String filePath = Path.of(path, "LoopGraph").toString();
        graphDrawer.plotGraph(relations, "LoopGraph", filePath);
    }


    private Variable createVariable(String fmuName, String variableName, UnitRelationship unitRelationship) {
        var scalarVar = new ModelDescription.ScalarVariable();
        scalarVar.name = variableName;
        return unitRelationship.new Variable(new RelationVariable(scalarVar, new LexIdentifier(fmuName, null)));
    }

}

