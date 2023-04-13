/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.k8s.taskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.k8s.K8sConstants.MANIFEST_FILES_DIR;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.BOJANA;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static software.wings.delegatetasks.k8s.K8sTestConstants.CONFIG_MAP_YAML;
import static software.wings.delegatetasks.k8s.K8sTestConstants.DEPLOYMENT_YAML;
import static software.wings.delegatetasks.k8s.K8sTestConstants.SECRET_YAML;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.k8s.K8sRollingBaseHandler;
import io.harness.delegate.k8s.beans.K8sHandlerConfig;
import io.harness.delegate.k8s.beans.K8sRollingHandlerConfig;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.KubernetesYamlException;
import io.harness.helpers.k8s.releasehistory.K8sReleaseHandler;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.releasehistory.IK8sRelease;
import io.harness.k8s.releasehistory.IK8sReleaseHistory;
import io.harness.k8s.releasehistory.K8SLegacyReleaseHistory;
import io.harness.k8s.releasehistory.K8sLegacyRelease;
import io.harness.k8s.releasehistory.K8sRelease;
import io.harness.k8s.releasehistory.K8sReleaseHistory;
import io.harness.k8s.releasehistory.ReleaseHistory;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.delegatetasks.k8s.K8sTaskHelper;
import software.wings.delegatetasks.k8s.K8sTestHelper;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.helm.request.HelmChartConfigParams;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.helpers.ext.k8s.request.K8sDelegateManifestConfig;
import software.wings.helpers.ext.k8s.request.K8sRollingDeployTaskParameters;
import software.wings.helpers.ext.k8s.response.K8sRollingDeployResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;

