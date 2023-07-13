/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.analysis.entities.SRMAnalysisStepExecutionDetail;
import io.harness.cvng.beans.change.SRMAnalysisStatus;
import io.harness.cvng.cdng.services.api.SRMAnalysisStepService;
import io.harness.cvng.core.beans.params.ServiceEnvironmentParams;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SRMAnalysisStepServiceImplTest extends CvNextGenTestBase {
  @Inject SRMAnalysisStepService srmAnalysisStepService;

  @Inject Clock clock;

  private BuilderFactory builderFactory;
  private String accountId;
  private String projectIdentifier;
  private String orgIdentifier;

  private String monitoredServiceIdentifier;
  private ServiceEnvironmentParams serviceEnvironmentParams;

  @Before
  public void setUp() throws Exception {
    builderFactory = BuilderFactory.getDefault();
    clock = builderFactory.getClock();
    accountId = builderFactory.getContext().getAccountId();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    monitoredServiceIdentifier = builderFactory.getContext().getMonitoredServiceIdentifier();
    serviceEnvironmentParams = ServiceEnvironmentParams.builderWithProjectParams(builderFactory.getProjectParams())
                                   .serviceIdentifier("service1")
                                   .environmentIdentifier("env1")
                                   .build();
    FieldUtils.writeField(srmAnalysisStepService, "clock", clock, true);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testAbortRunningMonitoredService() throws IllegalAccessException {
    String analysisExecutionDetailsId1 = srmAnalysisStepService.createSRMAnalysisStepExecution(
        getAmbiance(), monitoredServiceIdentifier, serviceEnvironmentParams, Duration.ofDays(1));
    clock = Clock.fixed(clock.instant().plus(3, ChronoUnit.DAYS), ZoneOffset.UTC);
    FieldUtils.writeField(srmAnalysisStepService, "clock", clock, true);
    srmAnalysisStepService.abortRunningStepsForMonitoredService(
        builderFactory.getProjectParams(), monitoredServiceIdentifier);
    SRMAnalysisStepExecutionDetail stepExecutionDetail1 =
        srmAnalysisStepService.getSRMAnalysisStepExecutionDetail(analysisExecutionDetailsId1);
    assertThat(stepExecutionDetail1.getAnalysisStatus()).isEqualTo(SRMAnalysisStatus.ABORTED);
  }

  private Ambiance getAmbiance() {
    HashMap<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", accountId);
    setupAbstractions.put("projectIdentifier", projectIdentifier);
    setupAbstractions.put("orgIdentifier", orgIdentifier);
    return Ambiance.newBuilder()
        .setPlanExecutionId(generateUuid())
        .setStageExecutionId(generateUuid())
        .addLevels(Level.newBuilder()
                       .setRuntimeId(generateUuid())
                       .setStartTs(clock.millis())
                       .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                       .build())
        .addLevels(Level.newBuilder()
                       .setRuntimeId(generateUuid())
                       .setIdentifier("srmAnalysisStepIdentifier")
                       .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                       .build())
        .putAllSetupAbstractions(setupAbstractions)
        .build();
  }
}
