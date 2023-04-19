/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.helm.HelmSubCommandType.TEMPLATE;
import static io.harness.k8s.model.Kind.ConfigMap;
import static io.harness.k8s.model.Kind.Deployment;
import static io.harness.k8s.model.Kind.Namespace;
import static io.harness.k8s.model.Kind.Service;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.beans.appmanifest.StoreType.CUSTOM;
import static software.wings.beans.appmanifest.StoreType.CUSTOM_OPENSHIFT_TEMPLATE;
import static software.wings.beans.appmanifest.StoreType.HelmChartRepo;
import static software.wings.beans.appmanifest.StoreType.HelmSourceRepo;
import static software.wings.beans.appmanifest.StoreType.KustomizeSourceRepo;
import static software.wings.beans.appmanifest.StoreType.Local;
import static software.wings.beans.appmanifest.StoreType.OC_TEMPLATES;
import static software.wings.beans.appmanifest.StoreType.Remote;
import static software.wings.utils.WingsTestConstants.LONG_TIMEOUT_INTERVAL;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FileData;
import io.harness.category.element.UnitTests;
import io.harness.delegate.k8s.beans.K8sHandlerConfig;
import io.harness.delegate.k8s.openshift.OpenShiftDelegateService;
import io.harness.delegate.service.ExecutionConfigOverrideFromFileOnDelegate;
import io.harness.delegate.task.helm.CustomManifestFetchTaskHelper;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.delegate.task.helm.HelmCommandFlag;
import io.harness.delegate.task.helm.HelmTaskHelperBase;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.delegate.task.k8s.k8sbase.KustomizeTaskHelper;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.HelmClientException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.filesystem.FileIo;
import io.harness.git.model.GitFile;
import io.harness.helm.HelmCliCommandType;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.model.HelmVersion;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LoggingInitializer;
import io.harness.manifest.CustomManifestService;
import io.harness.manifest.CustomManifestSource;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.GitConfig;
import software.wings.beans.GitConfig.ProviderType;
import software.wings.beans.GitFileConfig;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.beans.yaml.GitFetchFilesResult;
import software.wings.delegatetasks.ScmFetchFilesHelper;
import software.wings.delegatetasks.helm.HelmTaskHelper;
import software.wings.exception.ShellScriptException;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.helm.HelmHelper;
import software.wings.helpers.ext.helm.request.HelmChartConfigParams;
import software.wings.helpers.ext.k8s.K8sManagerHelper;
import software.wings.helpers.ext.k8s.request.K8sApplyTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.helpers.ext.k8s.request.K8sDelegateManifestConfig;
import software.wings.helpers.ext.k8s.request.K8sRollingDeployRollbackTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;
import software.wings.helpers.ext.k8s.response.K8sApplyResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskResponse;
import software.wings.service.intfc.GitService;
import software.wings.service.intfc.security.EncryptionService;

import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.zeroturnaround.exec.ProcessOutput;
import org.zeroturnaround.exec.ProcessResult;
import wiremock.com.google.common.collect.ImmutableList;
import wiremock.com.google.common.collect.ImmutableMap;

