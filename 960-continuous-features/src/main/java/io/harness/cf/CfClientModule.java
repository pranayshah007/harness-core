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
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CfClientModule extends AbstractModule {
  public static final String BATCH_PROCESSING_ON_PREM = "BATCH_PROCESSING_ON_PREM";
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
    String apiKey = cfClientConfig.getApiKey();
    log.info("Using CF API key {}", apiKey);
    if (isEmpty(apiKey)) {
      apiKey = "fake";
    }
    // Passing ApiKey as BATCH_PROCESSING_ON_PREM for Batch-Processing service on SMP env
    if (apiKey.equals(BATCH_PROCESSING_ON_PREM)) {
      return CfClient.getInstance();
    }

    log.info("Creating Feature Flag Client");
    CfClient client = CfClient.getInstance();
    client.initialize(apiKey,
        Config.builder()
            .configUrl(cfClientConfig.getConfigUrl())
            .eventUrl(cfClientConfig.getEventUrl())
            .streamEnabled(cfClientConfig.isStreamEnabled())
            .analyticsEnabled(cfClientConfig.isAnalyticsEnabled())
            .build());
    try {
      client.waitForInitialization();
    } catch (Exception e) {
      log.info(String.format("Unable to initialize SDK: %s%n", e.getMessage()));
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
