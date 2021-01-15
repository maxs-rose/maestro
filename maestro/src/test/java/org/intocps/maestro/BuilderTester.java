package org.intocps.maestro;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.intocps.maestro.ast.display.PrettyPrinter;
import org.intocps.maestro.ast.node.ASimulationSpecificationCompilationUnit;
import org.intocps.maestro.ast.node.PStm;
import org.intocps.maestro.core.Framework;
import org.intocps.maestro.core.messages.ErrorReporter;
import org.intocps.maestro.core.messages.IErrorReporter;
import org.intocps.maestro.framework.fmi2.Fmi2SimulationEnvironment;
import org.intocps.maestro.framework.fmi2.Fmi2SimulationEnvironmentConfiguration;
import org.intocps.maestro.framework.fmi2.api.Fmi2Builder;
import org.intocps.maestro.framework.fmi2.api.mabl.AMaBLVariableCreator;
import org.intocps.maestro.framework.fmi2.api.mabl.AMablValue;
import org.intocps.maestro.framework.fmi2.api.mabl.MablApiBuilder;
import org.intocps.maestro.framework.fmi2.api.mabl.scoping.DynamicActiveBuilderScope;
import org.intocps.maestro.framework.fmi2.api.mabl.variables.AMablFmi2ComponentAPI;
import org.intocps.maestro.framework.fmi2.api.mabl.variables.AMablFmu2Api;
import org.intocps.maestro.interpreter.DefaultExternalValueFactory;
import org.intocps.maestro.interpreter.MableInterpreter;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import static org.intocps.maestro.ast.MableAstFactory.newBoleanType;

public class BuilderTester {

    @Test
    public void wt() throws Exception {

        InputStream is = this.getClass().getClassLoader().getResourceAsStream("buildertester/buildertester.json");
        ObjectMapper mapper = new ObjectMapper();
        Fmi2SimulationEnvironmentConfiguration simulationEnvironmentConfiguration =
                mapper.readValue(is, Fmi2SimulationEnvironmentConfiguration.class);
        Fmi2SimulationEnvironment env = Fmi2SimulationEnvironment.of(simulationEnvironmentConfiguration, new IErrorReporter.SilentReporter());

        MablApiBuilder builder = new MablApiBuilder(env);
        AMaBLVariableCreator variableCreator = builder.variableCreator(); // CurrentScopeVariableCreator


        // Create the two FMUs
        AMablFmu2Api controllerFMU = variableCreator.createFMU("controllerFMU", env.getModelDescription("{controllerFMU}"), new URI(""));
        AMablFmu2Api tankFMU = variableCreator.createFMU("tankFMU", env.getModelDescription("{tankFMU}"), new URI(""));

        // Create the controller and tank instanes
        AMablFmi2ComponentAPI controller = controllerFMU.create("controller");
        AMablFmi2ComponentAPI tank = tankFMU.create("tank");
        DynamicActiveBuilderScope dynamicScope = builder.getDynamicScope();
 /*
        IMablScope scope1 = dynamicScope.getActiveScope();
        for (int i = 0; i < 4; i++) {
            dynamicScope.enterIf(null);
            AMablFmi2ComponentAPI tank2 = tankFMU.create("tank");
        }
        scope1.activate();*/
        AMablFmi2ComponentAPI tank2 = tankFMU.create("tank");


        controller.getPort("valve").linkTo(tank.getPort("valvecontrol"));
        tank.getPort("level").linkTo(controller.getPort("level"));

        Map<Fmi2Builder.Port, Fmi2Builder.Variable> allVars = tank.get();
        tank.share(allVars);


        controller.getAndShare("valve");
        controller.getAndShare();
        tank.set();
        Fmi2Builder.DoubleVariable<PStm> var = dynamicScope.store(123.123);

        //no this will not stay this way
        tank.set(tank.getPort("valvecontrol"), new AMablValue(newBoleanType(), true));

        var.set(456.678);
        allVars.put(allVars.keySet().iterator().next(), var);
        //tank.set(allVars);

        ASimulationSpecificationCompilationUnit program = builder.build();

        String test = PrettyPrinter.print(program);

        System.out.println(test);

        File workingDirectory = new File("target/apitest");
        workingDirectory.mkdirs();
        File specFile = new File(workingDirectory, "m.mabl");
        FileUtils.write(specFile, test, StandardCharsets.UTF_8);
        Mabl mabl = new Mabl(workingDirectory, workingDirectory);
        IErrorReporter reporter = new ErrorReporter();
        mabl.setReporter(reporter);
        mabl.setVerbose(true);

        mabl.parse(Collections.singletonList(specFile));

        mabl.typeCheck();
        mabl.verify(Framework.FMI2);
        if (reporter.getErrorCount() > 0) {
            reporter.printErrors(new PrintWriter(System.err, true));
            Assert.fail();
        }
        mabl.dump(workingDirectory);
        new MableInterpreter(
                new DefaultExternalValueFactory(workingDirectory, IOUtils.toInputStream(mabl.getRuntimeDataAsJsonString(), StandardCharsets.UTF_8)))
                .execute(mabl.getMainSimulationUnit());

    }
}