/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.PRANJAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.TaskData;
import io.harness.rule.Owner;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.beans.AwsConfig;
import software.wings.beans.NameValuePair;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.beans.WorkflowExecution;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.cloudwatch.CloudWatchDataCollectionInfo;
import software.wings.service.impl.cloudwatch.CloudWatchMetric;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.AwsHelperResourceService;
import software.wings.service.intfc.CloudWatchService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.MetricDataAnalysisService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.ExecutionContextImpl;
import software.wings.verification.VerificationStateAnalysisExecutionData;

import com.google.api.client.util.ArrayMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * Created by Pranjal on 05/25/2019
 */
public class CloudWatchStateTest extends APMStateVerificationTestBase {
  @InjectMocks private CloudWatchState cloudWatchState;
  @Mock SettingsService settingsService;
  @Mock MetricDataAnalysisService metricAnalysisService;
  @Mock AppService appService;
  @Mock WaitNotifyEngine waitNotifyEngine;
  @Mock WorkflowExecutionService workflowExecutionService;
  @Mock CloudWatchService cloudWatchService;
  @Mock DelegateService delegateService;
  @Mock SecretManager secretManager;
  @Mock AwsHelperResourceService awsHelperResourceService;

  @Before
  public void setup() throws IllegalAccessException {
    setupCommon();
    initMocks(this);
    setupCommonMocks();
    when(workflowExecutionService.getWorkflowExecution(any(), any()))
        .thenReturn(WorkflowExecution.builder().uuid(generateUuid()).build());
    when(secretManager.getEncryptionDetails(any(), any(), any())).thenReturn(Collections.emptyList());
    setupCvActivityLogService(cloudWatchState);
  }
  @Test
  @Owner(developers = PRANJAL)
  @Category(UnitTests.class)
  public void testValidateFieldsInvalidCase() {
    CloudWatchState cloudWatchState = new CloudWatchState("dummy");
    // not adding any metrics for verification
    Map<String, String> invalidFields = cloudWatchState.validateFields();
    assertThat(isEmpty(cloudWatchState.fetchEcsMetrics())).isTrue();
    assertThat(isEmpty(cloudWatchState.fetchLoadBalancerMetrics())).isTrue();
    assertThat(invalidFields.size() == 1).isTrue();
    assertThat(invalidFields.keySet().iterator().next()).isEqualTo("No metrics provided");
  }

  @Test
  @Owner(developers = PRANJAL)
  @Category(UnitTests.class)
  public void testValidateFieldsValidCaseLambdaProvided() {
    CloudWatchState cloudWatchState = new CloudWatchState("dummy");
    cloudWatchState.setShouldDoLambdaVerification(true);
    Map<String, String> invalidFields = cloudWatchState.validateFields();
    assertThat(isEmpty(cloudWatchState.fetchEcsMetrics())).isTrue();
    assertThat(isEmpty(cloudWatchState.fetchLoadBalancerMetrics())).isTrue();
    assertThat(isEmpty(cloudWatchState.fetchEc2Metrics())).isTrue();
    assertThat(cloudWatchState.isShouldDoLambdaVerification()).isTrue();
    assertThat(cloudWatchState.isShouldDoECSClusterVerification()).isFalse();

    assertThat(invalidFields.size() == 0).isTrue();
  }

  @Test
  @Owner(developers = PRANJAL)
  @Category(UnitTests.class)
  public void testValidateFieldsValidCaseECSProvided() {
    CloudWatchState cloudWatchState = new CloudWatchState("dummy");
    cloudWatchState.setShouldDoECSClusterVerification(true);
    Map<String, String> invalidFields = cloudWatchState.validateFields();
    assertThat(isEmpty(cloudWatchState.fetchEcsMetrics())).isTrue();
    assertThat(isEmpty(cloudWatchState.fetchLoadBalancerMetrics())).isTrue();
    assertThat(isEmpty(cloudWatchState.fetchEc2Metrics())).isTrue();
    assertThat(cloudWatchState.isShouldDoECSClusterVerification()).isTrue();
    assertThat(cloudWatchState.isShouldDoLambdaVerification()).isFalse();

    assertThat(invalidFields.size() == 0).isTrue();
  }

