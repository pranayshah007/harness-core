/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.k8s.K8sTestHelper.deployment;
import static io.harness.delegate.k8s.K8sTestHelper.service;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.rule.OwnerRule.ABHINAV2;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FileData;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.k8s.K8sBGDeployRequest;
import io.harness.delegate.task.k8s.K8sBGDeployResponse;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.K8sManifestDelegateConfig;
import io.harness.delegate.task.k8s.K8sRollingDeployRequest;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.delegate.task.k8s.KustomizeManifestDelegateConfig;
import io.harness.delegate.task.k8s.ManifestDelegateConfig;
import io.harness.delegate.task.k8s.client.K8sClient;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.KubernetesTaskException;
import io.harness.exception.KubernetesYamlException;
import io.harness.helpers.k8s.releasehistory.K8sReleaseHandler;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.KubernetesReleaseDetails;
import io.harness.k8s.exception.KubernetesExceptionExplanation;
import io.harness.k8s.exception.KubernetesExceptionHints;
import io.harness.k8s.exception.KubernetesExceptionMessages;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.model.HarnessAnnotations;
import io.harness.k8s.model.HarnessLabelValues;
import io.harness.k8s.model.HarnessLabels;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.K8sSteadyStateDTO;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.releasehistory.IK8sReleaseHistory;
import io.harness.k8s.releasehistory.K8SLegacyReleaseHistory;
import io.harness.k8s.releasehistory.K8sLegacyRelease;
import io.harness.k8s.releasehistory.K8sRelease;
import io.harness.k8s.releasehistory.ReleaseHistory;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import io.kubernetes.client.openapi.models.V1Secret;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(CDP)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class K8sBGRequestHandlerTest extends CategoryTest {
  @Mock ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;
  @Mock K8sTaskHelperBase k8sTaskHelperBase;
  @Mock KubernetesContainerService kubernetesContainerService;

  @Mock LogCallback logCallback;
  @Mock ILogStreamingTaskClient logStreamingTaskClient;
  @Mock KubernetesConfig kubernetesConfig;
  @Mock K8sInfraDelegateConfig k8sInfraDelegateConfig;
  @Mock K8sReleaseHandler releaseHandler;
  @Mock IK8sReleaseHistory releaseHistory;
  @Mock K8sRelease release;

  @Spy @InjectMocks K8sBGBaseHandler k8sBGBaseHandler;
  @Spy @InjectMocks K8sBGRequestHandler k8sBGRequestHandler;

  K8sDelegateTaskParams k8sDelegateTaskParams;
  CommandUnitsProgress commandUnitsProgress;
  final String workingDirectory = "/tmp";

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);

    k8sDelegateTaskParams = K8sDelegateTaskParams.builder().workingDirectory(workingDirectory).build();
    commandUnitsProgress = CommandUnitsProgress.builder().build();

    doReturn(logCallback)
        .when(k8sTaskHelperBase)
        .getLogCallback(eq(logStreamingTaskClient), anyString(), anyBoolean(), eq(commandUnitsProgress));
    doReturn(true)
        .when(k8sTaskHelperBase)
        .fetchManifestFilesAndWriteToDirectory(
            any(ManifestDelegateConfig.class), anyString(), eq(logCallback), anyLong(), anyString());
    doReturn(true)
        .when(k8sTaskHelperBase)
        .applyManifests(
            any(Kubectl.class), anyList(), eq(k8sDelegateTaskParams), eq(logCallback), anyBoolean(), eq(null));
    doReturn(true)
        .when(k8sTaskHelperBase)
        .doStatusCheck(any(Kubectl.class), any(KubernetesResourceId.class), eq(k8sDelegateTaskParams), eq(logCallback));

    doReturn(true)
        .when(k8sTaskHelperBase)
        .dryRunManifests(any(Kubectl.class), anyList(), eq(k8sDelegateTaskParams), eq(logCallback), anyBoolean());

    doReturn(kubernetesConfig)
        .when(containerDeploymentDelegateBaseHelper)
        .createKubernetesConfig(any(K8sInfraDelegateConfig.class), anyString(), eq(logCallback));

    on(k8sBGRequestHandler).set("useDeclarativeRollback", true);
    doReturn(releaseHandler).when(k8sTaskHelperBase).getReleaseHandler(anyBoolean());
    doReturn(releaseHistory).when(releaseHandler).getReleaseHistory(any(), anyString());
    doReturn(10).when(releaseHistory).getAndIncrementLastReleaseNumber();
    doReturn(release).when(releaseHandler).createRelease(any(), anyInt());
    doReturn(release).when(releaseHistory).getLatestRelease();
    doReturn(HarnessLabelValues.colorDefault).when(release).getReleaseColor();
    doReturn(new V1Secret()).when(release).getReleaseSecret();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteTaskInternal() throws Exception {
    final List<K8sPod> deployedPods = Collections.singletonList(K8sPod.builder().build());
    final K8sBGDeployRequest k8sBGDeployRequest =
        K8sBGDeployRequest.builder()
            .skipResourceVersioning(true)
            .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
            .manifestDelegateConfig(KustomizeManifestDelegateConfig.builder().build())
            .releaseName("releaseName")
            .useDeclarativeRollback(true)
            .build();
    K8sClient k8sClient = mock(K8sClient.class);
    doReturn(k8sClient).when(k8sTaskHelperBase).getKubernetesClient(anyBoolean());
    doReturn(true).when(k8sClient).performSteadyStateCheck(any(K8sSteadyStateDTO.class));
    doReturn(HarnessLabelValues.colorBlue)
        .when(k8sBGBaseHandler)
        .getPrimaryColor(any(KubernetesResource.class), eq(kubernetesConfig), eq(logCallback));
    doReturn(new ArrayList<>(asList(deployment(), service())))
        .when(k8sTaskHelperBase)
        .readManifests(anyList(), eq(logCallback), eq(true));
    doReturn(deployedPods)
        .when(k8sBGBaseHandler)
        .getAllPods(anyLong(), eq(kubernetesConfig), any(KubernetesResource.class), eq(HarnessLabelValues.colorBlue),
            eq(HarnessLabelValues.colorGreen), eq("releaseName"));

    K8sDeployResponse response = k8sBGRequestHandler.executeTaskInternal(
        k8sBGDeployRequest, k8sDelegateTaskParams, logStreamingTaskClient, commandUnitsProgress);

    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getK8sNGTaskResponse()).isNotNull();
    K8sBGDeployResponse bgDeployResponse = (K8sBGDeployResponse) response.getK8sNGTaskResponse();
    assertThat(bgDeployResponse.getPrimaryColor()).isEqualTo(HarnessLabelValues.colorBlue);
    assertThat(bgDeployResponse.getStageColor()).isEqualTo(HarnessLabelValues.colorGreen);
    assertThat(bgDeployResponse.getPrimaryServiceName()).isEqualTo("my-service");
    assertThat(bgDeployResponse.getStageServiceName()).isEqualTo("my-service-stage");
    assertThat(bgDeployResponse.getK8sPodList()).isEqualTo(deployedPods);
    verify(k8sBGRequestHandler)
        .getManifestOverrideFlies(k8sBGDeployRequest,
            KubernetesReleaseDetails.builder()
                .releaseNumber(10)
                .color(k8sBGBaseHandler.getInverseColor(HarnessLabelValues.colorDefault))
                .build()
                .toContextMap());
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalLegacyReleaseImplementation() throws Exception {
    final List<K8sPod> deployedPods = Collections.singletonList(K8sPod.builder().build());
    final K8sBGDeployRequest k8sBGDeployRequest =
        K8sBGDeployRequest.builder()
            .skipResourceVersioning(true)
            .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
            .manifestDelegateConfig(KustomizeManifestDelegateConfig.builder().build())
            .releaseName("releaseName")
            .useDeclarativeRollback(false)
            .build();
    K8SLegacyReleaseHistory releaseHistory = mock(K8SLegacyReleaseHistory.class);
    ReleaseHistory releaseHistoryContent = mock(ReleaseHistory.class);
    doReturn(releaseHistory).when(releaseHandler).getReleaseHistory(any(), any());
    doReturn(1).when(releaseHistory).getAndIncrementLastReleaseNumber();
    doReturn(releaseHistoryContent).when(releaseHistory).getReleaseHistory();
    K8sLegacyRelease legacyRelease = mock(K8sLegacyRelease.class);
    doReturn(legacyRelease).when(releaseHistoryContent).addReleaseToReleaseHistory(any());
    doReturn(legacyRelease).when(releaseHandler).createRelease(any(), anyInt());
    K8sClient k8sClient = mock(K8sClient.class);
    doReturn(k8sClient).when(k8sTaskHelperBase).getKubernetesClient(anyBoolean());
    doReturn(true).when(k8sClient).performSteadyStateCheck(any(K8sSteadyStateDTO.class));
    doReturn(HarnessLabelValues.colorBlue)
        .when(k8sBGBaseHandler)
        .getPrimaryColor(any(KubernetesResource.class), eq(kubernetesConfig), eq(logCallback));
    doReturn(new ArrayList<>(asList(deployment(), service())))
        .when(k8sTaskHelperBase)
        .readManifests(anyList(), eq(logCallback), eq(true));
    doReturn(deployedPods)
        .when(k8sBGBaseHandler)
        .getAllPods(anyLong(), eq(kubernetesConfig), any(KubernetesResource.class), eq(HarnessLabelValues.colorBlue),
            eq(HarnessLabelValues.colorGreen), eq("releaseName"));

    K8sDeployResponse response = k8sBGRequestHandler.executeTaskInternal(
        k8sBGDeployRequest, k8sDelegateTaskParams, logStreamingTaskClient, commandUnitsProgress);

    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getK8sNGTaskResponse()).isNotNull();
    K8sBGDeployResponse bgDeployResponse = (K8sBGDeployResponse) response.getK8sNGTaskResponse();
    assertThat(bgDeployResponse.getPrimaryColor()).isEqualTo(HarnessLabelValues.colorBlue);
    assertThat(bgDeployResponse.getStageColor()).isEqualTo(HarnessLabelValues.colorGreen);
    assertThat(bgDeployResponse.getPrimaryServiceName()).isEqualTo("my-service");
    assertThat(bgDeployResponse.getStageServiceName()).isEqualTo("my-service-stage");
    assertThat(bgDeployResponse.getK8sPodList()).isEqualTo(deployedPods);

    verify(legacyRelease).setManagedWorkload(any());
    verify(legacyRelease, times(2)).setManagedWorkloadRevision(any());
    verify(k8sBGRequestHandler)
        .getManifestOverrideFlies(
            k8sBGDeployRequest, KubernetesReleaseDetails.builder().releaseNumber(1).build().toContextMap());
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalFFPruneOn() throws Exception {
    final List<K8sPod> deployedPods = Collections.singletonList(K8sPod.builder().build());
    final K8sBGDeployRequest k8sBGDeployRequest =
        K8sBGDeployRequest.builder()
            .skipResourceVersioning(true)
            .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
            .manifestDelegateConfig(KustomizeManifestDelegateConfig.builder().build())
            .pruningEnabled(true)
            .useDeclarativeRollback(true)
            .releaseName("releaseName")
            .build();
    K8sClient k8sClient = mock(K8sClient.class);
    doReturn(k8sClient).when(k8sTaskHelperBase).getKubernetesClient(anyBoolean());
    doReturn(true).when(k8sClient).performSteadyStateCheck(any(K8sSteadyStateDTO.class));
    doReturn(HarnessLabelValues.colorBlue)
        .when(k8sBGBaseHandler)
        .getPrimaryColor(any(KubernetesResource.class), eq(kubernetesConfig), eq(logCallback));
    doReturn(new ArrayList<>(asList(deployment(), service())))
        .when(k8sTaskHelperBase)
        .readManifests(anyList(), eq(logCallback), eq(true));
    doReturn(deployedPods)
        .when(k8sBGBaseHandler)
        .getAllPods(anyLong(), eq(kubernetesConfig), any(KubernetesResource.class), eq(HarnessLabelValues.colorBlue),
            eq(HarnessLabelValues.colorGreen), eq("releaseName"));

    K8sDeployResponse response = k8sBGRequestHandler.executeTaskInternal(
        k8sBGDeployRequest, k8sDelegateTaskParams, logStreamingTaskClient, commandUnitsProgress);

    verify(k8sBGBaseHandler, times(1)).pruneForBg(any(), any(), anyString(), anyString(), any(), any(), any());
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getK8sNGTaskResponse()).isNotNull();
    K8sBGDeployResponse bgDeployResponse = (K8sBGDeployResponse) response.getK8sNGTaskResponse();
    assertThat(bgDeployResponse.getPrimaryColor()).isEqualTo(HarnessLabelValues.colorBlue);
    assertThat(bgDeployResponse.getStageColor()).isEqualTo(HarnessLabelValues.colorGreen);
    assertThat(bgDeployResponse.getPrimaryServiceName()).isEqualTo("my-service");
    assertThat(bgDeployResponse.getStageServiceName()).isEqualTo("my-service-stage");
    assertThat(bgDeployResponse.getK8sPodList()).isEqualTo(deployedPods);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalInvalidRequest() {
    final K8sRollingDeployRequest rollingDeployRequest = K8sRollingDeployRequest.builder().build();

    assertThatThrownBy(()
                           -> k8sBGRequestHandler.executeTaskInternal(rollingDeployRequest, k8sDelegateTaskParams,
                               logStreamingTaskClient, commandUnitsProgress))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalFetchManifestFilesAndWriteToDirectoryFailed() throws Exception {
    final K8sBGDeployRequest k8sBGDeployRequest =
        K8sBGDeployRequest.builder()
            .manifestDelegateConfig(KustomizeManifestDelegateConfig.builder().build())
            .accountId("id")
            .skipResourceVersioning(true)
            .releaseName("releaseName")
            .build();
    final RuntimeException thrownException = new RuntimeException("failed");

    doThrow(thrownException)
        .when(k8sTaskHelperBase)
        .fetchManifestFilesAndWriteToDirectory(
            any(ManifestDelegateConfig.class), anyString(), eq(logCallback), anyLong(), anyString(), anyBoolean());

    assertThatThrownBy(()
                           -> k8sBGRequestHandler.executeTaskInternal(
                               k8sBGDeployRequest, k8sDelegateTaskParams, logStreamingTaskClient, commandUnitsProgress))
        .isSameAs(thrownException);

    verify(k8sBGRequestHandler, never())
        .init(any(K8sBGDeployRequest.class), any(K8sDelegateTaskParams.class), any(LogCallback.class));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalInitFailed() throws Exception {
    final K8sBGDeployRequest k8sBGDeployRequest =
        K8sBGDeployRequest.builder().skipResourceVersioning(true).releaseName("releaseName").build();
    final RuntimeException runtimeException = new RuntimeException("failed");

    doThrow(runtimeException).when(k8sBGRequestHandler).init(k8sBGDeployRequest, k8sDelegateTaskParams, logCallback);

    assertThatThrownBy(()
                           -> k8sBGRequestHandler.executeTaskInternal(
                               k8sBGDeployRequest, k8sDelegateTaskParams, logStreamingTaskClient, commandUnitsProgress))
        .isSameAs(runtimeException);
    verify(k8sBGRequestHandler, never())
        .prepareForBlueGreen(any(K8sDelegateTaskParams.class), any(LogCallback.class), anyBoolean(), anyBoolean());
    verify(k8sTaskHelperBase, never())
        .saveRelease(anyBoolean(), anyBoolean(), any(KubernetesConfig.class), any(), any(), anyString());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalPrepareForBlueGreenFailed() throws Exception {
    final K8sBGDeployRequest k8sBGDeployRequest =
        K8sBGDeployRequest.builder().skipResourceVersioning(true).releaseName("releaseName").build();
    final RuntimeException thrownException = new RuntimeException("failed");

    doNothing().when(k8sBGRequestHandler).init(k8sBGDeployRequest, k8sDelegateTaskParams, logCallback);
    doThrow(thrownException)
        .when(k8sBGRequestHandler)
        .prepareForBlueGreen(k8sDelegateTaskParams, logCallback, true, false);

    assertThatThrownBy(()
                           -> k8sBGRequestHandler.executeTaskInternal(
                               k8sBGDeployRequest, k8sDelegateTaskParams, logStreamingTaskClient, commandUnitsProgress))
        .isSameAs(thrownException);

    verify(k8sTaskHelperBase, never())
        .saveRelease(anyBoolean(), anyBoolean(), any(KubernetesConfig.class), any(), any(), anyString());
    verify(k8sTaskHelperBase, never())
        .applyManifests(any(Kubectl.class), anyList(), any(K8sDelegateTaskParams.class), any(LogCallback.class),
            anyBoolean(), eq(null));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalApplyManifestFailed() throws Exception {
    final K8sBGDeployRequest k8sBGDeployRequest =
        K8sBGDeployRequest.builder()
            .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
            .skipResourceVersioning(true)
            .manifestDelegateConfig(KustomizeManifestDelegateConfig.builder().build())
            .releaseName("releaseName")
            .useDeclarativeRollback(true)
            .build();
    final RuntimeException exception = new RuntimeException("failed");

    doReturn(HarnessLabelValues.colorBlue)
        .when(k8sBGBaseHandler)
        .getPrimaryColor(any(KubernetesResource.class), eq(kubernetesConfig), eq(logCallback));
    doReturn(new ArrayList<>(asList(deployment(), service())))
        .when(k8sTaskHelperBase)
        .readManifests(anyList(), eq(logCallback), eq(true));

    doThrow(exception)
        .when(k8sTaskHelperBase)
        .applyManifests(
            any(Kubectl.class), anyList(), eq(k8sDelegateTaskParams), eq(logCallback), eq(true), eq(true), any());

    assertThatThrownBy(()
                           -> k8sBGRequestHandler.executeTaskInternal(
                               k8sBGDeployRequest, k8sDelegateTaskParams, logStreamingTaskClient, commandUnitsProgress))
        .isSameAs(exception);

    assertThatCode(() -> k8sBGRequestHandler.handleTaskFailure(k8sBGDeployRequest, exception))
        .doesNotThrowAnyException();

    verify(k8sTaskHelperBase)
        .saveRelease(anyBoolean(), anyBoolean(), any(KubernetesConfig.class), any(), any(), eq("releaseName"));
    verify(k8sTaskHelperBase)
        .applyManifests(
            any(Kubectl.class), anyList(), eq(k8sDelegateTaskParams), eq(logCallback), eq(true), eq(true), any());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalStatusCheckFailed() throws Exception {
    final K8sBGDeployRequest k8sBGDeployRequest =
        K8sBGDeployRequest.builder()
            .skipResourceVersioning(true)
            .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
            .manifestDelegateConfig(KustomizeManifestDelegateConfig.builder().build())
            .releaseName("releaseName")
            .useDeclarativeRollback(true)
            .build();
    final RuntimeException thrownException = new RuntimeException();
    K8sClient k8sClient = mock(K8sClient.class);

    doReturn(HarnessLabelValues.colorBlue)
        .when(k8sBGBaseHandler)
        .getPrimaryColor(any(KubernetesResource.class), eq(kubernetesConfig), eq(logCallback));
    doReturn(new ArrayList<>(asList(deployment(), service())))
        .when(k8sTaskHelperBase)
        .readManifests(anyList(), eq(logCallback), eq(true));

    doReturn(true)
        .when(k8sTaskHelperBase)
        .applyManifests(
            any(Kubectl.class), anyList(), eq(k8sDelegateTaskParams), eq(logCallback), eq(true), eq(true), eq(null));
    doReturn(k8sClient).when(k8sTaskHelperBase).getKubernetesClient(anyBoolean());
    doThrow(thrownException).when(k8sClient).performSteadyStateCheck(any(K8sSteadyStateDTO.class));
    assertThatThrownBy(()
                           -> k8sBGRequestHandler.executeTaskInternal(
                               k8sBGDeployRequest, k8sDelegateTaskParams, logStreamingTaskClient, commandUnitsProgress))
        .isSameAs(thrownException);

    assertThatCode(() -> k8sBGRequestHandler.handleTaskFailure(k8sBGDeployRequest, thrownException))
        .doesNotThrowAnyException();

    verify(k8sTaskHelperBase, times(2))
        .saveRelease(anyBoolean(), anyBoolean(), any(KubernetesConfig.class), any(), any(), eq("releaseName"));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testInit() throws Exception {
    testInit(false, false);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testInitSkipDryRun() throws Exception {
    testInit(true, false);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testInitFailed() throws Exception {
    testInit(false, true);
  }

  private void testInit(boolean skipDryRun, boolean throwException) throws Exception {
    final K8sManifestDelegateConfig k8sManifestDelegateConfig = K8sManifestDelegateConfig.builder().build();
    final KubernetesConfig kubernetesConfig = KubernetesConfig.builder().namespace("default").build();
    final List<FileData> renderedFiles = Collections.singletonList(FileData.builder().build());
    final List<String> valuesYamlFiles = Collections.singletonList("values");
    final List<KubernetesResource> resources = new ArrayList<>(asList(deployment(), service()));
    final K8sBGDeployRequest k8sBGDeployRequest = K8sBGDeployRequest.builder()
                                                      .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                                      .manifestDelegateConfig(k8sManifestDelegateConfig)
                                                      .releaseName("releaseName")
                                                      .timeoutIntervalInMin(10)
                                                      .valuesYamlList(valuesYamlFiles)
                                                      .skipDryRun(skipDryRun)
                                                      .build();
    final InvalidRequestException thrownException = new InvalidRequestException("failed");

    doReturn(kubernetesConfig)
        .when(containerDeploymentDelegateBaseHelper)
        .createKubernetesConfig(k8sInfraDelegateConfig, workingDirectory, logCallback);
    doReturn(renderedFiles)
        .when(k8sTaskHelperBase)
        .renderTemplate(eq(k8sDelegateTaskParams), eq(k8sManifestDelegateConfig), any(), eq(valuesYamlFiles), any(),
            eq("default"), eq(logCallback), eq(10));

    if (throwException) {
      doThrow(thrownException).when(k8sTaskHelperBase).readManifests(renderedFiles, logCallback, true);
    } else {
      doReturn(resources).when(k8sTaskHelperBase).readManifests(renderedFiles, logCallback, true);
    }

    if (throwException) {
      assertThatThrownBy(() -> k8sBGRequestHandler.init(k8sBGDeployRequest, k8sDelegateTaskParams, logCallback))
          .isSameAs(thrownException);
    } else {
      k8sBGRequestHandler.init(k8sBGDeployRequest, k8sDelegateTaskParams, logCallback);
    }

    verify(containerDeploymentDelegateBaseHelper)
        .createKubernetesConfig(k8sInfraDelegateConfig, workingDirectory, logCallback);
    verify(releaseHandler).getReleaseHistory(any(), eq("releaseName"));

    if (!throwException) {
      verify(k8sTaskHelperBase).setNamespaceToKubernetesResourcesIfRequired(resources, "default");
      verify(k8sTaskHelperBase, skipDryRun ? never() : times(1))
          .dryRunManifests(
              any(Kubectl.class), eq(resources), eq(k8sDelegateTaskParams), eq(logCallback), eq(true), anyBoolean());
    }
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testPrepareForBlueGreen() throws Exception {
    final List<KubernetesResource> resources = new ArrayList<>(asList(deployment(), service()));
    final IK8sReleaseHistory releaseHistory = mock(IK8sReleaseHistory.class);
    final Kubectl client = Kubectl.client("", "");

    on(k8sBGRequestHandler).set("resources", resources);
    on(k8sBGRequestHandler).set("releaseName", "releaseName");
    on(k8sBGRequestHandler).set("releaseHistory", releaseHistory);
    on(k8sBGRequestHandler).set("client", client);

    k8sBGRequestHandler.prepareForBlueGreen(k8sDelegateTaskParams, logCallback, false, false);

    verify(k8sBGBaseHandler)
        .cleanupForBlueGreen(eq(k8sDelegateTaskParams), eq(releaseHistory), eq(logCallback),
            eq(HarnessLabelValues.colorGreen), eq(HarnessLabelValues.colorBlue), any(), eq(client), any(),
            eq("releaseName"), anyBoolean());

    KubernetesResource primaryService = on(k8sBGRequestHandler).get("primaryService");
    KubernetesResource stageService = on(k8sBGRequestHandler).get("stageService");

    assertThat(primaryService.getResourceId().getName()).isEqualTo("my-service");
    assertResourceColor(primaryService, HarnessLabelValues.colorGreen);
    assertThat(stageService.getResourceId().getName()).isEqualTo("my-service-stage");
    assertResourceColor(stageService, HarnessLabelValues.colorBlue);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testPrepareForBlueGreenFFPruneOn() throws Exception {
    final List<KubernetesResource> resources = new ArrayList<>(asList(deployment(), service()));
    IK8sReleaseHistory releaseHistory = mock(IK8sReleaseHistory.class);
    final Kubectl client = Kubectl.client("", "");

    on(k8sBGRequestHandler).set("resources", resources);
    on(k8sBGRequestHandler).set("releaseName", "releaseName");
    on(k8sBGRequestHandler).set("releaseHistory", releaseHistory);
    on(k8sBGRequestHandler).set("client", client);

    doReturn(release).when(releaseHandler).createRelease(any(), anyInt());

    k8sBGRequestHandler.prepareForBlueGreen(k8sDelegateTaskParams, logCallback, false, true);

    verify(k8sBGBaseHandler)
        .cleanupForBlueGreen(eq(k8sDelegateTaskParams), eq(releaseHistory), eq(logCallback),
            eq(HarnessLabelValues.colorGreen), eq(HarnessLabelValues.colorBlue), any(), eq(client), any(),
            eq("releaseName"), anyBoolean());

    KubernetesResource primaryService = on(k8sBGRequestHandler).get("primaryService");
    KubernetesResource stageService = on(k8sBGRequestHandler).get("stageService");

    assertThat(primaryService.getResourceId().getName()).isEqualTo("my-service");
    assertResourceColor(primaryService, HarnessLabelValues.colorGreen);
    assertThat(stageService.getResourceId().getName()).isEqualTo("my-service-stage");
    assertResourceColor(stageService, HarnessLabelValues.colorBlue);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testPrepareForBlueGreenEmptyWorkloads() throws Exception {
    on(k8sBGRequestHandler).set("resources", emptyList());

    assertThatThrownBy(() -> k8sBGRequestHandler.prepareForBlueGreen(k8sDelegateTaskParams, logCallback, false, false))
        .matches(throwable -> {
          HintException hint = ExceptionUtils.cause(HintException.class, throwable);
          ExplanationException explanation = ExceptionUtils.cause(ExplanationException.class, throwable);
          KubernetesTaskException taskException = ExceptionUtils.cause(KubernetesTaskException.class, throwable);
          assertThat(hint).hasMessageContaining(KubernetesExceptionHints.BG_NO_WORKLOADS_FOUND);
          assertThat(explanation).hasMessageContaining(KubernetesExceptionExplanation.BG_NO_WORKLOADS_FOUND);
          assertThat(taskException).hasMessageContaining(KubernetesExceptionMessages.NO_WORKLOADS_FOUND);
          return true;
        });

    verify(logCallback)
        .saveExecutionLog(
            "\nNo workload found in the Manifests. Can't do  Blue/Green Deployment. Only Deployment, DeploymentConfig (OpenShift) and StatefulSet workloads are supported in Blue/Green workflow type.",
            ERROR, FAILURE);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testPrepareForBlueGreenMultipleWorkloads() throws Exception {
    on(k8sBGRequestHandler).set("resources", asList(deployment(), deployment(), deployment()));

    assertThatThrownBy(() -> k8sBGRequestHandler.prepareForBlueGreen(k8sDelegateTaskParams, logCallback, true, false))
        .matches(throwable -> {
          HintException hint = ExceptionUtils.cause(HintException.class, throwable);
          ExplanationException explanation = ExceptionUtils.cause(ExplanationException.class, throwable);
          KubernetesTaskException taskException = ExceptionUtils.cause(KubernetesTaskException.class, throwable);
          assertThat(hint).hasMessageContaining(KubernetesExceptionHints.BG_MULTIPLE_WORKLOADS);
          assertThat(explanation)
              .hasMessageContaining(format(KubernetesExceptionExplanation.BG_MULTIPLE_WORKLOADS, 3,
                  "Deployment/nginx-deployment, Deployment/nginx-deployment, Deployment/nginx-deployment"));
          assertThat(taskException).hasMessageContaining(KubernetesExceptionMessages.MULTIPLE_WORKLOADS);
          return true;
        });
    verify(logCallback)
        .saveExecutionLog(
            "\nThere are multiple workloads in the Service Manifests you are deploying. Blue/Green Workflows support a single Deployment, DeploymentConfig (OpenShift) or StatefulSet workload only. To deploy additional workloads in Manifests, annotate them with "
                + HarnessAnnotations.directApply + ": true",
            ERROR, FAILURE);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testPrepareBlueGreenNoServicesInManifest() throws Exception {
    on(k8sBGRequestHandler).set("resources", singletonList(deployment()));

    assertThatThrownBy(() -> k8sBGRequestHandler.prepareForBlueGreen(k8sDelegateTaskParams, logCallback, false, false))
        .matches(throwable -> {
          HintException hint = ExceptionUtils.cause(HintException.class, throwable);
          ExplanationException explanation = ExceptionUtils.cause(ExplanationException.class, throwable);
          KubernetesYamlException taskException = ExceptionUtils.cause(KubernetesYamlException.class, throwable);
          assertThat(hint).hasMessageContaining(KubernetesExceptionHints.BG_NO_SERVICE_FOUND);
          assertThat(explanation).hasMessageContaining(KubernetesExceptionExplanation.BG_NO_SERVICE_FOUND);
          assertThat(taskException.getParams().get("reason")).isEqualTo(KubernetesExceptionMessages.NO_SERVICE_FOUND);
          return true;
        });
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testPrepareBlueGreenMultipleServicesInManifest() throws Exception {
    on(k8sBGRequestHandler).set("resources", asList(deployment(), service(), service()));

    assertThatThrownBy(() -> k8sBGRequestHandler.prepareForBlueGreen(k8sDelegateTaskParams, logCallback, false, false))
        .matches(throwable -> {
          HintException hint = ExceptionUtils.cause(HintException.class, throwable);
          ExplanationException explanation = ExceptionUtils.cause(ExplanationException.class, throwable);
          KubernetesYamlException taskException = ExceptionUtils.cause(KubernetesYamlException.class, throwable);
          assertThat(hint).hasMessageContaining(KubernetesExceptionHints.BG_MULTIPLE_PRIMARY_SERVICE);
          assertThat(explanation)
              .hasMessageContaining(format(KubernetesExceptionExplanation.BG_MULTIPLE_PRIMARY_SERVICE, 2,
                  "Service/my-service, Service/my-service"));
          assertThat(taskException.getParams().get("reason")).isEqualTo(KubernetesExceptionMessages.MULTIPLE_SERVICES);
          return true;
        });
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testPrepareBlueGreenFailedToFindPrimaryColor() throws Exception {
    KubernetesResource primaryService = service();
    KubernetesConfig kubernetesConfig = KubernetesConfig.builder().build();
    on(k8sBGRequestHandler).set("resources", new ArrayList<>(asList(deployment(), primaryService)));
    on(k8sBGRequestHandler).set("kubernetesConfig", kubernetesConfig);

    doReturn(null).when(k8sBGBaseHandler).getPrimaryColor(primaryService, kubernetesConfig, logCallback);

    assertThatThrownBy(() -> k8sBGRequestHandler.prepareForBlueGreen(k8sDelegateTaskParams, logCallback, false, false))
        .matches(throwable -> {
          HintException hint = ExceptionUtils.cause(HintException.class, throwable);
          ExplanationException explanation = ExceptionUtils.cause(ExplanationException.class, throwable);
          KubernetesTaskException taskException = ExceptionUtils.cause(KubernetesTaskException.class, throwable);
          assertThat(hint).hasMessageContaining(format(KubernetesExceptionHints.BG_CONFLICTING_SERVICE, "my-service"));
          assertThat(explanation).hasMessageContaining(KubernetesExceptionExplanation.BG_CONFLICTING_SERVICE);
          assertThat(taskException)
              .hasMessageContaining(format(KubernetesExceptionMessages.BG_CONFLICTING_SERVICE, "my-service"));
          return true;
        });

    verify(logCallback)
        .saveExecutionLog(
            format(
                "Found conflicting service [%s] in the cluster. For blue/green deployment, the label [harness.io/color] is required in service selector. Delete this existing service to proceed",
                primaryService.getResourceId().getName()),
            ERROR, FAILURE);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testPrepareBlueGreenFailedToGetStageServiceInCluster() throws Exception {
    KubernetesResource primaryService = service();
    KubernetesConfig kubernetesConfig = KubernetesConfig.builder().build();
    on(k8sBGRequestHandler).set("resources", new ArrayList<>(asList(deployment(), primaryService)));
    on(k8sBGRequestHandler).set("kubernetesConfig", kubernetesConfig);
    InvalidRequestException thrownException = new InvalidRequestException("Failed to get");

    doThrow(thrownException).when(kubernetesContainerService).getService(kubernetesConfig, "my-service-stage");
    assertThatThrownBy(() -> k8sBGRequestHandler.prepareForBlueGreen(k8sDelegateTaskParams, logCallback, false, false))
        .isSameAs(thrownException);
  }

  private void assertResourceColor(KubernetesResource resource, String expected) {
    assertThat(resource.getField("spec.selector." + HarnessLabels.color.replaceAll("\\.", "[dot]")))
        .isEqualTo(expected);
  }
}
