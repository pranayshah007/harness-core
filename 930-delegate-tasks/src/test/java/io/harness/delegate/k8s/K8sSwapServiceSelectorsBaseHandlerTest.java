/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s;

import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.PRATYUSH;
import static io.harness.rule.OwnerRule.PUNEET;
import static io.harness.rule.OwnerRule.TARUN_UBA;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.K8sSwapServiceSelectorsRequest;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.delegate.task.k8s.KustomizeManifestDelegateConfig;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.KubernetesTaskException;
import io.harness.helpers.k8s.releasehistory.K8sReleaseHandler;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.exception.KubernetesExceptionExplanation;
import io.harness.k8s.exception.KubernetesExceptionHints;
import io.harness.k8s.exception.KubernetesExceptionMessages;
import io.harness.k8s.model.HarnessLabelValues;
import io.harness.k8s.model.HarnessLabels;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.releasehistory.IK8sRelease;
import io.harness.k8s.releasehistory.IK8sReleaseHistory;
import io.harness.k8s.releasehistory.K8sLegacyRelease;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceBuilder;
import io.kubernetes.client.openapi.models.V1ServiceSpecBuilder;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class K8sSwapServiceSelectorsBaseHandlerTest extends CategoryTest {
  @Mock private KubernetesContainerService kubernetesContainerService;
  @Mock LogCallback logCallback;
  @Mock K8sInfraDelegateConfig k8sInfraDelegateConfig;
  @Mock K8sReleaseHandler releaseHandler;
  @Mock K8sTaskHelperBase k8sTaskHelperBase;
  @Mock ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;
  @Mock KubernetesConfig kubernetesConfig;
  @Mock ILogStreamingTaskClient logStreamingTaskClient;
  @Mock IK8sReleaseHistory releaseHistory;
  @Mock K8sBGBaseHandler k8sBGBaseHandler;
  @Spy @InjectMocks private K8sSwapServiceSelectorsBaseHandler k8sSwapServiceSelectorsBaseHandler;
  @InjectMocks private K8sSwapServiceSelectorsHandler k8sSwapServiceSelectorsHandler;

  K8sDelegateTaskParams k8sDelegateTaskParams;
  CommandUnitsProgress commandUnitsProgress;
  final String workingDirectory = "/tmp";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    k8sDelegateTaskParams = K8sDelegateTaskParams.builder().workingDirectory(workingDirectory).build();
    commandUnitsProgress = CommandUnitsProgress.builder().build();
    doReturn(logCallback)
        .when(k8sTaskHelperBase)
        .getLogCallback(eq(logStreamingTaskClient), anyString(), anyBoolean(), eq(commandUnitsProgress));
    doReturn(kubernetesConfig)
        .when(containerDeploymentDelegateBaseHelper)
        .createKubernetesConfig(k8sInfraDelegateConfig, workingDirectory, logCallback);
    doReturn(releaseHandler).when(k8sTaskHelperBase).getReleaseHandler(anyBoolean());
    doReturn(releaseHistory).when(releaseHandler).getReleaseHistory(any(), any());
  }

  private V1Service createService(String serviceName, Map<String, String> labelSelectors) {
    V1ServiceSpecBuilder spec = new V1ServiceSpecBuilder().withSelector(labelSelectors);

    return new V1ServiceBuilder().withNewMetadata().withName(serviceName).endMetadata().withSpec(spec.build()).build();
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void shouldSwapServicesIfErrorFrameworkNotSupported() {
    V1Service service1 = createService("service1", ImmutableMap.of("label", "A"));
    V1Service service2 = createService("service2", ImmutableMap.of("label", "B"));

    when(kubernetesContainerService.getService(any(), eq(service1.getMetadata().getName()))).thenReturn(service1);
    when(kubernetesContainerService.getService(any(), eq(service2.getMetadata().getName()))).thenReturn(service2);
    when(kubernetesContainerService.createOrReplaceService(any(), any()))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArguments()[1]);

    boolean success =
        k8sSwapServiceSelectorsBaseHandler.swapServiceSelectors(null, "service1", "service2", logCallback);
    assertThat(success).isTrue();

    ArgumentCaptor<V1Service> serviceArgumentCaptor = ArgumentCaptor.forClass(V1Service.class);

    verify(kubernetesContainerService, times(2)).getService(any(), any());

    verify(kubernetesContainerService, times(2)).createOrReplaceService(eq(null), serviceArgumentCaptor.capture());

    V1Service updatedService1 = serviceArgumentCaptor.getAllValues().get(0);
    assertThat(updatedService1.getMetadata().getName()).isEqualTo("service1");
    assertThat(updatedService1.getSpec().getSelector().get("label")).isEqualTo("B");

    V1Service updatedService2 = serviceArgumentCaptor.getAllValues().get(1);
    assertThat(updatedService2.getMetadata().getName()).isEqualTo("service2");
    assertThat(updatedService2.getSpec().getSelector().get("label")).isEqualTo("A");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldSwapServicesIfErrorFrameworkSupported() {
    V1Service service1 = createService("service1", ImmutableMap.of("label", "A"));
    V1Service service2 = createService("service2", ImmutableMap.of("label", "B"));

    when(kubernetesContainerService.getService(any(), eq(service1.getMetadata().getName()))).thenReturn(service1);
    when(kubernetesContainerService.getService(any(), eq(service2.getMetadata().getName()))).thenReturn(service2);
    when(kubernetesContainerService.createOrReplaceService(any(), any()))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArguments()[1]);

    k8sSwapServiceSelectorsBaseHandler.swapServiceSelectors(null, "service1", "service2", logCallback, true);

    ArgumentCaptor<V1Service> serviceArgumentCaptor = ArgumentCaptor.forClass(V1Service.class);

    verify(kubernetesContainerService, times(2)).getService(any(), any());

    verify(kubernetesContainerService, times(2)).createOrReplaceService(eq(null), serviceArgumentCaptor.capture());

    V1Service updatedService1 = serviceArgumentCaptor.getAllValues().get(0);
    assertThat(updatedService1.getMetadata().getName()).isEqualTo("service1");
    assertThat(updatedService1.getSpec().getSelector().get("label")).isEqualTo("B");

    V1Service updatedService2 = serviceArgumentCaptor.getAllValues().get(1);
    assertThat(updatedService2.getMetadata().getName()).isEqualTo("service2");
    assertThat(updatedService2.getSpec().getSelector().get("label")).isEqualTo("A");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldThrowExceptionIfPrimaryNotFound() {
    V1Service service1 = createService("service1", ImmutableMap.of("label", "A"));
    when(kubernetesContainerService.getService(any(), eq(service1.getMetadata().getName()))).thenReturn(null);

    assertThatThrownBy(
        () -> k8sSwapServiceSelectorsBaseHandler.swapServiceSelectors(null, "service1", "service2", logCallback, true))
        .matches(throwable -> {
          HintException hint = ExceptionUtils.cause(HintException.class, throwable);
          ExplanationException explanation = ExceptionUtils.cause(ExplanationException.class, throwable);
          KubernetesTaskException taskException = ExceptionUtils.cause(KubernetesTaskException.class, throwable);
          assertThat(hint).hasMessageContaining(KubernetesExceptionHints.BG_SWAP_SERVICES_SERVICE_NOT_FOUND);
          assertThat(explanation)
              .hasMessageContaining(
                  format(KubernetesExceptionExplanation.BG_SWAP_SERVICES_SERVICE_NOT_FOUND, "service1"));
          assertThat(taskException)
              .hasMessageContaining(
                  format(KubernetesExceptionMessages.BG_SWAP_SERVICES_FAILED, "service1", "service2"));
          return true;
        });
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldReturnFalseWhenErrorFrameworkDisabledIfPrimaryNotFound() {
    V1Service service1 = createService("service1", ImmutableMap.of("label", "A"));
    when(kubernetesContainerService.getService(any(), eq(service1.getMetadata().getName()))).thenReturn(null);

    boolean success =
        k8sSwapServiceSelectorsBaseHandler.swapServiceSelectors(null, "service1", "service2", logCallback);
    assertThat(success).isFalse();
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldThrowExceptionIfStageNotFound() {
    V1Service service1 = createService("service1", ImmutableMap.of("label", "A"));
    V1Service service2 = createService("service2", ImmutableMap.of("label", "B"));

    when(kubernetesContainerService.getService(any(), eq(service1.getMetadata().getName()))).thenReturn(service1);
    when(kubernetesContainerService.getService(any(), eq(service2.getMetadata().getName()))).thenReturn(null);

    assertThatThrownBy(
        () -> k8sSwapServiceSelectorsBaseHandler.swapServiceSelectors(null, "service1", "service2", logCallback, true))
        .matches(throwable -> {
          HintException hint = ExceptionUtils.cause(HintException.class, throwable);
          ExplanationException explanation = ExceptionUtils.cause(ExplanationException.class, throwable);
          KubernetesTaskException taskException = ExceptionUtils.cause(KubernetesTaskException.class, throwable);
          assertThat(hint).hasMessageContaining(KubernetesExceptionHints.BG_SWAP_SERVICES_SERVICE_NOT_FOUND);
          assertThat(explanation)
              .hasMessageContaining(
                  format(KubernetesExceptionExplanation.BG_SWAP_SERVICES_SERVICE_NOT_FOUND, "service2"));
          assertThat(taskException)
              .hasMessageContaining(
                  format(KubernetesExceptionMessages.BG_SWAP_SERVICES_FAILED, "service1", "service2"));
          return true;
        });
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldReturnFalseWhenErrorFrameworkDisabledIfStageNotFound() {
    V1Service service1 = createService("service1", ImmutableMap.of("label", "A"));
    V1Service service2 = createService("service2", ImmutableMap.of("label", "B"));

    when(kubernetesContainerService.getService(any(), eq(service1.getMetadata().getName()))).thenReturn(service1);
    when(kubernetesContainerService.getService(any(), eq(service2.getMetadata().getName()))).thenReturn(null);

    boolean success =
        k8sSwapServiceSelectorsBaseHandler.swapServiceSelectors(null, "service1", "service2", logCallback);
    assertThat(success).isFalse();
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void shouldSwapBgEnvironmentLabel() throws Exception {
    V1Service service1 = createService("service1", ImmutableMap.of(HarnessLabels.color, HarnessLabelValues.colorBlue));
    V1Service service2 = createService("service2", ImmutableMap.of(HarnessLabels.color, HarnessLabelValues.colorGreen));
    final K8sSwapServiceSelectorsRequest k8sSwapServiceSelectorsRequest =
        K8sSwapServiceSelectorsRequest.builder()
            .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
            .manifestDelegateConfig(KustomizeManifestDelegateConfig.builder().build())
            .releaseName("releaseName")
            .useDeclarativeRollback(false)
            .service1(service1.getMetadata().getName())
            .service2(service2.getMetadata().getName())
            .build();
    K8sLegacyRelease k8sLegacyRelease = K8sLegacyRelease.builder()
                                            .status(IK8sRelease.Status.Succeeded)
                                            .bgEnvironment(HarnessLabelValues.bgStageEnv)
                                            .build();
    K8sLegacyRelease k8sLegacyReleasePrimary = K8sLegacyRelease.builder()
                                                   .status(IK8sRelease.Status.Succeeded)
                                                   .bgEnvironment(HarnessLabelValues.bgPrimaryEnv)
                                                   .build();

    doReturn(k8sLegacyReleasePrimary)
        .when(releaseHistory)
        .getLatestSuccessfulReleaseMatchingColor(HarnessLabelValues.colorGreen);
    doReturn(k8sLegacyRelease)
        .when(releaseHistory)
        .getLatestSuccessfulReleaseMatchingColor(HarnessLabelValues.colorBlue);
    doReturn(HarnessLabelValues.colorBlue).when(k8sBGBaseHandler).getInverseColor(HarnessLabelValues.colorGreen);
    doReturn(HarnessLabelValues.colorGreen).when(k8sBGBaseHandler).getInverseColor(HarnessLabelValues.colorBlue);
    when(kubernetesContainerService.getService(any(), eq(service1.getMetadata().getName()))).thenReturn(service1);
    when(kubernetesContainerService.getService(any(), eq(service2.getMetadata().getName()))).thenReturn(service2);
    when(kubernetesContainerService.createOrReplaceService(any(), any()))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArguments()[1]);

    K8sDeployResponse response = k8sSwapServiceSelectorsHandler.executeTaskInternal(
        k8sSwapServiceSelectorsRequest, k8sDelegateTaskParams, logStreamingTaskClient, commandUnitsProgress);

    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    verify(k8sTaskHelperBase, times(1)).saveRelease(anyBoolean(), anyBoolean(), any(), any(), any(), eq("releaseName"));
    assertThat(k8sLegacyRelease.getBgEnvironment()).isEqualTo(HarnessLabelValues.bgPrimaryEnv);
    assertThat(k8sLegacyReleasePrimary.getBgEnvironment()).isEqualTo(HarnessLabelValues.bgStageEnv);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void shouldSwapBgEnvironmentLabelDeclarativeRollback() throws Exception {
    V1Service service1 = createService("service1", ImmutableMap.of(HarnessLabels.color, HarnessLabelValues.colorBlue));
    V1Service service2 = createService("service2", ImmutableMap.of(HarnessLabels.color, HarnessLabelValues.colorGreen));
    final K8sSwapServiceSelectorsRequest k8sSwapServiceSelectorsRequest =
        K8sSwapServiceSelectorsRequest.builder()
            .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
            .manifestDelegateConfig(KustomizeManifestDelegateConfig.builder().build())
            .releaseName("releaseName")
            .useDeclarativeRollback(true)
            .service1(service1.getMetadata().getName())
            .service2(service2.getMetadata().getName())
            .build();
    K8sLegacyRelease k8sLegacyRelease = K8sLegacyRelease.builder()
                                            .status(IK8sRelease.Status.Succeeded)
                                            .bgEnvironment(HarnessLabelValues.bgStageEnv)
                                            .build();
    K8sLegacyRelease k8sLegacyReleasePrimary = K8sLegacyRelease.builder()
                                                   .status(IK8sRelease.Status.Succeeded)
                                                   .bgEnvironment(HarnessLabelValues.bgPrimaryEnv)
                                                   .build();

    doReturn(k8sLegacyReleasePrimary)
        .when(releaseHistory)
        .getLatestSuccessfulReleaseMatchingColor(HarnessLabelValues.colorGreen);
    doReturn(k8sLegacyRelease)
        .when(releaseHistory)
        .getLatestSuccessfulReleaseMatchingColor(HarnessLabelValues.colorBlue);
    doReturn(HarnessLabelValues.colorBlue).when(k8sBGBaseHandler).getInverseColor(HarnessLabelValues.colorGreen);
    doReturn(HarnessLabelValues.colorGreen).when(k8sBGBaseHandler).getInverseColor(HarnessLabelValues.colorBlue);
    when(kubernetesContainerService.getService(any(), eq(service1.getMetadata().getName()))).thenReturn(service1);
    when(kubernetesContainerService.getService(any(), eq(service2.getMetadata().getName()))).thenReturn(service2);
    when(kubernetesContainerService.createOrReplaceService(any(), any()))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArguments()[1]);

    K8sDeployResponse response = k8sSwapServiceSelectorsHandler.executeTaskInternal(
        k8sSwapServiceSelectorsRequest, k8sDelegateTaskParams, logStreamingTaskClient, commandUnitsProgress);

    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    verify(k8sTaskHelperBase, times(2)).saveRelease(anyBoolean(), anyBoolean(), any(), any(), any(), eq("releaseName"));
    assertThat(k8sLegacyRelease.getBgEnvironment()).isEqualTo(HarnessLabelValues.bgPrimaryEnv);
    assertThat(k8sLegacyReleasePrimary.getBgEnvironment()).isEqualTo(HarnessLabelValues.bgStageEnv);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void shouldThrowExceptionIfPrimaryNotFoundFork8sSwapServiceSelectorsHandler() {
    V1Service service1 = createService("service1", ImmutableMap.of(HarnessLabels.color, HarnessLabelValues.colorBlue));
    V1Service service2 = createService("service2", ImmutableMap.of(HarnessLabels.color, HarnessLabelValues.colorGreen));
    final K8sSwapServiceSelectorsRequest k8sSwapServiceSelectorsRequest =
        K8sSwapServiceSelectorsRequest.builder()
            .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
            .manifestDelegateConfig(KustomizeManifestDelegateConfig.builder().build())
            .releaseName("releaseName")
            .useDeclarativeRollback(true)
            .service1(service1.getMetadata().getName())
            .service2(service2.getMetadata().getName())
            .build();

    when(kubernetesContainerService.getService(any(), eq(service1.getMetadata().getName()))).thenReturn(null);

    assertThatThrownBy(()
                           -> k8sSwapServiceSelectorsHandler.executeTaskInternal(k8sSwapServiceSelectorsRequest,
                               k8sDelegateTaskParams, logStreamingTaskClient, commandUnitsProgress))
        .matches(throwable -> {
          HintException hint = ExceptionUtils.cause(HintException.class, throwable);
          ExplanationException explanation = ExceptionUtils.cause(ExplanationException.class, throwable);
          KubernetesTaskException taskException = ExceptionUtils.cause(KubernetesTaskException.class, throwable);
          assertThat(hint).hasMessageContaining(KubernetesExceptionHints.BG_SWAP_SERVICES_SERVICE_NOT_FOUND);
          assertThat(explanation)
              .hasMessageContaining(
                  format(KubernetesExceptionExplanation.BG_SWAP_SERVICES_SERVICE_NOT_FOUND, "service1"));
          assertThat(taskException)
              .hasMessageContaining(
                  format(KubernetesExceptionMessages.BG_SWAP_SERVICES_FAILED, "service1", "service2"));
          return true;
        });
  }
}
