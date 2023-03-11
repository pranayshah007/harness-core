/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.exception.WingsException.USER;
import static io.harness.k8s.releasehistory.IK8sRelease.Status.Failed;
import static io.harness.k8s.releasehistory.IK8sRelease.Status.InProgress;
import static io.harness.k8s.releasehistory.IK8sRelease.Status.Succeeded;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ACASIAN;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.k8s.K8sCanaryDeleteRequest;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.helpers.k8s.releasehistory.K8sReleaseHandler;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.releasehistory.IK8sRelease;
import io.harness.k8s.releasehistory.IK8sReleaseHistory;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import io.kubernetes.client.openapi.ApiException;
import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class K8sCanaryDeleteRequestHandlerTest extends CategoryTest {
  @Mock K8sTaskHelperBase k8sTaskHelperBase;
  @Mock K8sDeleteBaseHandler k8sDeleteBaseHandler;
  @Mock ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;

  @InjectMocks K8sCanaryDeleteRequestHandler requestHandler;

  @Mock K8sInfraDelegateConfig k8sInfraDelegateConfig;
  @Mock ILogStreamingTaskClient logStreamingTaskClient;
  @Mock LogCallback logCallback;
  @Mock K8sReleaseHandler releaseHandler;
  @Mock IK8sReleaseHistory releaseHistory;
  final String noReleaseHistory = "no-release-history";
  final String emptyReleasesHistory = "empty-releases-history";
  final String noInProgressReleaseHistory = "no-inprogress-history";
  final String canaryReleaseHistory = "canary-release-history";
  final String canaryFailedReleaseHistory = "canary-failed-release-history";
  final String canaryExceptionReleaseHistory = "canary-exception-release-history";
  final KubernetesConfig kubernetesConfig = KubernetesConfig.builder().build();
  final K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();
  final CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);

    doReturn(releaseHandler).when(k8sTaskHelperBase).getReleaseHandler(anyBoolean());
    doReturn(releaseHistory).when(releaseHandler).getReleaseHistory(any(), any());
    doReturn(kubernetesConfig)
        .when(containerDeploymentDelegateBaseHelper)
        .createKubernetesConfig(k8sInfraDelegateConfig, logCallback);

    ApiException apiException = new ApiException("Failed to get release history secret");
    InvalidRequestException exception =
        new InvalidRequestException("Failed to read release history", apiException, USER);
    doThrow(exception).when(releaseHandler).getReleaseHistory(kubernetesConfig, canaryExceptionReleaseHistory);

    doReturn(logCallback)
        .when(k8sTaskHelperBase)
        .getLogCallback(eq(logStreamingTaskClient), anyString(), anyBoolean(), eq(commandUnitsProgress));
    doReturn(
        singletonList(
            KubernetesResourceId.builder().namespace("default").kind("deployment").name("deployment-canary").build()))
        .when(k8sDeleteBaseHandler)
        .getResourceNameResourceIdsToDelete("default/deployment/deployment-canary");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDeleteByResourceId() throws Exception {
    K8sCanaryDeleteRequest deleteRequest = K8sCanaryDeleteRequest.builder()
                                               .canaryWorkloads("default/deployment/deployment-canary")
                                               .releaseName(noReleaseHistory)
                                               .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                               .build();

    K8sDeployResponse response = requestHandler.executeTaskInternal(
        deleteRequest, delegateTaskParams, logStreamingTaskClient, commandUnitsProgress);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(k8sTaskHelperBase)
        .delete(any(Kubectl.class), eq(delegateTaskParams), captor.capture(), eq(logCallback), eq(true));
    List<KubernetesResourceId> deletedResourceIds = (List<KubernetesResourceId>) captor.getValue();
    assertThat(deletedResourceIds.stream().map(KubernetesResourceId::namespaceKindNameRef))
        .containsExactly("default/deployment/deployment-canary");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void deleteFromReleaseHistory() throws Exception {
    K8sCanaryDeleteRequest deleteRequest = K8sCanaryDeleteRequest.builder()
                                               .releaseName(canaryReleaseHistory)
                                               .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                               .build();

    IK8sRelease latestRelease = mock(IK8sRelease.class);
    doReturn(false).when(releaseHistory).isEmpty();
    doReturn(latestRelease).when(releaseHistory).getLatestRelease();
    doReturn(InProgress).when(latestRelease).getReleaseStatus();
    doReturn(List.of(KubernetesResourceId.builder()
                         .kind("Deployment")
                         .name("test-value-deployment-canary")
                         .namespace("default")
                         .build()))
        .when(latestRelease)
        .getResourceIds();

    K8sDeployResponse response = requestHandler.executeTaskInternal(
        deleteRequest, delegateTaskParams, logStreamingTaskClient, commandUnitsProgress);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(k8sTaskHelperBase)
        .delete(any(Kubectl.class), eq(delegateTaskParams), captor.capture(), eq(logCallback), eq(true));
    List<KubernetesResourceId> deletedResourceIds = (List<KubernetesResourceId>) captor.getValue();
    assertThat(deletedResourceIds.stream().map(KubernetesResourceId::namespaceKindNameRef))
        .containsExactly("default/Deployment/test-value-deployment-canary");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void deleteFromReleaseHistoryFailedRelease() throws Exception {
    K8sCanaryDeleteRequest deleteRequest = K8sCanaryDeleteRequest.builder()
                                               .releaseName(canaryFailedReleaseHistory)
                                               .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                               .build();

    IK8sRelease latestRelease = mock(IK8sRelease.class);
    doReturn(false).when(releaseHistory).isEmpty();
    doReturn(latestRelease).when(releaseHistory).getLatestRelease();
    doReturn(Failed).when(latestRelease).getReleaseStatus();
    doReturn(List.of(KubernetesResourceId.builder()
                         .kind("Deployment")
                         .name("test-value-deployment-canary")
                         .namespace("default")
                         .build()))
        .when(latestRelease)
        .getResourceIds();

    K8sDeployResponse response = requestHandler.executeTaskInternal(
        deleteRequest, delegateTaskParams, logStreamingTaskClient, commandUnitsProgress);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(k8sTaskHelperBase)
        .delete(any(Kubectl.class), eq(delegateTaskParams), captor.capture(), eq(logCallback), eq(true));
    List<KubernetesResourceId> deletedResourceIds = (List<KubernetesResourceId>) captor.getValue();
    assertThat(deletedResourceIds.stream().map(KubernetesResourceId::namespaceKindNameRef))
        .containsExactly("default/Deployment/test-value-deployment-canary");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void skipDeleteNoWorkloadNameNoReleaseHistory() throws Exception {
    K8sCanaryDeleteRequest deleteRequest = K8sCanaryDeleteRequest.builder()
                                               .releaseName(noReleaseHistory)
                                               .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                               .build();

    doReturn(true).when(releaseHistory).isEmpty();

    K8sDeployResponse response = requestHandler.executeTaskInternal(
        deleteRequest, delegateTaskParams, logStreamingTaskClient, commandUnitsProgress);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    verify(k8sTaskHelperBase, never())
        .delete(any(Kubectl.class), eq(delegateTaskParams), anyListOf(KubernetesResourceId.class), eq(logCallback),
            eq(true));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void skipDeleteNoWorkloadNameEmptyReleases() throws Exception {
    K8sCanaryDeleteRequest deleteRequest = K8sCanaryDeleteRequest.builder()
                                               .releaseName(emptyReleasesHistory)
                                               .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                               .build();

    IK8sRelease latestRelease = mock(IK8sRelease.class);
    doReturn(false).when(releaseHistory).isEmpty();
    doReturn(latestRelease).when(releaseHistory).getLatestRelease();
    doReturn(Failed).when(latestRelease).getReleaseStatus();
    doReturn(emptyList()).when(latestRelease).getResourceIds();

    K8sDeployResponse response = requestHandler.executeTaskInternal(
        deleteRequest, delegateTaskParams, logStreamingTaskClient, commandUnitsProgress);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    verify(k8sTaskHelperBase, never())
        .delete(any(Kubectl.class), eq(delegateTaskParams), anyListOf(KubernetesResourceId.class), eq(logCallback),
            eq(true));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void skipDeleteNoWorkloadMissingCanaryRelease() throws Exception {
    K8sCanaryDeleteRequest deleteRequest = K8sCanaryDeleteRequest.builder()
                                               .releaseName(noInProgressReleaseHistory)
                                               .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                               .build();

    IK8sRelease latestRelease = mock(IK8sRelease.class);
    doReturn(false).when(releaseHistory).isEmpty();
    doReturn(latestRelease).when(releaseHistory).getLatestRelease();
    doReturn(Succeeded).when(latestRelease).getReleaseStatus();

    K8sDeployResponse response = requestHandler.executeTaskInternal(
        deleteRequest, delegateTaskParams, logStreamingTaskClient, commandUnitsProgress);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    verify(k8sTaskHelperBase, never())
        .delete(any(Kubectl.class), eq(delegateTaskParams), anyListOf(KubernetesResourceId.class), eq(logCallback),
            eq(true));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void deleteFromReleaseHistoryException() throws Exception {
    K8sCanaryDeleteRequest deleteRequest = K8sCanaryDeleteRequest.builder()
                                               .releaseName(canaryExceptionReleaseHistory)
                                               .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                               .build();

    assertThatThrownBy(()
                           -> requestHandler.executeTaskInternal(
                               deleteRequest, delegateTaskParams, logStreamingTaskClient, commandUnitsProgress))
        .matches(throwable -> {
          assertThat(throwable).isInstanceOf(InvalidRequestException.class);
          ApiException apiException = ExceptionUtils.cause(ApiException.class, throwable);
          assertThat(apiException).hasMessageContaining("Failed to get release history secret");
          return true;
        });

    verify(k8sTaskHelperBase, times(0))
        .delete(any(Kubectl.class), eq(delegateTaskParams), anyList(), eq(logCallback), eq(true));
  }
}
