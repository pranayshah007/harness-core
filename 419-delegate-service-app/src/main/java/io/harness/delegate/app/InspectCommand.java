package io.harness.delegate.app;

import io.dropwizard.Application;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;

public class InspectCommand<T extends io.dropwizard.Configuration> extends ConfiguredCommand<T> {

    public static final String PRIMARY_DATASTORE = "primaryDatastore";
    private final Class<T> configurationClass;

    public InspectCommand(Application<T> application) {
        super("inspect", "Parses and validates the configuration file");
        this.configurationClass = application.getConfigurationClass();
    }

    @Override
    protected Class<T> getConfigurationClass() {
        return this.configurationClass;
    }

    @Override
    protected void run(Bootstrap<T> bootstrap, Namespace namespace, T t) throws Exception {
        // TBA during mongo configuration
    }
}
