/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.app.modules.platform;

import io.harness.delegate.app.modules.common.DelegateHealthModule;
import io.harness.delegate.app.modules.common.DelegateManagerClientModule;
import io.harness.delegate.app.modules.common.DelegateManagerGrpcClientModule;
import io.harness.delegate.app.modules.common.DelegateTokensModule;
import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.logstreaming.LogStreamingModule;
import io.harness.metrics.MetricRegistryModule;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.AbstractModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@RequiredArgsConstructor
@Slf4j
public class DelegatePlatformModule extends AbstractModule {
  private final DelegateConfiguration configuration;

  @Override
  protected void configure() {
    super.configure();

    if (StringUtils.isNotEmpty(configuration.getClientCertificateFilePath())
        && StringUtils.isNotEmpty(configuration.getClientCertificateKeyFilePath())) {
      log.info("Delegate is running with mTLS enabled.");
    }

    install(new DelegateTokensModule(configuration));
    install(new DelegateHealthModule());

    install(new DelegatePlatformKryoModule());
    install(new MetricRegistryModule(new MetricRegistry()));

    install(new DelegateManagerClientModule());

    install(new LogStreamingModule(configuration.getLogStreamingServiceBaseUrl(),
        configuration.getClientCertificateFilePath(), configuration.getClientCertificateKeyFilePath(),
        configuration.isTrustAllCertificates()));
    install(new DelegateManagerGrpcClientModule(configuration));

    install(
        new DelegateExecutorsModule(configuration.isDynamicHandlingOfRequestEnabled())); // Check if some can be removed
    install(new DelegateCommonModule(configuration));
  }
}