/*
 * Do not use powermock because jacoco does not support coverage using it. If you are sure that jacoco supports it
 now,
 * only then use it. Meanwhile, move powermock based tests to K8sTaskHelperSecondaryTest
 */
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class K8sTaskHelperTest extends CategoryTest {
  @Mock private ExecutionLogCallback logCallback;
  @Mock private KubernetesContainerService mockKubernetesContainerService;
  @Mock private GitService mockGitService;
  @Mock private EncryptionService mockEncryptionService;
  @Mock private HelmTaskHelper mockHelmTaskHelper;
  @Mock private ExecutionLogCallback executionLogCallback;
  @Mock private KustomizeTaskHelper kustomizeTaskHelper;
  @Mock private OpenShiftDelegateService openShiftDelegateService;
  @Mock private K8sTaskHelperBase mockK8sTaskHelperBase;
  @Mock private HelmHelper helmHelper;
  @Mock private CustomManifestService customManifestService;
  @Mock private ExecutionConfigOverrideFromFileOnDelegate delegateLocalConfigService;
  @Mock private ScmFetchFilesHelper scmFetchFilesHelper;
  @Mock private ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;
  @Mock private CustomManifestFetchTaskHelper customManifestFetchTaskHelper;
  @Mock private HelmTaskHelperBase helmTaskHelperBase;

  private static final String REPO_URL = "helm-url";
  private String resourcePath = "k8s";
  private String deploymentYaml = "deployment.yaml";
  private String deploymentConfigYaml = "deployment-config.yaml";
  private String configMapYaml = "configMap.yaml";

  private final String flagValue = "--flag-test-1";
  private final HelmCommandFlag commandFlag =
      HelmCommandFlag.builder().valueMap(ImmutableMap.of(TEMPLATE, flagValue)).build();

  @Inject @InjectMocks private K8sTaskHelperBase k8sTaskHelperBase;
  @Inject @InjectMocks private K8sTaskHelper helper;
  @Inject private K8sTaskHelper spyHelper;
  @Inject private K8sTaskHelperBase spyHelperBase;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    LoggingInitializer.initializeLogging();
    spyHelper = Mockito.spy(helper);
    spyHelperBase = Mockito.spy(k8sTaskHelperBase);
    when(helmTaskHelperBase.isHelmLocalRepoSet()).thenReturn(false);
    when(helmTaskHelperBase.getHelmLocalRepositoryPath()).thenReturn("");
    doReturn(-1).when(helmTaskHelperBase).skipDefaultHelmValuesYaml(anyString(), any(), anyBoolean(), any());
  }

  private List<KubernetesResourceId> getKubernetesResourceIdList(String suffix) {
    List<KubernetesResourceId> kubernetesResourceIds = new ArrayList<>();
    kubernetesResourceIds.add(
        KubernetesResourceId.builder().kind(Namespace.name()).name("n" + suffix).namespace("default").build());
    kubernetesResourceIds.add(
        KubernetesResourceId.builder().kind(Deployment.name()).name("d" + suffix).namespace("default").build());
    kubernetesResourceIds.add(
        KubernetesResourceId.builder().kind(ConfigMap.name()).name("c" + suffix).namespace("default").build());
    kubernetesResourceIds.add(
        KubernetesResourceId.builder().kind(Service.name()).name("s" + suffix).namespace("default").build());
    return kubernetesResourceIds;
  }

  private List<FileData> prepareSomeCorrectManifestFiles() throws IOException {
    return asList(FileData.builder()
                      .fileContent(K8sTestHelper.readFileContent(deploymentYaml, resourcePath))
                      .fileName(deploymentYaml)
                      .build(),
        FileData.builder()
            .fileName(deploymentConfigYaml)
            .fileContent(K8sTestHelper.readFileContent(deploymentConfigYaml, resourcePath))
            .build(),
        FileData.builder()
            .fileName(configMapYaml)
            .fileContent(K8sTestHelper.readFileContent(configMapYaml, resourcePath))
            .build());
  }

  private FileData prepareValuesYamlFile() {
    return FileData.builder().fileName("values.yaml").fileContent("key:value").build();
  }

  @Test
  @Owner(developers = {YOGESH, TMACARI})
  @Category(UnitTests.class)
  public void fetchManifestFilesAndWriteToDirectory() throws Exception {
    fetchManifestFilesAndWriteToDirectory_local();
    fetchManifestFilesAndWriteToDirectory_helmChartRepo();
    fetchManifestFilesAndWriteToDirectory_gitRepo(HelmSourceRepo);
    fetchManifestFilesAndWriteToDirectory_gitRepo(Remote);
    fetchManifestFilesAndWriteToDirectory_usingScm();
    fetchManifestFilesAndWriteToDirectory_gitRepo(OC_TEMPLATES);
    fetchManifestFilesAndWriteToDirectory_gitRepo(KustomizeSourceRepo);
  }

  private void fetchManifestFilesAndWriteToDirectory_gitRepo(StoreType storeType) throws IOException {
    K8sTaskHelper spyHelper = spy(helper);
    doReturn("").when(spyHelperBase).getManifestFileNamesInLogFormat(anyString());
    assertThat(
        spyHelper.fetchManifestFilesAndWriteToDirectory(
            K8sDelegateManifestConfig.builder()
                .manifestStoreTypes(storeType)
                .gitConfig(GitConfig.builder().repoUrl(REPO_URL).build())
                .encryptedDataDetails(
                    Collections.singletonList(EncryptedDataDetail.builder().fieldName("serviceToken").build()))
                .gitFileConfig(
                    GitFileConfig.builder().filePath("dir/file").branch("master").connectorId("git-connector").build())
                .build(),
            "./dir", logCallback, LONG_TIMEOUT_INTERVAL))
        .isTrue();

    verify(mockGitService, times(1))
        .downloadFiles(
            eq(GitConfig.builder().repoUrl(REPO_URL).build()), any(GitFileConfig.class), eq("./dir"), eq(false), any());
    verify(mockEncryptionService, times(1)).decrypt(any(), anyList(), eq(false));

    // handle exception
    doThrow(new RuntimeException())
        .when(mockGitService)
        .downloadFiles(any(GitConfig.class), any(GitFileConfig.class), anyString(), eq(false), any());
    assertThat(
        spyHelper.fetchManifestFilesAndWriteToDirectory(
            K8sDelegateManifestConfig.builder()
                .manifestStoreTypes(storeType)
                .gitConfig(GitConfig.builder().repoUrl(REPO_URL).build())
                .gitFileConfig(
                    GitFileConfig.builder().filePath("dir/file").branch("master").connectorId("git-connector").build())
                .build(),
            "./dir", logCallback, LONG_TIMEOUT_INTERVAL))
        .isFalse();
    reset(mockGitService);
    reset(mockEncryptionService);
  }

  private void fetchManifestFilesAndWriteToDirectory_usingScm() throws IOException {
    K8sTaskHelper spyHelper = spy(helper);
    doReturn("").when(spyHelperBase).getManifestFileNamesInLogFormat(anyString());
    doReturn(true).when(scmFetchFilesHelper).shouldUseScm(anyBoolean(), any());
    assertThat(spyHelper.fetchManifestFilesAndWriteToDirectory(
                   K8sDelegateManifestConfig.builder()
                       .optimizedFilesFetch(true)
                       .manifestStoreTypes(Remote)
                       .encryptedDataDetails(
                           Collections.singletonList(EncryptedDataDetail.builder().fieldName("serviceToken").build()))
                       .gitConfig(GitConfig.builder().repoUrl(REPO_URL).providerType(ProviderType.GITHUB).build())
                       .gitFileConfig(GitFileConfig.builder()
                                          .useBranch(true)
                                          .filePath("dir/file")
                                          .branch("master")
                                          .connectorId("git-connector")
                                          .build())
                       .build(),
                   "./dir", logCallback, LONG_TIMEOUT_INTERVAL))
        .isTrue();

    verify(scmFetchFilesHelper, times(1)).downloadFilesUsingScm(any(), any(), any(), any());
    verify(mockGitService, times(0))
        .downloadFiles(
            eq(GitConfig.builder().repoUrl(REPO_URL).build()), any(GitFileConfig.class), eq("./dir"), eq(false), any());
    verify(mockEncryptionService, times(1)).decrypt(any(), anyList(), eq(false));

    // handle exception
    doReturn(true).when(scmFetchFilesHelper).shouldUseScm(anyBoolean(), any());
    doThrow(new RuntimeException()).when(scmFetchFilesHelper).downloadFilesUsingScm(any(), any(), any(), any());
    assertThat(spyHelper.fetchManifestFilesAndWriteToDirectory(
                   K8sDelegateManifestConfig.builder()
                       .optimizedFilesFetch(true)
                       .manifestStoreTypes(Remote)
                       .gitConfig(GitConfig.builder().repoUrl(REPO_URL).providerType(ProviderType.GITHUB).build())
                       .gitFileConfig(GitFileConfig.builder()
                                          .useBranch(true)
                                          .filePath("dir/file")
                                          .branch("master")
                                          .connectorId("git-connector")
                                          .build())
                       .build(),
                   "./dir", logCallback, LONG_TIMEOUT_INTERVAL))
        .isFalse();
    reset(mockGitService);
    reset(mockEncryptionService);
    reset(scmFetchFilesHelper);
  }

  private void fetchManifestFilesAndWriteToDirectory_helmChartRepo() throws Exception {
    doReturn("").when(mockK8sTaskHelperBase).getManifestFileNamesInLogFormat(anyString());
    final HelmChartConfigParams helmChartConfigParams = HelmChartConfigParams.builder().chartVersion("1.0").build();
    assertThat(helper.fetchManifestFilesAndWriteToDirectory(K8sDelegateManifestConfig.builder()
                                                                .manifestStoreTypes(StoreType.HelmChartRepo)
                                                                .helmChartConfigParams(helmChartConfigParams)
                                                                .build(),
                   "dir", logCallback, LONG_TIMEOUT_INTERVAL))
        .isTrue();

    verify(mockHelmTaskHelper, times(1)).printHelmChartInfoInExecutionLogs(helmChartConfigParams, logCallback);
    verify(mockHelmTaskHelper, times(1)).downloadChartFiles(eq(helmChartConfigParams), eq("dir"), anyLong(), eq(null));

    doThrow(new RuntimeException())
        .when(mockHelmTaskHelper)
        .downloadChartFiles(any(HelmChartConfigParams.class), anyString(), anyLong(), eq(null));
    assertThat(helper.fetchManifestFilesAndWriteToDirectory(K8sDelegateManifestConfig.builder()
                                                                .manifestStoreTypes(StoreType.HelmChartRepo)
                                                                .helmChartConfigParams(helmChartConfigParams)
                                                                .build(),
                   "dir", logCallback, LONG_TIMEOUT_INTERVAL))
        .isFalse();
  }

  private void fetchManifestFilesAndWriteToDirectory_local() throws IOException {
    String manifestFileDirectory = Files.createTempDirectory(generateUuid()).toString();
    List<FileData> manifestFiles = new ArrayList<>(prepareSomeCorrectManifestFiles());
    manifestFiles.add(prepareValuesYamlFile());
    boolean success =
        helper.fetchManifestFilesAndWriteToDirectory(K8sDelegateManifestConfig.builder()
                                                         .manifestStoreTypes(Local)
                                                         .manifestFiles(convertFileDataToManifestFiles(manifestFiles))
                                                         .build(),
            manifestFileDirectory, logCallback, LONG_TIMEOUT_INTERVAL);
    assertThat(success).isTrue();
    assertThat(Arrays.stream(new File(manifestFileDirectory).listFiles())
                   .filter(file -> file.length() > 0)
                   .map(File::getName)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder("deployment.yaml", "deployment-config.yaml", "configMap.yaml");
    FileIo.deleteDirectoryAndItsContentIfExists(manifestFileDirectory);

    // only values.yaml
    FileData fileData = prepareValuesYamlFile();
    software.wings.beans.dto.ManifestFile values = software.wings.beans.dto.ManifestFile.builder()
                                                       .fileName(fileData.getFileName())
                                                       .fileContent(fileData.getFileContent())
                                                       .build();
    assertThat(helper.fetchManifestFilesAndWriteToDirectory(
                   K8sDelegateManifestConfig.builder().manifestFiles(asList(values)).manifestStoreTypes(Local).build(),
                   manifestFileDirectory, logCallback, LONG_TIMEOUT_INTERVAL))
        .isTrue();

    // invalid manifest files directory
    assertThat(helper.fetchManifestFilesAndWriteToDirectory(
                   K8sDelegateManifestConfig.builder()
                       .manifestStoreTypes(Local)
                       .manifestFiles(convertFileDataToManifestFiles(prepareSomeCorrectManifestFiles()))
                       .build(),
                   "", logCallback, LONG_TIMEOUT_INTERVAL))
        .isFalse();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchManifestFilesAndWriteToDirectory_Custom() throws IOException {
    fetchManifestFilesAndWriteToDirectory_Custom(CUSTOM);
    fetchManifestFilesAndWriteToDirectory_Custom(CUSTOM_OPENSHIFT_TEMPLATE);
  }

  private void fetchManifestFilesAndWriteToDirectory_Custom(StoreType storeType) throws IOException {
    reset(customManifestService);
    String manifestDirectory = "manifest-files";
    doReturn("").when(mockK8sTaskHelperBase).getManifestFileNamesInLogFormat(anyString());
    CustomManifestSource customManifestSource = CustomManifestSource.builder().build();
    K8sDelegateManifestConfig delegateManifestConfig = K8sDelegateManifestConfig.builder()
                                                           .manifestStoreTypes(storeType)
                                                           .customManifestSource(customManifestSource)
                                                           .build();

    delegateManifestConfig.setCustomManifestEnabled(false);
    assertThat(helper.fetchManifestFilesAndWriteToDirectory(
                   delegateManifestConfig, manifestDirectory, executionLogCallback, 50000))
        .isFalse();
    verify(customManifestService, times(0))
        .downloadCustomSource(customManifestSource, manifestDirectory, executionLogCallback);

    delegateManifestConfig.setCustomManifestEnabled(true);
    assertThat(helper.fetchManifestFilesAndWriteToDirectory(
                   delegateManifestConfig, manifestDirectory, executionLogCallback, 50000))
        .isTrue();
    verify(customManifestService, times(1))
        .downloadCustomSource(customManifestSource, manifestDirectory, executionLogCallback);

    doThrow(new IOException("file doesn't exists"))
        .when(customManifestService)
        .downloadCustomSource(customManifestSource, manifestDirectory, executionLogCallback);
    assertThat(helper.fetchManifestFilesAndWriteToDirectory(
                   delegateManifestConfig, manifestDirectory, executionLogCallback, 50000))
        .isFalse();

    doThrow(new ShellScriptException("command not found", ErrorCode.GENERAL_ERROR, Level.ERROR, WingsException.USER))
        .when(customManifestService)
        .downloadCustomSource(customManifestSource, manifestDirectory, executionLogCallback);
    assertThat(helper.fetchManifestFilesAndWriteToDirectory(
                   delegateManifestConfig, manifestDirectory, executionLogCallback, 50000))
        .isFalse();

    doThrow(new NullPointerException())
        .when(customManifestService)
        .downloadCustomSource(customManifestSource, manifestDirectory, executionLogCallback);
    assertThat(helper.fetchManifestFilesAndWriteToDirectory(
                   delegateManifestConfig, manifestDirectory, executionLogCallback, 50000))
        .isFalse();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testFetchBoundCustomSource() throws Exception {
    reset(customManifestService);
    final String tempParentDir = "temp/repository/k8s/source/tempParentDir";
    final String tempDirPath = format("%s/temp-files", tempParentDir);
    final String manifestDirectory = "repository/k8s/source/manifest-files";
    doReturn("").when(mockK8sTaskHelperBase).getManifestFileNamesInLogFormat(anyString());
    CustomManifestSource customManifestSource = CustomManifestSource.builder()
                                                    .script("script")
                                                    .filePaths(singletonList("file1"))
                                                    .zippedManifestFileId("fileId")
                                                    .build();
    K8sDelegateManifestConfig delegateManifestConfig = K8sDelegateManifestConfig.builder()
                                                           .manifestStoreTypes(CUSTOM)
                                                           .customManifestSource(customManifestSource)
                                                           .customManifestEnabled(true)
                                                           .build();

    delegateManifestConfig.setBindValuesAndManifestFetchTask(true);
    FileIo.createDirectoryIfDoesNotExist(manifestDirectory);
    FileIo.createDirectoryIfDoesNotExist(tempDirPath);
    Files.createFile(Paths.get(tempDirPath, "test.yaml"));

    doReturn(tempParentDir).when(customManifestService).getWorkingDirectory();
    assertThat(helper.fetchManifestFilesAndWriteToDirectory(
                   delegateManifestConfig, manifestDirectory, executionLogCallback, 50000))
        .isTrue();
    File file = new File(manifestDirectory);
    assertThat(file.exists()).isTrue();
    assertThat(file.list()).hasSize(1);
    assertThat(file.list()).contains("test.yaml");
  }

  private List<software.wings.beans.dto.ManifestFile> convertFileDataToManifestFiles(List<FileData> fileDataList) {
    return fileDataList.stream()
        .map(p
            -> software.wings.beans.dto.ManifestFile.builder()
                   .fileName(p.getFileName())
                   .fileContent(p.getFileContent())
                   .build())
        .collect(Collectors.toList());
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void manifestFilesFromGitFetchFilesResult() {
    List<ManifestFile> manifestFiles = K8sManagerHelper.manifestFilesFromGitFetchFilesResult(
        GitFetchFilesResult.builder()
            .files(asList(GitFile.builder().fileContent("abc").filePath("file-1").build()))
            .build(),
        "");
    assertThat(manifestFiles).hasSize(1);
    assertThat(manifestFiles.get(0).getFileContent()).isEqualTo("abc");
    assertThat(manifestFiles.get(0).getFileName()).isEqualTo("file-1");

    assertThat(K8sManagerHelper.manifestFilesFromGitFetchFilesResult(GitFetchFilesResult.builder().build(), ""))
        .isEmpty();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void manifestFilesFromGitFetchFilesResult_EmptyFiles() {
    assertThat(K8sManagerHelper.manifestFilesFromGitFetchFilesResult(
                   GitFetchFilesResult.builder().files(emptyList()).build(), ""))
        .isEmpty();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void manifestFilesFromGitFetchFilesResult_NullFiles() {
    assertThat(K8sManagerHelper.manifestFilesFromGitFetchFilesResult(GitFetchFilesResult.builder().build(), ""))
        .isEmpty();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testRenderTemplateForGivenFilesLocal() throws Exception {
    fetchManifestFilesAndWriteToDirectory();
    final String workingDirectory = ".";
    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder()
                                                      .workingDirectory(workingDirectory)
                                                      .ocPath("oc")
                                                      .kubectlPath("kubectl")
                                                      .kubeconfigPath("config-path")
                                                      .build();

    final List<FileData> manifestFiles = spyHelper.renderTemplateForGivenFiles(k8sDelegateTaskParams,
        K8sDelegateManifestConfig.builder().manifestStoreTypes(Local).build(), ".", new ArrayList<>(),
        new ArrayList<>(), "release", "namespace", executionLogCallback, K8sApplyTaskParameters.builder().build(),
        false);

    assertThat(manifestFiles.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testRenderTemplateForGivenFilesGit() throws Exception {
    fetchManifestFilesAndWriteToDirectory();
    final String workingDirectory = ".";
    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder()
                                                      .workingDirectory(workingDirectory)
                                                      .ocPath("oc")
                                                      .kubectlPath("kubectl")
                                                      .kubeconfigPath("config-path")
                                                      .build();

    ProcessResult processResult = new ProcessResult(0, new ProcessOutput("".getBytes()));
    doReturn(processResult)
        .when(spyHelperBase)
        .executeCommandUsingUtils(any(K8sDelegateTaskParams.class), any(), any(), any(), any());

    final List<FileData> manifestFiles = spyHelper.renderTemplateForGivenFiles(k8sDelegateTaskParams,
        K8sDelegateManifestConfig.builder().manifestStoreTypes(Remote).build(), ".", new ArrayList<>(),
        new ArrayList<>(), "release", "namespace", executionLogCallback, K8sApplyTaskParameters.builder().build(),
        false);

    assertThat(manifestFiles.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testRenderTemplateForGivenFilesGitSkipRendering() throws Exception {
    fetchManifestFilesAndWriteToDirectory();
    final String workingDirectory = ".";
    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder()
                                                      .workingDirectory(workingDirectory)
                                                      .ocPath("oc")
                                                      .kubectlPath("kubectl")
                                                      .kubeconfigPath("config-path")
                                                      .build();

    FileData fileData = FileData.builder().fileName("test").build();
    List<FileData> manifestFilesList = singletonList(fileData);
    Mockito.when(mockK8sTaskHelperBase.readFilesFromDirectory(anyString(), anyList(), any(ExecutionLogCallback.class)))
        .thenReturn(manifestFilesList);

    final List<FileData> manifestFiles = spyHelper.renderTemplateForGivenFiles(k8sDelegateTaskParams,
        K8sDelegateManifestConfig.builder().manifestStoreTypes(Remote).build(), ".", new ArrayList<>(),
        new ArrayList<>(), "release", "namespace", executionLogCallback, K8sApplyTaskParameters.builder().build(),
        true);

    assertThat(manifestFiles).isEqualTo(manifestFilesList);
    verify(mockK8sTaskHelperBase, times(0)).renderManifestFilesForGoTemplate(any(), any(), any(), any(), anyLong());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testRenderTemplateForGiveFilesHelmSourceRepo() throws Exception {
    final String workingDirectory = ".";
    K8sDelegateTaskParams k8sDelegateTaskParams =
        K8sDelegateTaskParams.builder().workingDirectory(workingDirectory).helmPath("helm").build();

    spyHelper.renderTemplateForGivenFiles(k8sDelegateTaskParams,
        K8sDelegateManifestConfig.builder().manifestStoreTypes(HelmSourceRepo).helmCommandFlag(commandFlag).build(),
        ".", new ArrayList<>(), new ArrayList<>(), "release", "namespace", executionLogCallback,
        K8sApplyTaskParameters.builder().build(), false);

    verify(mockK8sTaskHelperBase)
        .renderTemplateForHelmChartFiles(
            any(), anyString(), any(), any(), anyString(), anyString(), any(), any(), anyLong(), eq(commandFlag));
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testRenderTemplateForGivenFilesKustomizeSourceRepo() throws Exception {
    final String workingDirectory = ".";
    K8sDelegateTaskParams k8sDelegateTaskParams =
        K8sDelegateTaskParams.builder().workingDirectory(workingDirectory).helmPath("helm").build();

    doReturn(new ArrayList<>())
        .when(kustomizeTaskHelper)
        .buildForApply(any(), any(), any(), any(), anyBoolean(), any(), any(), eq(Collections.emptyMap()));
    final List<FileData> manifestFiles = spyHelper.renderTemplateForGivenFiles(k8sDelegateTaskParams,
        K8sDelegateManifestConfig.builder().manifestStoreTypes(KustomizeSourceRepo).build(), ".", new ArrayList<>(),
        new ArrayList<>(), "release", "namespace", executionLogCallback, K8sApplyTaskParameters.builder().build(),
        false);
    verify(kustomizeTaskHelper).buildForApply(any(), any(), any(), any(), anyBoolean(), any(), any(), anyMap());
    assertThat(manifestFiles.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testRenderTemplateForGivenFilesKustomizeSourceRepoFFOn() throws Exception {
    final String workingDirectory = ".";
    K8sDelegateTaskParams k8sDelegateTaskParams =
        K8sDelegateTaskParams.builder().workingDirectory(workingDirectory).helmPath("helm").build();

    doReturn(new ArrayList<>())
        .when(kustomizeTaskHelper)
        .buildForApply(any(), any(), any(), any(), anyBoolean(), any(), any(), eq(Collections.emptyMap()));
    final List<FileData> manifestFiles = spyHelper.renderTemplateForGivenFiles(k8sDelegateTaskParams,
        K8sDelegateManifestConfig.builder().manifestStoreTypes(KustomizeSourceRepo).build(), ".", new ArrayList<>(),
        new ArrayList<>(), "release", "namespace", executionLogCallback, K8sApplyTaskParameters.builder().build(),
        false);
    verify(kustomizeTaskHelper).buildForApply(any(), any(), any(), any(), anyBoolean(), any(), any(), anyMap());
    assertThat(manifestFiles.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testRenderTemplateForGivenFilesOCTemplates() throws Exception {
    final String workingDirectory = ".";
    K8sDelegateTaskParams k8sDelegateTaskParams =
        K8sDelegateTaskParams.builder().workingDirectory(workingDirectory).helmPath("helm").build();

    when(openShiftDelegateService.processTemplatization(any(), any(), any(), any(), any()))
        .thenReturn(new ArrayList<>());
    final List<FileData> manifestFiles = spyHelper.renderTemplate(k8sDelegateTaskParams,
        K8sDelegateManifestConfig.builder()
            .manifestStoreTypes(OC_TEMPLATES)
            .gitFileConfig(GitFileConfig.builder().build())
            .build(),
        ".", new ArrayList<>(), "release", "namespace", executionLogCallback, K8sApplyTaskParameters.builder().build());

    verify(openShiftDelegateService).processTemplatization(any(), any(), any(), any(), any());
    assertThat(manifestFiles.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testRenderTemplateForGivenFilesHelmChartRepo() throws Exception {
    final String workingDirectory = ".";
    K8sDelegateTaskParams k8sDelegateTaskParams =
        K8sDelegateTaskParams.builder().workingDirectory(workingDirectory).helmPath("helm").build();

    ProcessResult processResult = new ProcessResult(0, new ProcessOutput("".getBytes()));
    doReturn(processResult).when(spyHelperBase).executeShellCommand(any(), any(), any(), anyLong());

    final List<FileData> manifestFiles = spyHelper.renderTemplateForGivenFiles(k8sDelegateTaskParams,
        K8sDelegateManifestConfig.builder()
            .helmChartConfigParams(HelmChartConfigParams.builder().chartName("chart").build())
            .manifestStoreTypes(HelmChartRepo)
            .build(),
        ".", new ArrayList<>(), new ArrayList<>(), "release", "namespace", executionLogCallback,
        K8sApplyTaskParameters.builder().build(), false);

    assertThat(manifestFiles.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testK8sTaskExecutionResponse() throws Exception {
    final K8sTaskResponse k8sTaskResponse = K8sApplyResponse.builder().build();
    final K8sTaskExecutionResponse executionResponse =
        spyHelper.getK8sTaskExecutionResponse(k8sTaskResponse, CommandExecutionStatus.FAILURE);

    assertThat(executionResponse.getK8sTaskResponse()).isEqualTo(k8sTaskResponse);
    assertThat(executionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testRenderTemplateHelmSourceRepo() throws Exception {
    final String workingDirectory = ".";
    final List<String> valuesFiles = Arrays.asList("values");
    K8sDelegateTaskParams k8sDelegateTaskParams =
        K8sDelegateTaskParams.builder().workingDirectory(workingDirectory).helmPath("helm").build();
    doReturn(Arrays.asList(FileData.builder().filePath("test").fileContent("manifest").build()))
        .when(mockK8sTaskHelperBase)
        .renderTemplateForHelm(any(), any(), anyList(), any(), any(), any(), any(), anyLong(), any(), eq(""));

    final List<FileData> manifestFiles = helper.renderTemplate(k8sDelegateTaskParams,
        K8sDelegateManifestConfig.builder().manifestStoreTypes(HelmSourceRepo).build(), ".", valuesFiles, "release",
        "namespace", executionLogCallback, K8sApplyTaskParameters.builder().helmVersion(HelmVersion.V3).build());

    assertThat(manifestFiles.size()).isEqualTo(1);
    verify(mockK8sTaskHelperBase, times(1))
        .renderTemplateForHelm(eq("helm"), eq("."), eq(valuesFiles), eq("release"), eq("namespace"),
            eq(executionLogCallback), eq(HelmVersion.V3), anyLong(), any(), eq(""));
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testRenderTemplateKustomizeSourceRepo() throws Exception {
    final String workingDirectory = ".";
    K8sDelegateTaskParams k8sDelegateTaskParams =
        K8sDelegateTaskParams.builder().workingDirectory(workingDirectory).helmPath("helm").build();

    when(kustomizeTaskHelper.build(any(), any(), any(), any(), any(), eq(Collections.emptyMap())))
        .thenReturn(new ArrayList<>());
    final List<FileData> manifestFiles = spyHelper.renderTemplate(k8sDelegateTaskParams,
        K8sDelegateManifestConfig.builder().manifestStoreTypes(KustomizeSourceRepo).build(), ".", new ArrayList<>(),
        "release", "namespace", executionLogCallback, K8sApplyTaskParameters.builder().build());
    verify(kustomizeTaskHelper).build(any(), any(), any(), any(), any(), anyMap());
    assertThat(manifestFiles.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testRenderTemplateOCTemplates() throws Exception {
    final String workingDirectory = ".";
    K8sDelegateTaskParams k8sDelegateTaskParams =
        K8sDelegateTaskParams.builder().workingDirectory(workingDirectory).helmPath("helm").build();

    when(openShiftDelegateService.processTemplatization(any(), any(), any(), any(), any()))
        .thenReturn(new ArrayList<>());
    final List<FileData> manifestFiles = spyHelper.renderTemplate(k8sDelegateTaskParams,
        K8sDelegateManifestConfig.builder()
            .manifestStoreTypes(OC_TEMPLATES)
            .gitFileConfig(GitFileConfig.builder().build())
            .build(),
        ".", new ArrayList<>(), "release", "namespace", executionLogCallback, K8sApplyTaskParameters.builder().build());

    verify(openShiftDelegateService).processTemplatization(any(), any(), any(), any(), any());
    assertThat(manifestFiles.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void TC1_testRenderTemplateHelmChartRepo() throws Exception {
    final String workingDirectory = ".";
    K8sDelegateTaskParams k8sDelegateTaskParams =
        K8sDelegateTaskParams.builder().workingDirectory(workingDirectory).helmPath("helm").build();

    helper.renderTemplate(k8sDelegateTaskParams,
        K8sDelegateManifestConfig.builder()
            .helmChartConfigParams(HelmChartConfigParams.builder().chartName("chart").build())
            .manifestStoreTypes(HelmChartRepo)
            .build(),
        ".", new ArrayList<>(), "release", "namespace", executionLogCallback, K8sApplyTaskParameters.builder().build());

    verify(mockK8sTaskHelperBase, times(1))
        .renderTemplateForHelm(eq("helm"), eq("./chart"), anyList(), any(), any(), eq(executionLogCallback), any(),
            anyLong(), any(), eq(""));
  }

  /**
   * Chart name with / eg. bitnami/nginx. The directory should be ./nginx
   */
  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void TC2_testRenderTemplateHelmChartRepo() throws Exception {
    final String workingDirectory = ".";
    K8sDelegateTaskParams k8sDelegateTaskParams =
        K8sDelegateTaskParams.builder().workingDirectory(workingDirectory).helmPath("helm").build();

    helper.renderTemplate(k8sDelegateTaskParams,
        K8sDelegateManifestConfig.builder()
            .helmChartConfigParams(HelmChartConfigParams.builder().chartName("bitnami/nginx").build())
            .manifestStoreTypes(HelmChartRepo)
            .build(),
        ".", new ArrayList<>(), "release", "namespace", executionLogCallback, K8sApplyTaskParameters.builder().build());

    verify(mockK8sTaskHelperBase, times(1))
        .renderTemplateForHelm(eq("helm"), eq("./nginx"), anyList(), any(), any(), eq(executionLogCallback), any(),
            anyLong(), any(), eq(""));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRenderTemplateRemoteRepo() throws Exception {
    final String workingDirectory = ".";
    final String manifestDirectory = "manifests/";
    K8sDelegateTaskParams k8sDelegateTaskParams =
        K8sDelegateTaskParams.builder().workingDirectory(workingDirectory).helmPath("helm").build();
    K8sDelegateManifestConfig manifestConfig = K8sDelegateManifestConfig.builder().manifestStoreTypes(Remote).build();
    List<FileData> manifestFiles = emptyList();
    on(helper).set("k8sTaskHelperBase", spyHelperBase);

    doReturn(manifestFiles).when(spyHelperBase).readManifestFilesFromDirectory(manifestDirectory);

    helper.renderTemplate(k8sDelegateTaskParams, manifestConfig, manifestDirectory, emptyList(), "release", "namespace",
        executionLogCallback, K8sApplyTaskParameters.builder().build());

    verify(spyHelperBase, times(1)).readManifestFilesFromDirectory(manifestDirectory);
    verify(spyHelperBase, times(1))
        .renderManifestFilesForGoTemplate(
            k8sDelegateTaskParams, manifestFiles, emptyList(), executionLogCallback, 600000);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRenderTemplateCustom() throws Exception {
    final String workingDirectory = ".";
    final String manifestDirectory = "manifests/";
    K8sDelegateTaskParams k8sDelegateTaskParams =
        K8sDelegateTaskParams.builder().workingDirectory(workingDirectory).build();
    K8sDelegateManifestConfig manifestConfig =
        K8sDelegateManifestConfig.builder().manifestStoreTypes(CUSTOM).customManifestEnabled(false).build();
    List<FileData> manifestFiles = emptyList();
    doReturn(manifestFiles).when(spyHelperBase).readManifestFilesFromDirectory(manifestDirectory);
    on(helper).set("k8sTaskHelperBase", spyHelperBase);

    // FF is disabled
    helper.renderTemplate(k8sDelegateTaskParams, manifestConfig, manifestDirectory, emptyList(), "release", "namespace",
        executionLogCallback, K8sApplyTaskParameters.builder().build());
    verify(spyHelperBase, times(0)).readManifestFilesFromDirectory(manifestDirectory);
    verify(spyHelperBase, times(0))
        .renderManifestFilesForGoTemplate(
            k8sDelegateTaskParams, manifestFiles, emptyList(), executionLogCallback, 600000);

    // FF is enabled
    manifestConfig.setCustomManifestEnabled(true);
    helper.renderTemplate(k8sDelegateTaskParams, manifestConfig, manifestDirectory, emptyList(), "release", "namespace",
        executionLogCallback, K8sApplyTaskParameters.builder().build());
    verify(spyHelperBase, times(1)).readManifestFilesFromDirectory(manifestDirectory);
    verify(spyHelperBase, times(1))
        .renderManifestFilesForGoTemplate(
            k8sDelegateTaskParams, manifestFiles, emptyList(), executionLogCallback, 600000);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRenderTemplateCustomOpenshiftTemplate() throws Exception {
    final String workingDirectory = ".";
    final String manifestDirectory = "manifests/";
    final String manifestTemplatePath = "manifest/template.yaml";
    final String ocPath = "oc";
    K8sDelegateTaskParams k8sDelegateTaskParams =
        K8sDelegateTaskParams.builder().ocPath(ocPath).workingDirectory(workingDirectory).build();
    K8sDelegateManifestConfig manifestConfig =
        K8sDelegateManifestConfig.builder()
            .manifestStoreTypes(CUSTOM_OPENSHIFT_TEMPLATE)
            .customManifestSource(
                CustomManifestSource.builder().filePaths(singletonList("manifest/template.yaml")).build())
            .customManifestEnabled(false)
            .build();

    // FF disabled
    helper.renderTemplate(k8sDelegateTaskParams, manifestConfig, manifestDirectory, emptyList(), "release", "namespace",
        executionLogCallback, K8sApplyTaskParameters.builder().build());
    verify(openShiftDelegateService, times(0))
        .processTemplatization(manifestDirectory, ocPath, manifestTemplatePath, executionLogCallback, emptyList());

    // FF enabled
    manifestConfig.setCustomManifestEnabled(true);
    helper.renderTemplate(k8sDelegateTaskParams, manifestConfig, manifestDirectory, emptyList(), "release", "namespace",
        executionLogCallback, K8sApplyTaskParameters.builder().build());
    verify(openShiftDelegateService, times(1))
        .processTemplatization(manifestDirectory, ocPath, manifestTemplatePath, executionLogCallback, emptyList());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void helmChartInfoFromDelegateManifestConfig() throws Exception {
    String workingDirectory = "working/directory";
    ArgumentCaptor<String> checkHelmChartDirCaptor = ArgumentCaptor.forClass(String.class);
    HelmChartInfo existingHelmChartInfo = HelmChartInfo.builder().name("chart").version("1.0.0").build();

    // When Store Type is HelmSourceRepo
    doReturn(existingHelmChartInfo).when(mockHelmTaskHelper).getHelmChartInfoFromChartDirectory(anyString());
    K8sDelegateManifestConfig delegateManifestConfig = K8sDelegateManifestConfig.builder()
                                                           .manifestStoreTypes(HelmSourceRepo)
                                                           .gitConfig(GitConfig.builder().branch("master").build())
                                                           .build();
    HelmChartInfo helmChartInfo = helper.getHelmChartDetails(delegateManifestConfig, workingDirectory);
    verify(mockHelmTaskHelper, times(1)).getHelmChartInfoFromChartDirectory(checkHelmChartDirCaptor.capture());
    assertThat(checkHelmChartDirCaptor.getValue()).isEqualTo(workingDirectory);
    assertHelmChartInfo(helmChartInfo);

    // When Store Type is HelmChartRepo
    reset(mockHelmTaskHelper);
    doReturn("url").when(helmHelper).getRepoUrlForHelmRepoConfig(any());
    doReturn(existingHelmChartInfo).when(mockHelmTaskHelper).getHelmChartInfoFromChartDirectory(anyString());
    delegateManifestConfig.setManifestStoreTypes(HelmChartRepo);
    delegateManifestConfig.setHelmChartConfigParams(HelmChartConfigParams.builder().chartName("chart").build());
    delegateManifestConfig.setGitConfig(null);
    helmChartInfo = helper.getHelmChartDetails(delegateManifestConfig, workingDirectory);
    verify(mockHelmTaskHelper, times(1)).getHelmChartInfoFromChartDirectory(checkHelmChartDirCaptor.capture());
    assertThat(checkHelmChartDirCaptor.getValue()).isEqualTo(Paths.get(workingDirectory, "chart").toString());
    assertHelmChartInfo(helmChartInfo);

    // When Store Type is other than Helm type
    reset(mockHelmTaskHelper);
    delegateManifestConfig.setManifestStoreTypes(Remote);
    helmChartInfo = helper.getHelmChartDetails(delegateManifestConfig, workingDirectory);
    verify(mockHelmTaskHelper, never()).getHelmChartInfoFromChartDirectory(anyString());
    assertThat(helmChartInfo).isNull();
  }

  private void assertHelmChartInfo(HelmChartInfo helmChartInfo) {
    assertThat(helmChartInfo.getName()).isEqualTo("chart");
    assertThat(helmChartInfo.getVersion()).isEqualTo("1.0.0");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchManifestFilesAndWriteToDirectory_properErrorOnHelmClientException() throws Exception {
    K8sDelegateManifestConfig manifestConfig = K8sDelegateManifestConfig.builder()
                                                   .helmChartConfigParams(HelmChartConfigParams.builder().build())
                                                   .manifestStoreTypes(HelmChartRepo)
                                                   .build();
    String manifestDirectory = "directory";
    String exceptionMessage = "Helm client exception message";

    doThrow(new HelmClientException(exceptionMessage, WingsException.USER, HelmCliCommandType.FETCH))
        .when(mockHelmTaskHelper)
        .downloadChartFiles(manifestConfig.getHelmChartConfigParams(), manifestDirectory, LONG_TIMEOUT_INTERVAL,
            manifestConfig.getHelmCommandFlag());
    helper.fetchManifestFilesAndWriteToDirectory(
        manifestConfig, manifestDirectory, executionLogCallback, LONG_TIMEOUT_INTERVAL);

    ArgumentCaptor<String> logMessageCaptor = ArgumentCaptor.forClass(String.class);
    verify(executionLogCallback)
        .saveExecutionLog(logMessageCaptor.capture(), eq(ERROR), eq(CommandExecutionStatus.FAILURE));
    String logMessage = logMessageCaptor.getValue();
    assertThat(logMessage).contains(exceptionMessage);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldRunStatusCheckForHelmResources() throws Exception {
    Kubectl client = Kubectl.client("kubectl", "config-path");
    List<KubernetesResourceId> resourceId =
        ImmutableList.of(KubernetesResourceId.builder().namespace("default").build());
    helper.doStatusCheckAllResourcesForHelm(client, resourceId, "", ".", "default", "path", new ExecutionLogCallback());

    verify(mockK8sTaskHelperBase, times(1))
        .doStatusCheckForAllResources(any(Kubectl.class), anyList(), any(K8sDelegateTaskParams.class), anyString(),
            any(ExecutionLogCallback.class), anyBoolean());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldGetEmptyResourcesFromRemoteManifests() throws Exception {
    K8sDelegateTaskParams params = K8sDelegateTaskParams.builder()
                                       .goTemplateClientPath(".")
                                       .kubeconfigPath("kubeconfig")
                                       .kubectlPath("kubectlPath")
                                       .build();
    K8sTaskParameters k8sTaskParameters = K8sRollingDeployRollbackTaskParameters.builder().build();
    K8sDelegateManifestConfig config =
        K8sDelegateManifestConfig.builder()
            .manifestStoreTypes(Remote)
            .manifestFiles(singletonList(software.wings.beans.dto.ManifestFile.builder().accountId("1234").build()))
            .build();

    FileData fileData = FileData.builder().fileName("test").build();
    Mockito.when(mockK8sTaskHelperBase.readFilesFromDirectory(anyString(), anyList(), any(ExecutionLogCallback.class)))
        .thenReturn(singletonList(fileData));

    Mockito
        .when(mockK8sTaskHelperBase.renderManifestFilesForGoTemplate(
            any(K8sDelegateTaskParams.class), anyList(), anyList(), any(LogCallback.class), anyLong()))
        .thenReturn(emptyList());

    List<KubernetesResource> resources =
        helper.getResourcesFromManifests(params, config, "manifestDir", singletonList("file.yaml"),
            singletonList("values.yaml"), "release", "default", new ExecutionLogCallback(), k8sTaskParameters, false);
    assertThat(resources).isEmpty();
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldGetResourcesFromRemoteManifests() throws Exception {
    K8sDelegateTaskParams params = K8sDelegateTaskParams.builder()
                                       .goTemplateClientPath(".")
                                       .kubeconfigPath("kubeconfig")
                                       .kubectlPath("kubectlPath")
                                       .build();
    K8sTaskParameters k8sTaskParameters = K8sRollingDeployRollbackTaskParameters.builder().build();
    K8sDelegateManifestConfig config =
        K8sDelegateManifestConfig.builder()
            .manifestStoreTypes(Remote)
            .manifestFiles(singletonList(software.wings.beans.dto.ManifestFile.builder().accountId("1234").build()))
            .build();
    KubernetesResource resource = KubernetesResource.builder().spec("spec").build();

    FileData fileData = FileData.builder().fileName("test").build();
    Mockito.when(mockK8sTaskHelperBase.readFilesFromDirectory(anyString(), anyList(), any(ExecutionLogCallback.class)))
        .thenReturn(singletonList(fileData));

    Mockito
        .when(mockK8sTaskHelperBase.renderManifestFilesForGoTemplate(
            any(K8sDelegateTaskParams.class), anyList(), anyList(), any(LogCallback.class), anyLong()))
        .thenReturn(singletonList(fileData));

    Mockito.when(mockK8sTaskHelperBase.readManifests(anyList(), any(LogCallback.class)))
        .thenReturn(singletonList(resource));
    doNothing()
        .when(mockK8sTaskHelperBase)
        .setNamespaceToKubernetesResourcesIfRequired(singletonList(resource), "default");

    List<KubernetesResource> resources =
        helper.getResourcesFromManifests(params, config, "manifestDir", singletonList("file.yaml"),
            singletonList("values.yaml"), "release", "default", new ExecutionLogCallback(), k8sTaskParameters, false);
    assertThat(resources).isNotEmpty();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testRestore() {
    KubernetesConfig kubernetesConfig = KubernetesConfig.builder().build();
    List<KubernetesResource> kubernetesResources = new ArrayList<>();
    K8sClusterConfig clusterConfig = K8sClusterConfig.builder().build();
    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder().build();
    K8sHandlerConfig k8sHandlerConfig = new K8sHandlerConfig();
    ExecutionLogCallback executionLogCallback = mock(ExecutionLogCallback.class);
    doReturn(kubernetesConfig).when(containerDeploymentDelegateHelper).getKubernetesConfig(any(), anyBoolean());
    boolean result = helper.restore(
        kubernetesResources, clusterConfig, k8sDelegateTaskParams, k8sHandlerConfig, executionLogCallback);
    assertThat(result).isTrue();
    assertThat(k8sHandlerConfig.getResources()).isEqualTo(kubernetesResources);
    assertThat(k8sHandlerConfig.getKubernetesConfig()).isEqualTo(kubernetesConfig);
    assertThat(k8sHandlerConfig.getClient()).isNotNull();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testRestoreFails() {
    List<KubernetesResource> kubernetesResources = new ArrayList<>();
    K8sClusterConfig clusterConfig = K8sClusterConfig.builder().build();
    K8sDelegateTaskParams k8sDelegateTaskParams = K8sDelegateTaskParams.builder().build();
    K8sHandlerConfig k8sHandlerConfig = new K8sHandlerConfig();
    ExecutionLogCallback executionLogCallback = mock(ExecutionLogCallback.class);
    doThrow(new InvalidRequestException(""))
        .when(containerDeploymentDelegateHelper)
        .getKubernetesConfig(any(), anyBoolean());
    boolean result = helper.restore(
        kubernetesResources, clusterConfig, k8sDelegateTaskParams, k8sHandlerConfig, executionLogCallback);
    assertThat(result).isFalse();
  }
}