import com.google.common.collect.ImmutableList;
import io.kubernetes.client.openapi.models.V1Secret;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class K8sRollingDeployTaskHandlerTest extends WingsBaseTest {
  @Mock private ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;
  @Mock private KubernetesContainerService kubernetesContainerService;
  @Mock private K8sTaskHelper k8sTaskHelper;
  @Mock private K8sTaskHelperBase k8sTaskHelperBase;
  @Mock private K8sRollingBaseHandler k8sRollingBaseHandler;
  @Mock private ExecutionLogCallback executionLogCallback;
  @Mock private K8sReleaseHandler releaseHandler;
  @Mock private IK8sRelease release;
  @Mock private IK8sReleaseHistory releaseHistory;

  @InjectMocks private K8sRollingDeployTaskHandler k8sRollingDeployTaskHandler;
  @InjectMocks private K8sRollingBaseHandler k8sRollingDeployTaskBaseHandler;

  @Before
  public void setUp() throws Exception {
    doReturn(executionLogCallback).when(k8sTaskHelper).getExecutionLogCallback(any(), any());
    doReturn(KubernetesConfig.builder().build())
        .when(containerDeploymentDelegateHelper)
        .getKubernetesConfig(any(), anyBoolean());

    List<KubernetesResource> kubernetesResources = getResources();
    doReturn(true).when(k8sTaskHelper).fetchManifestFilesAndWriteToDirectory(any(), any(), any(), anyLong());
    doReturn(kubernetesResources)
        .when(k8sTaskHelperBase)
        .readManifestAndOverrideLocalSecrets(any(), any(), anyBoolean());
    doReturn(true).when(k8sTaskHelperBase).dryRunManifests(any(), any(), any(), any(), anyBoolean());
    doReturn(true).when(k8sTaskHelperBase).applyManifests(any(), any(), any(), any(), anyBoolean(), any());
    doReturn(true)
        .when(k8sTaskHelperBase)
        .doStatusCheckForAllResources(any(), any(), any(), any(), any(), anyBoolean());
    doReturn(true)
        .when(k8sTaskHelperBase)
        .doStatusCheckForAllCustomResources(any(), any(), any(), any(), anyBoolean(), anyLong());
    doReturn(releaseHandler).when(k8sTaskHelperBase).getReleaseHandler(anyBoolean());
    doReturn(releaseHistory).when(releaseHandler).getReleaseHistory(any(), any());
    doReturn(10).when(releaseHistory).getNextReleaseNumber(anyBoolean());
    doReturn(release).when(releaseHandler).createRelease(anyString(), anyInt());
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testDryRunIsSkipped() throws Exception {
    K8sRollingDeployTaskParameters rollingDeployTaskParams =
        K8sRollingDeployTaskParameters.builder().skipDryRun(true).build();
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();
    ExecutionLogCallback executionLogCallback = new ExecutionLogCallback();

    doNothing().when(k8sTaskHelperBase).deleteSkippedManifestFiles(any(), any());
    when(k8sTaskHelper.renderTemplate(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(emptyList());
    doNothing().when(k8sTaskHelperBase).setNamespaceToKubernetesResourcesIfRequired(any(), any());
    when(k8sTaskHelperBase.readManifestAndOverrideLocalSecrets(any(), any(), anyBoolean())).thenReturn(emptyList());

    k8sRollingDeployTaskHandler.init(rollingDeployTaskParams, delegateTaskParams, executionLogCallback);
    verify(k8sTaskHelperBase, times(0)).dryRunManifests(any(), any(), any(), any(), anyBoolean());
    verify(k8sTaskHelperBase, times(0)).updateVirtualServiceManifestFilesWithRoutesForCanary(any(), any(), any());
    verify(k8sTaskHelperBase, times(0)).updateDestinationRuleManifestFilesWithSubsets(any(), any(), any(), any());
    verify(k8sTaskHelperBase, times(1)).readManifestAndOverrideLocalSecrets(any(), any(), anyBoolean());
    verify(k8sTaskHelper, times(1)).renderTemplate(any(), any(), any(), any(), any(), any(), any(), any());
    verify(k8sTaskHelperBase, times(1)).setNamespaceToKubernetesResourcesIfRequired(any(), any());
    verify(k8sTaskHelperBase, times(1)).deleteSkippedManifestFiles(any(), any());
    verify(containerDeploymentDelegateHelper, times(1)).getKubernetesConfig(any(), anyBoolean());
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testDryRunIsNotSkipped() throws Exception {
    K8sRollingDeployTaskParameters rollingDeployTaskParams =
        K8sRollingDeployTaskParameters.builder().skipDryRun(false).build();
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();
    ExecutionLogCallback executionLogCallback = new ExecutionLogCallback();

    doNothing().when(k8sTaskHelperBase).setNamespaceToKubernetesResourcesIfRequired(any(), any());
    doNothing().when(k8sTaskHelperBase).deleteSkippedManifestFiles(any(), any());
    when(k8sTaskHelper.renderTemplate(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(emptyList());
    when(k8sTaskHelperBase.readManifestAndOverrideLocalSecrets(any(), any(), anyBoolean())).thenReturn(emptyList());

    k8sRollingDeployTaskHandler.init(rollingDeployTaskParams, delegateTaskParams, executionLogCallback);
    verify(k8sTaskHelperBase, times(1)).dryRunManifests(any(), any(), any(), any(), anyBoolean());
    verify(k8sTaskHelperBase, times(0)).updateVirtualServiceManifestFilesWithRoutesForCanary(any(), any(), any());
    verify(k8sTaskHelperBase, times(0)).updateDestinationRuleManifestFilesWithSubsets(any(), any(), any(), any());
    verify(k8sTaskHelperBase, times(1)).setNamespaceToKubernetesResourcesIfRequired(any(), any());
    verify(k8sTaskHelperBase, times(1)).readManifestAndOverrideLocalSecrets(any(), any(), anyBoolean());
    verify(k8sTaskHelper, times(1)).renderTemplate(any(), any(), any(), any(), any(), any(), any(), any());
    verify(k8sTaskHelperBase, times(1)).deleteSkippedManifestFiles(any(), any());
    verify(containerDeploymentDelegateHelper, times(1)).getKubernetesConfig(any(), anyBoolean());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testAssignHelmChartInfo() throws Exception {
    K8sRollingDeployTaskHandler handler = spy(k8sRollingDeployTaskHandler);
    K8sDelegateManifestConfig manifestConfig = K8sDelegateManifestConfig.builder()
                                                   .manifestStoreTypes(StoreType.HelmChartRepo)
                                                   .helmChartConfigParams(HelmChartConfigParams.builder().build())
                                                   .build();
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().workingDirectory(".").build();
    K8sRollingDeployTaskParameters rollingDeployTaskParams = K8sRollingDeployTaskParameters.builder()
                                                                 .k8sDelegateManifestConfig(manifestConfig)
                                                                 .releaseName("releaseName")
                                                                 .skipDryRun(true)
                                                                 .useDeclarativeRollback(true)
                                                                 .build();
    HelmChartInfo helmChartInfo = HelmChartInfo.builder().name("chart").version("1.0.0").build();
    K8sReleaseHistory releaseHistory = mock(K8sReleaseHistory.class);
    doReturn(releaseHistory).when(releaseHandler).getReleaseHistory(any(), any());
    K8sRelease currentRelease = mock(K8sRelease.class);
    doReturn(currentRelease).when(releaseHandler).createRelease(anyString(), anyInt());
    doReturn(currentRelease).when(currentRelease).setReleaseData(anyList(), anyBoolean());
    K8sRollingHandlerConfig k8sRollingHandlerConfig = new K8sRollingHandlerConfig();
    k8sRollingHandlerConfig.setResources(Lists.emptyList());
    k8sRollingHandlerConfig.setReleaseHistory(releaseHistory);
    on(handler).set("k8sRollingHandlerConfig", k8sRollingHandlerConfig);
    doReturn(true).when(handler).init(
        any(K8sRollingDeployTaskParameters.class), any(K8sDelegateTaskParams.class), any(ExecutionLogCallback.class));
    doReturn(true)
        .when(k8sTaskHelper)
        .fetchManifestFilesAndWriteToDirectory(
            any(K8sDelegateManifestConfig.class), any(), any(ExecutionLogCallback.class), anyLong());
    doReturn(true)
        .when(k8sTaskHelperBase)
        .applyManifests(any(Kubectl.class), any(), any(K8sDelegateTaskParams.class), any(ExecutionLogCallback.class),
            anyBoolean(), any());
    doReturn(helmChartInfo)
        .when(k8sTaskHelper)
        .getHelmChartDetails(manifestConfig, Paths.get(".", MANIFEST_FILES_DIR).toString());

    K8sTaskExecutionResponse response = handler.executeTask(rollingDeployTaskParams, delegateTaskParams);
    K8sRollingDeployResponse rollingDeployResponse = (K8sRollingDeployResponse) response.getK8sTaskResponse();

    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(rollingDeployResponse.getHelmChartInfo()).isEqualTo(helmChartInfo);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void invalidTypeOfTaskParams() {
    assertThatExceptionOfType(InvalidArgumentsException.class)
        .isThrownBy(() -> k8sRollingDeployTaskHandler.executeTaskInternal(null, null))
        .withMessageContaining("INVALID_ARGUMENT");
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void getFailureResponse() throws Exception {
    k8sRollingDeployTaskHandler.executeTaskInternal(
        K8sRollingDeployTaskParameters.builder()
            .k8sDelegateManifestConfig(K8sDelegateManifestConfig.builder().build())
            .build(),
        K8sDelegateTaskParams.builder().workingDirectory("/some/dir/").build());
    verify(k8sTaskHelper, times(1))
        .fetchManifestFilesAndWriteToDirectory(
            any(K8sDelegateManifestConfig.class), any(), any(ExecutionLogCallback.class), anyLong());
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void prepareForRollingNotCanary() throws Exception {
    List<KubernetesResource> kubernetesResources = getResources();
    K8sReleaseHistory releaseHistory = mock(K8sReleaseHistory.class);
    doReturn(releaseHistory).when(releaseHandler).getReleaseHistory(any(), any());
    K8sRelease currentRelease = K8sRelease.builder().releaseSecret(new V1Secret()).build();
    doReturn(currentRelease).when(releaseHandler).createRelease(any(), anyInt());
    K8sRollingDeployTaskHandler handler = spy(k8sRollingDeployTaskHandler);
    doReturn(kubernetesResources)
        .when(k8sTaskHelperBase)
        .readManifestAndOverrideLocalSecrets(any(), any(ExecutionLogCallback.class), anyBoolean());
    doReturn(true)
        .when(k8sTaskHelper)
        .fetchManifestFilesAndWriteToDirectory(
            any(K8sDelegateManifestConfig.class), any(), any(ExecutionLogCallback.class), anyLong());
    doReturn(true)
        .when(k8sTaskHelperBase)
        .dryRunManifests(
            any(Kubectl.class), any(), any(K8sDelegateTaskParams.class), any(ExecutionLogCallback.class), anyBoolean());
    doReturn(true)
        .when(k8sTaskHelperBase)
        .applyManifests(any(Kubectl.class), any(), any(K8sDelegateTaskParams.class), any(ExecutionLogCallback.class),
            anyBoolean(), any());
    handler.executeTaskInternal(K8sRollingDeployTaskParameters.builder()
                                    .k8sDelegateManifestConfig(K8sDelegateManifestConfig.builder().build())
                                    .releaseName("releaseName")
                                    .isInCanaryWorkflow(false)
                                    .skipAddingSelectorToDeployment(false)
                                    .useDeclarativeRollback(true)
                                    .build(),
        K8sDelegateTaskParams.builder().workingDirectory("/some/dir/").build());

    verify(k8sTaskHelper, times(1))
        .fetchManifestFilesAndWriteToDirectory(
            any(K8sDelegateManifestConfig.class), any(), any(ExecutionLogCallback.class), anyLong());
    verify(releaseHandler).getReleaseHistory(any(), any());
    verify(containerDeploymentDelegateHelper, times(1)).getKubernetesConfig(any(), anyBoolean());
    verify(k8sTaskHelperBase, times(1))
        .dryRunManifests(
            any(Kubectl.class), any(), any(K8sDelegateTaskParams.class), any(ExecutionLogCallback.class), anyBoolean());
    verify(k8sTaskHelperBase, times(1))
        .applyManifests(any(Kubectl.class), any(), any(K8sDelegateTaskParams.class), any(ExecutionLogCallback.class),
            anyBoolean(), any());
    verify(k8sRollingBaseHandler, times(1)).addLabelsInDeploymentSelectorForCanary(eq(false), eq(false), any(), any());
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void prepareForRollingIsCanary() throws Exception {
    K8sReleaseHistory releaseHistory = mock(K8sReleaseHistory.class);
    doReturn(releaseHistory).when(releaseHandler).getReleaseHistory(any(), any());
    K8sRelease currentRelease = K8sRelease.builder().releaseSecret(new V1Secret()).build();
    doReturn(currentRelease).when(releaseHistory).getLatestRelease();
    doReturn(true)
        .when(k8sTaskHelper)
        .fetchManifestFilesAndWriteToDirectory(
            any(K8sDelegateManifestConfig.class), any(), any(ExecutionLogCallback.class), anyLong());
    List<KubernetesResource> kubernetesResources = getResources();
    doReturn(kubernetesResources)
        .when(k8sTaskHelperBase)
        .readManifestAndOverrideLocalSecrets(any(), any(ExecutionLogCallback.class), anyBoolean());
    K8sRollingDeployTaskHandler handler = spy(k8sRollingDeployTaskHandler);
    doReturn(true)
        .when(k8sTaskHelperBase)
        .dryRunManifests(
            any(Kubectl.class), any(), any(K8sDelegateTaskParams.class), any(ExecutionLogCallback.class), anyBoolean());
    doReturn(true)
        .when(k8sTaskHelperBase)
        .applyManifests(any(Kubectl.class), any(), any(K8sDelegateTaskParams.class), any(ExecutionLogCallback.class),
            anyBoolean(), any());
    handler.executeTaskInternal(K8sRollingDeployTaskParameters.builder()
                                    .k8sDelegateManifestConfig(K8sDelegateManifestConfig.builder().build())
                                    .releaseName("releaseName")
                                    .isInCanaryWorkflow(true)
                                    .skipAddingSelectorToDeployment(true)
                                    .useDeclarativeRollback(true)
                                    .build(),
        K8sDelegateTaskParams.builder().workingDirectory("/some/dir/").build());

    verify(k8sTaskHelper, times(1))
        .fetchManifestFilesAndWriteToDirectory(
            any(K8sDelegateManifestConfig.class), any(), any(ExecutionLogCallback.class), anyLong());
    verify(releaseHandler).getReleaseHistory(any(), any());
    verify(containerDeploymentDelegateHelper, times(1)).getKubernetesConfig(any(), anyBoolean());
    verify(k8sTaskHelperBase, times(1))
        .dryRunManifests(
            any(Kubectl.class), any(), any(K8sDelegateTaskParams.class), any(ExecutionLogCallback.class), anyBoolean());
    verify(k8sTaskHelperBase, times(1))
        .applyManifests(any(Kubectl.class), any(), any(K8sDelegateTaskParams.class), any(ExecutionLogCallback.class),
            anyBoolean(), any());
    verify(handler, never()).prune(any(), any(), any());
    verify(k8sRollingBaseHandler, times(1)).addLabelsInDeploymentSelectorForCanary(eq(true), eq(true), any(), any());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExceptionThrownWhenGettingLoadBalancerEndpoint() throws Exception {
    K8sReleaseHistory releaseHistory = mock(K8sReleaseHistory.class);
    doReturn(releaseHistory).when(releaseHandler).getReleaseHistory(any(), any());
    K8sRelease currentRelease = K8sRelease.builder().releaseSecret(new V1Secret()).build();
    doReturn(currentRelease).when(releaseHistory).getLatestRelease();
    doReturn(true)
        .when(k8sTaskHelper)
        .fetchManifestFilesAndWriteToDirectory(
            any(K8sDelegateManifestConfig.class), any(), any(ExecutionLogCallback.class), anyLong());
    List<KubernetesResource> kubernetesResources = getResources();
    doReturn(kubernetesResources)
        .when(k8sTaskHelperBase)
        .readManifestAndOverrideLocalSecrets(any(), any(ExecutionLogCallback.class), anyBoolean());
    K8sRollingDeployTaskHandler handler = spy(k8sRollingDeployTaskHandler);
    doReturn(true)
        .when(k8sTaskHelperBase)
        .dryRunManifests(
            any(Kubectl.class), any(), any(K8sDelegateTaskParams.class), any(ExecutionLogCallback.class), anyBoolean());
    doReturn(true)
        .when(k8sTaskHelperBase)
        .applyManifests(any(Kubectl.class), any(), any(K8sDelegateTaskParams.class), any(ExecutionLogCallback.class),
            anyBoolean(), any());

    doReturn(true)
        .when(k8sTaskHelperBase)
        .doStatusCheckForAllResources(any(Kubectl.class), any(), any(K8sDelegateTaskParams.class), any(),
            any(ExecutionLogCallback.class), anyBoolean());

    doReturn(true)
        .when(k8sTaskHelperBase)
        .doStatusCheckForAllCustomResources(any(Kubectl.class), any(), any(K8sDelegateTaskParams.class),
            any(ExecutionLogCallback.class), anyBoolean(), anyLong());

    doThrow(new InvalidArgumentsException("reason")).when(k8sTaskHelperBase).getLoadBalancerEndpoint(any(), any());

    assertThatExceptionOfType(InvalidArgumentsException.class)
        .isThrownBy(()
                        -> k8sRollingDeployTaskHandler.executeTaskInternal(
                            K8sRollingDeployTaskParameters.builder()
                                .k8sDelegateManifestConfig(K8sDelegateManifestConfig.builder().build())
                                .releaseName("releaseName")
                                .isInCanaryWorkflow(true)
                                .useDeclarativeRollback(true)
                                .build(),
                            K8sDelegateTaskParams.builder().workingDirectory("/some/dir/").build()))
        .withMessageContaining("reason");

    verify(executionLogCallback, times(1)).saveExecutionLog("Invalid argument(s): reason", ERROR, FAILURE);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testInitException() throws Exception {
    doThrow(new KubernetesYamlException("reason"))
        .when(k8sTaskHelperBase)
        .deleteSkippedManifestFiles(any(), any(ExecutionLogCallback.class));
    final boolean success = k8sRollingDeployTaskHandler.init(K8sRollingDeployTaskParameters.builder().build(),
        K8sDelegateTaskParams.builder().build(), mock(ExecutionLogCallback.class));
    assertThat(success).isFalse();
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testSuccessIfPassesStatusCheck() throws Exception {
    K8sRollingDeployTaskHandler handler = spy(k8sRollingDeployTaskHandler);
    K8sDelegateManifestConfig manifestConfig = K8sDelegateManifestConfig.builder()
                                                   .manifestStoreTypes(StoreType.HelmChartRepo)
                                                   .helmChartConfigParams(HelmChartConfigParams.builder().build())
                                                   .build();
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().workingDirectory(".").build();
    K8sRollingDeployTaskParameters rollingDeployTaskParams = K8sRollingDeployTaskParameters.builder()
                                                                 .releaseName("RN-123")
                                                                 .k8sDelegateManifestConfig(manifestConfig)
                                                                 .k8sClusterConfig(K8sClusterConfig.builder().build())
                                                                 .skipDryRun(true)
                                                                 .useDeclarativeRollback(true)
                                                                 .build();

    K8sRollingHandlerConfig k8sRollingHandlerConfig = new K8sRollingHandlerConfig();
    k8sRollingHandlerConfig.setResources(Lists.emptyList());
    k8sRollingHandlerConfig.setReleaseHistory(releaseHistory);
    on(handler).set("k8sRollingHandlerConfig", k8sRollingHandlerConfig);

    doReturn(true).when(handler).init(
        any(K8sRollingDeployTaskParameters.class), any(K8sDelegateTaskParams.class), any(ExecutionLogCallback.class));
    doReturn(true)
        .when(k8sTaskHelper)
        .fetchManifestFilesAndWriteToDirectory(
            any(K8sDelegateManifestConfig.class), any(), any(ExecutionLogCallback.class), anyLong());
    doReturn(true)
        .when(k8sTaskHelperBase)
        .applyManifests(any(Kubectl.class), any(), any(K8sDelegateTaskParams.class), any(ExecutionLogCallback.class),
            anyBoolean(), any());
    doReturn(true)
        .when(k8sTaskHelperBase)
        .doStatusCheckForAllResources(any(Kubectl.class), any(), any(K8sDelegateTaskParams.class), any(),
            any(ExecutionLogCallback.class), anyBoolean());

    doReturn(true)
        .when(k8sTaskHelperBase)
        .doStatusCheckForAllCustomResources(any(Kubectl.class), any(), any(K8sDelegateTaskParams.class),
            any(ExecutionLogCallback.class), anyBoolean(), anyLong());

    K8sTaskExecutionResponse response = handler.executeTask(rollingDeployTaskParams, delegateTaskParams);

    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testFailCrdStatusCheck() throws Exception {
    K8sRollingDeployTaskHandler handler = spy(k8sRollingDeployTaskHandler);
    K8sDelegateManifestConfig manifestConfig = K8sDelegateManifestConfig.builder()
                                                   .manifestStoreTypes(StoreType.HelmChartRepo)
                                                   .helmChartConfigParams(HelmChartConfigParams.builder().build())
                                                   .build();
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().workingDirectory(".").build();
    K8sRollingDeployTaskParameters rollingDeployTaskParams = K8sRollingDeployTaskParameters.builder()
                                                                 .releaseName("RN-123")
                                                                 .k8sDelegateManifestConfig(manifestConfig)
                                                                 .k8sClusterConfig(K8sClusterConfig.builder().build())
                                                                 .skipDryRun(true)
                                                                 .build();
    ReleaseHistory releaseHist = ReleaseHistory.createNew();
    releaseHist.setReleases(new ArrayList<>());
    K8sRollingHandlerConfig k8sRollingHandlerConfig = new K8sRollingHandlerConfig();
    k8sRollingHandlerConfig.setResources(ImmutableList.of(K8sTestHelper.deployment()));
    k8sRollingHandlerConfig.setManagedWorkloads(ImmutableList.of(K8sTestHelper.deployment()));
    k8sRollingHandlerConfig.setKubernetesConfig(KubernetesConfig.builder().build());
    on(handler).set("k8sRollingHandlerConfig", k8sRollingHandlerConfig);

    doReturn(true).when(handler).init(any(), any(), any());
    doReturn(true).when(k8sTaskHelper).fetchManifestFilesAndWriteToDirectory(any(), any(), any(), anyLong());
    doReturn(true).when(k8sTaskHelperBase).applyManifests(any(), any(), any(), any(), anyBoolean(), any());
    doReturn(true)
        .when(k8sTaskHelperBase)
        .doStatusCheckForAllResources(any(), any(), any(), any(), any(), anyBoolean());

    doReturn(false)
        .when(k8sTaskHelperBase)
        .doStatusCheckForAllCustomResources(any(), any(), any(), any(), anyBoolean(), anyLong());

    K8sTaskExecutionResponse response = handler.executeTask(rollingDeployTaskParams, delegateTaskParams);

    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testFailWorkloadsStatusCheck() throws Exception {
    K8sRollingDeployTaskHandler handler = spy(k8sRollingDeployTaskHandler);
    K8sDelegateManifestConfig manifestConfig = K8sDelegateManifestConfig.builder()
                                                   .manifestStoreTypes(StoreType.HelmChartRepo)
                                                   .helmChartConfigParams(HelmChartConfigParams.builder().build())
                                                   .build();
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().workingDirectory(".").build();
    K8sRollingDeployTaskParameters rollingDeployTaskParams = K8sRollingDeployTaskParameters.builder()
                                                                 .releaseName("RN-123")
                                                                 .k8sDelegateManifestConfig(manifestConfig)
                                                                 .k8sClusterConfig(K8sClusterConfig.builder().build())
                                                                 .skipDryRun(true)
                                                                 .build();
    ReleaseHistory releaseHist = ReleaseHistory.createNew();
    releaseHist.setReleases(new ArrayList<>());

    K8sRollingHandlerConfig k8sRollingHandlerConfig = new K8sRollingHandlerConfig();
    k8sRollingHandlerConfig.setResources(ImmutableList.of(K8sTestHelper.deployment()));
    k8sRollingHandlerConfig.setManagedWorkloads(ImmutableList.of(K8sTestHelper.deployment()));
    k8sRollingHandlerConfig.setKubernetesConfig(KubernetesConfig.builder().build());
    on(handler).set("k8sRollingHandlerConfig", k8sRollingHandlerConfig);

    doReturn(true).when(handler).init(any(), any(), any());
    doReturn(true).when(k8sTaskHelper).fetchManifestFilesAndWriteToDirectory(any(), any(), any(), anyLong());
    doReturn(true).when(k8sTaskHelperBase).applyManifests(any(), any(), any(), any(), anyBoolean(), any());
    doReturn(false)
        .when(k8sTaskHelperBase)
        .doStatusCheckForAllResources(any(), any(), any(), any(), any(), anyBoolean());

    doReturn(true)
        .when(k8sTaskHelperBase)
        .doStatusCheckForAllCustomResources(any(), any(), any(), any(), anyBoolean(), anyLong());

    K8sTaskExecutionResponse response = handler.executeTask(rollingDeployTaskParams, delegateTaskParams);

    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  private List<KubernetesResource> getResources() {
    List<KubernetesResource> kubernetesResources = new ArrayList<>();
    kubernetesResources.addAll(ManifestHelper.processYaml(DEPLOYMENT_YAML));
    return kubernetesResources;
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testVersioningOfConfigMapAndSecret() throws Exception {
    List<KubernetesResource> kubernetesResources = new ArrayList<>();
    kubernetesResources.addAll(ManifestHelper.processYaml(CONFIG_MAP_YAML));

    K8sRollingDeployTaskHandler handler = spy(k8sRollingDeployTaskHandler);
    K8SLegacyReleaseHistory releaseHistory = mock(K8SLegacyReleaseHistory.class);
    ReleaseHistory releaseHistoryContent = mock(ReleaseHistory.class);
    K8sLegacyRelease release = mock(K8sLegacyRelease.class);
    doReturn(releaseHistory).when(releaseHandler).getReleaseHistory(any(), any());
    doReturn(1).when(releaseHistory).getNextReleaseNumber(anyBoolean());
    doReturn(release).when(releaseHandler).createRelease(any(), anyInt());
    doReturn(release).when(release).setReleaseData(anyList(), anyBoolean());
    doReturn(1).when(release).getReleaseNumber();
    doReturn(releaseHistoryContent).when(releaseHistory).getReleaseHistory();
    doReturn(release).when(releaseHistoryContent).addReleaseToReleaseHistory(any());

    doReturn(kubernetesResources)
        .when(k8sTaskHelperBase)
        .readManifestAndOverrideLocalSecrets(any(), any(ExecutionLogCallback.class), anyBoolean());
    doReturn(true)
        .when(k8sTaskHelper)
        .fetchManifestFilesAndWriteToDirectory(
            any(K8sDelegateManifestConfig.class), any(), any(ExecutionLogCallback.class), anyLong());
    doReturn(true)
        .when(k8sTaskHelperBase)
        .dryRunManifests(
            any(Kubectl.class), any(), any(K8sDelegateTaskParams.class), any(ExecutionLogCallback.class), anyBoolean());

    handler.executeTaskInternal(K8sRollingDeployTaskParameters.builder()
                                    .k8sDelegateManifestConfig(K8sDelegateManifestConfig.builder().build())
                                    .releaseName("releaseName")
                                    .isInCanaryWorkflow(false)
                                    .useDeclarativeRollback(true)
                                    .build(),
        K8sDelegateTaskParams.builder().workingDirectory("/some/dir/").build());
    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(k8sTaskHelperBase, times(1))
        .applyManifests(any(Kubectl.class), captor.capture(), any(K8sDelegateTaskParams.class),
            any(ExecutionLogCallback.class), anyBoolean(), any());
    List<KubernetesResource> kubernetesResourceList = captor.getValue();
    assertThat(kubernetesResourceList.get(0).getResourceId().isVersioned()).isFalse();

    kubernetesResources.clear();
    kubernetesResources.addAll(ManifestHelper.processYaml(SECRET_YAML));
    handler.executeTaskInternal(K8sRollingDeployTaskParameters.builder()
                                    .k8sDelegateManifestConfig(K8sDelegateManifestConfig.builder().build())
                                    .releaseName("releaseName")
                                    .isInCanaryWorkflow(false)
                                    .useDeclarativeRollback(false)
                                    .build(),
        K8sDelegateTaskParams.builder().workingDirectory("/some/dir/").build());
    captor = ArgumentCaptor.forClass(List.class);
    verify(k8sTaskHelperBase, times(2))
        .applyManifests(any(Kubectl.class), captor.capture(), any(K8sDelegateTaskParams.class),
            any(ExecutionLogCallback.class), anyBoolean(), any());
    kubernetesResourceList = captor.getValue();
    assertThat(kubernetesResourceList.get(0).getResourceId().isVersioned()).isFalse();

    kubernetesResources.addAll(ManifestHelper.processYaml(CONFIG_MAP_YAML));
    kubernetesResources.addAll(ManifestHelper.processYaml(DEPLOYMENT_YAML));
    handler.executeTaskInternal(K8sRollingDeployTaskParameters.builder()
                                    .k8sDelegateManifestConfig(K8sDelegateManifestConfig.builder().build())
                                    .releaseName("releaseName")
                                    .useDeclarativeRollback(false)
                                    .isInCanaryWorkflow(false)
                                    .build(),
        K8sDelegateTaskParams.builder().workingDirectory("/some/dir/").build());
    captor = ArgumentCaptor.forClass(List.class);
    verify(k8sTaskHelperBase, times(3))
        .applyManifests(any(Kubectl.class), captor.capture(), any(K8sDelegateTaskParams.class),
            any(ExecutionLogCallback.class), anyBoolean(), any());
    kubernetesResourceList = captor.getValue();
    List<String> resoucesName = kubernetesResourceList.stream()
                                    .map(resource -> resource.getResourceId().getName())
                                    .collect(Collectors.toList());
    assertThat(kubernetesResourceList.get(0).getResourceId().isVersioned()).isTrue();
    assertThat(kubernetesResourceList.get(1).getResourceId().isVersioned()).isTrue();
    assertThat(resoucesName).containsExactlyInAnyOrder("mysecret-1", "deployment", "mycm-1");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testSkipVersioningForAllK8sObject() throws Exception {
    List<KubernetesResource> kubernetesResources = new ArrayList<>();
    kubernetesResources.addAll(ManifestHelper.processYaml(SECRET_YAML));
    kubernetesResources.addAll(ManifestHelper.processYaml(CONFIG_MAP_YAML));
    kubernetesResources.addAll(ManifestHelper.processYaml(DEPLOYMENT_YAML));

    K8sRollingDeployTaskHandler handler = spy(k8sRollingDeployTaskHandler);
    doReturn(kubernetesResources)
        .when(k8sTaskHelperBase)
        .readManifestAndOverrideLocalSecrets(any(), any(ExecutionLogCallback.class), anyBoolean());
    doReturn(true)
        .when(k8sTaskHelper)
        .fetchManifestFilesAndWriteToDirectory(
            any(K8sDelegateManifestConfig.class), any(), any(ExecutionLogCallback.class), anyLong());
    doReturn(true)
        .when(k8sTaskHelperBase)
        .dryRunManifests(
            any(Kubectl.class), any(), any(K8sDelegateTaskParams.class), any(ExecutionLogCallback.class), anyBoolean());

    handler.executeTaskInternal(K8sRollingDeployTaskParameters.builder()
                                    .k8sDelegateManifestConfig(K8sDelegateManifestConfig.builder().build())
                                    .releaseName("releaseName")
                                    .isInCanaryWorkflow(false)
                                    .skipVersioningForAllK8sObjects(true)
                                    .useDeclarativeRollback(true)
                                    .build(),
        K8sDelegateTaskParams.builder().workingDirectory("/some/dir/").build());

    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(releaseHandler).getReleaseHistory(any(), any());
    verify(k8sTaskHelperBase, times(1))
        .applyManifests(any(Kubectl.class), captor.capture(), any(K8sDelegateTaskParams.class),
            any(ExecutionLogCallback.class), anyBoolean(), any());
    List<KubernetesResource> kubernetesResourceList = captor.getValue();

    assertThat(kubernetesResourceList.get(0).getResourceId().isVersioned()).isFalse();
    assertThat(kubernetesResourceList.get(1).getResourceId().isVersioned()).isFalse();
    assertThat(kubernetesResourceList.get(2).getResourceId().isVersioned()).isFalse();
    List<String> resourcesName = kubernetesResourceList.stream()
                                     .map(resource -> resource.getResourceId().getName())
                                     .collect(Collectors.toList());
    assertThat(resourcesName).containsExactlyInAnyOrder("mysecret", "deployment", "mycm");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalInheritManifests() throws Exception {
    List<KubernetesResource> inheritedKubernetesResources = new ArrayList<>();
    inheritedKubernetesResources.addAll(ManifestHelper.processYaml(SECRET_YAML));
    inheritedKubernetesResources.addAll(ManifestHelper.processYaml(CONFIG_MAP_YAML));
    inheritedKubernetesResources.addAll(ManifestHelper.processYaml(DEPLOYMENT_YAML));

    K8sRollingDeployTaskHandler handler = spy(k8sRollingDeployTaskHandler);
    K8sReleaseHistory releaseHistory = mock(K8sReleaseHistory.class);
    when(releaseHandler.getReleaseHistory(any(), any())).thenReturn(releaseHistory);
    when(releaseHistory.getNextReleaseNumber(anyBoolean())).thenReturn(2);
    when(releaseHandler.createRelease(any(), anyInt()))
        .thenReturn(K8sRelease.builder().releaseSecret(new V1Secret()).build());
    doAnswer(invocation -> {
      Object[] args = invocation.getArguments();
      K8sHandlerConfig k8sRollingHandlerConfig = (K8sHandlerConfig) args[3];
      k8sRollingHandlerConfig.setKubernetesConfig(KubernetesConfig.builder().build());
      k8sRollingHandlerConfig.setClient(Kubectl.client("", ""));
      k8sRollingHandlerConfig.setResources(inheritedKubernetesResources);
      return true;
    })
        .when(k8sTaskHelper)
        .restore(any(), any(), any(), any(), any());
    handler.executeTaskInternal(K8sRollingDeployTaskParameters.builder()
                                    .releaseName("releaseName")
                                    .isInCanaryWorkflow(false)
                                    .skipVersioningForAllK8sObjects(true)
                                    .kubernetesResources(inheritedKubernetesResources)
                                    .inheritManifests(true)
                                    .useDeclarativeRollback(true)
                                    .build(),
        K8sDelegateTaskParams.builder().workingDirectory("/some/dir/").build());

    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(handler, times(0)).init(any(), any(), any());
    verify(k8sTaskHelper, times(1)).restore(any(), any(), any(), any(), any());
    verify(releaseHandler).getReleaseHistory(any(), any());
    verify(k8sTaskHelperBase, times(1))
        .applyManifests(any(Kubectl.class), captor.capture(), any(K8sDelegateTaskParams.class),
            any(ExecutionLogCallback.class), anyBoolean(), any());
    List<KubernetesResource> kubernetesResourceList = captor.getValue();

    assertThat(kubernetesResourceList.get(0).getResourceId().isVersioned()).isFalse();
    assertThat(kubernetesResourceList.get(1).getResourceId().isVersioned()).isFalse();
    assertThat(kubernetesResourceList.get(2).getResourceId().isVersioned()).isFalse();
    List<String> resourcesName = kubernetesResourceList.stream()
                                     .map(resource -> resource.getResourceId().getName())
                                     .collect(Collectors.toList());
    assertThat(resourcesName).containsExactlyInAnyOrder("mysecret", "deployment", "mycm");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalExportManifests() throws Exception {
    List<KubernetesResource> kubernetesResources = new ArrayList<>();
    kubernetesResources.addAll(ManifestHelper.processYaml(SECRET_YAML));
    kubernetesResources.addAll(ManifestHelper.processYaml(CONFIG_MAP_YAML));
    kubernetesResources.addAll(ManifestHelper.processYaml(DEPLOYMENT_YAML));

    K8sRollingDeployTaskHandler handler = spy(k8sRollingDeployTaskHandler);
    doReturn(kubernetesResources)
        .when(k8sTaskHelperBase)
        .readManifestAndOverrideLocalSecrets(any(), any(ExecutionLogCallback.class), anyBoolean());
    doReturn(true)
        .when(k8sTaskHelper)
        .fetchManifestFilesAndWriteToDirectory(
            any(K8sDelegateManifestConfig.class), any(), any(ExecutionLogCallback.class), anyLong());
    doReturn(true)
        .when(k8sTaskHelperBase)
        .dryRunManifests(
            any(Kubectl.class), any(), any(K8sDelegateTaskParams.class), any(ExecutionLogCallback.class), anyBoolean());

    K8sTaskExecutionResponse k8sTaskExecutionResponse =
        handler.executeTaskInternal(K8sRollingDeployTaskParameters.builder()
                                        .releaseName("releaseName")
                                        .isInCanaryWorkflow(false)
                                        .skipVersioningForAllK8sObjects(true)
                                        .exportManifests(true)
                                        .build(),
            K8sDelegateTaskParams.builder().workingDirectory("/some/dir/").build());
    assertThat(k8sTaskExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(((K8sRollingDeployResponse) k8sTaskExecutionResponse.getK8sTaskResponse()).getResources()).isNotNull();

    verify(handler, times(1)).init(any(), any(), any());
    verify(k8sTaskHelperBase, times(0))
        .applyManifests(any(Kubectl.class), any(), any(K8sDelegateTaskParams.class), any(ExecutionLogCallback.class),
            anyBoolean(), any());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalFailedGetPods() throws Exception {
    InvalidRequestException thrownException = new InvalidRequestException("Failed to get pods");

    doReturn(emptyList())
        .when(k8sRollingBaseHandler)
        .getExistingPods(anyLong(), any(), any(), any(), any(LogCallback.class));
    doThrow(thrownException).when(k8sRollingBaseHandler).getPods(anyLong(), any(), any(), any());

    assertThatThrownBy(()
                           -> k8sRollingDeployTaskHandler.executeTaskInternal(
                               K8sRollingDeployTaskParameters.builder()
                                   .k8sDelegateManifestConfig(K8sDelegateManifestConfig.builder().build())
                                   .releaseName("releaseName")
                                   .isInCanaryWorkflow(false)
                                   .useDeclarativeRollback(true)
                                   .build(),
                               K8sDelegateTaskParams.builder().workingDirectory("/some/dir/").build()))
        .isEqualTo(thrownException);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testPruneWithNoPreviousSuccessfulRelease() throws Exception {
    K8sRollingDeployTaskHandler handler = spy(k8sRollingDeployTaskHandler);
    K8sRollingDeployTaskParameters taskParameters = K8sRollingDeployTaskParameters.builder().build();
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();

    List<KubernetesResourceId> prunedResource = handler.prune(taskParameters, delegateTaskParams, null);

    assertThat(prunedResource).isEmpty();
    // do nothing if no previous successful release exist
    verifyNoMoreInteractions(k8sTaskHelperBase);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testPruneWithLastDeploymentAtFfOff() throws Exception {
    K8sRollingDeployTaskHandler handler = spy(k8sRollingDeployTaskHandler);
    K8sRollingDeployTaskParameters taskParameters = K8sRollingDeployTaskParameters.builder().build();
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();
    K8sLegacyRelease previousSuccessfulRelease = K8sLegacyRelease.builder().build();

    List<KubernetesResourceId> prunedResource =
        handler.prune(taskParameters, delegateTaskParams, previousSuccessfulRelease);

    assertThat(prunedResource).isEmpty();
    // do nothing if no spec found in previousSuccessfulRelease
    verifyNoMoreInteractions(k8sTaskHelperBase);
    verify(executionLogCallback, times(1))
        .saveExecutionLog("Previous successful deployment executed with pruning disabled, Pruning can't be done", INFO,
            CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testPruneWhenNoResourceToBePruned() throws Exception {
    K8sRollingDeployTaskHandler handler = spy(k8sRollingDeployTaskHandler);
    K8sRollingHandlerConfig k8sRollingHandlerConfig = new K8sRollingHandlerConfig();
    k8sRollingHandlerConfig.setKubernetesConfig(KubernetesConfig.builder().namespace("ns").build());
    on(handler).set("k8sRollingHandlerConfig", k8sRollingHandlerConfig);

    K8sRollingDeployTaskParameters taskParameters = K8sRollingDeployTaskParameters.builder().build();
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();
    K8sLegacyRelease previousSuccessfulRelease = K8sLegacyRelease.builder().resourcesWithSpec(getResources()).build();

    doNothing().when(k8sTaskHelperBase).setNamespaceToKubernetesResourcesIfRequired(anyList(), any());
    doReturn(emptyList()).when(k8sTaskHelperBase).getResourcesToBePrunedInOrder(any(), any());

    List<KubernetesResourceId> prunedResource =
        handler.prune(taskParameters, delegateTaskParams, previousSuccessfulRelease);

    assertThat(prunedResource).isEmpty();
    verify(k8sTaskHelperBase, times(1)).getResourcesToBePrunedInOrder(any(), any());
    verify(k8sTaskHelperBase, never())
        .executeDeleteHandlingPartialExecution(
            any(Kubectl.class), any(K8sDelegateTaskParams.class), any(), any(LogCallback.class), anyBoolean());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testPruneFail() throws Exception {
    K8sRollingDeployTaskHandler handler = spy(k8sRollingDeployTaskHandler);
    K8sRollingHandlerConfig k8sRollingHandlerConfig = new K8sRollingHandlerConfig();
    k8sRollingHandlerConfig.setKubernetesConfig(KubernetesConfig.builder().namespace("ns").build());
    on(handler).set("k8sRollingHandlerConfig", k8sRollingHandlerConfig);
    K8sRollingDeployTaskParameters taskParameters = K8sRollingDeployTaskParameters.builder().build();
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();
    K8sLegacyRelease previousSuccessfulRelease = K8sLegacyRelease.builder().resourcesWithSpec(getResources()).build();

    doNothing().when(k8sTaskHelperBase).setNamespaceToKubernetesResourcesIfRequired(anyList(), any());
    doReturn(singletonList(KubernetesResourceId.builder().build()))
        .when(k8sTaskHelperBase)
        .getResourcesToBePrunedInOrder(any(), any());
    doThrow(new InvalidRequestException("dummy exception"))
        .when(k8sTaskHelperBase)
        .executeDeleteHandlingPartialExecution(any(), any(), any(), any(), anyBoolean());

    List<KubernetesResourceId> prunedResource =
        handler.prune(taskParameters, delegateTaskParams, previousSuccessfulRelease);

    assertThat(prunedResource).isEmpty();
    verify(k8sTaskHelperBase, times(1)).executeDeleteHandlingPartialExecution(any(), any(), any(), any(), anyBoolean());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testPruneSuccess() throws Exception {
    K8sRollingDeployTaskHandler handler = spy(k8sRollingDeployTaskHandler);
    K8sRollingHandlerConfig k8sRollingHandlerConfig = new K8sRollingHandlerConfig();
    k8sRollingHandlerConfig.setKubernetesConfig(KubernetesConfig.builder().namespace("ns").build());
    on(handler).set("k8sRollingHandlerConfig", k8sRollingHandlerConfig);
    K8sRollingDeployTaskParameters taskParameters = K8sRollingDeployTaskParameters.builder().build();
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();
    K8sLegacyRelease previousSuccessfulRelease = K8sLegacyRelease.builder().resourcesWithSpec(getResources()).build();

    KubernetesResourceId resources = KubernetesResourceId.builder().name("config-map").build();
    doNothing().when(k8sTaskHelperBase).setNamespaceToKubernetesResourcesIfRequired(anyList(), any());
    doReturn(singletonList(resources)).when(k8sTaskHelperBase).getResourcesToBePrunedInOrder(any(), any());
    doReturn(singletonList(resources))
        .when(k8sTaskHelperBase)
        .executeDeleteHandlingPartialExecution(any(), any(), any(), any(), anyBoolean());

    List<KubernetesResourceId> prunedResource =
        handler.prune(taskParameters, delegateTaskParams, previousSuccessfulRelease);

    assertThat(prunedResource).hasSize(1);
    assertThat(prunedResource.get(0).getName()).isEqualTo("config-map");
    verify(k8sTaskHelperBase, times(1)).executeDeleteHandlingPartialExecution(any(), any(), any(), any(), anyBoolean());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testExecuteTaskOnApplyManifestsFailShouldSaveReleaseAndWorkloads() throws Exception {
    K8sRollingDeployTaskHandler handler = spy(k8sRollingDeployTaskHandler);
    K8sDelegateManifestConfig manifestConfig = K8sDelegateManifestConfig.builder()
                                                   .manifestStoreTypes(StoreType.HelmChartRepo)
                                                   .helmChartConfigParams(HelmChartConfigParams.builder().build())
                                                   .build();
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().workingDirectory(".").build();
    K8sRollingDeployTaskParameters rollingDeployTaskParams = K8sRollingDeployTaskParameters.builder()
                                                                 .releaseName("RN-123")
                                                                 .k8sDelegateManifestConfig(manifestConfig)
                                                                 .k8sClusterConfig(K8sClusterConfig.builder().build())
                                                                 .skipDryRun(true)
                                                                 .useDeclarativeRollback(false)
                                                                 .build();

    K8SLegacyReleaseHistory releaseHistory = mock(K8SLegacyReleaseHistory.class);
    ReleaseHistory releaseHistoryContent = mock(ReleaseHistory.class);

    K8sRollingHandlerConfig k8sRollingHandlerConfig = new K8sRollingHandlerConfig();
    k8sRollingHandlerConfig.setResources(ImmutableList.of(K8sTestHelper.deployment()));
    k8sRollingHandlerConfig.setManagedWorkloads(ImmutableList.of(K8sTestHelper.deployment()));
    k8sRollingHandlerConfig.setReleaseHistory(releaseHistory);
    k8sRollingHandlerConfig.setKubernetesConfig(KubernetesConfig.builder().build());
    on(handler).set("k8sRollingHandlerConfig", k8sRollingHandlerConfig);

    doReturn(true).when(handler).init(any(), any(), any());
    doReturn(true).when(k8sTaskHelper).fetchManifestFilesAndWriteToDirectory(any(), any(), any(), anyLong());
    doReturn(false).when(k8sTaskHelperBase).applyManifests(any(), any(), any(), any(), anyBoolean(), eq(null));

    K8sLegacyRelease currentRelease = mock(K8sLegacyRelease.class);
    doReturn(currentRelease).when(releaseHandler).createRelease(anyString(), anyInt());
    doReturn(currentRelease).when(currentRelease).setReleaseData(anyList(), anyBoolean());
    doReturn(releaseHistoryContent).when(releaseHistory).getReleaseHistory();
    doReturn(null).when(releaseHistoryContent).addReleaseToReleaseHistory(any());
    doReturn(releaseHistory).when(releaseHandler).getReleaseHistory(any(), any());

    K8sTaskExecutionResponse response = handler.executeTask(rollingDeployTaskParams, delegateTaskParams);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    verify(k8sRollingBaseHandler, times(1)).setManagedWorkloadsInRelease(any(), any(), any(), any());
    verify(k8sRollingBaseHandler, times(1)).setCustomWorkloadsInRelease(any(), any());
    verify(k8sTaskHelperBase, times(1)).saveRelease(anyBoolean(), anyBoolean(), any(), any(), any(), any());
  }
}
