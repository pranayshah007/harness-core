package io.harness.idp.configmanager.resources;

import com.google.inject.Inject;
import io.harness.idp.configmanager.service.ConfigManagerService;
import io.harness.idp.secretmanager.resource.EnvironmentVariableResource;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject}))
public class ConfigManagerResourceImpl implements EnvironmentVariableResource {
    private ConfigManagerService configManagerService;
}
