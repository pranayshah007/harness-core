/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl.sidekickexecutors;

import static io.harness.rule.OwnerRule.DHRUVX;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.activity.ActivityVerificationStatus;
import io.harness.cvng.core.beans.sidekick.VerificationJobInstanceCleanupSideKickData;
import io.harness.cvng.core.services.api.MonitoringSourcePerpetualTaskService;
import io.harness.cvng.core.services.api.SideKickExecutor;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.Collections;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class VerificationJobInstanceCleanupSideKickExecutorTest extends CvNextGenTestBase {
  @Inject private HPersistence hPersistence;
  @Inject private MonitoringSourcePerpetualTaskService monitoringSourcePerpetualTaskService;
  @Inject private VerificationJobInstanceCleanupSideKickExecutor sideKickExecutor;

  private VerificationJobInstanceCleanupSideKickData sideKickData;
  private BuilderFactory builderFactory;

  @Before
  public void setup() {
    this.builderFactory = BuilderFactory.getDefault();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testCanExecute_verificationJobInstanceIsStillProcessing() {
    VerificationJobInstance verificationJobInstance = builderFactory.verificationJobInstanceBuilder().build();
    verificationJobInstance.setVerificationStatus(ActivityVerificationStatus.IN_PROGRESS);
    hPersistence.save(verificationJobInstance);
    sideKickData = builderFactory
                       .getVerificationJobInstanceCleanupSideKickDataBuilder(
                           verificationJobInstance.getUuid(), Collections.emptyList())
                       .build();
    boolean canExecute = sideKickExecutor.canExecute(sideKickData);
    assertThat(canExecute).isFalse();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testCanExecute_verificationJobInstanceIsProcessed() {
    VerificationJobInstance verificationJobInstance = builderFactory.verificationJobInstanceBuilder().build();
    verificationJobInstance.setVerificationStatus(ActivityVerificationStatus.VERIFICATION_PASSED);
    hPersistence.save(verificationJobInstance);
    sideKickData = builderFactory
                       .getVerificationJobInstanceCleanupSideKickDataBuilder(
                           verificationJobInstance.getUuid(), Collections.emptyList())
                       .build();
    boolean canExecute = sideKickExecutor.canExecute(sideKickData);
    assertThat(canExecute).isTrue();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testExecute_noSourcesPresent() throws IllegalAccessException {
    MonitoringSourcePerpetualTaskService spiedMonitoringSourcePerpetualTaskService =
        spy(monitoringSourcePerpetualTaskService);
    FieldUtils.writeField(
        sideKickExecutor, "monitoringSourcePerpetualTaskService", spiedMonitoringSourcePerpetualTaskService, true);
    VerificationJobInstance verificationJobInstance = builderFactory.verificationJobInstanceBuilder().build();
    verificationJobInstance.setVerificationStatus(ActivityVerificationStatus.VERIFICATION_PASSED);
    hPersistence.save(verificationJobInstance);
    sideKickData = builderFactory
                       .getVerificationJobInstanceCleanupSideKickDataBuilder(
                           verificationJobInstance.getUuid(), Collections.emptyList())
                       .build();
    sideKickExecutor.execute(sideKickData);
    verify(spiedMonitoringSourcePerpetualTaskService, times(0)).deleteTask(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testExecute() throws IllegalAccessException {
    MonitoringSourcePerpetualTaskService spiedMonitoringSourcePerpetualTaskService =
        spy(monitoringSourcePerpetualTaskService);
    FieldUtils.writeField(
        sideKickExecutor, "monitoringSourcePerpetualTaskService", spiedMonitoringSourcePerpetualTaskService, true);
    VerificationJobInstance verificationJobInstance = builderFactory.verificationJobInstanceBuilder().build();
    verificationJobInstance.setVerificationStatus(ActivityVerificationStatus.VERIFICATION_PASSED);
    hPersistence.save(verificationJobInstance);
    sideKickData = builderFactory
                       .getVerificationJobInstanceCleanupSideKickDataBuilder(
                           verificationJobInstance.getUuid(), Collections.singletonList("dummySource"))
                       .build();
    sideKickExecutor.execute(sideKickData);
    verify(spiedMonitoringSourcePerpetualTaskService, times(1)).deleteTask(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testShouldRetry() {
    SideKickExecutor.RetryData retryData = sideKickExecutor.shouldRetry(5);
    assertThat(retryData.isShouldRetry()).isTrue();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testDelayExecutionBy() {
    Duration delay = sideKickExecutor.delayExecutionBy();
    assertThat(delay).isEqualTo(Duration.ofMinutes(30));
  }
}
