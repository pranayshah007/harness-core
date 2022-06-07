/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl.sidekickexecutors;

import io.harness.cvng.beans.activity.ActivityVerificationStatus;
import io.harness.cvng.core.beans.sidekick.VerificationJobInstanceCleanupSideKickData;
import io.harness.cvng.core.services.api.MonitoringSourcePerpetualTaskService;
import io.harness.cvng.core.services.api.SideKickExecutor;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.persistence.HPersistence;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;

@Singleton
@Slf4j
public class VerificationJobInstanceCleanupSideKickExecutor
    implements SideKickExecutor<VerificationJobInstanceCleanupSideKickData> {
  private static final List<Duration> RETRY_WAIT_DURATIONS = Lists.newArrayList(Duration.ofMinutes(30),
      Duration.ofMinutes(45), Duration.ofMinutes(60), Duration.ofMinutes(75), Duration.ofMinutes(90));
  @Inject private Clock clock;
  @Inject private HPersistence hPersistence;
  @Inject private MonitoringSourcePerpetualTaskService monitoringSourcePerpetualTaskService;
  @Inject private VerificationJobInstanceService verificationJobInstanceService;

  @Override
  public void execute(VerificationJobInstanceCleanupSideKickData sideKickInfo) {
    log.info("SidekickInfo {}", sideKickInfo);
    String verificationJobInstanceId = sideKickInfo.getVerificationJobInstanceIdentifier();
    log.info("Triggering cleanup for VerificationJobInstance {}", verificationJobInstanceId);
    List<String> sourceIdentifiers = sideKickInfo.getSourceIdentifiers();
    if (CollectionUtils.isNotEmpty(sourceIdentifiers)) {
      sourceIdentifiers.forEach(sourceIdentifier
          -> monitoringSourcePerpetualTaskService.deleteTask(sideKickInfo.getAccountIdentifier(),
              sideKickInfo.getOrgIdentifier(), sideKickInfo.getProjectIdentifier(), sourceIdentifier));
    }
    log.info("Cleanup complete for VerificationJobInstance {}", verificationJobInstanceId);
  }

  @Override
  public RetryData shouldRetry(int lastRetryCount) {
    return RetryData.builder()
        .shouldRetry(true)
        .nextRetryTime(getNextValidAfter(lastRetryCount, clock.instant()))
        .build();
  }

  @Override
  public boolean canExecute(VerificationJobInstanceCleanupSideKickData sideKickInfo) {
    String verificationJobInstanceId = sideKickInfo.getVerificationJobInstanceIdentifier();
    VerificationJobInstance verificationJobInstance =
        verificationJobInstanceService.getVerificationJobInstance(verificationJobInstanceId);
    return isVerificationJobProcessingComplete(verificationJobInstance);
  }

  @Override
  public Duration delayExecutionBy() {
    return Duration.ofMinutes(30);
  }

  private boolean isVerificationJobProcessingComplete(VerificationJobInstance verificationJobInstance) {
    return ActivityVerificationStatus.getFinalStates().contains(verificationJobInstance.getVerificationStatus());
  }

  public Instant getNextValidAfter(int retryCount, Instant currentTime) {
    return currentTime.plus(RETRY_WAIT_DURATIONS.get(Math.min(retryCount, RETRY_WAIT_DURATIONS.size() - 1)));
  }
}
