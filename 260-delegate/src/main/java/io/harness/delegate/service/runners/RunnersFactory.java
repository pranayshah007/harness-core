/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.runners;

import static io.harness.delegate.beans.RunnerType.RUNNER_TYPE_K8S;

import io.harness.delegate.service.handlermapping.exceptions.RunnerNotFoundException;
import io.harness.delegate.service.runners.itfc.Runner;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public final class RunnersFactory {
  private final Map<String, Runner> runnerRegistry = new HashMap<>();

  @Inject
  public RunnersFactory(final Runner k8SLiteRunner) {
    // add default runners
    addRunner(RUNNER_TYPE_K8S, k8SLiteRunner);
  }

  public void addRunner(String runnerType, Runner runner) {
    runnerRegistry.put(runnerType, runner);
  }

  public Runner get(String runnerType) {
    if (!runnerRegistry.containsKey(runnerType)) {
      log.error("Runner type {} not supported by this delegate. All supported runner types are {}", runnerType,
          String.join(", ", runnerRegistry.keySet()));
      throw new RunnerNotFoundException(runnerType);
    }
    return runnerRegistry.get(runnerType);
  }
}
