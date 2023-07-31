/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.impl;

import static io.harness.cvng.CVNGTestConstants.FIXED_TIME_FOR_TESTS;
import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.activity.entities.SRMStepAnalysisActivity;
import io.harness.cvng.activity.services.api.ActivityService;
import io.harness.cvng.analysis.entities.SRMAnalysisStepDetailDTO;
import io.harness.cvng.analysis.entities.SRMAnalysisStepExecutionDetail;
import io.harness.cvng.beans.change.SRMAnalysisStatus;
import io.harness.cvng.cdng.services.api.SRMAnalysisStepService;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.params.ServiceEnvironmentParams;
import io.harness.cvng.core.services.api.monitoredService.MSHealthReportService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.notification.beans.DeploymentImpactReportConditionSpec;
import io.harness.cvng.notification.beans.NotificationRuleCondition;
import io.harness.cvng.notification.beans.NotificationRuleConditionType;
import io.harness.cvng.notification.beans.NotificationRuleDTO;
import io.harness.cvng.notification.beans.NotificationRuleRefDTO;
import io.harness.cvng.notification.beans.NotificationRuleResponse;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.services.api.NotificationRuleService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import retrofit2.Call;
import retrofit2.Response;

public class SRMAnalysisStepServiceImplTest extends CvNextGenTestBase {
  @Inject SRMAnalysisStepService srmAnalysisStepService;

  SRMAnalysisStepService srmAnalysisStepServiceMock;

  @Inject MSHealthReportService msHealthReportService;

  @Inject MonitoredServiceService monitoredServiceService;

  @Inject NotificationRuleService notificationRuleService;
  @Inject ActivityService activityService;
  @Inject Clock clock;

  @Mock PipelineServiceClient pipelineServiceClient;

  private BuilderFactory builderFactory;

  private String monitoredServiceIdentifier;
  private ServiceEnvironmentParams serviceEnvironmentParams;

  private String analysisExecutionDetailsId;

