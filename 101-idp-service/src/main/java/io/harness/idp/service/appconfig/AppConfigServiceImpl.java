package io.harness.idp.service.appconfig;

import com.google.inject.Inject;
import io.harness.idp.repositories.appconfig.AppConfigRepository;
import lombok.AllArgsConstructor;

@AllArgsConstructor(onConstructor = @__({ @Inject}))
public class AppConfigServiceImpl implements AppConfigService {
    private AppConfigRepository appConfigRepository;
}
