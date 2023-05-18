/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.helm;

import static io.harness.annotations.dev.HarnessModule._930_DELEGATE_TASKS;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.exception.WingsException.USER;
import static io.harness.filesystem.FileIo.deleteDirectoryAndItsContentIfExists;
import static io.harness.helm.HelmCommandType.RELEASE_HISTORY;
import static io.harness.helm.HelmSubCommandType.TEMPLATE;
import static io.harness.k8s.model.HelmVersion.V2;
import static io.harness.k8s.model.HelmVersion.V3;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.helpers.ext.helm.HelmDeployServiceImpl.WORKING_DIR;
import static software.wings.utils.WingsTestConstants.BRANCH_NAME;
import static software.wings.utils.WingsTestConstants.COMMIT_REFERENCE;
import static software.wings.utils.WingsTestConstants.FILE_PATH;
import static software.wings.utils.WingsTestConstants.LONG_TIMEOUT_INTERVAL;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.replace;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.spy;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.concurent.HTimeLimiterMocker;
import io.harness.container.ContainerInfo;
import io.harness.delegate.task.helm.CustomManifestFetchTaskHelper;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.delegate.task.helm.HelmCommandFlag;
import io.harness.delegate.task.helm.HelmCommandResponse;
import io.harness.delegate.task.helm.HelmTaskHelperBase;
import io.harness.delegate.task.helm.HelmTestConstants;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.filesystem.FileIo;
import io.harness.git.model.GitFile;
import io.harness.helm.HelmClient;
import io.harness.helm.HelmClientImpl.HelmCliResponse;
import io.harness.helm.HelmCommandData;
import io.harness.helm.HelmCommandType;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.config.K8sGlobalConfigService;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.HelmVersion;
import io.harness.k8s.model.Kind;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.releasehistory.IK8sRelease;
import io.harness.k8s.releasehistory.ReleaseHistory;
import io.harness.logging.CommandExecutionStatus;
import io.harness.manifest.CustomManifestSource;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.GitConfig;
import software.wings.beans.GitFileConfig;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.beans.command.HelmDummyCommandUnitConstants;
import software.wings.beans.dto.HelmChartSpecification;
import software.wings.beans.yaml.GitFetchFilesResult;
import software.wings.delegatetasks.ScmFetchFilesHelper;
import software.wings.delegatetasks.helm.HarnessHelmDeployConfig;
import software.wings.delegatetasks.helm.HelmCommandHelper;
import software.wings.delegatetasks.helm.HelmDeployChartSpec;
import software.wings.delegatetasks.helm.HelmTaskHelper;
import software.wings.delegatetasks.k8s.K8sTaskHelper;
import software.wings.delegatetasks.validation.capabilities.HelmCommandRequest;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.helm.request.HelmChartConfigParams;
import software.wings.helpers.ext.helm.request.HelmInstallCommandRequest;
import software.wings.helpers.ext.helm.request.HelmReleaseHistoryCommandRequest;
import software.wings.helpers.ext.helm.request.HelmRollbackCommandRequest;
import software.wings.helpers.ext.helm.response.HelmInstallCommandResponse;
import software.wings.helpers.ext.helm.response.HelmListReleasesCommandResponse;
import software.wings.helpers.ext.helm.response.HelmReleaseHistoryCommandResponse;
import software.wings.helpers.ext.helm.response.ReleaseInfo;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.helpers.ext.k8s.request.K8sDelegateManifestConfig;
import software.wings.service.impl.ContainerServiceParams;
import software.wings.service.impl.yaml.GitClientHelper;
import software.wings.service.intfc.GitService;
import software.wings.service.intfc.security.EncryptionService;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FakeTimeLimiter;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import wiremock.com.google.common.collect.ImmutableMap;

@OwnedBy(CDP)
@TargetModule(_930_DELEGATE_TASKS)
public class HelmDeployServiceImplTest extends WingsBaseTest {
  @Mock private HelmClient helmClient;
  @Mock private GitService gitService;
  @Mock private TimeLimiter mockTimeLimiter;
  @Mock private EncryptionService encryptionService;
  @Mock private HelmCommandHelper helmCommandHelper;
  @Spy private HelmTaskHelperBase helmTaskHelperBase;
  @Mock private ExecutionLogCallback logCallback;
  @Mock private HelmTaskHelper helmTaskHelper;
  @Mock private ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;
  @Mock private K8sGlobalConfigService k8sGlobalConfigService;
  @Mock private K8sTaskHelper k8sTaskHelper;
  @Mock private K8sTaskHelperBase k8sTaskHelperBase;
  @Mock private GitClientHelper gitClientHelper;
  @Mock private ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;
  @Mock private KubernetesContainerService kubernetesContainerService;
  @Mock private HelmHelper helmHelper;
  @Mock private ScmFetchFilesHelper scmFetchFilesHelper;
  @Mock private CustomManifestFetchTaskHelper customManifestFetchTaskHelper;
  @InjectMocks private HelmDeployServiceImpl helmDeployService;

  @Captor private ArgumentCaptor<HelmCommandFlag> commandFlagCaptor;

  private final String flagValue = "--flag-test-1";
  private final HelmCommandFlag commandFlag =
      HelmCommandFlag.builder().valueMap(ImmutableMap.of(TEMPLATE, flagValue)).build();

  private HelmDeployServiceImpl spyHelmDeployService = spy(new HelmDeployServiceImpl());

  private TimeLimiter timeLimiter = new FakeTimeLimiter();
  private HelmInstallCommandRequest helmInstallCommandRequest;
  private HelmInstallCommandResponse helmInstallCommandResponse;
  private HelmCliResponse helmCliReleaseHistoryResponse;
  private HelmCliResponse helmCliListReleasesResponse;
  private HelmCliResponse helmCliResponse;
  private GitFileConfig gitFileConfig;
  ExecutionLogCallback executionLogCallback;

