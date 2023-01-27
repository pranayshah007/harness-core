package io.harness.service.appconfig;

import com.google.inject.Inject;
import io.harness.repositories.appconfig.AppConfigRepository;
import lombok.AllArgsConstructor;

@AllArgsConstructor(onConstructor = @__({ @Inject}))
public class AppConfigServiceImpl implements AppConfigService {
    private AppConfigRepository appConfigRepository;
}
