/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cf;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.cf.client.api.CfClient;
import io.harness.cf.client.api.Config;
import io.harness.cf.openapi.ApiClient;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CfClientModule extends AbstractModule {
  private static volatile CfClientModule instance;

  public static CfClientModule getInstance() {
    if (instance == null) {
      instance = new CfClientModule();
    }
    return instance;
  }

  private CfClientModule() {}

  @Provides
  @Singleton
  CfClient provideCfClient(CfClientConfig cfClientConfig) {
    log.info("Using CF API key {}", cfClientConfig.getApiKey());
    String apiKey = cfClientConfig.getApiKey();
    if (isEmpty(apiKey)) {
      apiKey = "fake";
    }

    final Config config = Config.builder()
                              .analyticsEnabled(cfClientConfig.isAnalyticsEnabled())
                              .configUrl(cfClientConfig.getConfigUrl())
                              .eventUrl(cfClientConfig.getEventUrl())
                              .readTimeout(cfClientConfig.getReadTimeout())
                              .connectionTimeout(cfClientConfig.getConnectionTimeout())
                              .build();

    final CfClient client = new CfClient(apiKey, config);

    final IntervalFunction function = IntervalFunction.ofExponentialBackoff(

        cfClientConfig.getSleepInterval(), 2);

    final RetryConfig retryConfig = RetryConfig.custom()
                                        .maxAttempts(cfClientConfig.getRetries())
                                        .intervalFunction(function)
                                        .retryOnResult(

                                            r -> !((Boolean) r))
                                        .build();

    final RetryRegistry registry = RetryRegistry.of(retryConfig);
    final Retry retry = registry.retry("cfClientInit", retryConfig);

    final Supplier<Boolean> retrySupplier = Retry.decorateSupplier(

        retry, client::isInitialized);

    if (retrySupplier.get()) {
      log.info("CF client has been initialized");
    } else {
      log.error("CF client has not been initialized");
    }

    return client;
  }

  @Provides
  @Singleton
  @Named("cfMigrationAPI")
  CFApi providesCfAPI(CfMigrationConfig migrationConfig) {
    ApiClient apiClient = new ApiClient();
    apiClient.setReadTimeout(migrationConfig.getReadTimeout());
    apiClient.setConnectTimeout(migrationConfig.getConnectionTimeout());
    apiClient.setBasePath(migrationConfig.getAdminUrl());
    return new CFApi(apiClient);
  }
}