  @Before
  public void setUp() throws Exception {
    helmInstallCommandRequest = createHelmInstallCommandRequest();
    helmInstallCommandResponse = createHelmInstallCommandResponse();
    helmCliReleaseHistoryResponse = createHelmCliResponse();
    helmCliListReleasesResponse = createHelmCliResponse();
    helmCliResponse = createHelmCliResponse();
    gitFileConfig = new GitFileConfig();
    gitFileConfig.setFilePath(HelmTestConstants.FILE_PATH_KEY);
    executionLogCallback = mock(ExecutionLogCallback.class);
    doNothing().when(executionLogCallback).saveExecutionLog(anyString());
    when(encryptionService.decrypt(any(), any(), eq(false))).thenReturn(null);
    when(gitService.fetchFilesByPath(any(), any(), any(), any(), any(), anyBoolean(), eq(false), any()))
        .thenReturn(GitFetchFilesResult.builder()
                        .files(asList(GitFile.builder().fileContent(HelmTestConstants.GIT_FILE_CONTENT_1_KEY).build(),
                            GitFile.builder().fileContent(HelmTestConstants.GIT_FILE_CONTENT_2_KEY).build()))
                        .build());
    when(helmCommandHelper.isValidChartSpecification(any())).thenReturn(true);
    when(helmCommandHelper.generateHelmDeployChartSpecFromYaml(any())).thenReturn(Optional.empty());
    when(helmClient.repoUpdate(any())).thenReturn(HelmCliResponse.builder().build());
    when(helmTaskHelperBase.parseHelmReleaseCommandOutput(eq(""), eq(RELEASE_HISTORY)))
        .thenReturn(Collections.emptyList());
    when(helmTaskHelperBase.isHelmLocalRepoSet()).thenReturn(false);
    when(helmTaskHelperBase.getHelmLocalRepositoryPath()).thenReturn("");
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testDeployInstall() throws Exception {
    helmCliReleaseHistoryResponse.setCommandExecutionStatus(CommandExecutionStatus.FAILURE);
    helmCliListReleasesResponse.setCommandExecutionStatus(SUCCESS);
    helmCliResponse.setCommandExecutionStatus(SUCCESS);
    when(helmClient.releaseHistory(any(), eq(false))).thenReturn(helmCliReleaseHistoryResponse);
    when(helmClient.install(any(), eq(false))).thenReturn(helmCliResponse);
    when(helmClient.listReleases(any(), eq(false))).thenReturn(helmCliListReleasesResponse);
    ArgumentCaptor<io.harness.helm.HelmCommandData> argumentCaptor = ArgumentCaptor.forClass(HelmCommandData.class);
    HelmCommandResponse helmCommandResponse = helmDeployService.deploy(helmInstallCommandRequest);
    assertThat(helmCommandResponse.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    verify(helmClient).install(argumentCaptor.capture(), eq(false));

    // Check if revokeReadPermission function called when Helm Version is V380
    helmInstallCommandRequest.setHelmVersion(HelmVersion.V380);
    doNothing().when(helmTaskHelperBase).revokeReadPermission(helmInstallCommandRequest.getKubeConfigLocation());
    helmDeployService.deploy(helmInstallCommandRequest);
    verify(helmTaskHelperBase, times(1)).revokeReadPermission(helmInstallCommandRequest.getKubeConfigLocation());
  }
  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testDeployInstallK116() throws Exception {
    helmCliReleaseHistoryResponse.setCommandExecutionStatus(CommandExecutionStatus.FAILURE);
    helmCliListReleasesResponse.setCommandExecutionStatus(SUCCESS);
    helmCliResponse.setCommandExecutionStatus(SUCCESS);
    List<KubernetesResource> resources = ImmutableList.of(
        KubernetesResource.builder()
            .resourceId(
                KubernetesResourceId.builder().name("helm-deploy").namespace("default").kind("Deployment").build())
            .build(),
        KubernetesResource.builder()
            .resourceId(
                KubernetesResourceId.builder().name("helm-deploy-1").namespace("default-1").kind("StatefulSet").build())
            .build(),
        KubernetesResource.builder()
            .resourceId(
                KubernetesResourceId.builder().name("helm-deploy-2").namespace("default").kind("StatefulSet").build())
            .build());
    ContainerInfo expectedContainerInfo = ContainerInfo.builder().hostName("test").build();
    List<ContainerInfo> containerInfos = ImmutableList.of(expectedContainerInfo);

    when(helmClient.releaseHistory(any(), eq(false))).thenReturn(helmCliReleaseHistoryResponse);
    when(helmClient.install(any(), eq(false))).thenReturn(helmCliResponse);
    when(helmClient.listReleases(any(), eq(false))).thenReturn(helmCliListReleasesResponse);
    when(containerDeploymentDelegateHelper.useK8sSteadyStateCheck(anyBoolean(), any(), any())).thenReturn(true);
    when(k8sTaskHelperBase.readManifests(any(), any())).thenReturn(resources);
    when(k8sTaskHelperBase.getContainerInfos(any(), any(), any(), anyLong())).thenReturn(containerInfos);
    when(k8sTaskHelperBase.doStatusCheckAllResourcesForHelm(any(), anyList(), any(), any(), any(), any(), any(), any()))
        .thenReturn(true);

    ArgumentCaptor<io.harness.helm.HelmCommandData> argumentCaptor = ArgumentCaptor.forClass(HelmCommandData.class);
    HelmInstallCommandResponse helmCommandResponse =
        (HelmInstallCommandResponse) helmDeployService.deploy(helmInstallCommandRequest);
    assertThat(helmCommandResponse.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(helmCommandResponse.getContainerInfoList()).isNotEmpty();
    ContainerInfo actualContainerInfo = helmCommandResponse.getContainerInfoList().get(0);
    assertThat(actualContainerInfo).isEqualTo(expectedContainerInfo);

    verify(helmClient).install(argumentCaptor.capture(), eq(false));

    verify(k8sTaskHelperBase, times(1))
        .doStatusCheckAllResourcesForHelm(any(Kubectl.class),
            eq(asList(
                KubernetesResourceId.builder().name("helm-deploy").namespace("default").kind("Deployment").build(),
                KubernetesResourceId.builder().name("helm-deploy-2").namespace("default").kind("StatefulSet").build())),
            any(), any(), eq("default"), any(), any(), any());
    verify(k8sTaskHelperBase, times(1))
        .doStatusCheckAllResourcesForHelm(any(Kubectl.class),
            eq(asList(KubernetesResourceId.builder()
                          .name("helm-deploy-1")
                          .namespace("default-1")
                          .kind("StatefulSet")
                          .build())),
            any(), any(), eq("default-1"), any(), any(), any());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testDeployInstallK116NonRepo() throws Exception {
    helmCliReleaseHistoryResponse.setCommandExecutionStatus(CommandExecutionStatus.FAILURE);
    helmCliListReleasesResponse.setCommandExecutionStatus(SUCCESS);
    helmCliResponse.setCommandExecutionStatus(SUCCESS);
    List<KubernetesResource> resources = ImmutableList.of(
        KubernetesResource.builder()
            .resourceId(
                KubernetesResourceId.builder().name("helm-deploy").namespace("default").kind("Deployment").build())
            .build());
    ContainerInfo expectedContainerInfo = ContainerInfo.builder().hostName("test").build();
    List<ContainerInfo> containerInfos = ImmutableList.of(expectedContainerInfo);

    when(helmClient.releaseHistory(any(), eq(false))).thenReturn(helmCliReleaseHistoryResponse);
    when(helmClient.install(any(), eq(false))).thenReturn(helmCliResponse);
    when(helmClient.listReleases(any(), eq(false))).thenReturn(helmCliListReleasesResponse);
    when(containerDeploymentDelegateHelper.useK8sSteadyStateCheck(anyBoolean(), any(), any())).thenReturn(true);
    when(k8sTaskHelperBase.readManifests(any(), any())).thenReturn(resources);
    when(k8sTaskHelperBase.getContainerInfos(any(), any(), any(), anyLong())).thenReturn(containerInfos);
    when(k8sTaskHelperBase.doStatusCheckAllResourcesForHelm(
             any(), anyList(), any(), any(), eq("default"), any(), any(), any()))
        .thenReturn(true);

    HelmInstallCommandRequest helmInstallCommandRequest = createHelmInstallCommandRequestNoSourceRepo();
    ArgumentCaptor<io.harness.helm.HelmCommandData> argumentCaptor = ArgumentCaptor.forClass(HelmCommandData.class);
    HelmInstallCommandResponse helmCommandResponse =
        (HelmInstallCommandResponse) helmDeployService.deploy(helmInstallCommandRequest);
    assertThat(helmInstallCommandRequest.getWorkingDir()).isNotEmpty();
    assertThat(helmCommandResponse.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(helmCommandResponse.getContainerInfoList()).isNotEmpty();
    ContainerInfo actualContainerInfo = helmCommandResponse.getContainerInfoList().get(0);
    assertThat(actualContainerInfo).isEqualTo(expectedContainerInfo);

    verify(helmClient).install(argumentCaptor.capture(), eq(false));
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testDeployUpgrade() throws Exception {
    helmCliReleaseHistoryResponse.setCommandExecutionStatus(SUCCESS);
    helmCliListReleasesResponse.setOutput(HelmTestConstants.LIST_RELEASE_V2);
    helmCliListReleasesResponse.setCommandExecutionStatus(SUCCESS);
    helmCliResponse.setCommandExecutionStatus(SUCCESS);

    when(helmClient.releaseHistory(any(), eq(false))).thenReturn(helmCliReleaseHistoryResponse);
    when(helmClient.upgrade(any(), eq(false))).thenReturn(helmCliResponse);
    when(helmClient.listReleases(any(), eq(false))).thenReturn(helmCliListReleasesResponse);

    ArgumentCaptor<io.harness.helm.HelmCommandData> argumentCaptor = ArgumentCaptor.forClass(HelmCommandData.class);
    HelmCommandResponse helmCommandResponse = helmDeployService.deploy(helmInstallCommandRequest);
    assertThat(helmCommandResponse.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    verify(helmClient).upgrade(argumentCaptor.capture(), eq(false));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDeployFailedUpgrade() throws Exception {
    HelmDeployServiceImpl spyHelmDeployService = spy(helmDeployService);
    helmCliReleaseHistoryResponse.setCommandExecutionStatus(SUCCESS);
    helmCliListReleasesResponse.setOutput(HelmTestConstants.LIST_RELEASE_V2);
    helmCliListReleasesResponse.setCommandExecutionStatus(SUCCESS);
    helmCliResponse.setCommandExecutionStatus(FAILURE);

    helmInstallCommandResponse.setCommandExecutionStatus(FAILURE);

    when(helmClient.releaseHistory(any(), eq(false))).thenReturn(helmCliReleaseHistoryResponse);
    when(helmClient.upgrade(any(), eq(false))).thenReturn(helmCliResponse);
    when(helmClient.listReleases(any(), eq(false))).thenReturn(helmCliListReleasesResponse);

    HelmCommandResponse response = spyHelmDeployService.deploy(helmInstallCommandRequest);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(FAILURE);
    verify(spyHelmDeployService, never())
        .getExecutionLogCallback(helmInstallCommandRequest, HelmDummyCommandUnitConstants.WaitForSteadyState);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDeployTimeout() throws Exception {
    TimeLimiter mockTimeLimiter = mock(TimeLimiter.class);
    on(helmDeployService).set("timeLimiter", mockTimeLimiter);
    helmCliReleaseHistoryResponse.setCommandExecutionStatus(SUCCESS);
    helmCliListReleasesResponse.setOutput(HelmTestConstants.LIST_RELEASE_V2);
    helmCliListReleasesResponse.setCommandExecutionStatus(SUCCESS);
    helmCliResponse.setOutput("Timed out");
    helmCliResponse.setCommandExecutionStatus(FAILURE);

    when(helmClient.releaseHistory(any(), eq(false))).thenReturn(helmCliReleaseHistoryResponse);
    when(helmClient.upgrade(any(), eq(false))).thenReturn(helmCliResponse);
    when(helmClient.listReleases(any(), eq(false))).thenReturn(helmCliListReleasesResponse);

    HTimeLimiterMocker.mockCallInterruptible(mockTimeLimiter).thenThrow(new UncheckedTimeoutException("Timed out"));

    HelmCommandResponse helmCommandResponse = helmDeployService.deploy(helmInstallCommandRequest);
    assertThat(helmCommandResponse.getCommandExecutionStatus()).isEqualTo(FAILURE);
    assertThat(helmCommandResponse.getOutput()).contains("Timed out");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDeployWingsException() throws Exception {
    GeneralException exception = new GeneralException("Something went wrong");
    when(helmClient.releaseHistory(any(), eq(false))).thenReturn(helmCliReleaseHistoryResponse);
    when(helmClient.upgrade(any(), eq(false))).thenThrow(exception);
    assertThatThrownBy(() -> helmDeployService.deploy(helmInstallCommandRequest)).isEqualTo(exception);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDeployException() throws Exception {
    IOException ioException = new IOException("Some I/O issue");
    when(helmClient.releaseHistory(any(), eq(false))).thenReturn(helmCliReleaseHistoryResponse);
    when(helmClient.upgrade(any(), eq(false))).thenThrow(ioException);

    HelmCommandResponse helmCommandResponse = helmDeployService.deploy(helmInstallCommandRequest);
    assertThat(helmCommandResponse.getCommandExecutionStatus()).isEqualTo(FAILURE);
    assertThat(helmCommandResponse.getOutput()).contains("Some I/O issue");
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testAddYamlValuesFromGitRepo() throws Exception {
    helmInstallCommandRequest.setGitConfig(GitConfig.builder().build());
    helmInstallCommandRequest.setGitFileConfig(gitFileConfig);
    helmCliReleaseHistoryResponse.setCommandExecutionStatus(CommandExecutionStatus.FAILURE);
    helmCliListReleasesResponse.setCommandExecutionStatus(SUCCESS);
    helmCliResponse.setCommandExecutionStatus(SUCCESS);

    when(helmClient.releaseHistory(any(), eq(false))).thenReturn(helmCliReleaseHistoryResponse);
    when(helmClient.listReleases(any(), eq(false))).thenReturn(helmCliListReleasesResponse);
    when(helmClient.install(any(), eq(false))).thenReturn(helmCliResponse);

    ArgumentCaptor<io.harness.helm.HelmCommandData> argumentCaptor = ArgumentCaptor.forClass(HelmCommandData.class);
    HelmCommandResponse helmCommandResponse = helmDeployService.deploy(helmInstallCommandRequest);
    assertThat(helmCommandResponse.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    verify(helmClient).install(argumentCaptor.capture(), eq(false));
    io.harness.helm.HelmCommandData commandRequest = argumentCaptor.getAllValues().get(0);
    assertThat(commandRequest.getYamlFiles().size()).isEqualTo(2);
    assertThat(commandRequest.getYamlFiles())
        .contains(HelmTestConstants.GIT_FILE_CONTENT_1_KEY, HelmTestConstants.GIT_FILE_CONTENT_2_KEY);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testAddYamlValuesFromGitRepoWithVariableYamlFiles() throws Exception {
    helmInstallCommandRequest.setGitConfig(GitConfig.builder().build());
    helmInstallCommandRequest.setGitFileConfig(gitFileConfig);
    helmInstallCommandRequest.setVariableOverridesYamlFiles(asList(HelmTestConstants.GIT_FILE_CONTENT_3_KEY));
    helmCliReleaseHistoryResponse.setCommandExecutionStatus(CommandExecutionStatus.FAILURE);
    helmCliListReleasesResponse.setCommandExecutionStatus(SUCCESS);
    helmCliResponse.setCommandExecutionStatus(SUCCESS);

    when(helmClient.releaseHistory(any(), eq(false))).thenReturn(helmCliReleaseHistoryResponse);
    when(helmClient.listReleases(any(), eq(false))).thenReturn(helmCliListReleasesResponse);
    when(helmClient.install(any(), eq(false))).thenReturn(helmCliResponse);

    ArgumentCaptor<io.harness.helm.HelmCommandData> argumentCaptor = ArgumentCaptor.forClass(HelmCommandData.class);
    HelmCommandResponse helmCommandResponse = helmDeployService.deploy(helmInstallCommandRequest);
    assertThat(helmCommandResponse.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    verify(helmClient).install(argumentCaptor.capture(), eq(false));
    io.harness.helm.HelmCommandData commandRequest = argumentCaptor.getAllValues().get(0);
    assertThat(commandRequest.getYamlFiles().size()).isEqualTo(3);
    assertThat(commandRequest.getYamlFiles())
        .contains(HelmTestConstants.GIT_FILE_CONTENT_1_KEY, HelmTestConstants.GIT_FILE_CONTENT_2_KEY,
            HelmTestConstants.GIT_FILE_CONTENT_3_KEY);
  }

  @Test(expected = WingsException.class)
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testAddValuesYamlFromGitRepoWithException() throws Exception {
    helmInstallCommandRequest.setGitConfig(GitConfig.builder().build());
    helmCliListReleasesResponse.setCommandExecutionStatus(SUCCESS);

    GitFileConfig gitFileConfig = new GitFileConfig();
    gitFileConfig.setFilePath(HelmTestConstants.FILE_PATH_KEY);
    helmInstallCommandRequest.setGitFileConfig(gitFileConfig);
    helmInstallCommandRequest.setVariableOverridesYamlFiles(asList(HelmTestConstants.GIT_FILE_CONTENT_3_KEY));
    helmCliReleaseHistoryResponse.setCommandExecutionStatus(CommandExecutionStatus.FAILURE);

    when(helmClient.releaseHistory(any(), eq(false))).thenReturn(helmCliReleaseHistoryResponse);
    when(helmClient.install(any(), eq(false))).thenReturn(helmCliResponse);
    when(gitService.fetchFilesByPath(any(), any(), any(), any(), any(), anyBoolean(), eq(false), any()))
        .thenThrow(new InvalidRequestException("WingsException", USER));
    when(helmClient.listReleases(any(), eq(false))).thenReturn(helmCliListReleasesResponse);

    helmDeployService.deploy(helmInstallCommandRequest);
  }

  private HelmInstallCommandRequest createHelmInstallCommandRequest() {
    HelmInstallCommandRequest request =
        HelmInstallCommandRequest.builder()
            .releaseName(HelmTestConstants.HELM_RELEASE_NAME_KEY)
            .kubeConfigLocation(HelmTestConstants.HELM_KUBE_CONFIG_LOCATION_KEY)
            .chartSpecification(HelmChartSpecification.builder().chartName(HelmTestConstants.CHART_NAME_KEY).build())
            .containerServiceParams(ContainerServiceParams.builder().namespace("default").build())
            .executionLogCallback(logCallback)
            .sourceRepoConfig(K8sDelegateManifestConfig.builder()
                                  .manifestStoreTypes(StoreType.HelmSourceRepo)
                                  .gitConfig(GitConfig.builder().build())
                                  .gitFileConfig(GitFileConfig.builder().filePath("test").build())
                                  .build())
            .build();
    request.setWorkingDir("tmp");
    return request;
  }

  private HelmInstallCommandRequest createHelmInstallCommandRequestNoSourceRepo() {
    return HelmInstallCommandRequest.builder()
        .releaseName(HelmTestConstants.HELM_RELEASE_NAME_KEY)
        .kubeConfigLocation(HelmTestConstants.HELM_KUBE_CONFIG_LOCATION_KEY)
        .containerServiceParams(ContainerServiceParams.builder().namespace("default").build())
        .chartSpecification(HelmChartSpecification.builder()
                                .chartName(HelmTestConstants.CHART_NAME_KEY)
                                .chartUrl("http://127.0.0.1")
                                .build())
        .executionLogCallback(logCallback)
        .build();
  }
  private HelmCliResponse createHelmCliResponse() {
    return HelmCliResponse.builder().output("").build();
  }

  private HelmInstallCommandResponse createHelmInstallCommandResponse() {
    return HelmInstallCommandResponse.builder().output("").commandExecutionStatus(SUCCESS).build();
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testIsHelm3() {
    assertThat(helmDeployService.isHelm3("v3.1.2+g19e47ee")).isTrue();
    assertThat(
        helmDeployService.isHelm3(
            "Client: &version.Version{SemVer:\"v2.13.1\", GitCommit:\"618447cbf203d147601b4b9bd7f8c37a5d39fbb4\", GitTreeState:\"clean\"}\n"
            + "Server: &version.Version{SemVer:\"v2.14.1\", GitCommit:\"5270352a09c7e8b6e8c9593002a73535276507c0\", GitTreeState:\"clean\"}"))
        .isFalse();
    assertThat(helmDeployService.isHelm3("")).isFalse();
    assertThat(helmDeployService.isHelm3(null)).isFalse();
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testEnsureHelm3Installed() throws InterruptedException, TimeoutException, IOException {
    successWhenHelm3PresentInClientTools();
    failureWhenHelm3AbsentInClientTools();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testisFailedStatus() {
    assertThat(helmDeployService.isFailedStatus("failed")).isTrue();
    assertThat(helmDeployService.isFailedStatus("FAILED")).isTrue();
    assertThat(helmDeployService.isFailedStatus("Failed")).isTrue();
    assertThat(helmDeployService.isFailedStatus("FailedAbc")).isFalse();
    assertThat(helmDeployService.isFailedStatus("Success")).isFalse();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testReleaseHistory() throws Exception {
    shouldListReleaseHistoryV2();
    shouldListReleaseHistoryV3();
    shouldNotThrowExceptionInReleaseHist();
  }

  private void shouldListReleaseHistoryV2() throws Exception {
    HelmReleaseHistoryCommandRequest request = HelmReleaseHistoryCommandRequest.builder().build();

    when(helmClient.releaseHistory(HelmCommandDataMapper.getHelmCommandData(request), false))
        .thenReturn(HelmCliResponse.builder()
                        .commandExecutionStatus(SUCCESS)
                        .output(HelmTestConstants.RELEASE_HIST_V2)
                        .build());

    HelmReleaseHistoryCommandResponse response = helmDeployService.releaseHistory(request);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getReleaseInfoList()).hasSize(5);
    assertThat(response.getReleaseInfoList().stream().map(ReleaseInfo::getRevision))
        .hasSameElementsAs(asList("1", "2", "3", "4", "5"));
    assertThat(response.getReleaseInfoList().stream().map(ReleaseInfo::getChart))
        .hasSameElementsAs(asList(
            "chartmuseum-2.3.1", "chartmuseum-2.3.2", "chartmuseum-2.3.3", "chartmuseum-2.3.4", "chartmuseum-2.3.5"));
  }

  private void shouldListReleaseHistoryV3() throws Exception {
    HelmReleaseHistoryCommandRequest request = HelmReleaseHistoryCommandRequest.builder().build();

    when(helmClient.releaseHistory(HelmCommandDataMapper.getHelmCommandData(request), false))
        .thenReturn(HelmCliResponse.builder()
                        .commandExecutionStatus(SUCCESS)
                        .output(HelmTestConstants.RELEASE_HIST_V3)
                        .build());

    HelmReleaseHistoryCommandResponse response = helmDeployService.releaseHistory(request);

    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getReleaseInfoList()).hasSize(4);
    assertThat(response.getReleaseInfoList().stream().map(ReleaseInfo::getRevision))
        .hasSameElementsAs(asList("1", "2", "3", "4"));
    assertThat(response.getReleaseInfoList().stream().map(ReleaseInfo::getChart))
        .hasSameElementsAs(asList("zetcd-0.1.4", "zetcd-0.1.9", "zetcd-0.2.9", "chartmuseum-2.7.0"));
  }

  private void shouldNotThrowExceptionInReleaseHist() throws Exception {
    HelmReleaseHistoryCommandRequest request = HelmReleaseHistoryCommandRequest.builder().build();

    when(helmClient.releaseHistory(HelmCommandDataMapper.getHelmCommandData(request), false))
        .thenThrow(new InterruptedException());

    HelmReleaseHistoryCommandResponse response = helmDeployService.releaseHistory(request);

    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getReleaseInfoList()).isEmpty();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testListRelease() throws Exception {
    shouldListReleaseV2();
    shouldListReleaseV3();
    shouldNotThrowExceptionInListRelease();
  }

  private void shouldListReleaseV2() throws Exception {
    HelmInstallCommandRequest request = HelmInstallCommandRequest.builder().build();

    when(helmClient.listReleases(HelmCommandDataMapper.getHelmCommandData(request), false))
        .thenReturn(HelmCliResponse.builder()
                        .commandExecutionStatus(SUCCESS)
                        .output(HelmTestConstants.LIST_RELEASE_V2)
                        .build());

    HelmListReleasesCommandResponse response = helmDeployService.listReleases(request);

    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getReleaseInfoList()).hasSize(1);
    assertThat(response.getReleaseInfoList().stream().map(ReleaseInfo::getName))
        .hasSameElementsAs(asList("helm-release-name"));
    assertThat(response.getReleaseInfoList().stream().map(ReleaseInfo::getRevision)).hasSameElementsAs(asList("85"));
    assertThat(response.getReleaseInfoList().stream().map(ReleaseInfo::getNamespace))
        .hasSameElementsAs(asList("default"));
  }

  private void shouldListReleaseV3() throws Exception {
    HelmInstallCommandRequest request = HelmInstallCommandRequest.builder().build();

    when(helmClient.listReleases(HelmCommandDataMapper.getHelmCommandData(request), false))
        .thenReturn(HelmCliResponse.builder()
                        .commandExecutionStatus(SUCCESS)
                        .output(HelmTestConstants.LIST_RELEASE_V3)
                        .build());

    HelmListReleasesCommandResponse response = helmDeployService.listReleases(request);

    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getReleaseInfoList()).hasSize(3);
    assertThat(response.getReleaseInfoList().stream().map(ReleaseInfo::getName))
        .hasSameElementsAs(asList("ft-test", "ft-test1", "helm2-http"));
    assertThat(response.getReleaseInfoList().stream().map(ReleaseInfo::getRevision))
        .hasSameElementsAs(asList("1", "1", "6"));
    assertThat(response.getReleaseInfoList().stream().map(ReleaseInfo::getNamespace))
        .hasSameElementsAs(asList("default", "harness", "default"));
  }

  private void shouldNotThrowExceptionInListRelease() throws Exception {
    HelmInstallCommandRequest request = HelmInstallCommandRequest.builder().build();

    when(helmClient.listReleases(HelmCommandDataMapper.getHelmCommandData(request), false))
        .thenThrow(new InterruptedException());

    HelmListReleasesCommandResponse response = helmDeployService.listReleases(request);

    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(FAILURE);
    assertThat(response.getReleaseInfoList()).isNull();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testEnsureHelmCliAndTillerInstalledIfInstalled() throws Exception {
    setFakeTimeLimiter();
    HelmInstallCommandRequest request = HelmInstallCommandRequest.builder().build();

    when(helmClient.getClientAndServerVersion(HelmCommandDataMapper.getHelmCommandData(request), false))
        .thenReturn(
            HelmCliResponse.builder().commandExecutionStatus(SUCCESS).output(HelmTestConstants.VERSION_V2).build());

    HelmCommandResponse response = helmDeployService.ensureHelmCliAndTillerInstalled(request);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getOutput()).isNotEmpty();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testEnsureHelmCliAndTillerInstalledIfNotInstalled() throws Exception {
    setFakeTimeLimiter();
    HelmInstallCommandRequest request = HelmInstallCommandRequest.builder().build();

    when(helmClient.getClientAndServerVersion(HelmCommandDataMapper.getHelmCommandData(request), false))
        .thenReturn(HelmCliResponse.builder().commandExecutionStatus(FAILURE).build());

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> helmDeployService.ensureHelmCliAndTillerInstalled(request));
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testEnsureHelmCliAndTillerInstalledIfV3Installed() throws Exception {
    setFakeTimeLimiter();
    HelmInstallCommandRequest request = HelmInstallCommandRequest.builder().build();

    when(helmClient.getClientAndServerVersion(HelmCommandDataMapper.getHelmCommandData(request), false))
        .thenReturn(
            HelmCliResponse.builder().commandExecutionStatus(SUCCESS).output(HelmTestConstants.VERSION_V3).build());

    HelmCommandResponse response = helmDeployService.ensureHelmCliAndTillerInstalled(request);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(FAILURE);
    assertThat(response.getOutput()).isNotEmpty();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testEnsureHelmCliAndTillerInstalledIfClusterUnreachable() throws Exception {
    setFakeTimeLimiter();
    HelmInstallCommandRequest request = HelmInstallCommandRequest.builder().build();

    when(helmClient.getClientAndServerVersion(HelmCommandDataMapper.getHelmCommandData(request), false))
        .thenThrow(new UncheckedTimeoutException());

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> helmDeployService.ensureHelmCliAndTillerInstalled(request));
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testAddRepoIfAlreadyAddedV2() throws Exception {
    HelmCommandRequest request =
        HelmInstallCommandRequest.builder()
            .executionLogCallback(executionLogCallback)
            .chartSpecification(
                HelmChartSpecification.builder().chartUrl("https://harness.jfrog.io/harness/helm").build())
            .build();

    when(helmClient.getHelmRepoList(HelmCommandDataMapper.getHelmCommandData(request)))
        .thenReturn(
            HelmCliResponse.builder().commandExecutionStatus(SUCCESS).output(HelmTestConstants.REPO_LIST_V2).build());

    helmDeployService.addPublicRepo(request);

    verify(helmClient, never()).addPublicRepo(any(), eq(false));
  }
  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testAddRepoIfAlreadyAddedV3() throws Exception {
    HelmCommandRequest request =
        HelmInstallCommandRequest.builder()
            .executionLogCallback(executionLogCallback)
            .chartSpecification(HelmChartSpecification.builder().chartUrl("https://charts.bitnami.com/bitnami").build())
            .build();

    when(helmClient.getHelmRepoList(HelmCommandDataMapper.getHelmCommandData(request)))
        .thenReturn(
            HelmCliResponse.builder().commandExecutionStatus(SUCCESS).output(HelmTestConstants.REPO_LIST_V3).build());

    helmDeployService.addPublicRepo(request);

    verify(helmClient, never()).addPublicRepo(any(), eq(false));
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testAddNewRepo() throws Exception {
    HelmCommandRequest request = HelmInstallCommandRequest.builder()
                                     .executionLogCallback(executionLogCallback)
                                     .chartSpecification(HelmChartSpecification.builder().chartUrl("abc.com").build())
                                     .build();

    when(helmClient.getHelmRepoList(HelmCommandDataMapper.getHelmCommandData(request)))
        .thenReturn(
            HelmCliResponse.builder().commandExecutionStatus(SUCCESS).output(HelmTestConstants.REPO_LIST_V2).build());
    when(helmClient.addPublicRepo(any(), eq(false)))
        .thenReturn(HelmCliResponse.builder().commandExecutionStatus(SUCCESS).build());

    HelmCommandResponse response = helmDeployService.addPublicRepo(request);

    verify(helmClient, times(1)).addPublicRepo(any(), eq(false));
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testAddNewRepoIfNotReachable() throws Exception {
    HelmCommandRequest request = HelmInstallCommandRequest.builder()
                                     .executionLogCallback(executionLogCallback)
                                     .chartSpecification(HelmChartSpecification.builder().chartUrl("abc.com").build())
                                     .build();

    when(helmClient.getHelmRepoList(HelmCommandDataMapper.getHelmCommandData(request)))
        .thenReturn(
            HelmCliResponse.builder().commandExecutionStatus(SUCCESS).output(HelmTestConstants.REPO_LIST_V2).build());
    when(helmClient.addPublicRepo(any(), eq(false)))
        .thenReturn(HelmCliResponse.builder().commandExecutionStatus(FAILURE).build());

    assertThatExceptionOfType(InvalidRequestException.class).isThrownBy(() -> helmDeployService.addPublicRepo(request));
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testAddNewRepoIfRepoListFails() throws Exception {
    HelmCommandRequest request = HelmInstallCommandRequest.builder()
                                     .executionLogCallback(executionLogCallback)
                                     .chartSpecification(HelmChartSpecification.builder().chartUrl("abc.com").build())
                                     .build();

    when(helmClient.getHelmRepoList(HelmCommandDataMapper.getHelmCommandData(request)))
        .thenReturn(HelmCliResponse.builder().commandExecutionStatus(FAILURE).output("command not found").build());
    when(helmClient.addPublicRepo(any(), eq(false)))
        .thenReturn(HelmCliResponse.builder().commandExecutionStatus(FAILURE).build());

    assertThatExceptionOfType(InvalidRequestException.class).isThrownBy(() -> helmDeployService.addPublicRepo(request));
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testFetchChartRepo() throws Exception {
    ArgumentCaptor<HelmChartConfigParams> chartConfigParamsArgumentCaptor =
        ArgumentCaptor.forClass(HelmChartConfigParams.class);
    ArgumentCaptor<String> stringArgumentCaptor = ArgumentCaptor.forClass(String.class);

    HelmInstallCommandRequest request =
        HelmInstallCommandRequest.builder()
            .executionLogCallback(executionLogCallback)
            .sourceRepoConfig(K8sDelegateManifestConfig.builder()
                                  .helmChartConfigParams(HelmChartConfigParams.builder().chartName("foo").build())
                                  .build())
            .helmCommandFlag(commandFlag)
            .build();

    helmDeployService.fetchChartRepo(request, LONG_TIMEOUT_INTERVAL);

    verify(helmTaskHelper, times(1))
        .downloadChartFiles(chartConfigParamsArgumentCaptor.capture(), stringArgumentCaptor.capture(),
            eq(LONG_TIMEOUT_INTERVAL), commandFlagCaptor.capture());

    HelmChartConfigParams helmChartConfigParams = chartConfigParamsArgumentCaptor.getValue();
    String directory = stringArgumentCaptor.getValue();
    assertThat(helmChartConfigParams).isNotNull();
    assertThat(directory).isNotEmpty();
    assertThat(commandFlagCaptor.getValue()).isEqualTo(commandFlag);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testFetchSourceRepo() {
    ArgumentCaptor<GitConfig> argumentCaptor = ArgumentCaptor.forClass(GitConfig.class);
    HelmInstallCommandRequest request =
        HelmInstallCommandRequest.builder()
            .sourceRepoConfig(K8sDelegateManifestConfig.builder()
                                  .manifestStoreTypes(StoreType.HelmSourceRepo)
                                  .gitConfig(GitConfig.builder().build())
                                  .gitFileConfig(GitFileConfig.builder().branch("master").useBranch(true).build())
                                  .build())
            .executionLogCallback(executionLogCallback)
            .build();

    helmDeployService.fetchSourceRepo(request);

    verify(encryptionService, times(1)).decrypt(argumentCaptor.capture(), any(), eq(false));
    verify(gitService, times(1))
        .downloadFiles(any(GitConfig.class), any(GitFileConfig.class), anyString(), eq(false), any());

    GitConfig gitConfig = argumentCaptor.getValue();
    assertThat(gitConfig.getBranch()).isNotEmpty();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testFetchSourceRepoOptimizedFileFetch() {
    ArgumentCaptor<GitConfig> argumentCaptor = ArgumentCaptor.forClass(GitConfig.class);
    doReturn(true).when(scmFetchFilesHelper).shouldUseScm(anyBoolean(), any());
    HelmInstallCommandRequest request =
        HelmInstallCommandRequest.builder()
            .sourceRepoConfig(K8sDelegateManifestConfig.builder()
                                  .manifestStoreTypes(StoreType.HelmSourceRepo)
                                  .gitConfig(GitConfig.builder().build())
                                  .gitFileConfig(GitFileConfig.builder().branch("master").useBranch(true).build())
                                  .optimizedFilesFetch(true)
                                  .build())
            .executionLogCallback(executionLogCallback)
            .build();

    helmDeployService.fetchSourceRepo(request);

    verify(encryptionService, times(1)).decrypt(argumentCaptor.capture(), any(), eq(false));
    verify(scmFetchFilesHelper, times(1)).downloadFilesUsingScm(any(), any(), any(), any());
    verify(gitService, times(0))
        .downloadFiles(any(GitConfig.class), any(GitFileConfig.class), anyString(), eq(false), any());

    GitConfig gitConfig = argumentCaptor.getValue();
    assertThat(gitConfig.getBranch()).isNotEmpty();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testDeployWithInvalidChartSpec() throws Exception {
    when(helmCommandHelper.isValidChartSpecification(any())).thenReturn(false);
    when(helmClient.listReleases(any(), eq(false)))
        .thenReturn(HelmCliResponse.builder().commandExecutionStatus(SUCCESS).build());
    when(helmClient.releaseHistory(any(), eq(false)))
        .thenReturn(HelmCliResponse.builder()
                        .commandExecutionStatus(SUCCESS)
                        .output(HelmTestConstants.RELEASE_HIST_V2)
                        .build());

    HelmInstallCommandRequest request =
        HelmInstallCommandRequest.builder().executionLogCallback(executionLogCallback).releaseName("abc").build();

    assertThatThrownBy(() -> helmDeployService.deploy(request))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Couldn't find valid helm chart specification");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testFetchSourceRepoIfNullRepoConfig() {
    HelmInstallCommandRequest request = HelmInstallCommandRequest.builder().build();

    helmDeployService.fetchSourceRepo(request);

    verify(encryptionService, never()).decrypt(any(), anyList(), eq(false));
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testRollback() throws Exception {
    setFakeTimeLimiter();
    HelmRollbackCommandRequest request = HelmRollbackCommandRequest.builder()
                                             .containerServiceParams(ContainerServiceParams.builder().build())
                                             .chartSpecification(HelmChartSpecification.builder()
                                                                     .chartName(HelmTestConstants.CHART_NAME_KEY)
                                                                     .chartUrl("http://127.0.0.1")
                                                                     .build())

                                             .releaseName("first-release")
                                             .build();

    KubernetesConfig kubernetesConfig = KubernetesConfig.builder().build();

    when(helmClient.rollback(any(HelmCommandData.class), eq(false)))
        .thenReturn(
            HelmCliResponse.builder().output("Rollback was a success.").commandExecutionStatus(SUCCESS).build());
    when(containerDeploymentDelegateHelper.getKubernetesConfig(any(K8sClusterConfig.class), anyBoolean()))
        .thenReturn(KubernetesConfig.builder().build());
    when(containerDeploymentDelegateHelper.getKubernetesConfig(any(ContainerServiceParams.class)))
        .thenReturn(KubernetesConfig.builder().build());
    when(containerDeploymentDelegateBaseHelper.getContainerInfosWhenReadyByLabels(
             any(), any(), any(), eq(Collections.emptyList())))
        .thenReturn(asList(new ContainerInfo()));
    when(containerDeploymentDelegateBaseHelper.getExistingPodsByLabels(any(KubernetesConfig.class), any(Map.class)))
        .thenReturn(Collections.emptyList());

    HelmInstallCommandResponse response = (HelmInstallCommandResponse) helmDeployService.rollback(request);
    assertThat(response.getContainerInfoList()).isNotEmpty();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRollbackK116WithSteadyCheck() throws Exception {
    setFakeTimeLimiter();
    KubernetesResourceId resource1 =
        KubernetesResourceId.builder().namespace("default-1").name("resource-1").kind(Kind.StatefulSet.name()).build();
    KubernetesResourceId resource2 =
        KubernetesResourceId.builder().namespace("default-2").name("resource-2").kind(Kind.Deployment.name()).build();
    KubernetesResourceId resource3 =
        KubernetesResourceId.builder().namespace("default-3").name("resource-3").kind(Kind.Deployment.name()).build();
    when(containerDeploymentDelegateHelper.useK8sSteadyStateCheck(anyBoolean(), any(), any())).thenReturn(true);

    HelmInstallCommandResponse result = null;
    ReleaseHistory releaseHistory = ReleaseHistory.createNew();

    // Empty release history
    assertThatThrownBy(() -> executeRollbackWithReleaseHistory(releaseHistory, 3))
        .hasMessageContaining("Unable to find release 3");

    // Trying to rollback to failed release
    releaseHistory.createNewRelease(singletonList(resource1));
    releaseHistory.setReleaseNumber(1);
    releaseHistory.setReleaseStatus(IK8sRelease.Status.Failed);
    assertThatThrownBy(() -> executeRollbackWithReleaseHistory(releaseHistory, 1))
        .hasMessageContaining(
            "Invalid status for release with number 1. Expected 'Succeeded' status, actual status is 'Failed'");

    releaseHistory.createNewRelease(singletonList(resource1));
    releaseHistory.setReleaseNumber(1);
    releaseHistory.setReleaseStatus(IK8sRelease.Status.Succeeded);
    // No such release
    assertThatThrownBy(() -> executeRollbackWithReleaseHistory(releaseHistory, 2))
        .hasMessageContaining("Unable to find release 2");

    // Rollback to release 1
    result = executeRollbackWithReleaseHistory(releaseHistory, 1);
    assertThat(result.getContainerInfoList().stream().map(ContainerInfo::getHostName)).containsExactly("resource-1");
    validateCreateNewRelease(2);

    releaseHistory.createNewRelease(asList(resource1, resource2));
    releaseHistory.setReleaseNumber(2);
    releaseHistory.setReleaseStatus(IK8sRelease.Status.Succeeded);
    // Rollback to release 2
    result = executeRollbackWithReleaseHistory(releaseHistory, 2);
    assertThat(result.getContainerInfoList().stream().map(ContainerInfo::getHostName))
        .containsExactlyInAnyOrder("resource-1", "resource-2");
    validateCreateNewRelease(3);

    releaseHistory.createNewRelease(asList(resource1, resource2, resource3));
    releaseHistory.setReleaseNumber(3);
    releaseHistory.setReleaseStatus(IK8sRelease.Status.Succeeded);
    releaseHistory.createNewRelease(singletonList(resource1));
    releaseHistory.setReleaseNumber(4);
    releaseHistory.setReleaseStatus(IK8sRelease.Status.Succeeded);
    // Rollback to release 3
    result = executeRollbackWithReleaseHistory(releaseHistory, 3);
    assertThat(result.getContainerInfoList().stream().map(ContainerInfo::getHostName))
        .containsExactlyInAnyOrder("resource-1", "resource-2", "resource-3");
    validateCreateNewRelease(5);
  }

  private void validateCreateNewRelease(int releaseNumber) throws Exception {
    ArgumentCaptor<String> releaseHistoryCaptor = ArgumentCaptor.forClass(String.class);
    verify(k8sTaskHelperBase, atLeastOnce())
        .saveReleaseHistory(any(), eq("release"), releaseHistoryCaptor.capture(), eq(true));
    ReleaseHistory storedHistory = ReleaseHistory.createFromData(releaseHistoryCaptor.getValue());
    assertThat(storedHistory.getRelease(releaseNumber)).isNotNull();
  }

  private HelmInstallCommandResponse executeRollbackWithReleaseHistory(ReleaseHistory releaseHistory, int version)
      throws Exception {
    HelmRollbackCommandRequest request = HelmRollbackCommandRequest.builder()
                                             .releaseName("release")
                                             .prevReleaseVersion(version)
                                             .containerServiceParams(ContainerServiceParams.builder().build())
                                             .chartSpecification(HelmChartSpecification.builder()
                                                                     .chartName(HelmTestConstants.CHART_NAME_KEY)
                                                                     .chartUrl("http://127.0.0.1")
                                                                     .build())
                                             .timeoutInMillis(LONG_TIMEOUT_INTERVAL)
                                             .build();
    List<ContainerInfo> containerInfosDefault1 =
        ImmutableList.of(ContainerInfo.builder().hostName("resource-1").build());
    List<ContainerInfo> containerInfosDefault2 =
        ImmutableList.of(ContainerInfo.builder().hostName("resource-2").build());
    List<ContainerInfo> containerInfosDefault3 =
        ImmutableList.of(ContainerInfo.builder().hostName("resource-3").build());

    when(helmClient.rollback(any(HelmCommandData.class), eq(false)))
        .thenReturn(
            HelmCliResponse.builder().output("Rollback was a success.").commandExecutionStatus(SUCCESS).build());
    when(k8sTaskHelperBase.getReleaseHistoryFromSecret(any(), eq("release"))).thenReturn(releaseHistory.getAsYaml());
    when(k8sTaskHelperBase.getContainerInfos(any(), eq("release"), eq("default-1"), eq(LONG_TIMEOUT_INTERVAL)))
        .thenReturn(containerInfosDefault1);
    when(k8sTaskHelperBase.getContainerInfos(any(), eq("release"), eq("default-2"), eq(LONG_TIMEOUT_INTERVAL)))
        .thenReturn(containerInfosDefault2);
    when(k8sTaskHelperBase.getContainerInfos(any(), eq("release"), eq("default-3"), eq(LONG_TIMEOUT_INTERVAL)))
        .thenReturn(containerInfosDefault3);
    when(k8sTaskHelperBase.doStatusCheckAllResourcesForHelm(any(), anyList(), any(), any(), any(), any(), any(), any()))
        .thenReturn(true);

    return (HelmInstallCommandResponse) helmDeployService.rollback(request);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRollbackTimeout() throws Exception {
    TimeLimiter mockTimeLimiter = mock(TimeLimiter.class);
    on(helmDeployService).set("timeLimiter", mockTimeLimiter);
    HelmRollbackCommandRequest request = HelmRollbackCommandRequest.builder().build();

    HTimeLimiterMocker.mockCallInterruptible(mockTimeLimiter).thenThrow(new UncheckedTimeoutException("Timed out"));
    doReturn(HelmCliResponse.builder().output("Rollback was a success.").commandExecutionStatus(SUCCESS).build())
        .when(helmClient)
        .rollback(any(HelmCommandData.class), eq(false));

    HelmCommandResponse helmCommandResponse = helmDeployService.rollback(request);
    assertThat(helmCommandResponse.getCommandExecutionStatus()).isEqualTo(FAILURE);
    assertThat(helmCommandResponse.getOutput()).contains("Timed out");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRollbackWingsException() throws Exception {
    GeneralException exception = new GeneralException("Something went wrong");
    HelmRollbackCommandRequest request = HelmRollbackCommandRequest.builder().build();
    doThrow(exception).when(helmClient).rollback(any(HelmCommandData.class), eq(false));

    assertThatThrownBy(() -> helmDeployService.rollback(request)).isEqualTo(exception);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRollbackException() throws Exception {
    IOException ioException = new IOException("Some I/O issue");
    HelmRollbackCommandRequest request = HelmRollbackCommandRequest.builder().build();
    doThrow(ioException).when(helmClient).rollback(any(HelmCommandData.class), eq(false));

    HelmCommandResponse helmCommandResponse = helmDeployService.rollback(request);
    assertThat(helmCommandResponse.getCommandExecutionStatus()).isEqualTo(FAILURE);
    assertThat(helmCommandResponse.getOutput()).contains("Some I/O issue");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRollbackFailedCommand() throws Exception {
    HelmRollbackCommandRequest request = HelmRollbackCommandRequest.builder().build();
    HelmCliResponse failureResponse =
        HelmCliResponse.builder().output("Unable to rollback").commandExecutionStatus(FAILURE).build();

    doReturn(failureResponse).when(helmClient).rollback(any(HelmCommandData.class), eq(false));

    HelmInstallCommandResponse response = (HelmInstallCommandResponse) helmDeployService.rollback(request);

    assertThat(response.getCommandExecutionStatus()).isEqualTo(FAILURE);
    assertThat(response.getOutput()).isEqualTo("Unable to rollback");
  }

  private void successWhenHelm3PresentInClientTools() throws InterruptedException, IOException, TimeoutException {
    doReturn("/client-tools/helm").when(k8sGlobalConfigService).getHelmPath(V3);

    HelmCommandResponse helmCommandResponse =
        helmDeployService.ensureHelm3Installed(HelmInstallCommandRequest.builder().helmVersion(V3).build());

    assertThat(helmCommandResponse.getCommandExecutionStatus()).isEqualTo(SUCCESS);
  }

  private void failureWhenHelm3AbsentInClientTools() throws InterruptedException, IOException, TimeoutException {
    doReturn("").when(k8sGlobalConfigService).getHelmPath(V3);

    HelmCommandResponse helmCommandResponse =
        helmDeployService.ensureHelm3Installed(HelmInstallCommandRequest.builder().helmVersion(V3).build());

    assertThat(helmCommandResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  private void setFakeTimeLimiter() {
    on(helmDeployService).set("timeLimiter", timeLimiter);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testFetchRepo() throws Exception {
    shouldCallFetchChartRepo();
    shouldCallFetchSourceRepo();
    shouldThrowExceptionForUnknownStoreType();
  }

  private void shouldThrowExceptionForUnknownStoreType() {
    HelmInstallCommandRequest helmInstallCommandRequest =
        HelmInstallCommandRequest.builder()
            .sourceRepoConfig(K8sDelegateManifestConfig.builder().manifestStoreTypes(StoreType.Local).build())
            .build();

    assertThatThrownBy(
        () -> spyHelmDeployService.fetchRepo(helmInstallCommandRequest, LONG_TIMEOUT_INTERVAL, logCallback))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Unsupported store type");
  }

  private void shouldCallFetchSourceRepo() throws Exception {
    HelmInstallCommandRequest helmInstallCommandRequest =
        HelmInstallCommandRequest.builder()
            .sourceRepoConfig(K8sDelegateManifestConfig.builder().manifestStoreTypes(StoreType.HelmSourceRepo).build())
            .build();
    doNothing().when(spyHelmDeployService).fetchSourceRepo(helmInstallCommandRequest);

    spyHelmDeployService.fetchRepo(helmInstallCommandRequest, LONG_TIMEOUT_INTERVAL, logCallback);

    verify(spyHelmDeployService, times(1)).fetchSourceRepo(helmInstallCommandRequest);
  }

  private void shouldCallFetchChartRepo() throws Exception {
    HelmInstallCommandRequest helmInstallCommandRequest =
        HelmInstallCommandRequest.builder()
            .sourceRepoConfig(K8sDelegateManifestConfig.builder().manifestStoreTypes(StoreType.HelmChartRepo).build())
            .build();
    doNothing().when(spyHelmDeployService).fetchChartRepo(helmInstallCommandRequest, LONG_TIMEOUT_INTERVAL);

    spyHelmDeployService.fetchRepo(helmInstallCommandRequest, LONG_TIMEOUT_INTERVAL, logCallback);

    verify(spyHelmDeployService, times(1)).fetchChartRepo(helmInstallCommandRequest, LONG_TIMEOUT_INTERVAL);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testEnsureHelmInstalled() {
    shouldCallEnsureHelm3InstalledWhenVersionV3();
    shouldCallEnsureHelm2InstalledWhenVersionV2();
    shouldCallEnsureHelm2InstalledWhenVersionNull();
  }

  private void shouldCallEnsureHelm2InstalledWhenVersionNull() {
    HelmInstallCommandRequest helmInstallCommandRequest = HelmInstallCommandRequest.builder().build();
    doReturn(null).when(spyHelmDeployService).ensureHelmCliAndTillerInstalled(helmInstallCommandRequest);

    spyHelmDeployService.ensureHelmInstalled(helmInstallCommandRequest);

    verify(spyHelmDeployService, times(2)).ensureHelmCliAndTillerInstalled(helmInstallCommandRequest);
  }

  private void shouldCallEnsureHelm2InstalledWhenVersionV2() {
    HelmInstallCommandRequest helmInstallCommandRequest = HelmInstallCommandRequest.builder().helmVersion(V2).build();
    doReturn(null).when(spyHelmDeployService).ensureHelmCliAndTillerInstalled(helmInstallCommandRequest);

    spyHelmDeployService.ensureHelmInstalled(helmInstallCommandRequest);

    verify(spyHelmDeployService, times(1)).ensureHelmCliAndTillerInstalled(helmInstallCommandRequest);
  }

  private void shouldCallEnsureHelm3InstalledWhenVersionV3() {
    HelmInstallCommandRequest helmInstallCommandRequest = HelmInstallCommandRequest.builder().helmVersion(V3).build();
    doReturn(null).when(spyHelmDeployService).ensureHelm3Installed(helmInstallCommandRequest);

    spyHelmDeployService.ensureHelmInstalled(helmInstallCommandRequest);

    verify(spyHelmDeployService, times(1)).ensureHelm3Installed(helmInstallCommandRequest);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testDeleteAndPurgeHelmReleaseName() throws Exception {
    HelmInstallCommandRequest helmInstallCommandRequest = HelmInstallCommandRequest.builder().build();
    HelmCliResponse helmCliResponse = HelmCliResponse.builder().build();
    doReturn(helmCliResponse)
        .when(helmClient)
        .deleteHelmRelease(HelmCommandDataMapper.getHelmCommandData(helmInstallCommandRequest), false);

    helmDeployService.deleteAndPurgeHelmRelease(helmInstallCommandRequest, new ExecutionLogCallback());

    verify(helmClient, times(1))
        .deleteHelmRelease(HelmCommandDataMapper.getHelmCommandData(helmInstallCommandRequest), false);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDeleteAndPurgeHelmReleaseNameNotFailOnException() throws Exception {
    doThrow(new IOException("Unable to execute process"))
        .when(helmClient)
        .deleteHelmRelease(HelmCommandDataMapper.getHelmCommandData(helmInstallCommandRequest), false);

    assertThatCode(
        () -> helmDeployService.deleteAndPurgeHelmRelease(helmInstallCommandRequest, new ExecutionLogCallback()))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void shouldFailIfSteadyStateCheckFails() throws Exception {
    helmCliReleaseHistoryResponse.setCommandExecutionStatus(CommandExecutionStatus.FAILURE);
    helmCliListReleasesResponse.setCommandExecutionStatus(SUCCESS);
    List<KubernetesResource> resources = ImmutableList.of(
        KubernetesResource.builder()
            .resourceId(
                KubernetesResourceId.builder().name("helm-deploy").namespace("default").kind("Deployment").build())
            .build(),
        KubernetesResource.builder()
            .resourceId(
                KubernetesResourceId.builder().name("helm-deploy-1").namespace("default-1").kind("StatefulSet").build())
            .build(),
        KubernetesResource.builder()
            .resourceId(
                KubernetesResourceId.builder().name("helm-deploy-2").namespace("default").kind("StatefulSet").build())
            .build());
    List<KubernetesResourceId> resourceIds =
        resources.stream().map(KubernetesResource::getResourceId).collect(Collectors.toList());

    when(containerDeploymentDelegateHelper.useK8sSteadyStateCheck(anyBoolean(), any(), any())).thenReturn(true);
    when(k8sTaskHelperBase.doStatusCheckAllResourcesForHelm(any(Kubectl.class), eq(resourceIds), anyString(),
             anyString(), anyString(), anyString(), any(ExecutionLogCallback.class), any()))
        .thenReturn(false);

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(()
                        -> helmDeployService.getKubectlContainerInfos(
                            helmInstallCommandRequest, resourceIds, executionLogCallback, LONG_TIMEOUT_INTERVAL))
        .withMessage("Steady state check failed");

    verify(k8sTaskHelperBase, times(1))
        .doStatusCheckAllResourcesForHelm(
            any(), any(), any(), anyString(), anyString(), anyString(), any(ExecutionLogCallback.class), any());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldExcludeRepoNameFromInlineChartNameIfUrlNotSet() throws Exception {
    ExecutionLogCallback executionLogCallback = spy(new ExecutionLogCallback());
    HelmInstallCommandRequest helmInstallCommandRequest =
        HelmInstallCommandRequest.builder()
            .chartSpecification(HelmChartSpecification.builder().chartName("repo/chartName").build())
            .executionLogCallback(executionLogCallback)
            .activityId("test")
            .build();
    helmDeployService.fetchInlineChartUrl(helmInstallCommandRequest, LONG_TIMEOUT_INTERVAL);
    verify(executionLogCallback, times(1)).saveExecutionLog("Helm Chart Repo checked-out locally");

    String workingDir = replace(WORKING_DIR, "${ACTIVITY_ID}", helmInstallCommandRequest.getActivityId());
    assertThat(helmInstallCommandRequest.getWorkingDir()).isEqualTo(workingDir + "/chartName");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldIncludeRepoNameWIthInlineChartNameIfUrlIsSet() throws Exception {
    ExecutionLogCallback executionLogCallback = spy(new ExecutionLogCallback());
    HelmInstallCommandRequest helmInstallCommandRequest =
        HelmInstallCommandRequest.builder()
            .chartSpecification(
                HelmChartSpecification.builder().chartName("repo/chartName").chartUrl("http://helm-repo").build())
            .executionLogCallback(executionLogCallback)
            .activityId("test")
            .build();

    helmDeployService.fetchInlineChartUrl(helmInstallCommandRequest, LONG_TIMEOUT_INTERVAL);
    verify(executionLogCallback, times(1)).saveExecutionLog("Helm Chart Repo checked-out locally");

    String workingDir = replace(WORKING_DIR, "${ACTIVITY_ID}", helmInstallCommandRequest.getActivityId());
    assertThat(helmInstallCommandRequest.getWorkingDir()).isEqualTo(workingDir + "/repo/chartName");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldThrowExceptionIfChartNameIsWrongFormat() throws Exception {
    ExecutionLogCallback executionLogCallback = spy(new ExecutionLogCallback());
    HelmInstallCommandRequest helmInstallCommandRequest =
        HelmInstallCommandRequest.builder()
            .chartSpecification(HelmChartSpecification.builder().chartName("repo/repo/chartName").build())
            .executionLogCallback(executionLogCallback)
            .activityId("test")
            .build();
    try {
      helmDeployService.fetchInlineChartUrl(helmInstallCommandRequest, LONG_TIMEOUT_INTERVAL);
    } catch (InvalidRequestException invalidRequestException) {
      assertThat(invalidRequestException.getMessage())
          .isEqualTo("Bad chart name specified, please specify in the following format: repo/chartName");
    }
    verify(executionLogCallback, times(0)).saveExecutionLog("Helm Chart Repo checked-out locally");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testPrintHelmChartKubernetesResourcesOnDeploy() throws Exception {
    helmInstallCommandRequest.setNamespace("default");
    helmInstallCommandRequest.setVariableOverridesYamlFiles(Collections.emptyList());
    String renderedHelmChart = "apiVersion: apps/v1\n"
        + "kind: Deployment\n"
        + "metadata:\n"
        + "  name: example\n"
        + "  labels: []\n"
        + "spec:\n"
        + "  selector:\n"
        + "    app: test-app\n"
        + "    release: helm-release\n"
        + "  template:\n"
        + "    metadata:\n"
        + "      name: example\n"
        + "      labels:\n"
        + "         app: test-app\n"
        + "         release: helm-release\n"
        + "---\n"
        + "apiVersion: v1\n"
        + "kind: Secret\n"
        + "metadata:\n"
        + "  name: example\n"
        + "type: Opaque\n"
        + "data:\n"
        + "  sample: c29tZXRpbWVzIHNjaWVuY2UgaXMgbW9yZSBhcnQgdGhhbiBzY2llbmNlLCBNb3J0eQ==\n";
    List<KubernetesResource> expectedLoggedResources = ManifestHelper.processYaml(renderedHelmChart);
    String expectedLoggedYaml = ManifestHelper.toYamlForLogs(expectedLoggedResources);
    HelmCliResponse renderedHelmChartResponse =
        HelmCliResponse.builder().commandExecutionStatus(SUCCESS).output(renderedHelmChart).build();

    when(helmClient.releaseHistory(any(), eq(false))).thenReturn(helmCliReleaseHistoryResponse);
    when(helmClient.upgrade(any(), eq(false))).thenReturn(helmCliResponse);
    when(helmClient.listReleases(any(), eq(false))).thenReturn(helmCliListReleasesResponse);
    doReturn(renderedHelmChartResponse)
        .when(helmClient)
        .renderChart(any(HelmCommandData.class), eq("./repository/helm/source/${ACTIVITY_ID}/test"), eq("default"),
            eq(Collections.emptyList()), eq(false));
    helmDeployService.deploy(helmInstallCommandRequest);
    ArgumentCaptor<String> savedLogsCaptor = ArgumentCaptor.forClass(String.class);
    verify(logCallback, atLeastOnce()).saveExecutionLog(savedLogsCaptor.capture());
    assertThat(savedLogsCaptor.getAllValues()).contains(expectedLoggedYaml);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRenderHelmChartFailed() throws Exception {
    String expectedMessage = "Failed to render chart location: %s. Reason %s";
    String namespace = "default";
    String chartLocation = "/chart";
    String output = "cli failed";
    List<String> valueOverrides = emptyList();
    doReturn(HelmCliResponse.builder().commandExecutionStatus(FAILURE).errorStreamOutput(output).build())
        .when(helmClient)
        .renderChart(HelmCommandDataMapper.getHelmCommandData(helmInstallCommandRequest), chartLocation, namespace,
            valueOverrides, false);

    assertThatThrownBy(
        () -> helmDeployService.renderHelmChart(helmInstallCommandRequest, namespace, chartLocation, valueOverrides))
        .hasMessageContaining(format(expectedMessage, chartLocation, output));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDeployWithHelmChartSpecFromValuesYaml() throws Exception {
    GitConfig gitConfig = GitConfig.builder().build();
    GitFile gitFileWithSimpleValuesYaml = GitFile.builder().fileContent("values").build();
    GitFile gitFileWithChartSpec = GitFile.builder().fileContent("chartSpec").build();
    HelmDeployChartSpec chartSpec =
        HelmDeployChartSpec.builder().name("fileChart").url("fileChartUrl").version("fileChartVer").build();
    GitFetchFilesResult gitFiles =
        GitFetchFilesResult.builder().files(asList(gitFileWithSimpleValuesYaml, gitFileWithChartSpec)).build();
    helmInstallCommandRequest.setChartSpecification(null);
    helmInstallCommandRequest.setGitConfig(gitConfig);
    helmInstallCommandRequest.setGitFileConfig(GitFileConfig.builder()
                                                   .connectorId(SETTING_ID)
                                                   .commitId(COMMIT_REFERENCE)
                                                   .branch(BRANCH_NAME)
                                                   .filePath(FILE_PATH)
                                                   .useBranch(true)
                                                   .build());

    doReturn(helmCliReleaseHistoryResponse)
        .when(helmClient)
        .releaseHistory(HelmCommandDataMapper.getHelmCommandData(helmInstallCommandRequest), false);
    doReturn(gitFiles)
        .when(gitService)
        .fetchFilesByPath(
            gitConfig, SETTING_ID, COMMIT_REFERENCE, BRANCH_NAME, singletonList(FILE_PATH), true, false, logCallback);
    doReturn(Optional.empty()).when(helmCommandHelper).generateHelmDeployChartSpecFromYaml("values");
    doReturn(Optional.of(HarnessHelmDeployConfig.builder().helmDeployChartSpec(chartSpec).build()))
        .when(helmCommandHelper)
        .generateHelmDeployChartSpecFromYaml("chartSpec");

    helmDeployService.deploy(helmInstallCommandRequest);

    HelmChartSpecification deployedSpec = helmInstallCommandRequest.getChartSpecification();
    assertThat(deployedSpec).isNotNull();
    assertThat(deployedSpec.getChartName()).isEqualTo("fileChart");
    assertThat(deployedSpec.getChartUrl()).isEqualTo("fileChartUrl");
    assertThat(deployedSpec.getChartVersion()).isEqualTo("fileChartVer");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDeployWithHelmChartSpecFromValuesYamlWithEmptyValues() throws Exception {
    GitConfig gitConfig = GitConfig.builder().build();
    GitFile gitFileWithChartSpec = GitFile.builder().fileContent("chartSpec").build();
    HelmDeployChartSpec chartSpec = HelmDeployChartSpec.builder().name(null).url(null).version("fileVersion").build();
    GitFetchFilesResult gitFiles = GitFetchFilesResult.builder().files(singletonList(gitFileWithChartSpec)).build();

    helmInstallCommandRequest.setGitConfig(gitConfig);
    helmInstallCommandRequest.setGitFileConfig(GitFileConfig.builder()
                                                   .connectorId(SETTING_ID)
                                                   .commitId(COMMIT_REFERENCE)
                                                   .branch(BRANCH_NAME)
                                                   .filePath(FILE_PATH)
                                                   .useBranch(true)
                                                   .build());

    doReturn(helmCliReleaseHistoryResponse)
        .when(helmClient)
        .releaseHistory(HelmCommandDataMapper.getHelmCommandData(helmInstallCommandRequest), false);
    doReturn(gitFiles)
        .when(gitService)
        .fetchFilesByPath(
            gitConfig, SETTING_ID, COMMIT_REFERENCE, BRANCH_NAME, singletonList(FILE_PATH), true, false, logCallback);
    doReturn(Optional.of(HarnessHelmDeployConfig.builder().helmDeployChartSpec(chartSpec).build()))
        .when(helmCommandHelper)
        .generateHelmDeployChartSpecFromYaml("chartSpec");

    helmDeployService.deploy(helmInstallCommandRequest);

    HelmChartSpecification deployedSpec = helmInstallCommandRequest.getChartSpecification();
    assertThat(deployedSpec).isNotNull();
    assertThat(deployedSpec.getChartName()).isEqualTo(HelmTestConstants.CHART_NAME_KEY);
    assertThat(deployedSpec.getChartVersion()).isEqualTo("fileVersion");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetHelmChartInfoFromChartSpec() throws Exception {
    helmInstallCommandRequest.setRepoConfig(null);
    String searchChartResponse = "NAME\tCHART VERSION\tAPP VERSION\nchartName\tchartVersion\tappVersion";
    String helmRepoListResponse = "NAME\tURL\nchartName\trepoUrl";
    helmInstallCommandRequest.getChartSpecification().setChartUrl("repoUrl");

    helmCliResponse.setOutput("");
    helmCliResponse.setCommandExecutionStatus(SUCCESS);

    doReturn(helmCliReleaseHistoryResponse)
        .when(helmClient)
        .releaseHistory(HelmCommandDataMapper.getHelmCommandData(helmInstallCommandRequest), false);
    doReturn(helmCliResponse)
        .when(helmClient)
        .upgrade(HelmCommandDataMapper.getHelmCommandData(helmInstallCommandRequest), false);
    doReturn(helmCliListReleasesResponse)
        .when(helmClient)
        .listReleases(HelmCommandDataMapper.getHelmCommandData(helmInstallCommandRequest), false);

    doReturn(HelmCliResponse.builder().output(searchChartResponse).build())
        .when(helmClient)
        .searchChart(any(HelmCommandData.class), anyString());
    doReturn(HelmCliResponse.builder().commandExecutionStatus(SUCCESS).output(helmRepoListResponse).build())
        .when(helmClient)
        .getHelmRepoList(any(HelmCommandData.class));

    HelmInstallCommandResponse response =
        (HelmInstallCommandResponse) helmDeployService.deploy(helmInstallCommandRequest);

    assertThat(response.getHelmChartInfo()).isNotNull();
    assertThat(response.getHelmChartInfo().getName()).isEqualTo(HelmTestConstants.CHART_NAME_KEY);
    assertThat(response.getHelmChartInfo().getVersion()).isEqualTo("chartVersion");
    assertThat(response.getHelmChartInfo().getRepoUrl()).isEqualTo("repoUrl");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetHelmChartInfoFromChartSpecEmptyValues() throws Exception {
    helmInstallCommandRequest.setRepoConfig(null);
    helmInstallCommandRequest.getChartSpecification().setChartName("chartName");

    doReturn(helmCliReleaseHistoryResponse)
        .when(helmClient)
        .releaseHistory(HelmCommandDataMapper.getHelmCommandData(helmInstallCommandRequest), false);
    doReturn(helmCliResponse)
        .when(helmClient)
        .upgrade(HelmCommandDataMapper.getHelmCommandData(helmInstallCommandRequest), false);
    doReturn(helmCliListReleasesResponse)
        .when(helmClient)
        .listReleases(HelmCommandDataMapper.getHelmCommandData(helmInstallCommandRequest), false);

    doReturn(HelmCliResponse.builder().output("").build())
        .when(helmClient)
        .searchChart(any(HelmCommandData.class), anyString());
    doReturn(HelmCliResponse.builder().output("").build())
        .when(helmClient)
        .getHelmRepoList(HelmCommandDataMapper.getHelmCommandData(helmInstallCommandRequest));

    HelmInstallCommandResponse response =
        (HelmInstallCommandResponse) helmDeployService.deploy(helmInstallCommandRequest);

    assertThat(response.getHelmChartInfo().getName()).isEqualTo("chartName");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetHelmChartInfoVersionFromChartSpec() throws Exception {
    helmInstallCommandRequest.setRepoConfig(null);
    helmInstallCommandRequest.getChartSpecification().setChartName("chartName");
    helmInstallCommandRequest.getChartSpecification().setChartVersion("chartVersionFromSpec");

    doReturn(helmCliReleaseHistoryResponse)
        .when(helmClient)
        .releaseHistory(HelmCommandDataMapper.getHelmCommandData(helmInstallCommandRequest), false);
    doReturn(helmCliResponse)
        .when(helmClient)
        .upgrade(HelmCommandDataMapper.getHelmCommandData(helmInstallCommandRequest), false);
    doReturn(helmCliListReleasesResponse)
        .when(helmClient)
        .listReleases(HelmCommandDataMapper.getHelmCommandData(helmInstallCommandRequest), false);

    HelmInstallCommandResponse response =
        (HelmInstallCommandResponse) helmDeployService.deploy(helmInstallCommandRequest);

    assertThat(response.getHelmChartInfo().getVersion()).isEqualTo("chartVersionFromSpec");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetHelmChartInfoRepourlFromChartSpecChartName() throws Exception {
    helmInstallCommandRequest.setRepoConfig(null);
    helmInstallCommandRequest.getChartSpecification().setChartVersion("chartVersion");
    helmInstallCommandRequest.getChartSpecification().setChartName("chartRepoUrlFromSpec/chartName");

    doReturn(helmCliReleaseHistoryResponse)
        .when(helmClient)
        .releaseHistory(HelmCommandDataMapper.getHelmCommandData(helmInstallCommandRequest), false);
    doReturn(helmCliResponse)
        .when(helmClient)
        .upgrade(HelmCommandDataMapper.getHelmCommandData(helmInstallCommandRequest), false);
    doReturn(helmCliListReleasesResponse)
        .when(helmClient)
        .listReleases(HelmCommandDataMapper.getHelmCommandData(helmInstallCommandRequest), false);

    HelmInstallCommandResponse response =
        (HelmInstallCommandResponse) helmDeployService.deploy(helmInstallCommandRequest);

    assertThat(response.getHelmChartInfo().getRepoUrl()).isEqualTo("chartRepoUrlFromSpec");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetHelmChartInfoFromRemoteRepo() throws Exception {
    testGetHelmChartInfoFromRemoteRepo(StoreType.HelmChartRepo);
    testGetHelmChartInfoFromRemoteRepo(StoreType.HelmSourceRepo);
  }

  private void testGetHelmChartInfoFromRemoteRepo(StoreType storeType) throws Exception {
    K8sDelegateManifestConfig manifestConfig =
        K8sDelegateManifestConfig.builder()
            .manifestStoreTypes(storeType)
            .helmChartConfigParams(HelmChartConfigParams.builder().chartName("chartName").build())
            .gitConfig(GitConfig.builder().build())
            .gitFileConfig(GitFileConfig.builder().build())
            .build();
    helmInstallCommandRequest.setRepoConfig(manifestConfig);
    HelmChartInfo helmChartInfo = HelmChartInfo.builder().name("chartName").build();

    helmCliResponse.setOutput("");
    helmCliResponse.setCommandExecutionStatus(SUCCESS);

    doReturn(helmCliReleaseHistoryResponse)
        .when(helmClient)
        .releaseHistory(HelmCommandDataMapper.getHelmCommandData(helmInstallCommandRequest), false);
    doReturn(helmCliResponse)
        .when(helmClient)
        .upgrade(HelmCommandDataMapper.getHelmCommandData(helmInstallCommandRequest), false);
    doReturn(helmCliListReleasesResponse)
        .when(helmClient)
        .listReleases(HelmCommandDataMapper.getHelmCommandData(helmInstallCommandRequest), false);
    doReturn(helmChartInfo).when(helmTaskHelper).getHelmChartInfoFromChartsYamlFile(helmInstallCommandRequest);

    HelmInstallCommandResponse response =
        (HelmInstallCommandResponse) helmDeployService.deploy(helmInstallCommandRequest);

    assertThat(response.getHelmChartInfo()).isEqualTo(helmChartInfo);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDeleteHelmReleaseAtEnd() throws Exception {
    String releaseListOutput = "NAME\tREVISION\tSTATUS\tCHART\tNAMESPACE\n";
    releaseListOutput += "releaseX\t3\tSUCCESS\tchart\tdefault\n";
    releaseListOutput += "releaseY\t1\tSUCCESS\tchart\tdefault\n";
    releaseListOutput += "releaseZ\t1\tFAILED\tchart\tdefault\n";
    releaseListOutput += "release\t1\tFAILED\tchart\tdefault\n";
    helmInstallCommandRequest.setReleaseName("release");

    doReturn(HelmCliResponse.builder().output(releaseListOutput).commandExecutionStatus(SUCCESS).build())
        .when(helmClient)
        .listReleases(HelmCommandDataMapper.getHelmCommandData(helmInstallCommandRequest), false);

    helmDeployService.deploy(helmInstallCommandRequest);

    verify(helmClient, times(1))
        .deleteHelmRelease(HelmCommandDataMapper.getHelmCommandData(helmInstallCommandRequest), false);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRepoUpdateOnDeploy() throws Exception {
    helmInstallCommandRequest.setRepoConfig(null);

    doReturn(helmCliReleaseHistoryResponse)
        .when(helmClient)
        .releaseHistory(HelmCommandDataMapper.getHelmCommandData(helmInstallCommandRequest), false);
    ArgumentCaptor<io.harness.helm.HelmCommandData> argumentCaptor = ArgumentCaptor.forClass(HelmCommandData.class);
    helmDeployService.deploy(helmInstallCommandRequest);

    verify(helmClient, times(1)).repoUpdate(argumentCaptor.capture());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRepoUpdateOnDeployNonInstallRequest() throws Exception {
    helmInstallCommandRequest.setRepoConfig(null);
    helmInstallCommandRequest.setHelmCommandType(HelmCommandType.LIST_RELEASE);
    doReturn(helmCliReleaseHistoryResponse)
        .when(helmClient)
        .releaseHistory(HelmCommandDataMapper.getHelmCommandData(helmInstallCommandRequest), false);
    helmDeployService.deploy(helmInstallCommandRequest);

    verify(helmClient, never()).repoUpdate(HelmCommandDataMapper.getHelmCommandData(helmInstallCommandRequest));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRepoUpdateOnDeployException() throws Exception {
    IOException thrownException = new IOException("Unable to do something");
    helmInstallCommandRequest.setRepoConfig(null);

    doReturn(helmCliReleaseHistoryResponse)
        .when(helmClient)
        .releaseHistory(HelmCommandDataMapper.getHelmCommandData(helmInstallCommandRequest), false);
    doThrow(thrownException)
        .when(helmClient)
        .repoUpdate(HelmCommandDataMapper.getHelmCommandData(helmInstallCommandRequest));

    HelmInstallCommandResponse response =
        (HelmInstallCommandResponse) helmDeployService.deploy(helmInstallCommandRequest);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(FAILURE);
    assertThat(response.getOutput()).contains("Unable to do something");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testFetchCustomSourceHandleException() throws Exception {
    HelmInstallCommandRequest request =
        HelmInstallCommandRequest.builder()
            .executionLogCallback(executionLogCallback)
            .sourceRepoConfig(K8sDelegateManifestConfig.builder()
                                  .customManifestEnabled(false)
                                  .customManifestSource(CustomManifestSource.builder()
                                                            .script("script")
                                                            .filePaths(singletonList("file1"))
                                                            .zippedManifestFileId("fileId")
                                                            .build())
                                  .build())
            .build();

    assertThatThrownBy(() -> helmDeployService.fetchCustomSourceManifest(request, logCallback))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Can not use store type: CUSTOM, with feature flag off");

    request.setRepoConfig(null);
    assertThatThrownBy(() -> helmDeployService.fetchCustomSourceManifest(request, logCallback))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Source Config can not be null");

    request.setRepoConfig(K8sDelegateManifestConfig.builder().customManifestEnabled(true).build());
    assertThatThrownBy(() -> helmDeployService.fetchCustomSourceManifest(request, logCallback))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Custom Manifest Source can not be null");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testFetchCustomSource() throws Exception {
    final String workingDirPath = "./repository/helm/source/ACTIVITY_ID";
    final String manifestDirPath = format("%s/manifestDir", workingDirPath);
    HelmInstallCommandRequest request =
        HelmInstallCommandRequest.builder()
            .executionLogCallback(executionLogCallback)
            .activityId("ACTIVITY_ID")
            .accountId("ACCOUNT_ID")
            .sourceRepoConfig(K8sDelegateManifestConfig.builder()
                                  .customManifestEnabled(true)
                                  .customManifestSource(CustomManifestSource.builder()
                                                            .script("script")
                                                            .filePaths(singletonList("file1"))
                                                            .zippedManifestFileId("fileId")
                                                            .build())
                                  .build())
            .build();

    FileIo.createDirectoryIfDoesNotExist(workingDirPath);
    FileIo.createDirectoryIfDoesNotExist(manifestDirPath);
    Files.createFile(Paths.get(manifestDirPath, "test.yaml"));
    doNothing()
        .when(customManifestFetchTaskHelper)
        .downloadAndUnzipCustomSourceManifestFiles(anyString(), anyString(), anyString());

    helmDeployService.fetchCustomSourceManifest(request, logCallback);

    verify(customManifestFetchTaskHelper, times(1))
        .downloadAndUnzipCustomSourceManifestFiles(anyString(), anyString(), anyString());
    File workingDir = new File(workingDirPath);
    assertThat(workingDir.exists());
    assertThat(workingDir.list()).hasSize(1);
    assertThat(workingDir.list()).contains("test.yaml");
    FileIo.deleteDirectoryAndItsContentIfExists(workingDirPath);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testFetchCustomSourceException() throws Exception {
    final String manifestDirPath = "./repository/helm/source/ACTIVITY_ID/";
    try {
      HelmInstallCommandRequest request =
          HelmInstallCommandRequest.builder()
              .executionLogCallback(executionLogCallback)
              .activityId("ACTIVITY_ID")
              .accountId("ACCOUNT_ID")
              .sourceRepoConfig(K8sDelegateManifestConfig.builder()
                                    .customManifestEnabled(true)
                                    .customManifestSource(CustomManifestSource.builder()
                                                              .script("script")
                                                              .filePaths(singletonList("file1/test.yaml"))
                                                              .zippedManifestFileId("fileId")
                                                              .build())
                                    .build())
              .build();

      FileIo.createDirectoryIfDoesNotExist(manifestDirPath);
      FileIo.writeFile(manifestDirPath + "/test.yaml", new byte[] {});

      doNothing()
          .when(customManifestFetchTaskHelper)
          .downloadAndUnzipCustomSourceManifestFiles(anyString(), anyString(), anyString());

      assertThatThrownBy(() -> helmDeployService.fetchCustomSourceManifest(request, logCallback))
          .isInstanceOf(InvalidRequestException.class)
          .hasMessage(
              "Provided helm chart path point to a file inside the helm chart. Path to the helm chart directory should be provided instead")
          .hasRootCauseInstanceOf(IOException.class);

    } finally {
      deleteDirectoryAndItsContentIfExists(manifestDirPath.toString());
    }
  }
}
