/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.integration;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.SOWMYA;
import static io.harness.rule.OwnerRule.SRIRAM;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.service.impl.newrelic.NewRelicMetricDataRecord.DEFAULT_GROUP_NAME;

import static javax.ws.rs.client.Entity.entity;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.VerificationIntegrationBase;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.DeprecatedIntegrationTests;
import io.harness.jobs.workflow.timeseries.WorkflowTimeSeriesAnalysisJob;
import io.harness.managerclient.VerificationManagerClientHelper;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.scm.ScmSecret;
import io.harness.scm.SecretName;
import io.harness.serializer.JsonUtils;
import io.harness.service.intfc.TimeSeriesAnalysisService;

import software.wings.APMFetchConfig;
import software.wings.beans.APMVerificationConfig;
import software.wings.beans.CountsByStatuses;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
import software.wings.beans.WorkflowExecution;
import software.wings.delegatetasks.DelegateStateType;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.analysis.AnalysisComparisonStrategy;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.analysis.MLAnalysisType;
import software.wings.service.impl.analysis.MetricDataAnalysisServiceImpl;
import software.wings.service.impl.newrelic.LearningEngineAnalysisTask;
import software.wings.service.impl.newrelic.LearningEngineAnalysisTask.LearningEngineAnalysisTaskKeys;
import software.wings.service.impl.newrelic.NewRelicMetricDataRecord;
import software.wings.service.intfc.MetricDataAnalysisService;
import software.wings.service.intfc.analysis.ClusterLevel;
import software.wings.service.intfc.verification.CVActivityLogService;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateType;
import software.wings.sm.states.DatadogState;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;

