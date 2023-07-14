/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.impl;

import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.activity.entities.SRMStepAnalysisActivity;
import io.harness.cvng.activity.services.api.ActivityService;
import io.harness.cvng.analysis.entities.SRMAnalysisStepDetailDTO;
import io.harness.cvng.analysis.entities.SRMAnalysisStepExecutionDetail;
import io.harness.cvng.beans.change.SRMAnalysisStatus;
import io.harness.cvng.cdng.services.api.SRMAnalysisStepService;
import io.harness.cvng.core.beans.params.ServiceEnvironmentParams;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SRMAnalysisStepServiceImplTest extends CvNextGenTestBase {
  @Inject SRMAnalysisStepService srmAnalysisStepService;

  @Inject ActivityService activityService;
  @Inject Clock clock;

  private BuilderFactory builderFactory;

  private String monitoredServiceIdentifier;
  private ServiceEnvironmentParams serviceEnvironmentParams;

  private String analysisExecutionDetailsId;

  private String activityId;
  @Before
  public void setUp() throws Exception {
    builderFactory = BuilderFactory.getDefault();
    clock = builderFactory.getClock();
    monitoredServiceIdentifier = builderFactory.getContext().getMonitoredServiceIdentifier();
    serviceEnvironmentParams = ServiceEnvironmentParams.builderWithProjectParams(builderFactory.getProjectParams())
                                   .serviceIdentifier("service1")
                                   .environmentIdentifier("env1")
                                   .build();
    analysisExecutionDetailsId = srmAnalysisStepService.createSRMAnalysisStepExecution(
        builderFactory.getAmbiance(builderFactory.getProjectParams()), monitoredServiceIdentifier,
        serviceEnvironmentParams, Duration.ofDays(1));
    SRMStepAnalysisActivity stepAnalysisActivity = builderFactory.getSRMStepAnalysisActivityBuilder()
                                                       .executionNotificationDetailsId(analysisExecutionDetailsId)
                                                       .build();
    activityId = activityService.createActivity(stepAnalysisActivity);
    FieldUtils.writeField(srmAnalysisStepService, "clock", clock, true);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testAbortRunningMonitoredService() throws IllegalAccessException {
    clock = Clock.fixed(clock.instant().plus(3, ChronoUnit.DAYS), ZoneOffset.UTC);
    FieldUtils.writeField(srmAnalysisStepService, "clock", clock, true);
    srmAnalysisStepService.abortRunningStepsForMonitoredService(
        builderFactory.getProjectParams(), monitoredServiceIdentifier);
    SRMAnalysisStepExecutionDetail stepExecutionDetail1 =
        srmAnalysisStepService.getSRMAnalysisStepExecutionDetail(analysisExecutionDetailsId);
    assertThat(stepExecutionDetail1.getAnalysisStatus()).isEqualTo(SRMAnalysisStatus.ABORTED);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testAbortRunningStep() {
    SRMAnalysisStepExecutionDetail stepExecutionDetail =
        srmAnalysisStepService.getSRMAnalysisStepExecutionDetail(analysisExecutionDetailsId);
    assertThat(stepExecutionDetail.getAnalysisStatus()).isEqualTo(SRMAnalysisStatus.RUNNING);

    SRMAnalysisStepDetailDTO analysisStepDetailDTO =
        srmAnalysisStepService.abortRunningSrmAnalysisStep(analysisExecutionDetailsId);
    assertThat(analysisStepDetailDTO.getAnalysisStatus()).isEqualTo(SRMAnalysisStatus.ABORTED);
    assertThat(analysisStepDetailDTO.getMonitoredServiceIdentifier()).isEqualTo(monitoredServiceIdentifier);
    assertThat(analysisStepDetailDTO.getAnalysisStartTime()).isEqualTo(stepExecutionDetail.getAnalysisStartTime());
    assertThat(analysisStepDetailDTO.getAnalysisEndTime()).isEqualTo(clock.millis());
    assertThat(analysisStepDetailDTO.getExecutionDetailIdentifier()).isEqualTo(analysisExecutionDetailsId);

    stepExecutionDetail = srmAnalysisStepService.getSRMAnalysisStepExecutionDetail(analysisExecutionDetailsId);
    assertThat(stepExecutionDetail.getAnalysisStatus()).isEqualTo(SRMAnalysisStatus.ABORTED);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetSummaryOfStep() {
    SRMAnalysisStepExecutionDetail stepExecutionDetail =
        srmAnalysisStepService.getSRMAnalysisStepExecutionDetail(analysisExecutionDetailsId);
    SRMAnalysisStepDetailDTO analysisStepDetailDTO = srmAnalysisStepService.getSRMAnalysisSummary(activityId);

    assertThat(analysisStepDetailDTO.getAnalysisStatus()).isEqualTo(SRMAnalysisStatus.RUNNING);
    assertThat(analysisStepDetailDTO.getMonitoredServiceIdentifier()).isEqualTo(monitoredServiceIdentifier);
    assertThat(analysisStepDetailDTO.getAnalysisStartTime()).isEqualTo(stepExecutionDetail.getAnalysisStartTime());
    assertThat(analysisStepDetailDTO.getAnalysisEndTime()).isEqualTo(stepExecutionDetail.getAnalysisEndTime());
    assertThat(analysisStepDetailDTO.getExecutionDetailIdentifier()).isEqualTo(analysisExecutionDetailsId);
  }
}
