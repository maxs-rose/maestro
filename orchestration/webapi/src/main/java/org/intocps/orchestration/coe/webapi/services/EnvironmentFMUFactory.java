package org.intocps.orchestration.coe.webapi.services;

import org.intocps.fmi.IFmu;
import org.intocps.orchestration.coe.AbortSimulationException;
import org.intocps.orchestration.coe.IFmuFactory;

import java.io.File;
import java.net.URI;

public class EnvironmentFMUFactory implements IFmuFactory {

    @Override
    public boolean accept(URI uri) {
        return uri.getScheme() != null && (uri.getScheme().equals("external"));
    }

    @Override
    public IFmu instantiate(File sessionRoot, URI uri) throws Exception {
        if (accept(uri)) {
            if (EnvironmentFMU.getInstance() != null)
                return EnvironmentFMU.getInstance();
            {
                throw new AbortSimulationException(
                        "Environment FMU has not instantiated");
            }
        } else {
            throw new AbortSimulationException(
                    "unable to handle instantiation of: " + uri);
        }
    }
}