@Slf4j
public class DataDogIntegrationTest extends VerificationIntegrationBase {
  @Inject private ScmSecret scmSecret;
  @Inject private TimeSeriesAnalysisService timeSeriesAnalysisService;
  @Inject private VerificationManagerClientHelper managerClient;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private CVActivityLogService cvActivityLogService;
  private MetricDataAnalysisService metricDataAnalysisService;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    metricDataAnalysisService = new MetricDataAnalysisServiceImpl();
    FieldUtils.writeField(metricDataAnalysisService, "wingsPersistence", wingsPersistence, true);
    loginAdminUser();
  }

  @Test
  @Owner(developers = SRIRAM)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("TODO: provide proper description why this test is disabled")
  public void fetch() {
    APMVerificationConfig config = new APMVerificationConfig();
    config.setAccountId(accountId);
    config.setUrl("https://app.datadoghq.com/api/v1/");
    config.setValidationUrl("metrics?from=12345");
    List<APMVerificationConfig.KeyValues> optionsList = new ArrayList<>();
    optionsList.add(APMVerificationConfig.KeyValues.builder()
                        .key("api_key")
                        .value(scmSecret.decryptToString(new SecretName("apm_verfication_config_api_key")))
                        .encrypted(false)
                        .build());
    optionsList.add(APMVerificationConfig.KeyValues.builder()
                        .key("application_key")
                        .value(scmSecret.decryptToString(new SecretName("datadog_application_key")))
                        .encrypted(false)
                        .build());
    config.setOptionsList(optionsList);

    SettingAttribute settingAttribute = wingsPersistence.createQuery(SettingAttribute.class)
                                            .filter(SettingAttributeKeys.name, "datadog_connector")
                                            .get();

    String serverConfigId;
    if (settingAttribute == null) {
      serverConfigId = wingsPersistence
                           .save(Lists.newArrayList(SettingAttribute.Builder.aSettingAttribute()
                                                        .withAccountId(accountId)
                                                        .withName("datadog_connector")
                                                        .withValue(config)
                                                        .build()))
                           .get(0);
    } else {
      serverConfigId = settingAttribute.getUuid();
    }

    WebTarget target =
        client.target(API_BASE + "/timeseries/fetch?accountId=" + accountId + "&serverConfigId=" + serverConfigId);

    RestResponse<String> restResponse = getRequestBuilderWithAuthHeader(target).post(
        entity(APMFetchConfig.builder()
                   .url("metrics?from=" + System.currentTimeMillis() / TimeUnit.SECONDS.toMillis(1))
                   .build(),
            APPLICATION_JSON),
        new GenericType<RestResponse<String>>() {});
    assertThat(restResponse.getResource()).isNotNull();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("TODO: provide proper description why this test is disabled")
  public void txnDatadog() throws InterruptedException {
    final String workflowId = UUID.randomUUID().toString();
    final String workflowExecutionId = UUID.randomUUID().toString();
    final String serviceId = UUID.randomUUID().toString();
    final String stateExecutionId = UUID.randomUUID().toString();
    final String appId = wingsPersistence.save(anApplication().name(generateUuid()).accountId(accountId).build());
    final String delegateTaskId = UUID.randomUUID().toString();

    StateExecutionInstance stateExecutionInstance = new StateExecutionInstance();
    String prevStateExecutionId = UUID.randomUUID().toString();
    stateExecutionInstance.setAppId(appId);
    stateExecutionInstance.setUuid(prevStateExecutionId);
    stateExecutionInstance.setStatus(ExecutionStatus.RUNNING);
    wingsPersistence.save(stateExecutionInstance);

    WorkflowExecution workflowExecution =
        WorkflowExecution.builder()
            .workflowId(workflowId)
            .appId(appId)
            .serviceIds(Lists.newArrayList(serviceId))
            .name(workflowId + "-prev-execution-" + 0)
            .status(ExecutionStatus.SUCCESS)
            .breakdown(CountsByStatuses.Builder.aCountsByStatuses().withSuccess(1).build())
            .build();
    String prevWorkflowExecutionId = wingsPersistence.save(workflowExecution);

    NewRelicMetricDataRecord record = new NewRelicMetricDataRecord();
    record.setName("New Relic Heartbeat");
    record.setAppId(appId);
    record.setWorkflowId(workflowId);
    record.setWorkflowExecutionId(prevWorkflowExecutionId);
    record.setServiceId(serviceId);
    record.setStateExecutionId(prevStateExecutionId);
    record.setTimeStamp(System.currentTimeMillis());
    record.setDataCollectionMinute(0);
    record.setLevel(ClusterLevel.HF);
    record.setStateType(DelegateStateType.DATA_DOG);

    NewRelicMetricDataRecord record1 = new NewRelicMetricDataRecord();
    record1.setName("Dummy txn1");
    record1.setAppId(appId);
    record1.setWorkflowId(workflowId);
    record1.setWorkflowExecutionId(prevWorkflowExecutionId);
    record1.setServiceId(serviceId);
    record1.setStateExecutionId(prevStateExecutionId);
    record1.setTimeStamp(System.currentTimeMillis());
    record1.setDataCollectionMinute(0);
    record1.setValues(new HashMap<>());
    record1.getValues().put("Hits", 20.0);
    record1.getValues().put("Request Duration", 2.0);
    record1.setHost("host1");
    record1.setStateType(DelegateStateType.DATA_DOG);

    timeSeriesAnalysisService.saveMetricData(
        accountId, appId, prevStateExecutionId, delegateTaskId, Lists.newArrayList(record, record1));

    stateExecutionInstance.setStatus(ExecutionStatus.SUCCESS);
    wingsPersistence.save(stateExecutionInstance);

    stateExecutionInstance = new StateExecutionInstance();
    stateExecutionInstance.setAppId(appId);
    stateExecutionInstance.setUuid(stateExecutionId);
    stateExecutionInstance.setStatus(ExecutionStatus.RUNNING);
    wingsPersistence.save(stateExecutionInstance);

    workflowExecution = WorkflowExecution.builder()
                            .uuid(workflowExecutionId)
                            .workflowId(workflowId)
                            .appId(appId)
                            .name(workflowId + "-curr-execution-" + 0)
                            .status(ExecutionStatus.RUNNING)
                            .build();
    wingsPersistence.save(workflowExecution);

    record = new NewRelicMetricDataRecord();
    record.setName("New Relic Heartbeat");
    record.setAppId(appId);
    record.setWorkflowId(workflowId);
    record.setWorkflowExecutionId(workflowExecutionId);
    record.setServiceId(serviceId);
    record.setStateExecutionId(stateExecutionId);
    record.setTimeStamp(System.currentTimeMillis());
    record.setDataCollectionMinute(0);
    record.setLevel(ClusterLevel.H0);
    record.setStateType(DelegateStateType.DATA_DOG);

    record1 = new NewRelicMetricDataRecord();
    record1.setName("Dummy txn1");
    record1.setAppId(appId);
    record1.setWorkflowId(workflowId);
    record1.setWorkflowExecutionId(workflowExecutionId);
    record1.setServiceId(serviceId);
    record1.setStateExecutionId(stateExecutionId);
    record1.setTimeStamp(System.currentTimeMillis());
    record1.setDataCollectionMinute(0);
    record1.setValues(new HashMap<>());
    record1.getValues().put("Hits", 20.0);
    record1.getValues().put("Request Duration", 2.0);
    record1.setTag("Servlet");
    record1.setHost("host1");
    record1.setStateType(DelegateStateType.DATA_DOG);

    timeSeriesAnalysisService.saveMetricData(
        accountId, appId, stateExecutionId, delegateTaskId, Lists.newArrayList(record, record1));

    timeSeriesAnalysisService.saveMetricTemplates(accountId, appId, StateType.DATA_DOG, stateExecutionId,
        DatadogState.metricDefinitions(DatadogState
                                           .metrics(Optional.of(Lists.newArrayList("trace.servlet.request.duration",
                                                        "trace.servlet.request.hits")),
                                               Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty())
                                           .values()));

    String lastSuccessfulWorkflowExecutionIdWithData =
        timeSeriesAnalysisService.getLastSuccessfulWorkflowExecutionIdWithData(
            StateType.DATA_DOG, appId, workflowId, serviceId);
    AnalysisContext analysisContext = AnalysisContext.builder()
                                          .accountId(accountId)
                                          .appId(appId)
                                          .workflowId(workflowId)
                                          .workflowExecutionId(workflowExecutionId)
                                          .stateExecutionId(stateExecutionId)
                                          .serviceId(serviceId)
                                          .controlNodes(Collections.singletonMap("host1", DEFAULT_GROUP_NAME))
                                          .testNodes(Collections.singletonMap("host1", DEFAULT_GROUP_NAME))
                                          .isSSL(true)
                                          .appPort(9090)
                                          .comparisonStrategy(AnalysisComparisonStrategy.COMPARE_WITH_PREVIOUS)
                                          .timeDuration(1)
                                          .stateType(StateType.DATA_DOG)
                                          .correlationId(UUID.randomUUID().toString())
                                          .prevWorkflowExecutionId(lastSuccessfulWorkflowExecutionIdWithData)
                                          .smooth_window(1)
                                          .parallelProcesses(1)
                                          .comparisonWindow(1)
                                          .tolerance(1)
                                          .prevWorkflowExecutionId(prevWorkflowExecutionId)
                                          .build();
    JobExecutionContext jobExecutionContext = mock(JobExecutionContext.class);
    JobDataMap jobDataMap = mock(JobDataMap.class);
    when(jobDataMap.getLong("timestamp")).thenReturn(System.currentTimeMillis());
    when(jobDataMap.getString("jobParams")).thenReturn(JsonUtils.asJson(analysisContext));
    when(jobDataMap.getString("delegateTaskId")).thenReturn(UUID.randomUUID().toString());
    when(jobExecutionContext.getMergedJobDataMap()).thenReturn(jobDataMap);
    when(jobExecutionContext.getScheduler()).thenReturn(mock(Scheduler.class));
    when(jobExecutionContext.getJobDetail()).thenReturn(mock(JobDetail.class));

    new WorkflowTimeSeriesAnalysisJob
        .MetricAnalysisGenerator(timeSeriesAnalysisService, learningEngineService, managerClient, analysisContext,
            Optional.of(jobExecutionContext), cvActivityLogService)
        .run();

    Thread.sleep(10000);
    List<LearningEngineAnalysisTask> analysisTasks =
        wingsPersistence.createQuery(LearningEngineAnalysisTask.class)
            .filter(LearningEngineAnalysisTaskKeys.state_execution_id, stateExecutionId)
            .asList();
    assertThat(analysisTasks).hasSize(1);

    LearningEngineAnalysisTask task = analysisTasks.iterator().next();

    assertThat(task).isNotNull();
    assertThat(task.getStateType()).isEqualByComparingTo(StateType.DATA_DOG);
    assertThat(task.getMl_analysis_type()).isEqualByComparingTo(MLAnalysisType.TIME_SERIES);
  }
}
