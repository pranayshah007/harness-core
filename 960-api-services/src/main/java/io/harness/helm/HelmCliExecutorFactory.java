/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.helm;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.threading.ThreadPool;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_K8S})
public class HelmCliExecutorFactory {
  private static final String HELM_CLI_CORE_POOL_SIZE_ENV = "HELM_CLI_CORE_POOL_SIZE";
  private static final String HELM_CLI_MAX_POOL_SIZE_ENV = "HELM_CLI_MAX_POOL_SIZE";

  private static final String HELM_CLI_CORE_POOL_SIZE_ENV_VAL = System.getenv(HELM_CLI_CORE_POOL_SIZE_ENV);
  private static final String HELM_CLI_MAX_POOL_SIZE_ENV_VAL = System.getenv(HELM_CLI_MAX_POOL_SIZE_ENV);

  private static final int DEFAULT_CORE_POOL_SIZE = 1;
  private static final int DEFAULT_MAX_CORE_POOL_SIZE = 20;

  public static ExecutorService create() {
    return ThreadPool.create(getCorePoolSize(), getMaxPoolSize(), 5, TimeUnit.SECONDS,
        new ThreadFactoryBuilder().setNameFormat("helm-cli-%d").setPriority(Thread.MIN_PRIORITY).build());
  }

  private static int getCorePoolSize() {
    if (isNotEmpty(HELM_CLI_CORE_POOL_SIZE_ENV_VAL)) {
      return parseNumberValue(HELM_CLI_CORE_POOL_SIZE_ENV_VAL, DEFAULT_CORE_POOL_SIZE);
    }

    return DEFAULT_CORE_POOL_SIZE;
  }

  private static int getMaxPoolSize() {
    if (isNotEmpty(HELM_CLI_MAX_POOL_SIZE_ENV_VAL)) {
      return parseNumberValue(HELM_CLI_MAX_POOL_SIZE_ENV_VAL, DEFAULT_MAX_CORE_POOL_SIZE);
    }

    return DEFAULT_MAX_CORE_POOL_SIZE;
  }

  private static int parseNumberValue(String number, int defaultValue) {
    try {
      return Integer.parseInt(number);
    } catch (NumberFormatException e) {
      log.warn("Invalid value provided, defaulting to value {}", defaultValue, e);
      return defaultValue;
    }
  }
}
