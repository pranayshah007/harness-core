/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.k8s.K8sConstants.MANIFEST_FILES_DIR;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ACASIAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.k8s.DeleteResourcesType;
import io.harness.delegate.task.k8s.K8sDeleteRequest;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.delegate.task.k8s.ManifestDelegateConfig;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class K8sDeleteRequestHandlerTest extends CategoryTest {
  @Mock private K8sTaskHelperBase k8sTaskHelperBase;
  @Mock private K8sDeleteBaseHandler k8sDeleteBaseHandler;
  @Mock private ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;
  @InjectMocks private K8sDeleteRequestHandler k8sDeleteRequestHandler;
  @Mock ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock private LogCallback logCallback;
  @Mock private K8sInfraDelegateConfig k8sInfraDelegateConfig;
  @Mock private ManifestDelegateConfig manifestDelegateConfig;

  private final Integer timeoutIntervalInMin = 10;
  private final long timeoutIntervalInMillis = 60 * timeoutIntervalInMin * 1000;
  private final String accountId = "accountId";
  private final String namespace = "default";
  private final String releaseName = "test-release";
  private final KubernetesConfig kubernetesConfig = KubernetesConfig.builder().namespace(namespace).build();
  private final String workingDirectory = "manifest";
  private final String invalidWorkingDirectory = "invalid";
  private final String manifestFileDirectory = Paths.get(workingDirectory, MANIFEST_FILES_DIR).toString();
  private final String invalidManifestFileDirectory = Paths.get(invalidWorkingDirectory, MANIFEST_FILES_DIR).toString();
  private final String kubectlPath = "clientPath";
  private final String kubeconfigPath = "configPath";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    doReturn(kubernetesConfig)
        .when(containerDeploymentDelegateBaseHelper)
        .createKubernetesConfig(k8sInfraDelegateConfig, workingDirectory, logCallback);
    doReturn(logCallback)
        .when(k8sTaskHelperBase)
        .getLogCallback(eq(iLogStreamingTaskClient), anyString(), anyBoolean(), any());
    doReturn(true)
        .when(k8sTaskHelperBase)
        .fetchManifestFilesAndWriteToDirectory(
            manifestDelegateConfig, manifestFileDirectory, logCallback, timeoutIntervalInMillis, accountId);
    doReturn(false)
        .when(k8sTaskHelperBase)
        .fetchManifestFilesAndWriteToDirectory(
            manifestDelegateConfig, invalidManifestFileDirectory, logCallback, timeoutIntervalInMillis, accountId);
    doReturn(true)
        .when(k8sTaskHelperBase)
        .applyManifests(
            any(Kubectl.class), anyList(), any(K8sDelegateTaskParams.class), eq(logCallback), anyBoolean(), any());
    doReturn(K8sDeployResponse.builder().commandExecutionStatus(SUCCESS).build())
        .when(k8sDeleteBaseHandler)
        .getSuccessResponse();
    doReturn(namespace).when(k8sInfraDelegateConfig).getNamespace();
  }

  /* ResourceName unit tests*/
  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testDeleteByResourceNameSuccess() throws Exception {
    K8sDeleteRequest deleteRequest = K8sDeleteRequest.builder()
                                         .deleteResourcesType(DeleteResourcesType.ResourceName)
                                         .resources("Deployment/test-deployment")
                                         .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                         .build();

    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder()
                                                   .workingDirectory(workingDirectory)
                                                   .kubectlPath(kubectlPath)
                                                   .kubeconfigPath(kubeconfigPath)
                                                   .build();

    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    List<KubernetesResourceId> kubernetesResources =
        Arrays.asList(KubernetesResourceId.builder().kind("Deployment").name("test-deployment").build());

    when(k8sDeleteBaseHandler.getResourceIdsToDelete(deleteRequest, kubernetesConfig, logCallback))
        .thenReturn(kubernetesResources);
    doNothing()
        .when(k8sTaskHelperBase)
        .delete(any(Kubectl.class), eq(delegateTaskParams), eq(kubernetesResources), eq(logCallback), eq(true));

    K8sDeployResponse response = k8sDeleteRequestHandler.executeTaskInternal(
        deleteRequest, delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    verify(k8sDeleteBaseHandler, times(1))
        .getResourceIdsToDelete(eq(deleteRequest), eq(kubernetesConfig), eq(logCallback));
    verify(k8sTaskHelperBase, times(1))
        .delete(any(Kubectl.class), eq(delegateTaskParams), eq(kubernetesResources), eq(logCallback), eq(true));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testDeleteByResourceNameNoResourcesSuccess() throws Exception {
    K8sDeleteRequest deleteRequest = K8sDeleteRequest.builder()
                                         .deleteResourcesType(DeleteResourcesType.ResourceName)
                                         .resources("")
                                         .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                         .build();

    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder()
                                                   .workingDirectory(workingDirectory)
                                                   .kubectlPath(kubectlPath)
                                                   .kubeconfigPath(kubeconfigPath)
                                                   .build();

    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    List<KubernetesResourceId> kubernetesResources = Collections.emptyList();

    when(k8sDeleteBaseHandler.getResourceIdsToDelete(deleteRequest, kubernetesConfig, logCallback))
        .thenReturn(kubernetesResources);

    K8sDeployResponse response = k8sDeleteRequestHandler.executeTaskInternal(
        deleteRequest, delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    verify(k8sDeleteBaseHandler, times(1))
        .getResourceIdsToDelete(eq(deleteRequest), eq(kubernetesConfig), eq(logCallback));
    verify(k8sTaskHelperBase, times(0))
        .delete(any(Kubectl.class), any(K8sDelegateTaskParams.class), anyList(), any(), anyBoolean());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testDeleteByResourceNameFailure() throws Exception {
    K8sDeleteRequest deleteRequest = K8sDeleteRequest.builder()
                                         .deleteResourcesType(DeleteResourcesType.ResourceName)
                                         .resources("test-deployment")
                                         .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                         .build();

    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder()
                                                   .workingDirectory(workingDirectory)
                                                   .kubectlPath(kubectlPath)
                                                   .kubeconfigPath(kubeconfigPath)
                                                   .build();

    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    when(k8sDeleteBaseHandler.getResourceIdsToDelete(deleteRequest, kubernetesConfig, logCallback))
        .thenThrow(new RuntimeException("Resource kind is missing"));

    K8sDeployResponse response = null;
    try {
      response = k8sDeleteRequestHandler.executeTaskInternal(
          deleteRequest, delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);
    } catch (Exception e) {
      assertThat(response).isNull();
      verify(k8sDeleteBaseHandler, times(1))
          .getResourceIdsToDelete(eq(deleteRequest), eq(kubernetesConfig), eq(logCallback));
      verify(k8sTaskHelperBase, times(0))
          .delete(any(Kubectl.class), any(K8sDelegateTaskParams.class), anyList(), any(), anyBoolean());
    }
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testDeleteByResourceNameFailureDelete() throws Exception {
    K8sDeleteRequest deleteRequest = K8sDeleteRequest.builder()
                                         .deleteResourcesType(DeleteResourcesType.ResourceName)
                                         .resources("Deployment/test-deployment")
                                         .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                         .build();

    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder()
                                                   .workingDirectory(workingDirectory)
                                                   .kubectlPath(kubectlPath)
                                                   .kubeconfigPath(kubeconfigPath)
                                                   .build();

    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    List<KubernetesResourceId> kubernetesResources =
        Arrays.asList(KubernetesResourceId.builder().kind("Deployment").name("test-deployment").build());

    when(k8sDeleteBaseHandler.getResourceIdsToDelete(deleteRequest, kubernetesConfig, logCallback))
        .thenReturn(kubernetesResources);
    doNothing()
        .when(k8sTaskHelperBase)
        .delete(any(Kubectl.class), eq(delegateTaskParams), eq(kubernetesResources), eq(logCallback), eq(true));

    K8sDeployResponse response = k8sDeleteRequestHandler.executeTaskInternal(
        deleteRequest, delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    verify(k8sDeleteBaseHandler, times(1))
        .getResourceIdsToDelete(eq(deleteRequest), eq(kubernetesConfig), eq(logCallback));
    verify(k8sTaskHelperBase, times(1))
        .delete(any(Kubectl.class), eq(delegateTaskParams), eq(kubernetesResources), eq(logCallback), eq(true));
  }

  /* ReleaseName unit tests*/

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testDeleteByReleaseNameSuccess() throws Exception {
    K8sDeleteRequest deleteRequest = K8sDeleteRequest.builder()
                                         .deleteResourcesType(DeleteResourcesType.ReleaseName)
                                         .releaseName(releaseName)
                                         .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                         .build();

    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder()
                                                   .workingDirectory(workingDirectory)
                                                   .kubectlPath(kubectlPath)
                                                   .kubeconfigPath(kubeconfigPath)
                                                   .build();

    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    List<KubernetesResourceId> kubernetesResources =
        Arrays.asList(KubernetesResourceId.builder().kind("Deployment").name("test-deployment").build());

    when(k8sDeleteBaseHandler.getResourceIdsToDelete(deleteRequest, kubernetesConfig, logCallback))
        .thenReturn(kubernetesResources);
    doNothing()
        .when(k8sTaskHelperBase)
        .delete(any(Kubectl.class), eq(delegateTaskParams), eq(kubernetesResources), eq(logCallback), eq(true));

    K8sDeployResponse response = k8sDeleteRequestHandler.executeTaskInternal(
        deleteRequest, delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    verify(k8sDeleteBaseHandler, times(1))
        .getResourceIdsToDelete(eq(deleteRequest), eq(kubernetesConfig), eq(logCallback));
    verify(k8sTaskHelperBase, times(1))
        .delete(any(Kubectl.class), eq(delegateTaskParams), eq(kubernetesResources), eq(logCallback), eq(true));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testDeleteByReleaseNameNoResourcesSuccess() throws Exception {
    K8sDeleteRequest deleteRequest = K8sDeleteRequest.builder()
                                         .deleteResourcesType(DeleteResourcesType.ReleaseName)
                                         .releaseName(releaseName)
                                         .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                         .build();

    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder()
                                                   .workingDirectory(workingDirectory)
                                                   .kubectlPath(kubectlPath)
                                                   .kubeconfigPath(kubeconfigPath)
                                                   .build();

    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    List<KubernetesResourceId> kubernetesResources = Collections.emptyList();

    when(k8sDeleteBaseHandler.getResourceIdsToDelete(deleteRequest, kubernetesConfig, logCallback))
        .thenReturn(kubernetesResources);

    K8sDeployResponse response = k8sDeleteRequestHandler.executeTaskInternal(
        deleteRequest, delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    verify(k8sDeleteBaseHandler, times(1))
        .getResourceIdsToDelete(eq(deleteRequest), eq(kubernetesConfig), eq(logCallback));
    verify(k8sTaskHelperBase, times(0))
        .delete(any(Kubectl.class), any(K8sDelegateTaskParams.class), anyList(), any(), anyBoolean());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testDeleteByReleaseNameFailure() throws Exception {
    K8sDeleteRequest deleteRequest = K8sDeleteRequest.builder()
                                         .deleteResourcesType(DeleteResourcesType.ReleaseName)
                                         .releaseName(releaseName)
                                         .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                         .build();

    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder()
                                                   .workingDirectory(workingDirectory)
                                                   .kubectlPath(kubectlPath)
                                                   .kubeconfigPath(kubeconfigPath)
                                                   .build();

    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    when(k8sDeleteBaseHandler.getResourceIdsToDelete(deleteRequest, kubernetesConfig, logCallback))
        .thenThrow(new RuntimeException("Resource kind is missing"));

    K8sDeployResponse response = null;

    try {
      response = k8sDeleteRequestHandler.executeTaskInternal(
          deleteRequest, delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);
    } catch (Exception e) {
      assertThat(response).isNull();
      verify(k8sDeleteBaseHandler, times(1))
          .getResourceIdsToDelete(eq(deleteRequest), eq(kubernetesConfig), eq(logCallback));
      verify(k8sTaskHelperBase, times(0))
          .delete(any(Kubectl.class), any(K8sDelegateTaskParams.class), anyList(), any(), anyBoolean());
    }
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testDeleteByReleaseNameFailureDelete() throws Exception {
    K8sDeleteRequest deleteRequest = K8sDeleteRequest.builder()
                                         .deleteResourcesType(DeleteResourcesType.ReleaseName)
                                         .releaseName(releaseName)
                                         .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                         .build();

    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder()
                                                   .workingDirectory(workingDirectory)
                                                   .kubectlPath(kubectlPath)
                                                   .kubeconfigPath(kubeconfigPath)
                                                   .build();

    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    List<KubernetesResourceId> kubernetesResources =
        Arrays.asList(KubernetesResourceId.builder().kind("Deployment").name("test-deployment").build());

    when(k8sDeleteBaseHandler.getResourceIdsToDelete(deleteRequest, kubernetesConfig, logCallback))
        .thenReturn(kubernetesResources);
    doNothing()
        .when(k8sTaskHelperBase)
        .delete(any(Kubectl.class), eq(delegateTaskParams), eq(kubernetesResources), eq(logCallback), eq(true));

    K8sDeployResponse response = k8sDeleteRequestHandler.executeTaskInternal(
        deleteRequest, delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    verify(k8sDeleteBaseHandler, times(1))
        .getResourceIdsToDelete(eq(deleteRequest), eq(kubernetesConfig), eq(logCallback));
    verify(k8sTaskHelperBase, times(1))
        .delete(any(Kubectl.class), eq(delegateTaskParams), eq(kubernetesResources), eq(logCallback), eq(true));
  }

  /* ManifestPath unit tests*/
  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testDeleteByManifestPathSuccess() throws Exception {
    List<String> valuesYaml = Collections.emptyList();
    K8sDeleteRequest deleteRequest = K8sDeleteRequest.builder()
                                         .deleteResourcesType(DeleteResourcesType.ManifestPath)
                                         .accountId(accountId)
                                         .filePaths("deployment.yaml,service.yaml")
                                         .releaseName(releaseName)
                                         .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                         .manifestDelegateConfig(manifestDelegateConfig)
                                         .valuesYamlList(valuesYaml)
                                         .timeoutIntervalInMin(timeoutIntervalInMin)
                                         .build();

    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder()
                                                   .workingDirectory(workingDirectory)
                                                   .kubectlPath(kubectlPath)
                                                   .kubeconfigPath(kubeconfigPath)
                                                   .build();

    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    List<String> deleteFilePaths = Arrays.asList("deployment.yaml", "service.yaml");
    List<KubernetesResource> kubernetesResources =
        Arrays.asList(KubernetesResource.builder()
                          .resourceId(KubernetesResourceId.builder().kind("Deployment").name("test-deployment").build())
                          .spec("")
                          .build(),
            KubernetesResource.builder()
                .resourceId(KubernetesResourceId.builder().kind("Service").name("test-service").build())
                .spec("")
                .build());

    when(k8sTaskHelperBase.getResourcesFromManifests(delegateTaskParams, manifestDelegateConfig, manifestFileDirectory,
             deleteFilePaths, valuesYaml, releaseName, namespace, logCallback, timeoutIntervalInMin, false))
        .thenReturn(kubernetesResources);
    doNothing()
        .when(k8sTaskHelperBase)
        .deleteManifests(any(Kubectl.class), eq(kubernetesResources), eq(delegateTaskParams), eq(logCallback));

    K8sDeployResponse response = k8sDeleteRequestHandler.executeTaskInternal(
        deleteRequest, delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    verify(k8sTaskHelperBase, times(1))
        .getResourcesFromManifests(eq(delegateTaskParams), eq(manifestDelegateConfig), eq(manifestFileDirectory),
            eq(deleteFilePaths), anyList(), eq(releaseName), eq(namespace), eq(logCallback), eq(timeoutIntervalInMin),
            eq(false));
    verify(k8sTaskHelperBase, times(1))
        .deleteManifests(any(Kubectl.class), eq(kubernetesResources), eq(delegateTaskParams), eq(logCallback));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testDeleteByEmptyManifestPathFail() throws Exception {
    List<String> valuesYaml = Collections.emptyList();
    K8sDeleteRequest deleteRequest = K8sDeleteRequest.builder()
                                         .deleteResourcesType(DeleteResourcesType.ManifestPath)
                                         .accountId(accountId)
                                         .filePaths("")
                                         .releaseName(releaseName)
                                         .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                         .manifestDelegateConfig(manifestDelegateConfig)
                                         .valuesYamlList(valuesYaml)
                                         .timeoutIntervalInMin(timeoutIntervalInMin)
                                         .build();

    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder()
                                                   .workingDirectory(workingDirectory)
                                                   .kubectlPath(kubectlPath)
                                                   .kubeconfigPath(kubeconfigPath)
                                                   .build();

    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    doThrow(new NullPointerException("resources are not provided"))
        .when(k8sTaskHelperBase)
        .deleteManifests(any(Kubectl.class), eq(null), eq(delegateTaskParams), eq(logCallback));

    K8sDeployResponse response = null;

    try {
      response = k8sDeleteRequestHandler.executeTaskInternal(
          deleteRequest, delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);
    } catch (Exception e) {
      assertThat(response).isNull();
      verify(k8sTaskHelperBase, times(0))
          .getResourcesFromManifests(any(K8sDelegateTaskParams.class), any(ManifestDelegateConfig.class), anyString(),
              anyList(), anyList(), anyString(), anyString(), any(), anyInt(), eq(false));
      verify(k8sTaskHelperBase, times(1))
          .deleteManifests(any(Kubectl.class), eq(null), eq(delegateTaskParams), eq(logCallback));
    }
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldFailManifestPathDeleteWhenFetchFilesFails() throws Exception {
    List<String> valuesYaml = Collections.emptyList();
    K8sDeleteRequest deleteRequest = K8sDeleteRequest.builder()
                                         .deleteResourcesType(DeleteResourcesType.ManifestPath)
                                         .accountId(accountId)
                                         .filePaths("")
                                         .releaseName(releaseName)
                                         .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                         .manifestDelegateConfig(manifestDelegateConfig)
                                         .valuesYamlList(valuesYaml)
                                         .timeoutIntervalInMin(timeoutIntervalInMin)
                                         .build();

    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder()
                                                   .workingDirectory(invalidWorkingDirectory)
                                                   .kubectlPath(kubectlPath)
                                                   .kubeconfigPath(kubeconfigPath)
                                                   .build();

    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    K8sDeployResponse response = k8sDeleteRequestHandler.executeTaskInternal(
        deleteRequest, delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    verify(k8sTaskHelperBase, times(0))
        .getResourcesFromManifests(
            any(), any(), anyString(), anyList(), anyList(), anyString(), anyString(), any(), anyInt(), eq(false));
    verify(k8sTaskHelperBase, times(1)).deleteManifests(any(Kubectl.class), any(), any(), any());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testDeleteByManifestPathFailure() throws Exception {
    List<String> valuesYaml = Collections.emptyList();
    K8sDeleteRequest deleteRequest = K8sDeleteRequest.builder()
                                         .deleteResourcesType(DeleteResourcesType.ManifestPath)
                                         .accountId(accountId)
                                         .filePaths("deployment.yaml,service.yaml")
                                         .releaseName(releaseName)
                                         .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                         .manifestDelegateConfig(manifestDelegateConfig)
                                         .valuesYamlList(valuesYaml)
                                         .timeoutIntervalInMin(timeoutIntervalInMin)
                                         .build();

    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder()
                                                   .workingDirectory(workingDirectory)
                                                   .kubectlPath(kubectlPath)
                                                   .kubeconfigPath(kubeconfigPath)
                                                   .build();

    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    List<String> deleteFilePaths = Arrays.asList("deployment.yaml", "service.yaml");
    List<KubernetesResource> kubernetesResources =
        Arrays.asList(KubernetesResource.builder()
                          .resourceId(KubernetesResourceId.builder().kind("Deployment").name("test-deployment").build())
                          .spec("")
                          .build(),
            KubernetesResource.builder()
                .resourceId(KubernetesResourceId.builder().kind("Service").name("test-service").build())
                .spec("")
                .build());

    when(k8sTaskHelperBase.getResourcesFromManifests(delegateTaskParams, manifestDelegateConfig, manifestFileDirectory,
             deleteFilePaths, valuesYaml, releaseName, namespace, logCallback, timeoutIntervalInMin, false))
        .thenReturn(kubernetesResources);
    doNothing()
        .when(k8sTaskHelperBase)
        .deleteManifests(any(Kubectl.class), eq(kubernetesResources), eq(delegateTaskParams), eq(logCallback));

    K8sDeployResponse response = k8sDeleteRequestHandler.executeTaskInternal(
        deleteRequest, delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    verify(k8sTaskHelperBase, times(1))
        .getResourcesFromManifests(eq(delegateTaskParams), eq(manifestDelegateConfig), eq(manifestFileDirectory),
            eq(deleteFilePaths), anyList(), eq(releaseName), eq(namespace), eq(logCallback), eq(timeoutIntervalInMin),
            eq(false));
    verify(k8sTaskHelperBase, times(1))
        .deleteManifests(any(Kubectl.class), eq(kubernetesResources), eq(delegateTaskParams), eq(logCallback));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testFailDeleteByManifestPathWhenFailToGetResources() throws Exception {
    List<String> valuesYaml = Collections.emptyList();
    K8sDeleteRequest deleteRequest = K8sDeleteRequest.builder()
                                         .deleteResourcesType(DeleteResourcesType.ManifestPath)
                                         .accountId(accountId)
                                         .filePaths("deployment.yaml,service.yaml")
                                         .releaseName(releaseName)
                                         .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                         .manifestDelegateConfig(manifestDelegateConfig)
                                         .valuesYamlList(valuesYaml)
                                         .timeoutIntervalInMin(timeoutIntervalInMin)
                                         .build();

    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder()
                                                   .workingDirectory(workingDirectory)
                                                   .kubectlPath(kubectlPath)
                                                   .kubeconfigPath(kubeconfigPath)
                                                   .build();

    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    List<String> deleteFilePaths = Arrays.asList("deployment.yaml", "service.yaml");
    when(k8sTaskHelperBase.getResourcesFromManifests(delegateTaskParams, manifestDelegateConfig, manifestFileDirectory,
             deleteFilePaths, valuesYaml, releaseName, namespace, logCallback, timeoutIntervalInMin, false))
        .thenThrow(new RuntimeException("Something went wrong"));

    K8sDeployResponse response = null;

    try {
      response = k8sDeleteRequestHandler.executeTaskInternal(
          deleteRequest, delegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);
    } catch (Exception e) {
      assertThat(response).isNull();
      verify(k8sTaskHelperBase, times(1))
          .getResourcesFromManifests(eq(delegateTaskParams), eq(manifestDelegateConfig), eq(manifestFileDirectory),
              eq(deleteFilePaths), anyList(), eq(releaseName), eq(namespace), eq(logCallback), eq(timeoutIntervalInMin),
              eq(false));
      verify(k8sTaskHelperBase, times(0)).deleteManifests(any(), any(), any(), any());
    }
  }
}
