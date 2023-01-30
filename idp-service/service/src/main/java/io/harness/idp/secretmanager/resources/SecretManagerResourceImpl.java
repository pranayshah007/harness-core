package io.harness.idp.secretmanager.resources;

import com.google.inject.Inject;
import io.harness.idp.secretmanager.resource.EnvironmentVariableResource;
import io.harness.idp.environmentvariable.service.EnvironmentVariableService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject}))
public class SecretManagerResourceImpl implements EnvironmentVariableResource {
    private EnvironmentVariableService environmentVariableService;
}
