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
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FileData;
import io.harness.beans.NGInstanceUnitType;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.k8s.beans.K8sCanaryHandlerConfig;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.k8s.K8sCanaryDeployRequest;
import io.harness.delegate.task.k8s.K8sCanaryDeployResponse;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.K8sManifestDelegateConfig;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.delegate.task.k8s.KustomizeManifestDelegateConfig;
import io.harness.delegate.task.k8s.ManifestDelegateConfig;
import io.harness.delegate.task.k8s.OpenshiftManifestDelegateConfig;
import io.harness.delegate.task.k8s.data.K8sCanaryDataException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.model.Release;
import io.harness.k8s.model.ReleaseHistory;
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
public class K8sCanaryRequestHandlerTest extends CategoryTest {
  @Mock private K8sTaskHelperBase k8sTaskHelperBase;
  @Mock private K8sCanaryBaseHandler k8sCanaryBaseHandler;
  @Mock private ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;

  @InjectMocks private K8sCanaryRequestHandler k8sCanaryRequestHandler;

  @Mock ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock private LogCallback logCallback;
  @Mock private K8sInfraDelegateConfig k8sInfraDelegateConfig;
  @Mock private ManifestDelegateConfig manifestDelegateConfig;

  private final Integer timeoutIntervalInMin = 10;
  private final long timeoutIntervalInMillis = 60 * timeoutIntervalInMin * 1000;
  private final String accountId = "accountId";
  private final String namespace = "default";
  private final KubernetesConfig kubernetesConfig = KubernetesConfig.builder().namespace(namespace).build();
  private final String workingDirectory = "manifest";
  private final String invalidWorkingDirectory = "invalid";
  private final String manifestFileDirectory = Paths.get(workingDirectory, MANIFEST_FILES_DIR).toString();
  private final String invalidManifestFileDirectory = Paths.get(invalidWorkingDirectory, MANIFEST_FILES_DIR).toString();

