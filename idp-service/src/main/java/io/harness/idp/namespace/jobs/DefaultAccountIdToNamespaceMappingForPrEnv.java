/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.namespace.jobs;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.namespace.service.NamespaceService;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.dropwizard.lifecycle.Managed;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.IDP)
public class DefaultAccountIdToNamespaceMappingForPrEnv implements Managed {
  @Inject @Named("env") private String env;
  @Inject @Named("prEnvDefaultBackstageNamespace") private String prEnvDefaultBackstageNamespace;
  private ExecutorService executorService;
  private final NamespaceService namespaceService;

  private static final String DEFAULT_PR_ENV_ACCOUNT_ID = "kmpySmUISimoRrJL6NL73w";

  private static final String PR_ENV_TYPE = "pr";

  @Inject
  public DefaultAccountIdToNamespaceMappingForPrEnv(
      @Named("DefaultPREnvAccountIdToNamespaceMappingCreator") ExecutorService executorService,
      NamespaceService namespaceService) {
    this.executorService = executorService;
    this.namespaceService = namespaceService;
  }

  @Override
  public void start() throws Exception {
    executorService = Executors.newSingleThreadExecutor(
        new ThreadFactoryBuilder().setNameFormat("default-entry-creator-for-pr-env").build());
    executorService.submit(this::run);
  }

  @Override
  public void stop() throws Exception {
    executorService.shutdownNow();
    executorService.awaitTermination(30, TimeUnit.SECONDS);
  }

  public void run() {
    log.info("Creating default account id to namespace mapping for PR env.....");
    try {
      if (env.equals(PR_ENV_TYPE) && !prEnvDefaultBackstageNamespace.isEmpty()) {
        namespaceService.createPREnvDefaultMappingEntry(DEFAULT_PR_ENV_ACCOUNT_ID, prEnvDefaultBackstageNamespace);
        log.info("Default account id ( {} ) to namespace ( {} ) mapping created in PR env", DEFAULT_PR_ENV_ACCOUNT_ID,
            prEnvDefaultBackstageNamespace);
      }
    } catch (Exception e) {
      log.error("Default entry creation for account id to namespace mapping is unsuccessful in PR env", e);
    }
  }
}