  @Test
  @Owner(developers = PRANJAL)
  @Category(UnitTests.class)
  public void testValidateFieldsValidCaseLoadBalancerProvided() {
    CloudWatchState cloudWatchState = new CloudWatchState("dummy");
    Map<String, List<CloudWatchMetric>> metrics = new HashMap<>();
    List<CloudWatchMetric> metricList = new ArrayList<>();
    metricList.add(CloudWatchMetric.builder().metricName("asdf").build());
    metrics.put("loadbalancer", metricList);

    cloudWatchState.setLoadBalancerMetrics(metrics);
    Map<String, String> invalidFields = cloudWatchState.validateFields();
    assertThat(isEmpty(cloudWatchState.fetchEcsMetrics())).isTrue();
    assertThat(isNotEmpty(cloudWatchState.fetchLoadBalancerMetrics())).isTrue();
    assertThat(isEmpty(cloudWatchState.fetchEc2Metrics())).isTrue();
    assertThat(cloudWatchState.isShouldDoLambdaVerification()).isFalse();
    assertThat(cloudWatchState.isShouldDoECSClusterVerification()).isFalse();

    assertThat(invalidFields.size() == 0).isTrue();
  }

  @Test
  @Owner(developers = PRANJAL)
  @Category(UnitTests.class)
  public void testValidateFieldsValidCaseEC2Provided() {
    CloudWatchState cloudWatchState = new CloudWatchState("dummy");
    List<CloudWatchMetric> metricList = new ArrayList<>();
    metricList.add(CloudWatchMetric.builder().metricName("asdf").build());

    cloudWatchState.setEc2Metrics(metricList);
    Map<String, String> invalidFields = cloudWatchState.validateFields();
    assertThat(isEmpty(cloudWatchState.fetchEcsMetrics())).isTrue();
    assertThat(isEmpty(cloudWatchState.fetchLoadBalancerMetrics())).isTrue();
    assertThat(isNotEmpty(cloudWatchState.fetchEc2Metrics())).isTrue();
    assertThat(cloudWatchState.isShouldDoLambdaVerification()).isFalse();
    assertThat(cloudWatchState.isShouldDoECSClusterVerification()).isFalse();

    assertThat(invalidFields.size() == 0).isTrue();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testTriggerAnalysisDataCollection_resolveRegionAndConnectorIdWithActualValueOfRegion() {
    AnalysisContext analysisContext = mock(AnalysisContext.class);
    VerificationStateAnalysisExecutionData executionData = mock(VerificationStateAnalysisExecutionData.class);
    Map<String, String> hosts = new HashMap<>();
    cloudWatchState.setRegion("${serviceVariable.cloudwatchRegion}");
    hosts.put("aws.host", "default");
    String resolvedAnalysisServerConfigId = generateUuid();
    when(executionContext.renderExpression(eq("${serviceVariable.cloudwatchRegion}"))).thenReturn("us-east1");

    AwsConfig awsConfig = AwsConfig.builder().build();
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withUuid(resolvedAnalysisServerConfigId)
                                            .withValue(awsConfig)
                                            .withName("aws config")
                                            .build();
    wingsPersistence.save(settingAttribute);
    when(cloudWatchService.getCloudWatchMetrics()).thenReturn(new ArrayMap<>());
    when(settingsService.get(eq(resolvedAnalysisServerConfigId))).thenReturn(settingAttribute);
    when(appService.get(anyString())).thenReturn(application);
    String analysisServerConfigId = "${workflow.variables.connectorName}";
    cloudWatchState.setAnalysisServerConfigId(analysisServerConfigId);

    CloudWatchState spyState = spy(cloudWatchState);
    when(spyState.getResolvedConnectorId(any(), eq("analysisServerConfigId"), eq(analysisServerConfigId)))
        .thenReturn(resolvedAnalysisServerConfigId);

    spyState.triggerAnalysisDataCollection(executionContext, analysisContext, executionData, hosts);
    ArgumentCaptor<DelegateTask> argument = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(argument.capture());
    TaskData taskData = argument.getValue().getData();
    Object parameters[] = taskData.getParameters();
    assertThat(1).isEqualTo(parameters.length);
    assertThat(TaskType.CLOUD_WATCH_COLLECT_METRIC_DATA.name()).isEqualTo(taskData.getTaskType());
    CloudWatchDataCollectionInfo cloudWatchDataCollectionInfo = (CloudWatchDataCollectionInfo) parameters[0];
    assertThat(cloudWatchDataCollectionInfo.getAwsConfig()).isEqualTo(settingAttribute.getValue());
    assertThat(cloudWatchDataCollectionInfo.getRegion()).isEqualTo("us-east1");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testTriggerAnalysisDataCollection_resolveRegionAndConnectorIdWithMappedValueOfRegion() {
    AnalysisContext analysisContext = mock(AnalysisContext.class);
    VerificationStateAnalysisExecutionData executionData = mock(VerificationStateAnalysisExecutionData.class);
    Map<String, String> hosts = new HashMap<>();
    cloudWatchState.setRegion("${serviceVariable.cloudwatchRegion}");
    hosts.put("aws.host", "default");
    String resolvedAnalysisServerConfigId = generateUuid();
    when(executionContext.renderExpression(eq("${serviceVariable.cloudwatchRegion}"))).thenReturn("US East (Ohio)");
    NameValuePair nameValuePair = NameValuePair.builder().name("US East (Ohio)").value("us-east1").build();
    when(awsHelperResourceService.getAwsRegions()).thenReturn(Arrays.asList(nameValuePair));

    AwsConfig awsConfig = AwsConfig.builder().build();
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withUuid(resolvedAnalysisServerConfigId)
                                            .withValue(awsConfig)
                                            .withName("aws config")
                                            .build();
    wingsPersistence.save(settingAttribute);
    when(cloudWatchService.getCloudWatchMetrics()).thenReturn(new ArrayMap<>());
    when(settingsService.get(eq(resolvedAnalysisServerConfigId))).thenReturn(settingAttribute);
    when(appService.get(anyString())).thenReturn(application);
    String analysisServerConfigId = "${workflow.variables.connectorName}";
    cloudWatchState.setAnalysisServerConfigId(analysisServerConfigId);

    CloudWatchState spyState = spy(cloudWatchState);
    when(spyState.getResolvedConnectorId(any(), eq("analysisServerConfigId"), eq(analysisServerConfigId)))
        .thenReturn(resolvedAnalysisServerConfigId);

    spyState.triggerAnalysisDataCollection(executionContext, analysisContext, executionData, hosts);
    ArgumentCaptor<DelegateTask> argument = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(argument.capture());
    TaskData taskData = argument.getValue().getData();
    Object parameters[] = taskData.getParameters();
    assertThat(1).isEqualTo(parameters.length);
    assertThat(TaskType.CLOUD_WATCH_COLLECT_METRIC_DATA.name()).isEqualTo(taskData.getTaskType());
    CloudWatchDataCollectionInfo cloudWatchDataCollectionInfo = (CloudWatchDataCollectionInfo) parameters[0];
    assertThat(cloudWatchDataCollectionInfo.getAwsConfig()).isEqualTo(settingAttribute.getValue());
    assertThat(cloudWatchDataCollectionInfo.getRegion()).isEqualTo("us-east1");
  }
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testTriggerAnalysisDataCollection_nullSettingAttribute() {
    ExecutionContextImpl executionContext = mock(ExecutionContextImpl.class);
    AnalysisContext analysisContext = mock(AnalysisContext.class);
    VerificationStateAnalysisExecutionData executionData = mock(VerificationStateAnalysisExecutionData.class);
    Map<String, String> hosts = new HashMap<>();
    hosts.put("aws.host", "default");
    cloudWatchState.setAnalysisServerConfigId("wrongAnalysisServerConfigId");
    assertThatThrownBy(
        () -> cloudWatchState.triggerAnalysisDataCollection(executionContext, analysisContext, executionData, hosts))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("No Aws setting with id: wrongAnalysisServerConfigId found");
  }
}