  private K8sCanaryHandlerConfig k8sCanaryHandlerConfig;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    doReturn(kubernetesConfig)
        .when(containerDeploymentDelegateBaseHelper)
        .createKubernetesConfig(k8sInfraDelegateConfig);
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
        .applyManifests(any(Kubectl.class), anyListOf(KubernetesResource.class), any(K8sDelegateTaskParams.class),
            eq(logCallback), anyBoolean());
    k8sCanaryHandlerConfig = k8sCanaryRequestHandler.getK8sCanaryHandlerConfig();
    k8sCanaryHandlerConfig.setKubernetesConfig(kubernetesConfig);
    k8sCanaryHandlerConfig.setManifestFilesDirectory(manifestFileDirectory);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDryRunIsSkipped() throws Exception {
    testDryRun(true);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDryRunIsNotSkipped() throws Exception {
    testDryRun(false);
  }

  public void testDryRun(boolean skipDryRun) throws Exception {
    String releaseName = "releaseName";
    List<String> valuesYamlList = emptyList();
    List<String> openshiftParamList = singletonList("file");
    List<FileData> manifestFiles = emptyList();
    List<KubernetesResource> kubernetesResources = emptyList();
    K8sCanaryDeployRequest k8sCanaryDeployRequest = K8sCanaryDeployRequest.builder()
                                                        .releaseName(releaseName)
                                                        .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                                        .manifestDelegateConfig(manifestDelegateConfig)
                                                        .valuesYamlList(valuesYamlList)
                                                        .openshiftParamList(openshiftParamList)
                                                        .skipDryRun(skipDryRun)
                                                        .timeoutIntervalInMin(timeoutIntervalInMin)
                                                        .build();
    K8sDelegateTaskParams delegateTaskParams =
        K8sDelegateTaskParams.builder().workingDirectory(workingDirectory).build();

    when(k8sTaskHelperBase.renderTemplate(delegateTaskParams, manifestDelegateConfig, manifestFileDirectory,
             openshiftParamList, releaseName, namespace, logCallback, timeoutIntervalInMin))
        .thenReturn(manifestFiles);

    when(k8sTaskHelperBase.readManifests(manifestFiles, logCallback, true)).thenReturn(kubernetesResources);
    when(k8sTaskHelperBase.getReleaseHistoryDataFromConfigMap(kubernetesConfig, releaseName)).thenReturn(null);
    doNothing().when(k8sTaskHelperBase).deleteSkippedManifestFiles(null, logCallback);

    k8sCanaryRequestHandler.init(k8sCanaryDeployRequest, delegateTaskParams, logCallback);
    int wantedDryRunInvocations = skipDryRun ? 0 : 1;
    verify(k8sTaskHelperBase, times(wantedDryRunInvocations))
        .dryRunManifests(any(), any(), any(), any(), eq(true), anyBoolean());
    verify(k8sTaskHelperBase, times(1)).readManifests(manifestFiles, logCallback, true);
    verify(k8sTaskHelperBase, times(1))
        .renderTemplate(delegateTaskParams, manifestDelegateConfig, manifestFileDirectory, openshiftParamList,
            releaseName, namespace, logCallback, timeoutIntervalInMin);
    verify(k8sTaskHelperBase, times(1)).deleteSkippedManifestFiles(manifestFileDirectory, logCallback);
    verify(k8sTaskHelperBase, times(1)).getReleaseHistoryDataFromConfigMap(kubernetesConfig, releaseName);
    verify(k8sCanaryBaseHandler, times(1))
        .updateDestinationRuleManifestFilesWithSubsets(kubernetesResources, kubernetesConfig, logCallback);
    verify(k8sCanaryBaseHandler, times(1))
        .updateVirtualServiceManifestFilesWithRoutes(kubernetesResources, kubernetesConfig, logCallback);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testgetManifestOverrideFlies() {
    String releaseName = "releaseName";
    List<String> valuesYamlList = Arrays.asList("value1", "value2");
    List<String> kustomizePatchesList = Arrays.asList("patch1", "patch2");
    List<String> openShiftParamList = Arrays.asList("param1", "param2");
    K8sCanaryDeployRequest canaryDeployRequest =
        K8sCanaryDeployRequest.builder()
            .releaseName(releaseName)
            .kustomizePatchesList(kustomizePatchesList)
            .manifestDelegateConfig(KustomizeManifestDelegateConfig.builder().build())
            .useLatestKustomizeVersion(true)
            .accountId(accountId)
            .build();
    assertThat(k8sCanaryRequestHandler.getManifestOverrideFlies(canaryDeployRequest).get(0)).isEqualTo("patch1");

    canaryDeployRequest = K8sCanaryDeployRequest.builder()
                              .releaseName(releaseName)
                              .useLatestKustomizeVersion(true)
                              .manifestDelegateConfig(K8sManifestDelegateConfig.builder().build())
                              .valuesYamlList(valuesYamlList)
                              .accountId(accountId)
                              .build();
    assertThat(k8sCanaryRequestHandler.getManifestOverrideFlies(canaryDeployRequest).get(0)).isEqualTo("value1");

    canaryDeployRequest = K8sCanaryDeployRequest.builder()
                              .releaseName(releaseName)
                              .openshiftParamList(openShiftParamList)
                              .manifestDelegateConfig(OpenshiftManifestDelegateConfig.builder().build())
                              .useLatestKustomizeVersion(true)
                              .accountId(accountId)
                              .build();
    assertThat(k8sCanaryRequestHandler.getManifestOverrideFlies(canaryDeployRequest).get(0)).isEqualTo("param1");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void invalidTypeOfTaskParams() {
    assertThatExceptionOfType(InvalidArgumentsException.class)
        .isThrownBy(() -> k8sCanaryRequestHandler.executeTaskInternal(null, null, null, null))
        .withMessageContaining("INVALID_ARGUMENT");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecute() throws Exception {
    String releaseName = "releaseName";
    K8sCanaryDeployRequest canaryDeployRequest = K8sCanaryDeployRequest.builder()
                                                     .releaseName(releaseName)
                                                     .timeoutIntervalInMin(timeoutIntervalInMin)
                                                     .manifestDelegateConfig(manifestDelegateConfig)
                                                     .accountId(accountId)
                                                     .build();
    K8sDelegateTaskParams delegateTaskParams =
        K8sDelegateTaskParams.builder().workingDirectory(workingDirectory).build();
    K8sCanaryRequestHandler spyRequestHandler = spy(k8sCanaryRequestHandler);
    K8sCanaryHandlerConfig k8sCanaryHandlerConfig = spyRequestHandler.getK8sCanaryHandlerConfig();

    ReleaseHistory releaseHistory = ReleaseHistory.createNew();
    releaseHistory.setReleases(asList(Release.builder().number(2).build()));
    k8sCanaryHandlerConfig.setCanaryWorkload(ManifestHelper.processYaml(K8sTestConstants.DEPLOYMENT_YAML).get(0));
    k8sCanaryHandlerConfig.setResources(Collections.emptyList());
    k8sCanaryHandlerConfig.setReleaseHistory(releaseHistory);
    k8sCanaryHandlerConfig.setCurrentRelease(releaseHistory.getLatestRelease());
    k8sCanaryHandlerConfig.setTargetInstances(3);
    k8sCanaryHandlerConfig.setKubernetesConfig(kubernetesConfig);

    doNothing().when(spyRequestHandler).init(canaryDeployRequest, delegateTaskParams, logCallback);
    doNothing().when(spyRequestHandler).prepareForCanary(canaryDeployRequest, delegateTaskParams, logCallback);
    doReturn(true)
        .when(k8sTaskHelperBase)
        .doStatusCheck(any(Kubectl.class), eq(k8sCanaryHandlerConfig.getCanaryWorkload().getResourceId()),
            eq(delegateTaskParams), eq(logCallback));
    doReturn(Arrays.asList(K8sPod.builder().build()))
        .when(k8sCanaryBaseHandler)
        .getAllPods(k8sCanaryHandlerConfig, releaseName, timeoutIntervalInMillis);

    K8sDeployResponse k8sDeployResponse =
        spyRequestHandler.executeTask(canaryDeployRequest, delegateTaskParams, iLogStreamingTaskClient, null);
    verify(k8sCanaryBaseHandler, times(1)).wrapUp(any(Kubectl.class), eq(delegateTaskParams), eq(logCallback));
    verify(k8sTaskHelperBase, times(1))
        .saveReleaseHistoryInConfigMap(kubernetesConfig, releaseName, releaseHistory.getAsYaml());
    K8sCanaryDeployResponse canaryDeployResponse = (K8sCanaryDeployResponse) k8sDeployResponse.getK8sNGTaskResponse();
    assertThat(k8sDeployResponse.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(canaryDeployResponse.getCanaryWorkload()).isEqualTo("Deployment/deployment");
    assertThat(canaryDeployResponse.getCurrentInstances()).isEqualTo(3);
    assertThat(canaryDeployResponse.getReleaseNumber()).isEqualTo(2);
    assertThat(canaryDeployResponse.getK8sPodList()).hasSize(1);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testInit() throws Exception {
    String releaseName = "releaseName";
    List<KubernetesResource> deployment = ManifestHelper.processYaml(K8sTestConstants.DEPLOYMENT_YAML);
    List<FileData> manifestFiles = emptyList();
    List<String> valuesYamlList = emptyList();
    List<String> openshiftParamList = emptyList();
    K8sCanaryDeployRequest canaryDeployRequest = K8sCanaryDeployRequest.builder()
                                                     .timeoutIntervalInMin(timeoutIntervalInMin)
                                                     .releaseName(releaseName)
                                                     .manifestDelegateConfig(manifestDelegateConfig)
                                                     .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                                     .valuesYamlList(valuesYamlList)
                                                     .openshiftParamList(openshiftParamList)
                                                     .build();
    K8sDelegateTaskParams delegateTaskParams =
        K8sDelegateTaskParams.builder().workingDirectory(workingDirectory).build();
    k8sCanaryRequestHandler.getK8sCanaryHandlerConfig().setManifestFilesDirectory(manifestFileDirectory);

    doReturn(manifestFiles)
        .when(k8sTaskHelperBase)
        .renderTemplate(delegateTaskParams, manifestDelegateConfig, manifestFileDirectory, valuesYamlList, releaseName,
            namespace, logCallback, timeoutIntervalInMin);
    doReturn(deployment).when(k8sTaskHelperBase).readManifests(manifestFiles, logCallback, true);
    k8sCanaryRequestHandler.init(canaryDeployRequest, delegateTaskParams, logCallback);

    verify(k8sTaskHelperBase, times(1)).deleteSkippedManifestFiles(manifestFileDirectory, logCallback);
    verify(k8sTaskHelperBase, times(1))
        .renderTemplate(delegateTaskParams, manifestDelegateConfig, manifestFileDirectory, valuesYamlList, releaseName,
            namespace, logCallback, timeoutIntervalInMin);
    verify(k8sTaskHelperBase, times(1)).setNamespaceToKubernetesResourcesIfRequired(deployment, "default");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testInitException() throws Exception {
    String releaseName = "releaseName";
    List<String> valuesYamlList = singletonList("file");
    K8sCanaryDeployRequest canaryDeployRequest = K8sCanaryDeployRequest.builder()
                                                     .timeoutIntervalInMin(timeoutIntervalInMin)
                                                     .releaseName(releaseName)
                                                     .manifestDelegateConfig(manifestDelegateConfig)
                                                     .valuesYamlList(valuesYamlList)
                                                     .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                                     .build();
    RuntimeException thrownException = new RuntimeException();
    K8sDelegateTaskParams delegateTaskParams =
        K8sDelegateTaskParams.builder().workingDirectory(workingDirectory).build();
    doThrow(thrownException)
        .when(k8sTaskHelperBase)
        .renderTemplate(delegateTaskParams, manifestDelegateConfig, manifestFileDirectory, valuesYamlList, releaseName,
            namespace, logCallback, timeoutIntervalInMin);

    assertThatThrownBy(() -> k8sCanaryRequestHandler.init(canaryDeployRequest, delegateTaskParams, logCallback))
        .isSameAs(thrownException);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testPrepareForCanaryCount() throws Exception {
    K8sCanaryHandlerConfig canaryHandlerConfig = k8sCanaryRequestHandler.getK8sCanaryHandlerConfig();
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();
    K8sCanaryDeployRequest deployRequest = K8sCanaryDeployRequest.builder()
                                               .instanceUnitType(NGInstanceUnitType.COUNT)
                                               .instances(4)
                                               .skipResourceVersioning(true)
                                               .build();
    doReturn(true)
        .when(k8sCanaryBaseHandler)
        .prepareForCanary(canaryHandlerConfig, delegateTaskParams, true, logCallback, true, false);
    doReturn(1).when(k8sCanaryBaseHandler).getCurrentInstances(canaryHandlerConfig, delegateTaskParams, logCallback);

    k8sCanaryRequestHandler.prepareForCanary(deployRequest, delegateTaskParams, logCallback);
    verify(k8sCanaryBaseHandler, times(1)).updateTargetInstances(canaryHandlerConfig, 4, logCallback);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testPrepareForCanaryPercentage() throws Exception {
    Integer currentInstances = 4;
    K8sCanaryHandlerConfig k8sCanaryHandlerConfig = k8sCanaryRequestHandler.getK8sCanaryHandlerConfig();
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();
    K8sCanaryDeployRequest deployRequest =
        K8sCanaryDeployRequest.builder().instanceUnitType(NGInstanceUnitType.PERCENTAGE).instances(70).build();
    doReturn(currentInstances)
        .when(k8sCanaryBaseHandler)
        .getCurrentInstances(k8sCanaryHandlerConfig, delegateTaskParams, logCallback);
    doReturn(3).when(k8sTaskHelperBase).getTargetInstancesForCanary(70, currentInstances, logCallback);

    k8sCanaryRequestHandler.prepareForCanary(deployRequest, delegateTaskParams, logCallback);
    verify(k8sTaskHelperBase, times(1)).getTargetInstancesForCanary(70, currentInstances, logCallback);
    verify(k8sCanaryBaseHandler, times(1)).updateTargetInstances(k8sCanaryHandlerConfig, 3, logCallback);
    verify(k8sCanaryBaseHandler, times(1))
        .prepareForCanary(k8sCanaryHandlerConfig, delegateTaskParams, false, logCallback, true, false);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldGetReleaseDataFromConfigMapUsingK8sClient() throws Exception {
    final String releaseName = "releaseName";
    final K8sCanaryDeployRequest deployRequest =
        K8sCanaryDeployRequest.builder()
            .valuesYamlList(emptyList())
            .releaseName(releaseName)
            .manifestDelegateConfig(KustomizeManifestDelegateConfig.builder().build())
            .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
            .build();
    final K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();

    when(k8sTaskHelperBase.renderTemplate(delegateTaskParams, manifestDelegateConfig, manifestFileDirectory,
             emptyList(), releaseName, namespace, logCallback, timeoutIntervalInMin))
        .thenReturn(emptyList());
    when(k8sTaskHelperBase.readManifests(emptyList(), logCallback)).thenReturn(emptyList());
    when(k8sTaskHelperBase.getReleaseHistoryDataFromConfigMap(kubernetesConfig, releaseName)).thenReturn(null);

    k8sCanaryRequestHandler.init(deployRequest, delegateTaskParams, logCallback);
    verify(k8sTaskHelperBase, times(1)).getReleaseHistoryDataFromConfigMap(kubernetesConfig, releaseName);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldSaveReleaseHistoryUsingK8sClient() throws Exception {
    final KubernetesResource deployment = ManifestHelper.processYaml(K8sTestConstants.DEPLOYMENT_YAML).get(0);
    final K8sCanaryDeployRequest request = K8sCanaryDeployRequest.builder()
                                               .valuesYamlList(emptyList())
                                               .timeoutIntervalInMin(timeoutIntervalInMin)
                                               .accountId(accountId)
                                               .releaseName("success")
                                               .instanceUnitType(NGInstanceUnitType.COUNT)
                                               .instances(3)
                                               .manifestDelegateConfig(manifestDelegateConfig)
                                               .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                               .build();

    final K8sDelegateTaskParams delegateTaskParams =
        K8sDelegateTaskParams.builder().workingDirectory(workingDirectory).build();
    ReleaseHistory releaseHist = ReleaseHistory.createNew();
    releaseHist.setReleases(asList(Release.builder().number(2).build()));
    doReturn(releaseHist.getAsYaml())
        .when(k8sTaskHelperBase)
        .getReleaseHistoryDataFromConfigMap(kubernetesConfig, "success");

    k8sCanaryHandlerConfig.setCanaryWorkload(deployment);
    k8sCanaryHandlerConfig.setResources(emptyList());
    k8sCanaryHandlerConfig.setCurrentRelease(releaseHist.getLatestRelease());
    k8sCanaryHandlerConfig.setTargetInstances(3);

    k8sCanaryRequestHandler.executeTask(request, delegateTaskParams, iLogStreamingTaskClient, null);
    verify(k8sTaskHelperBase, times(1))
        .saveReleaseHistoryInConfigMap(kubernetesConfig, "success", releaseHist.getAsYaml());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCanaryWorkloadDeployedIfManifestNotApplied() {
    assertThatThrownBy(() -> testExecutionWithFailure(new RuntimeException("error"), null)).matches(throwable -> {
      K8sCanaryDataException dataException = ExceptionUtils.cause(K8sCanaryDataException.class, throwable);
      assertThat(dataException).isNotNull();
      assertThat(dataException.isCanaryWorkloadDeployed()).isFalse();
      assertThat(dataException).isNotNull();
      return true;
    });
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCanaryWorkloadDeployedIfManifestApplied() {
    assertThatThrownBy(() -> testExecutionWithFailure(null, new RuntimeException("error"))).matches(throwable -> {
      K8sCanaryDataException dataException = ExceptionUtils.cause(K8sCanaryDataException.class, throwable);
      assertThat(dataException).isNotNull();
      assertThat(dataException.isCanaryWorkloadDeployed()).isTrue();
      assertThat(dataException).isNotNull();
      return true;
    });
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testShouldNotSaveReleaseHistoryIfManifestNotApplied() throws Exception {
    assertThatThrownBy(() -> testExecutionWithFailure(new RuntimeException("error"), null)).matches(throwable -> {
      K8sCanaryDataException dataException = ExceptionUtils.cause(K8sCanaryDataException.class, throwable);
      assertThat(dataException).isNotNull();
      return true;
    });

    verify(k8sCanaryBaseHandler, never()).failAndSaveKubernetesRelease(any(K8sCanaryHandlerConfig.class), anyString());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testShouldSaveReleaseHistoryIfManifestApplied() throws Exception {
    assertThatThrownBy(() -> testExecutionWithFailure(null, new RuntimeException("error"))).matches(throwable -> {
      K8sCanaryDataException dataException = ExceptionUtils.cause(K8sCanaryDataException.class, throwable);
      assertThat(dataException).isNotNull();
      return true;
    });

    verify(k8sCanaryBaseHandler, times(1)).failAndSaveKubernetesRelease(k8sCanaryHandlerConfig, "failed");
  }

  private void testExecutionWithFailure(Exception applyThrowable, Exception statusCheckThrowable) throws Exception {
    final K8sCanaryDeployRequest request = K8sCanaryDeployRequest.builder()
                                               .valuesYamlList(emptyList())
                                               .timeoutIntervalInMin(timeoutIntervalInMin)
                                               .accountId(accountId)
                                               .releaseName("failed")
                                               .manifestDelegateConfig(manifestDelegateConfig)
                                               .valuesYamlList(singletonList("file"))
                                               .instanceUnitType(NGInstanceUnitType.COUNT)
                                               .instances(3)
                                               .k8sInfraDelegateConfig(k8sInfraDelegateConfig)
                                               .build();
    final K8sDelegateTaskParams delegateTaskParams =
        K8sDelegateTaskParams.builder().workingDirectory(workingDirectory).build();
    ReleaseHistory releaseHist = ReleaseHistory.createNew();
    releaseHist.setReleases(asList(Release.builder().number(2).build()));
    doReturn(releaseHist.getAsYaml())
        .when(k8sTaskHelperBase)
        .getReleaseHistoryDataFromConfigMap(kubernetesConfig, "success");

    k8sCanaryHandlerConfig.setCanaryWorkload(ManifestHelper.processYaml(K8sTestConstants.DEPLOYMENT_YAML).get(0));
    k8sCanaryHandlerConfig.setResources(emptyList());
    k8sCanaryHandlerConfig.setCurrentRelease(releaseHist.getLatestRelease());

    if (applyThrowable != null) {
      doThrow(applyThrowable)
          .when(k8sTaskHelperBase)
          .applyManifests(
              any(Kubectl.class), eq(emptyList()), eq(delegateTaskParams), eq(logCallback), eq(true), eq(true));
    } else if (statusCheckThrowable != null) {
      doThrow(statusCheckThrowable)
          .when(k8sTaskHelperBase)
          .doStatusCheck(
              any(Kubectl.class), any(KubernetesResourceId.class), eq(delegateTaskParams), eq(logCallback), eq(true));
    }

    Exception thrown = applyThrowable != null ? applyThrowable : statusCheckThrowable;

    assertThatThrownBy(
        () -> k8sCanaryRequestHandler.executeTask(request, delegateTaskParams, iLogStreamingTaskClient, null))
        .isSameAs(thrown);

    k8sCanaryRequestHandler.handleTaskFailure(request, thrown);
  }
}
