/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.app.health;

import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.delegate.service.DelegateAgentService;
import io.harness.health.HealthException;
import io.harness.health.HealthMonitor;

import javax.inject.Inject;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DelegateHealthMonitor implements HealthMonitor {
  private final DelegateAgentService delegateAgentService;
  private final DelegateConfiguration delegateConfiguration;

  @Inject
  public DelegateHealthMonitor(DelegateAgentService delegateAgentService, DelegateConfiguration delegateConfiguration) {
    this.delegateAgentService = delegateAgentService;
    this.delegateConfiguration = delegateConfiguration;
  }

  @Override
  public Duration healthExpectedResponseTimeout() {
    return Duration.ofSeconds(1);
  }

  @Override
  public Duration healthValidFor() {
    return Duration.ofMinutes(5);
  }

  @Override
  public void isHealthy() {
    if (delegateConfiguration.isPollForTasks() && !delegateAgentService.isHeartbeatHealthy()) {
      throw new HealthException("Polling mode delegate is not healthy. Not able to send heartbeat.");
    } else if (!delegateConfiguration.isPollForTasks() && !delegateAgentService.isSocketHealthy()) {
      throw new HealthException("Delegate is not healthy. Socket is not open.");
    }
  }
}
