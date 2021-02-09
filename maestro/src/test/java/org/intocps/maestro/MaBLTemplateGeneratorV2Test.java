package org.intocps.maestro;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.intocps.maestro.ast.display.PrettyPrinter;
import org.intocps.maestro.ast.node.ASimulationSpecificationCompilationUnit;
import org.intocps.maestro.cli.MaestroV1SimulationConfiguration;
import org.intocps.maestro.core.Framework;
import org.intocps.maestro.core.api.FixedStepSizeAlgorithm;
import org.intocps.maestro.framework.fmi2.Fmi2SimulationEnvironmentConfiguration;
import org.intocps.maestro.template.MaBLTemplateConfiguration;
import org.intocps.maestro.template.MaBLTemplateGeneratorV2;
import org.junit.Test;

import java.io.File;
import java.nio.file.Paths;

public class MaBLTemplateGeneratorV2Test {

    @Test
    public void GenerateSingleWaterTankTemplate() throws Exception {
        final double endTime = 10.0;
        final double stepSize = 0.1;
        File configurationDirectory = Paths.get("src", "test", "resources").toFile();

        MaestroV1SimulationConfiguration conf = new ObjectMapper()
                .readValue(new File(configurationDirectory, "env_mabltemplategeneratorv2test.json"), MaestroV1SimulationConfiguration.class);

        Fmi2SimulationEnvironmentConfiguration simulationEnvironmentConfiguration = new ObjectMapper()
                .readValue(new File(configurationDirectory, "env_mabltemplategeneratorv2test.json"), Fmi2SimulationEnvironmentConfiguration.class);

        MaBLTemplateConfiguration.MaBLTemplateConfigurationBuilder b = new MaBLTemplateConfiguration.MaBLTemplateConfigurationBuilder();
        FixedStepSizeAlgorithm stepSizeAlgorithm = new FixedStepSizeAlgorithm(endTime, stepSize);

        MaBLTemplateConfiguration mtc = b.useInitializer(true, "{}").setStepAlgorithm(stepSizeAlgorithm).setFramework(Framework.FMI2)
                .setFrameworkConfig(Framework.FMI2, simulationEnvironmentConfiguration).setLoggingOn(true).setLogLevels(conf.logLevels).build();


        ASimulationSpecificationCompilationUnit aSimulationSpecificationCompilationUnit = MaBLTemplateGeneratorV2.generateTemplate(mtc);
        System.out.println(PrettyPrinter.print(aSimulationSpecificationCompilationUnit));
    }
}