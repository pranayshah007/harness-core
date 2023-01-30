package io.harness.idp.appconfig.service;

import com.google.inject.Inject;
import io.harness.repositories.appconfig.AppConfigRepository;
import lombok.AllArgsConstructor;

@AllArgsConstructor(onConstructor = @__({ @Inject}))
public class AppConfigServiceImpl implements AppConfigService {
    private AppConfigRepository appConfigRepository;
}
