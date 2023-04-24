/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.aws.ecs.ecstaskhandler.deploy;

import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.LOVISH_BANSAL;
import static io.harness.rule.OwnerRule.SAINATH;
import static io.harness.rule.OwnerRule.SATYAM;

import static software.wings.beans.InstanceUnitType.COUNT;
import static software.wings.beans.command.EcsResizeParams.EcsResizeParamsBuilder.anEcsResizeParams;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static wiremock.com.google.common.collect.Lists.newArrayList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.ContainerServiceData;
import software.wings.beans.AwsConfig;
import software.wings.beans.InstanceUnitType;
import software.wings.beans.command.EcsResizeParams;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.beans.container.AwsAutoScalarConfig;
import software.wings.cloudprovider.aws.AwsClusterService;
import software.wings.cloudprovider.aws.EcsContainerService;
import software.wings.delegatetasks.aws.ecs.ecstaskhandler.EcsCommandTaskHelper;
import software.wings.helpers.ext.ecs.response.EcsDeployRollbackDataFetchResponse;
import software.wings.service.impl.AwsHelperService;
import software.wings.service.intfc.aws.delegate.AwsAppAutoScalingHelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsEcsHelperServiceDelegate;

import com.amazonaws.services.applicationautoscaling.model.DeregisterScalableTargetRequest;
import com.amazonaws.services.applicationautoscaling.model.DescribeScalableTargetsResult;
import com.amazonaws.services.applicationautoscaling.model.ScalableTarget;
import com.amazonaws.services.ecs.model.NetworkMode;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.Service;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class EcsDeployCommandTaskHelperTest extends WingsBaseTest {
  @Mock private AwsClusterService mockAwsClusterService;
  @Mock private AwsAppAutoScalingHelperServiceDelegate mockAwsAppAutoScalingService;
  @Mock private AwsHelperService mockAwsHelperService;
  @Mock private AwsEcsHelperServiceDelegate mockAwsEcsHelperServiceDelegate;
  @Mock private EcsContainerService mockEcsContainerService;
  @Mock private EcsCommandTaskHelper mockEcsCommandTaskHelper;
  @Spy @InjectMocks @Inject private EcsDeployCommandTaskHelper helper;

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testDeregisterAutoScalarsIfExists() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(any());
    ContextData data = ContextData.builder()
                           .awsConfig(AwsConfig.builder().build())
                           .resizeParams(anEcsResizeParams()
                                             .withRegion("us-east-1")
                                             .withPreviousEcsAutoScalarsAlreadyRemoved(false)
                                             .withPreviousAwsAutoScalarConfigs(singletonList(
                                                 AwsAutoScalarConfig.builder().scalableTargetJson("ScalTJson").build()))
                                             .build())
                           .build();
    ScalableTarget target = new ScalableTarget().withResourceId("resId").withScalableDimension("scalDim");
    doReturn(target).when(mockAwsAppAutoScalingService).getScalableTargetFromJson(any());
    DescribeScalableTargetsResult result = new DescribeScalableTargetsResult().withScalableTargets(
        new ScalableTarget().withResourceId("resId").withScalableDimension("scalDim"));
    doReturn(result).when(mockAwsAppAutoScalingService).listScalableTargets(any(), any(), any(), any());
    helper.deregisterAutoScalarsIfExists(data, mockCallback);
    ArgumentCaptor<DeregisterScalableTargetRequest> captor =
        ArgumentCaptor.forClass(DeregisterScalableTargetRequest.class);
    verify(mockAwsAppAutoScalingService).deregisterScalableTarget(any(), any(), any(), captor.capture());
    DeregisterScalableTargetRequest request = captor.getValue();
    assertThat(request).isNotNull();
    assertThat(request.getResourceId()).isEqualTo("resId");
    assertThat(request.getScalableDimension()).isEqualTo("scalDim");
    assertThat(request.getServiceNamespace()).isEqualTo("ecs");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testDeleteAutoScalarForNewService() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(any());
    ContextData data = ContextData.builder()
                           .awsConfig(AwsConfig.builder().build())
                           .resizeParams(anEcsResizeParams()
                                             .withRegion("us-east-1")
                                             .withContainerServiceName("foo__1")
                                             .withClusterName("cluster")
                                             .withPreviousEcsAutoScalarsAlreadyRemoved(false)
                                             .withPreviousAwsAutoScalarConfigs(singletonList(
                                                 AwsAutoScalarConfig.builder().scalableTargetJson("ScalTJson").build()))
                                             .build())
                           .build();
    String resId = "resId";
    doReturn(resId).when(mockEcsCommandTaskHelper).getResourceIdForEcsService(any(), any());
    DescribeScalableTargetsResult result = new DescribeScalableTargetsResult().withScalableTargets(
        new ScalableTarget().withResourceId("resId").withScalableDimension("scalDim"));
    doReturn(result).when(mockAwsAppAutoScalingService).listScalableTargets(any(), any(), any(), any());
    helper.deleteAutoScalarForNewService(data, mockCallback);
    ArgumentCaptor<DeregisterScalableTargetRequest> captor =
        ArgumentCaptor.forClass(DeregisterScalableTargetRequest.class);
    verify(mockAwsAppAutoScalingService).deregisterScalableTarget(any(), any(), any(), captor.capture());
    DeregisterScalableTargetRequest request = captor.getValue();
    assertThat(request).isNotNull();
    assertThat(request.getResourceId()).isEqualTo("resId");
    assertThat(request.getScalableDimension()).isEqualTo("scalDim");
    assertThat(request.getServiceNamespace()).isEqualTo("ecs");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testRestoreAutoScalarConfigs() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(any());
    ContextData data = ContextData.builder()
                           .awsConfig(AwsConfig.builder().build())
                           .resizeParams(anEcsResizeParams()
                                             .withRollback(true)
                                             .withRegion("us-east-1")
                                             .withContainerServiceName("foo__1")
                                             .withClusterName("cluster")
                                             .withPreviousEcsAutoScalarsAlreadyRemoved(false)
                                             .withPreviousAwsAutoScalarConfigs(
                                                 singletonList(AwsAutoScalarConfig.builder()
                                                                   .scalableTargetJson("ScalTJson")
                                                                   .scalingPolicyJson(new String[] {"ScalPJson"})
                                                                   .build()))
                                             .build())
                           .build();
    doNothing().when(helper).deleteAutoScalarForNewService(any(), any());
    ScalableTarget target = new ScalableTarget().withResourceId("resId").withScalableDimension("scalDim");
    doReturn(target).when(mockAwsAppAutoScalingService).getScalableTargetFromJson(any());
    DescribeScalableTargetsResult result = new DescribeScalableTargetsResult();
    doReturn(result).when(mockAwsAppAutoScalingService).listScalableTargets(any(), any(), any(), any());
    helper.restoreAutoScalarConfigs(data, mock(ContainerServiceData.class), mockCallback);
    verify(mockEcsCommandTaskHelper).registerScalableTargetForEcsService(any(), any(), any(), any(), any(), any());
    verify(mockEcsCommandTaskHelper)
        .upsertScalingPolicyIfRequired(any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testCreateAutoScalarConfigIfServiceReachedMaxSize() {
    ContextData data = ContextData.builder()
                           .awsConfig(AwsConfig.builder().build())
                           .resizeParams(anEcsResizeParams()
                                             .withRegion("us-east-1")
                                             .withContainerServiceName("foo__1")
                                             .withClusterName("cluster")
                                             .withUseFixedInstances(true)
                                             .withFixedInstances(2)
                                             .withAwsAutoScalarConfigForNewService(
                                                 singletonList(AwsAutoScalarConfig.builder()
                                                                   .scalableTargetJson("ScalTJson")
                                                                   .scalingPolicyJson(new String[] {"ScalPJson"})
                                                                   .build()))
                                             .build())
                           .build();
    ContainerServiceData containerServiceData = ContainerServiceData.builder().name("foo__1").desiredCount(2).build();
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(any());
    String resId = "resId";
    doReturn(resId).when(mockEcsCommandTaskHelper).getResourceIdForEcsService(any(), any());
    ScalableTarget target = new ScalableTarget().withResourceId("resId").withScalableDimension("scalDim");
    doReturn(target).when(mockAwsAppAutoScalingService).getScalableTargetFromJson(any());
    helper.createAutoScalarConfigIfServiceReachedMaxSize(data, containerServiceData, mockCallback);
    ArgumentCaptor<ScalableTarget> captor = ArgumentCaptor.forClass(ScalableTarget.class);
    verify(mockEcsCommandTaskHelper)
        .registerScalableTargetForEcsService(any(), any(), any(), any(), any(), captor.capture());
    ScalableTarget value = captor.getValue();
    assertThat(value).isNotNull();
    assertThat(value.getResourceId()).isEqualTo("resId");
    verify(mockEcsContainerService, times(1))
        .waitForServiceToReachStableState(any(), any(), any(), any(), any(), any(), anyInt());
    verify(mockEcsCommandTaskHelper)
        .upsertScalingPolicyIfRequired(any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetDeployingToHundredPercent() {
    EcsResizeParams resizeParams = anEcsResizeParams()
                                       .withRollback(false)
                                       .withInstanceUnitType(COUNT)
                                       .withUseFixedInstances(true)
                                       .withInstanceCount(2)
                                       .withFixedInstances(2)
                                       .build();
    assertThat(helper.getDeployingToHundredPercent(resizeParams)).isTrue();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetNewInstanceData() {
    ContextData data = ContextData.builder()
                           .resizeParams(anEcsResizeParams()
                                             .withRegion("us-east-1")
                                             .withClusterName("cluster")
                                             .withContainerServiceName("foo__1")
                                             .withInstanceCount(2)
                                             .withUseFixedInstances(true)
                                             .withFixedInstances(2)
                                             .withInstanceUnitType(COUNT)
                                             .build())
                           .build();
    doReturn(Optional.of(new Service().withServiceName("foo__1").withDesiredCount(1)))
        .when(mockAwsClusterService)
        .getService(any(), any(), any(), any(), any());
    ContainerServiceData instanceData = helper.getNewInstanceData(data, mock(ExecutionLogCallback.class));
    assertThat(instanceData).isNotNull();
    assertThat(instanceData.getDesiredCount()).isEqualTo(2);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetOldInstanceData() {
    ContextData data = ContextData.builder()
                           .resizeParams(anEcsResizeParams()
                                             .withRegion("us-east-1")
                                             .withClusterName("cluster")
                                             .withContainerServiceName("foo__3")
                                             .withInstanceCount(2)
                                             .withUseFixedInstances(true)
                                             .withFixedInstances(2)
                                             .withInstanceUnitType(COUNT)
                                             .withDownsizeInstanceCount(0)
                                             .build())
                           .build();
    ContainerServiceData newServiceData = ContainerServiceData.builder().name("foo__3").previousCount(0).build();
    LinkedHashMap<String, Integer> oldActiverServiceCounts = new LinkedHashMap<>();
    oldActiverServiceCounts.put("foo__1", 1);
    oldActiverServiceCounts.put("foo__2", 1);
    oldActiverServiceCounts.put("foo__3", 0);
    doReturn(oldActiverServiceCounts)
        .when(mockAwsClusterService)
        .getActiveServiceCounts(any(), any(), any(), any(), any());
    Map<String, String> imageMap = new HashMap<>();
    imageMap.put("foo__1", "img__1");
    imageMap.put("foo__2", "img__2");
    imageMap.put("foo__3", "img__3");
    doReturn(imageMap).when(mockAwsClusterService).getActiveServiceImages(any(), any(), any(), any(), any(), any());
    List<ContainerServiceData> oldInstanceData = helper.getOldInstanceData(data, newServiceData);
    assertThat(oldInstanceData).isNotNull();
    assertThat(oldInstanceData.size()).isEqualTo(2);
    assertThat(oldInstanceData.get(0).getDesiredCount()).isEqualTo(0);
    assertThat(oldInstanceData.get(1).getDesiredCount()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListOfStringArrayToMap() {
    Map<String, Integer> map =
        helper.listOfStringArrayToMap(newArrayList(new String[] {"foo__1", "1"}, new String[] {"foo__2", "2"}));
    assertThat(map).isNotNull();
    assertThat(map.size()).isEqualTo(2);
    assertThat(map.get("foo__1")).isEqualTo(1);
    assertThat(map.get("foo__2")).isEqualTo(2);
  }

  @Test
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void testRegisterRunTaskDefinitionWithRegisterTaskDefinitionRequest() {
    doReturn(null).when(mockAwsClusterService).createTask(any(), any(), any(), any());
    ExecutionLogCallback mockExecutionLogCallback = mock(ExecutionLogCallback.class);

    ArgumentCaptor<RegisterTaskDefinitionRequest> registerTaskDefinitionRequestArgumentCaptor =
        ArgumentCaptor.forClass(RegisterTaskDefinitionRequest.class);

    // with empty executionRoleArn and FARGATE launchType
    RegisterTaskDefinitionRequest registerTaskDefinitionRequest = new RegisterTaskDefinitionRequest();
    registerTaskDefinitionRequest.withExecutionRoleArn("");
    String launchType = "FARGATE";
    helper.registerRunTaskDefinitionWithRegisterTaskDefinitionRequest(
        null, registerTaskDefinitionRequest, launchType, null, null, mockExecutionLogCallback);
    verify(helper).registerRunTaskDefinitionWithRegisterTaskDefinitionRequest(
        any(), registerTaskDefinitionRequestArgumentCaptor.capture(), any(), any(), any(), any());

    RegisterTaskDefinitionRequest registerTaskDefinitionRequestArgumentCaptorValue =
        registerTaskDefinitionRequestArgumentCaptor.getValue();
    assertThat(registerTaskDefinitionRequestArgumentCaptorValue.getExecutionRoleArn()).isEqualTo(null);
    assertThat(registerTaskDefinitionRequest.getNetworkMode()).isEqualTo(NetworkMode.Awsvpc.toString());
    assertThat(registerTaskDefinitionRequest.getRequiresCompatibilities()).contains("FARGATE");

    // with non empty executionRoleArn and non FARGATE launchType
    registerTaskDefinitionRequest.withExecutionRoleArn("executionRoleArn");
    registerTaskDefinitionRequest.withCpu("cpu");
    registerTaskDefinitionRequest.withMemory("memory");
    helper.registerRunTaskDefinitionWithRegisterTaskDefinitionRequest(
        null, registerTaskDefinitionRequest, null, null, null, mockExecutionLogCallback);
    verify(helper, times(2))
        .registerRunTaskDefinitionWithRegisterTaskDefinitionRequest(
            any(), registerTaskDefinitionRequestArgumentCaptor.capture(), any(), any(), any(), any());

    registerTaskDefinitionRequestArgumentCaptorValue = registerTaskDefinitionRequestArgumentCaptor.getValue();
    assertThat(registerTaskDefinitionRequestArgumentCaptorValue.getExecutionRoleArn()).isEqualTo("executionRoleArn");
    assertThat(registerTaskDefinitionRequest.getCpu()).isEqualTo(null);
    assertThat(registerTaskDefinitionRequest.getMemory()).isEqualTo(null);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testEmptyEcsDeployRollbackDataFetchResponse() {
    EcsDeployRollbackDataFetchResponse response = helper.getEmptyEcsDeployRollbackDataFetchResponse();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(response.getOutput()).isEqualTo(StringUtils.EMPTY);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testGetDownsizeByAmount() {
    assertThat(helper.getDownsizeByAmount(ContextData.builder().blueGreen(true).build(), 15, 5, 0)).isEqualTo(0);
    assertThat(
        helper.getDownsizeByAmount(ContextData.builder()
                                       .resizeParams(anEcsResizeParams()
                                                         .withDownsizeInstanceCount(100)
                                                         .withFixedInstances(5)
                                                         .withUseFixedInstances(true)
                                                         .withDownsizeInstanceUnitType(InstanceUnitType.PERCENTAGE)
                                                         .build())
                                       .build(),
            15, 5, 0))
        .isEqualTo(10);
  }
  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testGetDownsizeByAmountifTargetInstancesZero() {
    assertThat(helper.getDownsizeByAmount(ContextData.builder().blueGreen(true).build(), 15, 5, 0)).isEqualTo(0);
    assertThat(
        helper.getDownsizeByAmount(ContextData.builder()
                                       .blueGreen(false)
                                       .resizeParams(anEcsResizeParams()
                                                         .withDownsizeInstanceCount(100)
                                                         .withFixedInstances(0)
                                                         .withUseFixedInstances(true)
                                                         .withDownsizeInstanceUnitType(InstanceUnitType.PERCENTAGE)
                                                         .build())
                                       .build(),
            15, 0, 0))
        .isEqualTo(0);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testGetServiceDesiredCount() {
    EcsResizeParams resizeParams = anEcsResizeParams()
                                       .withRegion("us-east-1")
                                       .withPreviousEcsAutoScalarsAlreadyRemoved(false)
                                       .withPreviousAwsAutoScalarConfigs(singletonList(
                                           AwsAutoScalarConfig.builder().scalableTargetJson("ScalTJson").build()))
                                       .withContainerServiceName(SERVICE_NAME)
                                       .build();
    ContextData data = ContextData.builder()
                           .awsConfig(AwsConfig.builder().build())
                           .resizeParams(resizeParams)
                           .encryptedDataDetails(new ArrayList<>())
                           .build();

    doReturn(Optional.of(new Service().withDesiredCount(12)))
        .when(mockAwsClusterService)
        .getService(resizeParams.getRegion(), data.getSettingAttribute(), data.getEncryptedDataDetails(),
            resizeParams.getClusterName(), resizeParams.getContainerServiceName());

    Optional<Integer> serviceDesiredCount = helper.getServiceDesiredCount(data);
    assertThat(serviceDesiredCount).isEqualTo(Optional.of(12));
    verify(mockAwsClusterService)
        .getService(resizeParams.getRegion(), data.getSettingAttribute(), data.getEncryptedDataDetails(),
            resizeParams.getClusterName(), resizeParams.getContainerServiceName());

    doReturn(Optional.empty())
        .when(mockAwsClusterService)
        .getService(resizeParams.getRegion(), data.getSettingAttribute(), data.getEncryptedDataDetails(),
            resizeParams.getClusterName(), resizeParams.getContainerServiceName());

    serviceDesiredCount = helper.getServiceDesiredCount(data);
    assertThat(serviceDesiredCount).isEqualTo(Optional.empty());
  }
}
