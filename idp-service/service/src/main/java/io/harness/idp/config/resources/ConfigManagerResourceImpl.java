package io.harness.idp.config.resources;

import com.google.inject.Inject;
import io.harness.idp.config.service.AppConfigService;
import io.harness.idp.config.resource.ConfigManagerResource;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject}))
public class ConfigManagerResourceImpl implements ConfigManagerResource {
    private AppConfigService appConfigService;
}