  private String activityId;
  @Before
  public void setUp() throws Exception {
    srmAnalysisStepServiceMock = spy(srmAnalysisStepService);
    msHealthReportService = spy(msHealthReportService);
    builderFactory = BuilderFactory.getDefault();
    clock = FIXED_TIME_FOR_TESTS;
    Call<ResponseDTO<Object>> pipelineSummaryCall = mock(Call.class);
    doReturn(pipelineSummaryCall).when(pipelineServiceClient).getExecutionDetailV2(any(), any(), any(), any());
    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode pipelineExecutionSummary = objectMapper.createObjectNode();
    pipelineExecutionSummary.put("name", "Mocked Pipeline");
    ObjectNode mockResponse = objectMapper.createObjectNode();
    mockResponse.set("pipelineExecutionSummary", pipelineExecutionSummary);
    when(pipelineSummaryCall.execute()).thenReturn(Response.success(ResponseDTO.newResponse(mockResponse)));
    FieldUtils.writeField(srmAnalysisStepService, "pipelineServiceClient", pipelineServiceClient, true);
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
    stepAnalysisActivity.setUuid(analysisExecutionDetailsId);
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
    assertThat(stepExecutionDetail1.getPipelineName()).isEqualTo("Mocked Pipeline");
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testAbortRunningStep() {
    SRMAnalysisStepExecutionDetail stepExecutionDetail =
        srmAnalysisStepService.getSRMAnalysisStepExecutionDetail(analysisExecutionDetailsId);
    assertThat(stepExecutionDetail.getAnalysisStatus()).isEqualTo(SRMAnalysisStatus.RUNNING);

    SRMAnalysisStepDetailDTO analysisStepDetailDTO =
        srmAnalysisStepServiceMock.abortRunningSrmAnalysisStep(analysisExecutionDetailsId);
    assertThat(analysisStepDetailDTO.getAnalysisStatus()).isEqualTo(SRMAnalysisStatus.ABORTED);
    assertThat(analysisStepDetailDTO.getMonitoredServiceIdentifier()).isEqualTo(monitoredServiceIdentifier);
    assertThat(analysisStepDetailDTO.getAnalysisStartTime()).isEqualTo(stepExecutionDetail.getAnalysisStartTime());
    assertThat(analysisStepDetailDTO.getAnalysisEndTime()).isEqualTo(clock.millis());
    assertThat(analysisStepDetailDTO.getExecutionDetailIdentifier()).isEqualTo(analysisExecutionDetailsId);

    stepExecutionDetail = srmAnalysisStepService.getSRMAnalysisStepExecutionDetail(analysisExecutionDetailsId);
    assertThat(stepExecutionDetail.getAnalysisStatus()).isEqualTo(SRMAnalysisStatus.ABORTED);
    assertThat(stepExecutionDetail.getPipelineName()).isEqualTo("Mocked Pipeline");
    verify(srmAnalysisStepServiceMock).handleReportNotification(any());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCompleteSrmAnalysisStep() {
    SRMAnalysisStepExecutionDetail stepExecutionDetail =
        srmAnalysisStepService.getSRMAnalysisStepExecutionDetail(analysisExecutionDetailsId);
    assertThat(stepExecutionDetail.getAnalysisStatus()).isEqualTo(SRMAnalysisStatus.RUNNING);

    srmAnalysisStepServiceMock.completeSrmAnalysisStep(stepExecutionDetail);
    stepExecutionDetail = srmAnalysisStepService.getSRMAnalysisStepExecutionDetail(analysisExecutionDetailsId);
    assertThat(stepExecutionDetail.getAnalysisStatus()).isEqualTo(SRMAnalysisStatus.COMPLETED);
    assertThat(stepExecutionDetail.getMonitoredServiceIdentifier()).isEqualTo(monitoredServiceIdentifier);
    assertThat(stepExecutionDetail.getAnalysisStartTime()).isEqualTo(stepExecutionDetail.getAnalysisStartTime());
    assertThat(stepExecutionDetail.getUuid()).isEqualTo(analysisExecutionDetailsId);
    assertThat(stepExecutionDetail.getPipelineName()).isEqualTo("Mocked Pipeline");
    verify(srmAnalysisStepServiceMock).handleReportNotification(any());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testHandleReportNotification() throws IllegalAccessException {
    FieldUtils.writeField(srmAnalysisStepService, "msHealthReportService", msHealthReportService, true);
    MonitoredServiceDTO monitoredServiceDTO =
        monitoredServiceService
            .createDefault(builderFactory.getProjectParams(), builderFactory.getContext().getServiceIdentifier(),
                builderFactory.getContext().getEnvIdentifier())
            .getMonitoredServiceDTO();
    NotificationRuleDTO notificationRuleDTO =
        builderFactory.getNotificationRuleDTOBuilder(NotificationRuleType.MONITORED_SERVICE).build();
    notificationRuleDTO.setConditions(
        Collections.singletonList(NotificationRuleCondition.builder()
                                      .type(NotificationRuleConditionType.DEPLOYMENT_IMPACT_REPORT)
                                      .spec(DeploymentImpactReportConditionSpec.builder().build())
                                      .build()));
    NotificationRuleResponse notificationRuleResponse =
        notificationRuleService.create(builderFactory.getContext().getProjectParams(), notificationRuleDTO);
    monitoredServiceDTO.setNotificationRuleRefs(
        Arrays.asList(NotificationRuleRefDTO.builder()
                          .notificationRuleRef(notificationRuleResponse.getNotificationRule().getIdentifier())
                          .enabled(true)
                          .build()));
    monitoredServiceService.update(builderFactory.getContext().getAccountId(), monitoredServiceDTO);

    SRMAnalysisStepExecutionDetail stepExecutionDetail =
        srmAnalysisStepService.getSRMAnalysisStepExecutionDetail(analysisExecutionDetailsId);
    srmAnalysisStepService.handleReportNotification(stepExecutionDetail);

    verify(msHealthReportService).sendReportNotification(any(), any(), any(), any(), any(), any());
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
