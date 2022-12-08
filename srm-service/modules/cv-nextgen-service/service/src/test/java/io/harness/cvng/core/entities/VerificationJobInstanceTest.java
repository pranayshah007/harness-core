/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import static io.harness.cvng.core.services.CVNextGenConstants.DATA_COLLECTION_DELAY;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.models.VerificationType;
import io.harness.cvng.verificationjob.entities.CanaryVerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.rule.Owner;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class VerificationJobInstanceTest extends CategoryTest {
  private CanaryVerificationJob resolvedJob;
  private CanaryVerificationJob verificationJob;
  private String accountId;
  private String monitoringSourceIdentifier;
  private String projectIdentifier;
  private String orgIdentifier;
  private BuilderFactory builderFactory;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    accountId = builderFactory.getContext().getAccountId();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    verificationJob = new CanaryVerificationJob();
    verificationJob.setDuration(Duration.ofMinutes(10));
    this.monitoringSourceIdentifier = generateUuid();
    resolvedJob = new CanaryVerificationJob(); // TODO: use superbuilder
    resolvedJob.setDuration("5m", false);
  }
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testBuilder_deploymentStartTimeRoundDown() {
    Instant deploymentStartTime = Instant.parse("2020-04-22T10:02:06Z");
    VerificationJobInstance verificationJobInstance = builderFactory.verificationJobInstanceBuilder()
                                                          .deploymentStartTime(deploymentStartTime)
                                                          .startTime(deploymentStartTime.plus(Duration.ofMinutes(2)))
                                                          .resolvedJob(resolvedJob)
                                                          .build();
    assertThat(verificationJobInstance.getDeploymentStartTime()).isEqualTo(Instant.parse("2020-04-22T10:02:00Z"));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testBuilder_startTimeAndDeploymentStartTimeAreInSameMinute() {
    Instant deploymentStartTime = Instant.parse("2020-04-22T10:02:06Z");
    Instant startTime = Instant.parse("2020-04-22T10:02:58Z");
    VerificationJobInstance verificationJobInstance = builderFactory.verificationJobInstanceBuilder()
                                                          .deploymentStartTime(deploymentStartTime)
                                                          .startTime(startTime)
                                                          .resolvedJob(resolvedJob)
                                                          .build();
    assertThat(verificationJobInstance.getDeploymentStartTime()).isEqualTo(Instant.parse("2020-04-22T10:02:00Z"));
    assertThat(verificationJobInstance.getStartTime()).isEqualTo(Instant.parse("2020-04-22T10:03:00Z"));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testBuilder_startTimeAndDeploymentStartTimeAreDifferentMinute() {
    Instant deploymentStartTime = Instant.parse("2020-04-22T10:02:06Z");
    Instant startTime = Instant.parse("2020-04-22T10:03:58Z");
    VerificationJobInstance verificationJobInstance = builderFactory.verificationJobInstanceBuilder()
                                                          .deploymentStartTime(deploymentStartTime)
                                                          .startTime(startTime)
                                                          .resolvedJob(resolvedJob)
                                                          .build();
    assertThat(verificationJobInstance.getDeploymentStartTime()).isEqualTo(Instant.parse("2020-04-22T10:02:00Z"));
    assertThat(verificationJobInstance.getStartTime()).isEqualTo(Instant.parse("2020-04-22T10:03:00Z"));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testBuilder_startTimeIsBeforeDeploymentStartTime() {
    Instant deploymentStartTime = Instant.parse("2020-04-22T10:04:06Z");
    Instant startTime = Instant.parse("2020-04-22T10:03:58Z");
    assertThatThrownBy(()
                           -> builderFactory.verificationJobInstanceBuilder()
                                  .deploymentStartTime(deploymentStartTime)
                                  .startTime(startTime)
                                  .resolvedJob(resolvedJob)
                                  .build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Deployment start time should be before verification start time.");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testBuilder_setDefaultDataCollectionDelay() {
    Instant deploymentStartTime = Instant.parse("2020-04-22T10:04:06Z");
    Instant startTime = Instant.parse("2020-04-22T10:05:58Z");
    assertThat(builderFactory.verificationJobInstanceBuilder()
                   .deploymentStartTime(deploymentStartTime)
                   .startTime(startTime)
                   .build()
                   .getDataCollectionDelay())
        .isEqualTo(DATA_COLLECTION_DELAY);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetProgressPercentage_emptyProgressLogs() {
    Instant deploymentStartTime = Instant.parse("2020-04-22T10:04:06Z");
    Instant startTime = Instant.parse("2020-04-22T10:05:58Z");
    VerificationJobInstance verificationJobInstance = builderFactory.verificationJobInstanceBuilder()
                                                          .deploymentStartTime(deploymentStartTime)
                                                          .startTime(startTime)
                                                          .build();
    assertThat(verificationJobInstance.getProgressPercentage()).isEqualTo(0);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetRemainingTime_emptyProgressLogs() {
    Instant deploymentStartTime = Instant.parse("2020-04-22T10:04:06Z");
    Instant startTime = Instant.parse("2020-04-22T10:05:58Z");
    VerificationJobInstance verificationJobInstance = builderFactory.verificationJobInstanceBuilder()
                                                          .deploymentStartTime(deploymentStartTime)
                                                          .startTime(startTime)
                                                          .resolvedJob(verificationJob)
                                                          .build();
    assertThat(verificationJobInstance.getRemainingTime(Instant.now())).isEqualTo(Duration.ofMinutes(15));
  }
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetProgressPercentage_withProgressLog() {
    Instant deploymentStartTime = Instant.parse("2020-04-22T10:04:06Z");
    Instant startTime = Instant.parse("2020-04-22T10:05:00Z");
    VerificationJobInstance.ProgressLog progressLog = VerificationJobInstance.AnalysisProgressLog.builder()
                                                          .startTime(startTime)
                                                          .endTime(startTime.plus(Duration.ofMinutes(1)))
                                                          .verificationTaskId(generateUuid())
                                                          .log("log")
                                                          .isFinalState(true)
                                                          .build();
    CVConfig cvConfig = newCVConfig();
    VerificationJobInstance verificationJobInstance =
        builderFactory.verificationJobInstanceBuilder()
            .deploymentStartTime(deploymentStartTime)
            .startTime(startTime)
            .progressLogs(Arrays.asList(progressLog))
            .cvConfigMap(Collections.singletonMap(cvConfig.getUuid(), cvConfig))
            .resolvedJob(verificationJob)
            .build();
    assertThat(verificationJobInstance.getProgressPercentage()).isEqualTo(10);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetProgressPercentage_withMultipleVerificationTasks() {
    Instant deploymentStartTime = Instant.parse("2020-04-22T10:04:06Z");
    Instant startTime = Instant.parse("2020-04-22T10:05:00Z");
    VerificationJobInstance.ProgressLog progressLog = VerificationJobInstance.AnalysisProgressLog.builder()
                                                          .startTime(startTime)
                                                          .endTime(startTime.plus(Duration.ofMinutes(1)))
                                                          .verificationTaskId(generateUuid())
                                                          .log("log")
                                                          .isFinalState(true)
                                                          .build();
    Map<String, CVConfig> cvConfigMap = new HashMap<>();
    CVConfig cvConfig1 = newCVConfig();
    CVConfig cvConfig2 = newCVConfig();
    cvConfigMap.put(cvConfig1.getUuid(), cvConfig1);
    cvConfigMap.put(cvConfig2.getUuid(), cvConfig2);
    VerificationJobInstance verificationJobInstance = builderFactory.verificationJobInstanceBuilder()
                                                          .deploymentStartTime(deploymentStartTime)
                                                          .startTime(startTime)
                                                          .progressLogs(Arrays.asList(progressLog))
                                                          .cvConfigMap(cvConfigMap)
                                                          .resolvedJob(verificationJob)
                                                          .build();
    assertThat(verificationJobInstance.getProgressPercentage()).isEqualTo(5);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetRemainingTime_withProgressLog() {
    Instant deploymentStartTime = Instant.parse("2020-04-22T10:04:06Z");
    Instant startTime = Instant.parse("2020-04-22T10:05:00Z");
    VerificationJobInstance.ProgressLog progressLog = VerificationJobInstance.AnalysisProgressLog.builder()
                                                          .startTime(startTime)
                                                          .endTime(startTime.plus(Duration.ofMinutes(1)))
                                                          .verificationTaskId(generateUuid())
                                                          .log("log")
                                                          .isFinalState(true)
                                                          .build();
    CVConfig cvConfig = newCVConfig();
    VerificationJobInstance verificationJobInstance =
        builderFactory.verificationJobInstanceBuilder()
            .deploymentStartTime(deploymentStartTime)
            .startTime(startTime)
            .progressLogs(Arrays.asList(progressLog))
            .cvConfigMap(Collections.singletonMap(cvConfig.getUuid(), cvConfig))
            .resolvedJob(verificationJob)
            .build();
    assertThat(verificationJobInstance.getRemainingTime(Instant.parse("2020-04-22T10:09:00Z")))
        .isEqualTo(Duration.ofMinutes(18));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testRemainingTime_withMultipleVerificationTasks() {
    Instant deploymentStartTime = Instant.parse("2020-04-22T10:04:06Z");
    Instant startTime = Instant.parse("2020-04-22T10:05:00Z");
    VerificationJobInstance.ProgressLog progressLog = VerificationJobInstance.AnalysisProgressLog.builder()
                                                          .startTime(startTime)
                                                          .endTime(startTime.plus(Duration.ofMinutes(1)))
                                                          .verificationTaskId(generateUuid())
                                                          .log("log")
                                                          .isFinalState(true)
                                                          .build();
    Map<String, CVConfig> cvConfigMap = new HashMap<>();
    CVConfig cvConfig1 = newCVConfig();
    CVConfig cvConfig2 = newCVConfig();
    cvConfigMap.put(cvConfig1.getUuid(), cvConfig1);
    cvConfigMap.put(cvConfig2.getUuid(), cvConfig2);
    VerificationJobInstance verificationJobInstance = builderFactory.verificationJobInstanceBuilder()
                                                          .deploymentStartTime(deploymentStartTime)
                                                          .startTime(startTime)
                                                          .progressLogs(Arrays.asList(progressLog))
                                                          .cvConfigMap(cvConfigMap)
                                                          .resolvedJob(verificationJob)
                                                          .build();
    assertThat(verificationJobInstance.getRemainingTime(Instant.parse("2020-04-22T10:08:00Z")))
        .isEqualTo(Duration.ofMinutes(19));
  }

  private CVConfig newCVConfig() {
    SplunkCVConfig cvConfig = new SplunkCVConfig();
    cvConfig.setUuid(generateUuid());
    cvConfig.setQuery("exception");
    cvConfig.setServiceInstanceIdentifier("serviceInstanceIdentifier");
    cvConfig.setVerificationType(VerificationType.LOG);
    cvConfig.setAccountId(accountId);
    cvConfig.setConnectorIdentifier(generateUuid());
    cvConfig.setMonitoredServiceIdentifier("service_env");
    cvConfig.setProjectIdentifier(projectIdentifier);
    cvConfig.setOrgIdentifier(orgIdentifier);
    cvConfig.setIdentifier(monitoringSourceIdentifier);
    cvConfig.setMonitoringSourceName(generateUuid());
    cvConfig.setCategory(CVMonitoringCategory.PERFORMANCE);
    cvConfig.setProductName("productName");
    return cvConfig;
  }
}
