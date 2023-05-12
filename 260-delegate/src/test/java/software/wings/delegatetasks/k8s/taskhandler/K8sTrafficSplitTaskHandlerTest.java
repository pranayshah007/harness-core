/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.k8s.taskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.BOJANA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.InvalidArgumentsException;
import io.harness.helpers.k8s.releasehistory.K8sReleaseHandler;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.model.HarnessAnnotations;
import io.harness.k8s.model.IstioDestinationWeight;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sExpressions;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.releasehistory.IK8sRelease;
import io.harness.k8s.releasehistory.IK8sReleaseHistory;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.delegatetasks.k8s.K8sTaskHelper;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.helpers.ext.k8s.request.K8sTrafficSplitTaskParameters;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskResponse;

import io.fabric8.istio.api.networking.v1alpha3.VirtualService;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class K8sTrafficSplitTaskHandlerTest extends WingsBaseTest {
  @Mock private K8sTaskHelper k8sTaskHelper;
  @Mock private ExecutionLogCallback executionLogCallback;
  @Mock private KubernetesContainerService kubernetesContainerService;
  @Mock private ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;
  @Mock private K8sTaskHelperBase k8sTaskHelperBase;
  @Mock K8sReleaseHandler releaseHandler;
  @Mock IK8sReleaseHistory releaseHistory;
  @InjectMocks private K8sTrafficSplitTaskHandler k8sTrafficSplitTaskHandler;

  private static final String RELEASE_NAME = "releaseName";
  private static final String VIRTUAL_SERVICE = "virtualService";

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    doReturn(KubernetesConfig.builder().build())
        .when(containerDeploymentDelegateHelper)
        .getKubernetesConfig(any(K8sClusterConfig.class), anyBoolean());
    when(k8sTaskHelperBase.getReleaseHandler(anyBoolean())).thenReturn(releaseHandler);
    when(releaseHandler.getReleaseHistory(any(), any())).thenReturn(releaseHistory);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testNullGetManagedVirtualServiceResources() throws Exception {
    K8sTrafficSplitTaskParameters k8sTrafficSplitTaskParams =
        K8sTrafficSplitTaskParameters.builder()
            .releaseName(RELEASE_NAME)
            .virtualServiceName(K8sExpressions.virtualServiceNameExpression)
            .build();

    IK8sRelease latestRelease = mock(IK8sRelease.class);
    KubernetesConfig kubernetesConfig = KubernetesConfig.builder().build();

    on(k8sTrafficSplitTaskHandler).set("kubernetesConfig", kubernetesConfig);

    when(containerDeploymentDelegateHelper.getKubernetesConfig(any(K8sClusterConfig.class), anyBoolean()))
        .thenReturn(KubernetesConfig.builder().build());
    when(releaseHistory.isEmpty()).thenReturn(false);
    when(releaseHistory.getLatestRelease()).thenReturn(latestRelease);
    when(latestRelease.getResourceIds())
        .thenReturn(List.of(
            KubernetesResourceId.builder().kind("VirtualService").name(VIRTUAL_SERVICE).namespace("default").build()));

    when(kubernetesContainerService.getFabric8IstioVirtualService(any(KubernetesConfig.class), anyString()))
        .thenReturn(null);

    boolean status = k8sTrafficSplitTaskHandler.init(k8sTrafficSplitTaskParams, executionLogCallback);
    ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<LogLevel> logLevelCaptor = ArgumentCaptor.forClass(LogLevel.class);
    ArgumentCaptor<CommandExecutionStatus> commandExecutionStatusCaptor =
        ArgumentCaptor.forClass(CommandExecutionStatus.class);
    verify(executionLogCallback, times(2))
        .saveExecutionLog(msgCaptor.capture(), logLevelCaptor.capture(), commandExecutionStatusCaptor.capture());
    assertThat(status).isFalse();
    assertThat(logLevelCaptor.getValue()).isEqualTo(ERROR);
    assertThat(commandExecutionStatusCaptor.getValue()).isEqualTo(FAILURE);
    assertThat(msgCaptor.getAllValues().get(0)).isEqualTo("Error evaluating expression ${k8s.virtualServiceName}");
    assertThat(msgCaptor.getAllValues().get(1))
        .isEqualTo("\n"
            + "No managed VirtualService found. Atleast one VirtualService should be present and marked with annotation harness.io/managed: true");
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void invalidTypeOfTaskParams() {
    assertThatExceptionOfType(InvalidArgumentsException.class)
        .isThrownBy(() -> k8sTrafficSplitTaskHandler.executeTaskInternal(null, null))
        .withMessageContaining("INVALID_ARGUMENT");
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void initBasedOnCustomVirtualServiceNameFail() {
    K8sTrafficSplitTaskParameters k8sTrafficSplitTaskParams = K8sTrafficSplitTaskParameters.builder()
                                                                  .k8sClusterConfig(K8sClusterConfig.builder().build())
                                                                  .virtualServiceName("customVirtualServiceName")
                                                                  .build();

    boolean status = k8sTrafficSplitTaskHandler.init(k8sTrafficSplitTaskParams, executionLogCallback);
    verify(containerDeploymentDelegateHelper, times(1)).getKubernetesConfig(any(K8sClusterConfig.class), anyBoolean());
    verify(kubernetesContainerService, times(1))
        .getFabric8IstioVirtualService(any(KubernetesConfig.class), anyString());
    assertThat(status).isFalse();
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void applySuccess() {
    K8sTrafficSplitTaskHandler handler = Mockito.spy(k8sTrafficSplitTaskHandler);
    doReturn(true).when(handler).init(any(K8sTrafficSplitTaskParameters.class), any(ExecutionLogCallback.class));

    final K8sTaskExecutionResponse response = handler.executeTaskInternal(
        K8sTrafficSplitTaskParameters.builder().k8sClusterConfig(K8sClusterConfig.builder().build()).build(),
        K8sDelegateTaskParams.builder().build());
    verify(handler, times(1)).init(any(K8sTrafficSplitTaskParameters.class), any(ExecutionLogCallback.class));
    verify(containerDeploymentDelegateHelper, times(1)).getKubernetesConfig(any(K8sClusterConfig.class), anyBoolean());
    verify(k8sTaskHelper, times(1))
        .getK8sTaskExecutionResponse(any(K8sTaskResponse.class), any(CommandExecutionStatus.class));
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void printDestinationWeights() throws Exception {
    K8sTrafficSplitTaskParameters k8sTrafficSplitTaskParams =
        K8sTrafficSplitTaskParameters.builder()
            .k8sClusterConfig(K8sClusterConfig.builder().build())
            .virtualServiceName(K8sExpressions.virtualServiceNameExpression)
            .istioDestinationWeights(Arrays.asList(new IstioDestinationWeight()))
            .build();
    when(releaseHandler.getReleaseHistory(any(), any())).thenReturn(null);
    k8sTrafficSplitTaskHandler.executeTaskInternal(k8sTrafficSplitTaskParams, K8sDelegateTaskParams.builder().build());
    verify(containerDeploymentDelegateHelper, times(2)).getKubernetesConfig(any(K8sClusterConfig.class), anyBoolean());
    verify(releaseHandler, times(1)).getReleaseHistory(nullable(KubernetesConfig.class), nullable(String.class));
    verify(k8sTaskHelper, times(1))
        .getK8sTaskExecutionResponse(any(K8sTaskResponse.class), any(CommandExecutionStatus.class));
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void getManagedVirtualServiceResources() throws Exception {
    K8sTrafficSplitTaskParameters k8sTrafficSplitTaskParams =
        K8sTrafficSplitTaskParameters.builder()
            .k8sClusterConfig(K8sClusterConfig.builder().build())
            .virtualServiceName(K8sExpressions.virtualServiceNameExpression)
            .build();
    IK8sRelease latestRelease = mock(IK8sRelease.class);
    when(releaseHistory.isEmpty()).thenReturn(false);
    when(releaseHistory.getLatestRelease()).thenReturn(latestRelease);
    when(latestRelease.getResourceIds())
        .thenReturn(List.of(
            KubernetesResourceId.builder().kind("VirtualService").name(VIRTUAL_SERVICE).namespace("default").build()));
    VirtualService istioVirtualService = mock(VirtualService.class);
    ObjectMeta metadata = mock(ObjectMeta.class);
    Map<String, String> annotations = new HashMap<>();
    annotations.put(HarnessAnnotations.managed, "true");
    when(istioVirtualService.getMetadata()).thenReturn(metadata);
    when(metadata.getAnnotations()).thenReturn(annotations);
    when(kubernetesContainerService.getFabric8IstioVirtualService(any(KubernetesConfig.class), anyString()))
        .thenReturn(istioVirtualService);
    boolean status = k8sTrafficSplitTaskHandler.init(k8sTrafficSplitTaskParams, executionLogCallback);
    verify(containerDeploymentDelegateHelper, times(1)).getKubernetesConfig(any(K8sClusterConfig.class), anyBoolean());
    verify(releaseHandler, times(1)).getReleaseHistory(nullable(KubernetesConfig.class), nullable(String.class));
    verify(kubernetesContainerService, times(2))
        .getFabric8IstioVirtualService(any(KubernetesConfig.class), anyString());
    assertThat(status).isTrue();
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testInitBasedOnDefaultVirtualServiceName() throws IOException {
    K8sTrafficSplitTaskParameters k8sTrafficSplitTaskParams =
        K8sTrafficSplitTaskParameters.builder()
            .releaseName(RELEASE_NAME)
            .virtualServiceName(K8sExpressions.virtualServiceNameExpression)
            .build();

    KubernetesConfig kubernetesConfig = KubernetesConfig.builder().build();

    on(k8sTrafficSplitTaskHandler).set("kubernetesConfig", kubernetesConfig);

    when(containerDeploymentDelegateHelper.getKubernetesConfig(any(K8sClusterConfig.class), anyBoolean()))
        .thenReturn(KubernetesConfig.builder().build());
    when(releaseHistory.isEmpty()).thenReturn(false);
    when(releaseHistory.getLatestRelease()).thenReturn(null);
    when(kubernetesContainerService.getFabric8IstioVirtualService(nullable(KubernetesConfig.class), any()))
        .thenReturn(null);

    boolean status = k8sTrafficSplitTaskHandler.init(k8sTrafficSplitTaskParams, executionLogCallback);
    assertThat(status).isTrue();

    ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<LogLevel> logLevelCaptor = ArgumentCaptor.forClass(LogLevel.class);
    ArgumentCaptor<CommandExecutionStatus> commandExecutionStatusCaptor =
        ArgumentCaptor.forClass(CommandExecutionStatus.class);
    verify(executionLogCallback, times(2))
        .saveExecutionLog(msgCaptor.capture(), logLevelCaptor.capture(), commandExecutionStatusCaptor.capture());

    assertThat(logLevelCaptor.getValue()).isEqualTo(INFO);
    assertThat(commandExecutionStatusCaptor.getValue()).isEqualTo(SUCCESS);

    verify(executionLogCallback, times(4)).saveExecutionLog(msgCaptor.capture());
    assertThat(msgCaptor.getAllValues()).contains("\nNo resources found in release history");
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testInitMoreThanOneVirtualServiceFound() throws IOException {
    K8sTrafficSplitTaskParameters k8sTrafficSplitTaskParams =
        K8sTrafficSplitTaskParameters.builder()
            .releaseName(RELEASE_NAME)
            .virtualServiceName(K8sExpressions.virtualServiceNameExpression)
            .build();

    IK8sRelease latestRelease = mock(IK8sRelease.class);
    when(releaseHistory.isEmpty()).thenReturn(false);
    when(releaseHistory.getLatestRelease()).thenReturn(latestRelease);
    when(latestRelease.getResourceIds())
        .thenReturn(List.of(
            KubernetesResourceId.builder().kind("VirtualService").name(VIRTUAL_SERVICE).namespace("default").build(),
            KubernetesResourceId.builder().kind("VirtualService").build()));

    VirtualService istioVirtualService = mock(VirtualService.class);
    ObjectMeta metadata = mock(ObjectMeta.class);
    Map<String, String> annotations = new HashMap<>();
    annotations.put(HarnessAnnotations.managed, "true");
    when(istioVirtualService.getMetadata()).thenReturn(metadata);
    when(metadata.getAnnotations()).thenReturn(annotations);
    when(kubernetesContainerService.getFabric8IstioVirtualService(
             nullable(KubernetesConfig.class), nullable(String.class)))
        .thenReturn(istioVirtualService);

    boolean status = k8sTrafficSplitTaskHandler.init(k8sTrafficSplitTaskParams, executionLogCallback);
    assertThat(status).isFalse();

    ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<LogLevel> logLevelCaptor = ArgumentCaptor.forClass(LogLevel.class);
    ArgumentCaptor<CommandExecutionStatus> commandExecutionStatusCaptor =
        ArgumentCaptor.forClass(CommandExecutionStatus.class);
    verify(executionLogCallback, times(2))
        .saveExecutionLog(msgCaptor.capture(), logLevelCaptor.capture(), commandExecutionStatusCaptor.capture());
    assertThat(logLevelCaptor.getValue()).isEqualTo(ERROR);
    assertThat(commandExecutionStatusCaptor.getValue()).isEqualTo(FAILURE);
    assertThat(msgCaptor.getAllValues())
        .contains("Error evaluating expression ${k8s.virtualServiceName}",
            "\nMore than one VirtualService found.  Only one VirtualService can be marked with annotation harness.io/managed: true");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldGetReleaseDataUsingK8sClient() throws Exception {
    K8sTrafficSplitTaskParameters k8sTrafficSplitTaskParams =
        K8sTrafficSplitTaskParameters.builder()
            .k8sClusterConfig(K8sClusterConfig.builder().build())
            .virtualServiceName(K8sExpressions.virtualServiceNameExpression)
            .build();
    IK8sRelease latestRelease = mock(IK8sRelease.class);
    when(releaseHistory.isEmpty()).thenReturn(false);
    when(releaseHistory.getLatestRelease()).thenReturn(latestRelease);
    when(latestRelease.getResourceIds())
        .thenReturn(List.of(
            KubernetesResourceId.builder().kind("VirtualService").name(VIRTUAL_SERVICE).namespace("default").build()));
    VirtualService istioVirtualService = mock(VirtualService.class);
    ObjectMeta metadata = mock(ObjectMeta.class);
    Map<String, String> annotations = new HashMap<>();
    annotations.put(HarnessAnnotations.managed, "true");
    when(istioVirtualService.getMetadata()).thenReturn(metadata);
    when(metadata.getAnnotations()).thenReturn(annotations);
    when(kubernetesContainerService.getFabric8IstioVirtualService(any(KubernetesConfig.class), anyString()))
        .thenReturn(istioVirtualService);

    k8sTrafficSplitTaskHandler.init(k8sTrafficSplitTaskParams, executionLogCallback);
    verify(releaseHandler, times(1)).getReleaseHistory(nullable(KubernetesConfig.class), nullable(String.class));
  }
}
