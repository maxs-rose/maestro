package org.intocps.maestro.webapi;

import org.intocps.maestro.webapi.services.CoeService;
import org.intocps.maestro.webapi.services.SimulatorManagementService;
import org.intocps.orchestration.coe.scala.Coe;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Profile("mocked_coe")
@Configuration
public class MockedCoeTestConfiguration {

    @Bean
    @Primary
    public CoeService coeService() {
        return new CoeService(Mockito.mock(Coe.class));
    }

    @Bean
    @Primary
    public SimulatorManagementService simulationManagementService() {
        return Mockito.mock(SimulatorManagementService.class);
    }
}