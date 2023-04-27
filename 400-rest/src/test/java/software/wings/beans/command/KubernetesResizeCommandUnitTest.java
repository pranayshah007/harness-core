/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.BRETT;

import static software.wings.beans.InstanceUnitType.COUNT;
import static software.wings.beans.InstanceUnitType.PERCENTAGE;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.command.CommandExecutionContext.Builder.aCommandExecutionContext;
import static software.wings.beans.command.KubernetesResizeParams.KubernetesResizeParamsBuilder.aKubernetesResizeParams;
import static software.wings.utils.WingsTestConstants.CLUSTER_NAME;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.container.ContainerInfo;
import io.harness.container.ContainerInfo.Status;
import io.harness.delegate.beans.pcf.ResizeStrategy;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.ContainerServiceData;
import software.wings.beans.GcpConfig;
import software.wings.beans.InstanceUnitType;
import software.wings.beans.SettingAttribute;
import software.wings.beans.command.ContainerResizeCommandUnit.ContextData;
import software.wings.beans.command.KubernetesResizeParams.KubernetesResizeParamsBuilder;
import software.wings.cloudprovider.gke.GkeClusterService;
import software.wings.service.intfc.WorkflowService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.ReplicationControllerBuilder;
import io.fabric8.kubernetes.api.model.autoscaling.v2beta1.HorizontalPodAutoscaler;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class KubernetesResizeCommandUnitTest extends WingsBaseTest {
  @Mock private GkeClusterService gkeClusterService;
  @Mock private KubernetesContainerService kubernetesContainerService;
  @Mock private ExecutionLogCallback executionLogCallback;
  @InjectMocks @Inject private WorkflowService workflowService;

  @InjectMocks private KubernetesResizeCommandUnit kubernetesResizeCommandUnit = new KubernetesResizeCommandUnit();

  private KubernetesConfig kubernetesConfig = KubernetesConfig.builder()
                                                  .masterUrl("https://1.1.1.1/")
                                                  .username("admin".toCharArray())
                                                  .password("password".toCharArray())
                                                  .namespace("default")
                                                  .build();

  private KubernetesResizeParamsBuilder resizeParamsBuilder = aKubernetesResizeParams()
                                                                  .withClusterName(CLUSTER_NAME)
                                                                  .withNamespace("default")
                                                                  .withUseAutoscaler(false)
                                                                  .withSubscriptionId("subscriptionId")
                                                                  .withResourceGroup("resourceGroup")
                                                                  .withApiVersion("v1")
                                                                  .withUseIstioRouteRule(false)
                                                                  .withRollback(false)
                                                                  .withResizeStrategy(ResizeStrategy.RESIZE_NEW_FIRST);
  private SettingAttribute computeProvider = aSettingAttribute().withValue(GcpConfig.builder().build()).build();
  private CommandExecutionContext.Builder contextBuilder =
      aCommandExecutionContext().cloudProviderSetting(computeProvider.toDTO()).cloudProviderCredentials(emptyList());

  @Before
  public void setup() {
    when(gkeClusterService.getCluster(
             any(software.wings.beans.dto.SettingAttribute.class), eq(emptyList()), any(), any(), anyBoolean()))
        .thenReturn(kubernetesConfig);
    when(kubernetesContainerService.setControllerPodCount(
             eq(kubernetesConfig), eq(CLUSTER_NAME), any(), anyInt(), anyInt(), anyInt(), any()))
        .thenAnswer(i -> buildContainerInfos((Integer) i.getArguments()[4], (Integer) i.getArguments()[3]));
    when(kubernetesContainerService.getController(any(), any())).thenReturn(new ReplicationControllerBuilder().build());
  }

  private List<ContainerInfo> buildContainerInfos(int count, int previousCount) {
    List<ContainerInfo> containerInfos = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      containerInfos.add(ContainerInfo.builder()
                             .status(Status.SUCCESS)
                             .hostName("host" + i)
                             .containerId("c" + i)
                             .newContainer(i >= previousCount)
                             .build());
    }
    return containerInfos;
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldExecuteFail() {
    when(kubernetesContainerService.getControllerPodCount(eq(kubernetesConfig), anyString()))
        .thenReturn(Optional.empty());
    KubernetesResizeParams resizeParams = resizeParamsBuilder.but()
                                              .withContainerServiceName("rc-name-0")
                                              .withUseFixedInstances(false)
                                              .withMaxInstances(5)
                                              .withInstanceCount(100)
                                              .withInstanceUnitType(PERCENTAGE)
                                              .build();

    CommandExecutionContext context = contextBuilder.but().containerResizeParams(resizeParams).build();
    CommandExecutionStatus status = kubernetesResizeCommandUnit.execute(context);
    assertThat(status).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldUseMaxInstancesWithPercentage() {
    LinkedHashMap<String, Integer> activeServiceCounts = new LinkedHashMap<>();

    ResizeCommandUnitExecutionData executionData =
        execute(activeServiceCounts, 0, "rc-name-0", false, 5, 0, 100, PERCENTAGE);

    assertThat(executionData.getContainerInfos().size()).isEqualTo(5);
    assertThat(executionData.getNewInstanceData().size()).isEqualTo(1);
    ContainerServiceData contextNewServiceData = executionData.getNewInstanceData().get(0);
    assertThat(contextNewServiceData.getPreviousCount()).isEqualTo(0);
    assertThat(contextNewServiceData.getDesiredCount()).isEqualTo(5);

    assertThat(executionData.getOldInstanceData().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldUsePercentageOfMaxInstances() {
    LinkedHashMap<String, Integer> activeServiceCounts = new LinkedHashMap<>();

    ResizeCommandUnitExecutionData executionData =
        execute(activeServiceCounts, 0, "rc-name-0", false, 5, 0, 20, PERCENTAGE);

    assertThat(executionData.getContainerInfos().size()).isEqualTo(1);
    assertThat(executionData.getNewInstanceData().size()).isEqualTo(1);
    ContainerServiceData contextNewServiceData = executionData.getNewInstanceData().get(0);
    assertThat(contextNewServiceData.getPreviousCount()).isEqualTo(0);
    assertThat(contextNewServiceData.getDesiredCount()).isEqualTo(1);

    assertThat(executionData.getOldInstanceData().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldResizeAndDownsize() {
    LinkedHashMap<String, Integer> activeServiceCounts = new LinkedHashMap<>();
    activeServiceCounts.put("rc-name-0", 1);

    ResizeCommandUnitExecutionData executionData = execute(activeServiceCounts, 1, "rc-name-1", false, 2, 0, 2, COUNT);

    assertThat(executionData.getContainerInfos().size()).isEqualTo(1);
    assertThat(executionData.getNewInstanceData().size()).isEqualTo(1);
    ContainerServiceData contextNewServiceData = executionData.getNewInstanceData().get(0);
    assertThat(contextNewServiceData.getPreviousCount()).isEqualTo(1);
    assertThat(contextNewServiceData.getDesiredCount()).isEqualTo(2);

    assertThat(executionData.getOldInstanceData().size()).isEqualTo(1);
    ContainerServiceData contextOldServiceData = executionData.getOldInstanceData().get(0);
    assertThat(contextOldServiceData.getPreviousCount()).isEqualTo(1);
    assertThat(contextOldServiceData.getDesiredCount()).isEqualTo(0);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldDownsizeMultiple() {
    LinkedHashMap<String, Integer> activeServiceCounts = new LinkedHashMap<>();
    activeServiceCounts.put("rc-name-0", 1);
    activeServiceCounts.put("rc-name-1", 2);

    ResizeCommandUnitExecutionData executionData = execute(activeServiceCounts, 0, "rc-name-2", false, 3, 0, 3, COUNT);

    assertThat(executionData.getContainerInfos().size()).isEqualTo(3);
    assertThat(executionData.getNewInstanceData().size()).isEqualTo(1);
    ContainerServiceData contextNewServiceData = executionData.getNewInstanceData().get(0);
    assertThat(contextNewServiceData.getPreviousCount()).isEqualTo(0);
    assertThat(contextNewServiceData.getDesiredCount()).isEqualTo(3);

    assertThat(executionData.getOldInstanceData().size()).isEqualTo(2);
    ContainerServiceData contextOldServiceData = executionData.getOldInstanceData().get(0);
    assertThat(contextOldServiceData.getName()).isEqualTo("rc-name-0");
    assertThat(contextOldServiceData.getPreviousCount()).isEqualTo(1);
    assertThat(contextOldServiceData.getDesiredCount()).isEqualTo(0);
    contextOldServiceData = executionData.getOldInstanceData().get(1);
    assertThat(contextOldServiceData.getName()).isEqualTo("rc-name-1");
    assertThat(contextOldServiceData.getPreviousCount()).isEqualTo(2);
    assertThat(contextOldServiceData.getDesiredCount()).isEqualTo(0);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldUseFixedInstancesWithCount() {
    LinkedHashMap<String, Integer> activeServiceCounts = new LinkedHashMap<>();
    activeServiceCounts.put("rc-name-0", 2);
    activeServiceCounts.put("rc-name-1", 2);

    ResizeCommandUnitExecutionData executionData = execute(activeServiceCounts, 0, "rc-name-2", true, 0, 3, 3, COUNT);

    assertThat(executionData.getContainerInfos().size()).isEqualTo(3);
    assertThat(executionData.getNewInstanceData().size()).isEqualTo(1);
    ContainerServiceData contextNewServiceData = executionData.getNewInstanceData().get(0);
    assertThat(contextNewServiceData.getPreviousCount()).isEqualTo(0);
    assertThat(contextNewServiceData.getDesiredCount()).isEqualTo(3);

    assertThat(executionData.getOldInstanceData().size()).isEqualTo(2);
    ContainerServiceData contextOldServiceData = executionData.getOldInstanceData().get(0);
    assertThat(contextOldServiceData.getName()).isEqualTo("rc-name-0");
    assertThat(contextOldServiceData.getPreviousCount()).isEqualTo(2);
    assertThat(contextOldServiceData.getDesiredCount()).isEqualTo(0);
    contextOldServiceData = executionData.getOldInstanceData().get(1);
    assertThat(contextOldServiceData.getName()).isEqualTo("rc-name-1");
    assertThat(contextOldServiceData.getPreviousCount()).isEqualTo(2);
    assertThat(contextOldServiceData.getDesiredCount()).isEqualTo(0);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldCapCountAtFixed() {
    LinkedHashMap<String, Integer> activeServiceCounts = new LinkedHashMap<>();
    activeServiceCounts.put("rc-name-0", 3);

    ResizeCommandUnitExecutionData executionData = execute(activeServiceCounts, 0, "rc-name-1", true, 0, 3, 5, COUNT);

    assertThat(executionData.getContainerInfos().size()).isEqualTo(3);
    assertThat(executionData.getNewInstanceData().size()).isEqualTo(1);
    ContainerServiceData contextNewServiceData = executionData.getNewInstanceData().get(0);
    assertThat(contextNewServiceData.getPreviousCount()).isEqualTo(0);
    assertThat(contextNewServiceData.getDesiredCount()).isEqualTo(3);

    assertThat(executionData.getOldInstanceData().size()).isEqualTo(1);
    ContainerServiceData contextOldServiceData = executionData.getOldInstanceData().get(0);
    assertThat(contextOldServiceData.getName()).isEqualTo("rc-name-0");
    assertThat(contextOldServiceData.getPreviousCount()).isEqualTo(3);
    assertThat(contextOldServiceData.getDesiredCount()).isEqualTo(0);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldUseFixedInstancesWithPercentage() {
    LinkedHashMap<String, Integer> activeServiceCounts = new LinkedHashMap<>();
    activeServiceCounts.put("rc-name-0", 2);
    activeServiceCounts.put("rc-name-1", 2);

    ResizeCommandUnitExecutionData executionData =
        execute(activeServiceCounts, 0, "rc-name-2", true, 0, 3, 100, PERCENTAGE);

    assertThat(executionData.getContainerInfos().size()).isEqualTo(3);
    assertThat(executionData.getNewInstanceData().size()).isEqualTo(1);
    ContainerServiceData contextNewServiceData = executionData.getNewInstanceData().get(0);
    assertThat(contextNewServiceData.getPreviousCount()).isEqualTo(0);
    assertThat(contextNewServiceData.getDesiredCount()).isEqualTo(3);

    assertThat(executionData.getOldInstanceData().size()).isEqualTo(2);
    ContainerServiceData contextOldServiceData = executionData.getOldInstanceData().get(0);
    assertThat(contextOldServiceData.getName()).isEqualTo("rc-name-0");
    assertThat(contextOldServiceData.getPreviousCount()).isEqualTo(2);
    assertThat(contextOldServiceData.getDesiredCount()).isEqualTo(0);
    contextOldServiceData = executionData.getOldInstanceData().get(1);
    assertThat(contextOldServiceData.getName()).isEqualTo("rc-name-1");
    assertThat(contextOldServiceData.getPreviousCount()).isEqualTo(2);
    assertThat(contextOldServiceData.getDesiredCount()).isEqualTo(0);
  }

  private ResizeCommandUnitExecutionData execute(LinkedHashMap<String, Integer> activeServiceCounts,
      int controllerPodCount, String controllerName, boolean useFixedInstances, int maxInstances, int fixedInstances,
      int instanceCount, InstanceUnitType instanceUnitType) {
    when(kubernetesContainerService.getControllerPodCount(eq(kubernetesConfig), any()))
        .thenReturn(Optional.of(controllerPodCount));
    when(kubernetesContainerService.getActiveServiceCountsWithLabels(eq(kubernetesConfig), any()))
        .thenReturn(activeServiceCounts);
    KubernetesResizeParams resizeParams = resizeParamsBuilder.but()
                                              .withContainerServiceName(controllerName)
                                              .withUseFixedInstances(useFixedInstances)
                                              .withInstanceCount(instanceCount)
                                              .withInstanceUnitType(instanceUnitType)
                                              .withFixedInstances(fixedInstances)
                                              .withMaxInstances(maxInstances)
                                              .build();

    CommandExecutionContext context = contextBuilder.but().containerResizeParams(resizeParams).build();
    CommandExecutionStatus status = kubernetesResizeCommandUnit.execute(context);
    ResizeCommandUnitExecutionData executionData = (ResizeCommandUnitExecutionData) context.getCommandExecutionData();

    assertThat(status).isEqualTo(CommandExecutionStatus.SUCCESS);
    return executionData;
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testMissingVirtualServiceInResizeCommand() {
    KubernetesResizeParams resizeParams = resizeParamsBuilder.but()
                                              .withContainerServiceName("rc-name-0")
                                              .withUseFixedInstances(true)
                                              .withUseIstioRouteRule(true)
                                              .withInstanceUnitType(PERCENTAGE)
                                              .withInstanceCount(100)
                                              .withUseAutoscaler(true)
                                              .build();

    CommandExecutionContext context = contextBuilder.but().containerResizeParams(resizeParams).build();
    ContextData contextData = new ContextData(context);
    try {
      kubernetesResizeCommandUnit.postExecution(contextData, null, null);
      fail("Should not reach here.");
    } catch (Exception e) {
      assertThat(e.getMessage()).isEqualTo("Virtual Service [rc-name] not found");
      verify(kubernetesContainerService).createOrReplaceAutoscaler(any(), any());
    }
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetTrafficWeights() {
    KubernetesResizeParams resizeParams =
        resizeParamsBuilder.but().withContainerServiceName("containerServiceName").withInstanceCount(100).build();
    CommandExecutionContext context = contextBuilder.but().containerResizeParams(resizeParams).build();
    ContextData contextData = new ContextData(context);

    Map<String, Integer> trafficWeights = kubernetesResizeCommandUnit.getTrafficWeights(contextData);
    assertThat(trafficWeights).isEmpty();

    when(kubernetesContainerService.getTrafficWeights(any(KubernetesConfig.class), eq("containerServiceName")))
        .thenReturn(ImmutableMap.of("containerServiceName", 30));
    resizeParams.setUseIstioRouteRule(true);
    trafficWeights = kubernetesResizeCommandUnit.getTrafficWeights(contextData);
    assertThat(trafficWeights).containsKey("containerServiceName");
    assertThat(trafficWeights.get("containerServiceName")).isEqualTo(30);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testDeleteAutoScalarIsCalled() throws IOException {
    String yamlHPA = workflowService.getHPAYamlStringWithCustomMetric(2, 10, 60);
    HorizontalPodAutoscaler horizontalPodAutoscaler = KubernetesHelper.loadYaml(yamlHPA);
    when(kubernetesContainerService.getAutoscaler(any(KubernetesConfig.class), anyString(), anyString()))
        .thenReturn(horizontalPodAutoscaler);

    resizeParamsBuilder.withUseAutoscaler(true);
    resizeParamsBuilder.withRollback(true);

    CommandExecutionContext context = contextBuilder.but().containerResizeParams(resizeParamsBuilder.build()).build();
    ContextData contextData = new ContextData(context);
    kubernetesResizeCommandUnit.executeResize(
        contextData, ContainerServiceData.builder().name("target-name").build(), executionLogCallback);
    verify(kubernetesContainerService).deleteAutoscaler(any(KubernetesConfig.class), eq("target-name"));
  }
}
