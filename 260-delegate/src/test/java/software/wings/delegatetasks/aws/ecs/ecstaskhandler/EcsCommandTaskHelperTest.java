/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.aws.ecs.ecstaskhandler;

import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.service.intfc.aws.delegate.AwsAppAutoScalingHelperServiceDelegate;

import com.amazonaws.services.applicationautoscaling.model.Alarm;
import com.amazonaws.services.applicationautoscaling.model.PutScalingPolicyResult;
import com.amazonaws.services.applicationautoscaling.model.RegisterScalableTargetResult;
import com.amazonaws.services.applicationautoscaling.model.ScalableTarget;
import com.amazonaws.services.applicationautoscaling.model.ScalingPolicy;
import com.amazonaws.services.cloudwatch.model.MetricAlarm;
import com.amazonaws.services.cloudwatch.model.PutMetricAlarmResult;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class EcsCommandTaskHelperTest extends WingsBaseTest {
  @Mock private ExecutionLogCallback executionLogCallback;
  @Mock private AwsAppAutoScalingHelperServiceDelegate appAutoScalingService;
  @Mock private ScalableTarget scalableTarget;

  @InjectMocks private EcsCommandTaskHelper ecsCommandTaskHelper;

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testRegisterScalableTargetForEcsService() {
    String region = "region";
    AwsConfig awsConfig = AwsConfig.builder().build();
    RegisterScalableTargetResult registerScalableTargetResult = new RegisterScalableTargetResult();
    doNothing().when(executionLogCallback).saveExecutionLog(anyString());
    doReturn(registerScalableTargetResult)
        .when(appAutoScalingService)
        .registerScalableTarget(anyString(), any(), anyList(), any());

    ecsCommandTaskHelper.registerScalableTargetForEcsService(
        appAutoScalingService, region, awsConfig, Collections.emptyList(), executionLogCallback, scalableTarget);

    verify(appAutoScalingService).registerScalableTarget(eq(region), eq(awsConfig), eq(Collections.emptyList()), any());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testRegisterScalableTargetForEcsServiceWithScalableTargetNull() {
    String region = "region";
    AwsConfig awsConfig = AwsConfig.builder().build();
    RegisterScalableTargetResult registerScalableTargetResult = new RegisterScalableTargetResult();
    doNothing().when(executionLogCallback).saveExecutionLog(anyString());
    doReturn(registerScalableTargetResult)
        .when(appAutoScalingService)
        .registerScalableTarget(anyString(), any(), anyList(), any());

    ecsCommandTaskHelper.registerScalableTargetForEcsService(
        appAutoScalingService, region, awsConfig, Collections.emptyList(), executionLogCallback, null);

    verify(appAutoScalingService, never())
        .registerScalableTarget(eq("region"), eq(awsConfig), eq(Collections.emptyList()), any());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testUpsetScalingPolicyIfRequiredWithEmptyPolicyJson() {
    AwsConfig awsConfig = AwsConfig.builder().build();
    RegisterScalableTargetResult registerScalableTargetResult = new RegisterScalableTargetResult();
    doNothing().when(executionLogCallback).saveExecutionLog(anyString());
    doReturn(registerScalableTargetResult)
        .when(appAutoScalingService)
        .registerScalableTarget(anyString(), any(), anyList(), any());

    ecsCommandTaskHelper.upsertScalingPolicyIfRequired(
        null, null, "", "region", awsConfig, appAutoScalingService, Collections.emptyList(), executionLogCallback);

    verify(appAutoScalingService, never()).getScalingPolicyFromJson(any());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testUpsetScalingPolicyIfRequiredWithoutCloudWatchAlarms() {
    String region = "region";
    String policyJson = "{}";
    AwsConfig awsConfig = AwsConfig.builder().build();
    doNothing().when(executionLogCallback).saveExecutionLog(anyString());
    doReturn(Arrays.asList(new ScalingPolicy())).when(appAutoScalingService).getScalingPolicyFromJson(eq(policyJson));
    doReturn(new PutScalingPolicyResult())
        .when(appAutoScalingService)
        .upsertScalingPolicy(anyString(), any(), anyList(), any());

    ecsCommandTaskHelper.upsertScalingPolicyIfRequired(policyJson, "resourceId", "scalableDimension", region, awsConfig,
        appAutoScalingService, Collections.emptyList(), executionLogCallback);

    verify(appAutoScalingService).upsertScalingPolicy(eq(region), eq(awsConfig), eq(Collections.emptyList()), any());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testUpsetScalingPolicyIfRequiredWithoutMetricAlarms() {
    String region = "region";
    String policyJson = "{}";
    AwsConfig awsConfig = AwsConfig.builder().build();
    ScalingPolicy scalingPolicy = new ScalingPolicy();
    scalingPolicy.setAlarms(Arrays.asList(new Alarm()));
    doNothing().when(executionLogCallback).saveExecutionLog(anyString());
    doReturn(Arrays.asList(scalingPolicy)).when(appAutoScalingService).getScalingPolicyFromJson(eq(policyJson));
    doReturn(new PutScalingPolicyResult())
        .when(appAutoScalingService)
        .upsertScalingPolicy(anyString(), any(), anyList(), any());
    doReturn(Arrays.asList()).when(appAutoScalingService).fetchAlarmsByName(anyString(), any(), anyList(), any());

    ecsCommandTaskHelper.upsertScalingPolicyIfRequired(policyJson, "resourceId", "scalableDimension", region, awsConfig,
        appAutoScalingService, Collections.emptyList(), executionLogCallback);

    verify(appAutoScalingService).upsertScalingPolicy(eq(region), eq(awsConfig), eq(Collections.emptyList()), any());
    verify(appAutoScalingService).fetchAlarmsByName(eq(region), eq(awsConfig), eq(Collections.emptyList()), any());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testUpsetScalingPolicyIfRequiredWithMetricAlarms() {
    String region = "region";
    String policyJson = "{}";
    AwsConfig awsConfig = AwsConfig.builder().build();
    ScalingPolicy scalingPolicy = new ScalingPolicy();
    scalingPolicy.setAlarms(Arrays.asList(new Alarm()));
    doNothing().when(executionLogCallback).saveExecutionLog(anyString());
    doReturn(Arrays.asList(scalingPolicy)).when(appAutoScalingService).getScalingPolicyFromJson(eq(policyJson));
    doReturn(new PutScalingPolicyResult())
        .when(appAutoScalingService)
        .upsertScalingPolicy(anyString(), any(), anyList(), any());
    doReturn(Arrays.asList(new MetricAlarm()))
        .when(appAutoScalingService)
        .fetchAlarmsByName(anyString(), any(), anyList(), any());
    doReturn(new PutMetricAlarmResult())
        .when(appAutoScalingService)
        .putMetricAlarm(anyString(), any(), anyList(), any());

    ecsCommandTaskHelper.upsertScalingPolicyIfRequired(policyJson, "resourceId", "scalableDimension", region, awsConfig,
        appAutoScalingService, Collections.emptyList(), executionLogCallback);

    verify(appAutoScalingService).upsertScalingPolicy(eq(region), eq(awsConfig), eq(Collections.emptyList()), any());
    verify(appAutoScalingService).fetchAlarmsByName(eq(region), eq(awsConfig), eq(Collections.emptyList()), any());
  }
  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetResourceIdForEcsService() {
    String resourceId = ecsCommandTaskHelper.getResourceIdForEcsService("serviceName", "clusterName");
    assertThat(resourceId).isNotNull();
    assertThat(resourceId).isEqualTo("service/clusterName/serviceName");
  }
}
