/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.aws.ecs.ecstaskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.RAGHVENDRA;
import static io.harness.rule.OwnerRule.SATYAM;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.beans.container.AwsAutoScalarConfig;
import software.wings.cloudprovider.aws.EcsContainerService;
import software.wings.service.impl.AwsHelperService;
import software.wings.service.intfc.aws.delegate.AwsAppAutoScalingHelperServiceDelegate;

import com.amazonaws.services.applicationautoscaling.model.DescribeScalableTargetsRequest;
import com.amazonaws.services.applicationautoscaling.model.DescribeScalableTargetsResult;
import com.amazonaws.services.applicationautoscaling.model.ScalableTarget;
import com.amazonaws.services.applicationautoscaling.model.ServiceNamespace;
import com.amazonaws.services.ecs.model.DescribeServicesResult;
import com.amazonaws.services.ecs.model.Service;
import com.google.inject.Inject;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class EcsSwapRoutesCommandTaskHelperTest extends WingsBaseTest {
  @Mock private AwsHelperService mockAwsHelperService;
  @Mock private EcsContainerService mockEcsContainerService;
  @Mock private AwsAppAutoScalingHelperServiceDelegate mockAwsAppAutoScalingService;
  @Mock private EcsCommandTaskHelper mockEcsCommandTaskHelper;

  @InjectMocks @Inject private EcsSwapRoutesCommandTaskHelper taskHelper;

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testUpsizeOlderService() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    doReturn(new DescribeServicesResult().withServices(new Service().withDesiredCount(0)))
        .when(mockAwsHelperService)
        .describeServices(anyString(), any(), anyList(), any());
    taskHelper.upsizeOlderService(
        AwsConfig.builder().build(), emptyList(), "us-east-1", "cluster", 1, "foo_1", mockCallback, 20, false);
    verify(mockEcsContainerService).updateServiceCount(any());
    verify(mockEcsContainerService).waitForTasksToBeInRunningStateWithHandledExceptions(any());
    verify(mockEcsContainerService).waitForServiceToReachSteadyState(eq(20), any());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testDownsizeOlderService() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    taskHelper.downsizeOlderService(
        AwsConfig.builder().build(), emptyList(), "us-east-1", "cluster", "foo_1", mockCallback, 1);
    verify(mockEcsContainerService).updateServiceCount(any());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testUpdateServiceTags() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    doReturn(new DescribeServicesResult().withServices(new Service().withServiceArn("arn_2")))
        .doReturn(new DescribeServicesResult().withServices(new Service().withServiceArn("arn_1")))
        .when(mockAwsHelperService)
        .describeServices(anyString(), any(), anyList(), any());
    taskHelper.updateServiceTags(
        AwsConfig.builder().build(), emptyList(), "us-east-1", "cluster", "foo_2", "foo_1", false, mockCallback);
    verify(mockAwsHelperService, times(2)).untagService(anyString(), anyList(), any(), any());
    verify(mockAwsHelperService, times(2)).tagService(anyString(), anyList(), any(), any());
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testRestoreAwsAutoScalarConfigs() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    String scalableTargetJson = "scalableTargetJson";
    String[] scalingPolicyJsons = new String[] {"scalingPolicyJson"};
    String resourceId = "abc";
    String scalableDimension = "ecs:service:DesiredCount";
    List<AwsAutoScalarConfig> previousAwsAutoScalarConfigs = asList(AwsAutoScalarConfig.builder()
                                                                        .resourceId(resourceId)
                                                                        .scalingPolicyJson(scalingPolicyJsons)
                                                                        .scalableTargetJson(scalableTargetJson)
                                                                        .build());
    DescribeScalableTargetsRequest describeScalableTargetsRequest = new DescribeScalableTargetsRequest()
                                                                        .withResourceIds(asList(resourceId))
                                                                        .withScalableDimension(scalableDimension)
                                                                        .withServiceNamespace(ServiceNamespace.Ecs);
    ScalableTarget scalableTarget = new ScalableTarget();
    scalableTarget.setResourceId(resourceId);
    scalableTarget.setScalableDimension(scalableDimension);
    DescribeScalableTargetsResult describeScalableTargetsResult = new DescribeScalableTargetsResult();
    describeScalableTargetsResult.setScalableTargets(asList(scalableTarget));
    doReturn(scalableTarget).when(mockAwsAppAutoScalingService).getScalableTargetFromJson(eq(scalableTargetJson));

    doNothing().when(mockCallback).saveExecutionLog(anyString());

    taskHelper.restoreAwsAutoScalarConfig(
        AwsConfig.builder().build(), emptyList(), "us-east-1", previousAwsAutoScalarConfigs, true, mockCallback);

    verify(mockAwsAppAutoScalingService, times(1)).getScalableTargetFromJson(eq(scalableTargetJson));
  }
}
