package io.harness.idp.config.service;

import com.google.inject.Inject;
import io.harness.idp.config.repositories.AppConfigRepository;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject}))
public class AppConfigServiceImpl implements AppConfigService {
    private AppConfigRepository appConfigRepository;
}
