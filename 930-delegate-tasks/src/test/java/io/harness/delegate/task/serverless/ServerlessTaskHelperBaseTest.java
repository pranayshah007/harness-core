/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.serverless;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ALLU_VAMSI;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifactory.ArtifactoryNgService;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.service.git.NGGitService;
import io.harness.connector.service.git.NGGitServiceImpl;
import io.harness.connector.task.git.GitDecryptionHelper;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthCredentialsDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthenticationDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.ServerlessAwsLambdaServerInstanceInfo;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaManifestSchema;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.task.artifactory.ArtifactoryRequestMapper;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.git.ScmFetchFilesHelperNG;
import io.harness.encryption.SecretRefData;
import io.harness.exception.HintException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.git.GitClientV2;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import java.util.*;

import io.harness.serverless.model.AwsLambdaFunctionDetails;
import io.harness.serverless.model.ServerlessDelegateTaskParams;
import io.harness.shell.SshSessionConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.wings.service.intfc.aws.delegate.AwsLambdaHelperServiceDelegateNG;

@OwnedBy(CDP)
public class ServerlessTaskHelperBaseTest extends CategoryTest {
  private static final String ARTIFACTORY_ARTIFACT_PATH = "artifactPath";
  private static final String ARTIFACTORY_ARTIFACT_NAME = "artifactName";
  private static final String ARTIFACT_FILE_NAME = "artifactFile";
  private static final String ARTIFACT_ZIP_REGEX = ".*\\.zip";
  private static final String ARTIFACT_JAR_REGEX = ".*\\.jar";
  private static final String ARTIFACT_WAR_REGEX = ".*\\.war";
  private static final String ARTIFACT_PATH_REGEX = ".*<\\+artifact\\.path>.*";
  private static final String ARTIFACT_PATH = "<+artifact.path>";

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private ArtifactoryNgService artifactoryNgService;
  @Mock private ArtifactoryRequestMapper artifactoryRequestMapper;
  @Mock private SecretDecryptionService secretDecryptionService;
  @Mock private ServerlessInfraConfigHelper serverlessInfraConfigHelper;
  @Mock private AwsNgConfigMapper awsNgConfigMapper;
  @Mock private AwsLambdaHelperServiceDelegateNG awsLambdaHelperServiceDelegateNG;
  @InjectMocks @Spy private ServerlessTaskHelperBase serverlessTaskHelperBase;

  private static final String ARTIFACT_DIRECTORY = "./repository/serverless/";
  private static final String ARTIFACTORY_PATH = "asdffasd.zip";
  String repositoryName = "dfsgvgasd";

  ArtifactoryConfigRequest artifactoryConfigRequest = ArtifactoryConfigRequest.builder().build();
  SecretRefData password = SecretRefData.builder().build();
  ArtifactoryAuthCredentialsDTO artifactoryAuthCredentialsDTO =
                                         ArtifactoryUsernamePasswordAuthDTO.builder().passwordRef(password).build();
  ArtifactoryAuthenticationDTO artifactoryAuthenticationDTO = ArtifactoryAuthenticationDTO.builder()
                                        .credentials(artifactoryAuthCredentialsDTO)
                                        .authType(ArtifactoryAuthType.USER_PASSWORD)
                                        .build();
  ArtifactoryConnectorDTO connectorConfigDTO =
                                        ArtifactoryConnectorDTO.builder().auth(artifactoryAuthenticationDTO).build();
  ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder().connectorConfig(connectorConfigDTO).build();
  List<EncryptedDataDetail> encryptedDataDetailsList =
                                        Arrays.asList(EncryptedDataDetail.builder().fieldName("afsd").build());
  ServerlessArtifactConfig serverlessArtifactConfig = ServerlessArtifactoryArtifactConfig.builder()
                                        .artifactPath(ARTIFACTORY_PATH)
                                        .connectorDTO(connectorInfoDTO)
                                        .encryptedDataDetails(encryptedDataDetailsList)
                                        .repositoryName(repositoryName)
                                        .build();

  private ServerlessDelegateTaskParams serverlessDelegateTaskParams = ServerlessDelegateTaskParams.builder()
          .workingDirectory("/dir/").serverlessClientPath("/scPath").build();
  private GitConfigDTO gitConfigDTO = GitConfigDTO.builder().url("url").build();
  @Mock LogCallback logCallback;
  @Mock ServerlessGitFetchTaskHelper serverlessGitFetchTaskHelper;
  @Mock ScmFetchFilesHelperNG scmFetchFilesHelper;
  @Mock GitDecryptionHelper gitDecryptionHelper;
  @Mock NGGitServiceImpl ngGitService;
  @Mock SshSessionConfig sshSessionConfig;
  @Mock GitClientV2 gitClientV2;

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void fetchManifestFilesAndWriteToDirectoryTest() throws IOException {
    GitStoreDelegateConfig gitStoreDelegateConfig = GitStoreDelegateConfig.builder()
            .branch("branch").commitId("commitId").connectorName("connector").manifestId("manifest").gitConfigDTO(gitConfigDTO)
            .fetchType(FetchType.BRANCH).paths(new ArrayList<String>(Arrays.asList("path1", "path2"))).optimizedFilesFetch(true).build();
    ServerlessAwsLambdaManifestConfig serverlessManifestConfig = ServerlessAwsLambdaManifestConfig.builder()
            .manifestPath("manifestPath").configOverridePath("path").gitStoreDelegateConfig(gitStoreDelegateConfig).build();

    doNothing().when(serverlessGitFetchTaskHelper).decryptGitStoreConfig(gitStoreDelegateConfig);
    doNothing().when(scmFetchFilesHelper).downloadFilesUsingScm("/dir/", gitStoreDelegateConfig, logCallback);
    doReturn("fileLog").when(serverlessTaskHelperBase).getManifestFileNamesInLogFormat(serverlessDelegateTaskParams.getWorkingDirectory());

    serverlessTaskHelperBase.fetchManifestFilesAndWriteToDirectory(serverlessManifestConfig,
            "accountId", logCallback, serverlessDelegateTaskParams);

    verify(logCallback).saveExecutionLog(color("Successfully fetched following files:", White, Bold));
    verify(logCallback).saveExecutionLog(
            color(format("Fetching %s files with identifier: %s", gitStoreDelegateConfig.getManifestType(),
                    gitStoreDelegateConfig.getManifestId()), White, Bold));
    verify(logCallback).saveExecutionLog("Using optimized file fetch");
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void downloadFilesFromGitNotOptimizedFilesFetchTest() throws IOException {
    GitStoreDelegateConfig gitStoreDelegateConfig = GitStoreDelegateConfig.builder()
            .branch("branch").commitId("commitId").connectorName("connector").manifestId("manifest").gitConfigDTO(gitConfigDTO)
            .fetchType(FetchType.BRANCH).paths(new ArrayList<String>(Arrays.asList("path1", "path2"))).optimizedFilesFetch(false).build();
    ServerlessAwsLambdaManifestConfig serverlessManifestConfig = ServerlessAwsLambdaManifestConfig.builder()
            .manifestPath("manifestPath").configOverridePath("path").gitStoreDelegateConfig(gitStoreDelegateConfig).build();

    doNothing().when(serverlessGitFetchTaskHelper).decryptGitStoreConfig(gitStoreDelegateConfig);
    doNothing().when(scmFetchFilesHelper).downloadFilesUsingScm("/dir/", gitStoreDelegateConfig, logCallback);
    doReturn("fileLog").when(serverlessTaskHelperBase).getManifestFileNamesInLogFormat(serverlessDelegateTaskParams.getWorkingDirectory());
    doNothing().when(gitDecryptionHelper).decryptGitConfig(gitConfigDTO, gitStoreDelegateConfig.getEncryptedDataDetails());
    doNothing().when(ngGitService).downloadFiles(gitStoreDelegateConfig, "/dir/", "acoountId", sshSessionConfig, gitConfigDTO);
    serverlessTaskHelperBase.fetchManifestFilesAndWriteToDirectory(serverlessManifestConfig,
            "accountId", logCallback, serverlessDelegateTaskParams);

  }

  @Test(expected = HintException.class)
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void downloadFilesFromGitExceptionTest() throws Exception {
    GitStoreDelegateConfig gitStoreDelegateConfig = GitStoreDelegateConfig.builder()
            .branch("branch").commitId("commitId").connectorName("connector").manifestId("manifest").gitConfigDTO(gitConfigDTO)
            .fetchType(FetchType.BRANCH).paths(new ArrayList<String>(Arrays.asList("path1", "path2"))).optimizedFilesFetch(false).build();
    ServerlessAwsLambdaManifestConfig serverlessManifestConfig = ServerlessAwsLambdaManifestConfig.builder()
            .manifestPath("manifestPath").configOverridePath("path").gitStoreDelegateConfig(gitStoreDelegateConfig).build();

    doNothing()
            .when(serverlessGitFetchTaskHelper)
            .decryptGitStoreConfig(gitStoreDelegateConfig);
    doNothing()
            .when(scmFetchFilesHelper)
            .downloadFilesUsingScm("/dir/", gitStoreDelegateConfig, logCallback);
    doReturn("fileLog").when(serverlessTaskHelperBase)
            .getManifestFileNamesInLogFormat(serverlessDelegateTaskParams.getWorkingDirectory());
    doNothing()
            .when(gitDecryptionHelper)
            .decryptGitConfig(gitConfigDTO, gitStoreDelegateConfig.getEncryptedDataDetails());
    doReturn(sshSessionConfig).when(gitDecryptionHelper)
            .getSSHSessionConfig(gitStoreDelegateConfig.getSshKeySpecDTO(), gitStoreDelegateConfig.getEncryptedDataDetails());

    doThrow(IOException.class)
            .when(ngGitService)
            .downloadFiles(gitStoreDelegateConfig, "/dir/", "accountId", sshSessionConfig, gitConfigDTO);

    serverlessTaskHelperBase.fetchManifestFilesAndWriteToDirectory(serverlessManifestConfig,
            "accountId", logCallback, serverlessDelegateTaskParams);
  }

//  @Test(expected = HintException.class)
//  @Owner(developers = ALLU_VAMSI)
//  @Category(UnitTests.class)
//  public void getManifestFileNamesInLogFormatTest(){
//
//  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void fetchArtifactTest() throws Exception {
    doReturn(artifactoryConfigRequest).when(artifactoryRequestMapper).toArtifactoryRequest(connectorConfigDTO);
    Map<String, String> artifactMetadata = new HashMap<>();
    artifactMetadata.put(ARTIFACTORY_ARTIFACT_PATH, repositoryName + "/" + ARTIFACTORY_PATH);
    artifactMetadata.put(ARTIFACTORY_ARTIFACT_NAME, repositoryName + "/" + ARTIFACTORY_PATH);

    String input = "asfd";
    InputStream inputStream = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
    String artifactPath = Paths
            .get(((ServerlessArtifactoryArtifactConfig) serverlessArtifactConfig).getRepositoryName(),
                    ((ServerlessArtifactoryArtifactConfig) serverlessArtifactConfig).getArtifactPath())
            .toString();

    doReturn(inputStream)
            .when(artifactoryNgService)
            .downloadArtifacts(artifactoryConfigRequest, repositoryName, artifactMetadata, ARTIFACTORY_ARTIFACT_PATH,
                    ARTIFACTORY_ARTIFACT_NAME);
    serverlessTaskHelperBase.fetchArtifact(serverlessArtifactConfig, logCallback, ARTIFACT_DIRECTORY);
    verify(logCallback)
            .saveExecutionLog(color(
                    format("Downloading %s artifact with identifier: %s", serverlessArtifactConfig.getServerlessArtifactType(),
                            ((ServerlessArtifactoryArtifactConfig) serverlessArtifactConfig).getIdentifier()),
                    White, Bold));
    verify(logCallback).saveExecutionLog("Artifactory Artifact Path: " + artifactPath);
    verify(logCallback).saveExecutionLog(color("Successfully downloaded artifact..", White, Bold));
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void fetchArtifactFailCreateFileTest() throws Exception {
    doReturn(artifactoryConfigRequest).when(artifactoryRequestMapper).toArtifactoryRequest(connectorConfigDTO);
    Map<String, String> artifactMetadata = new HashMap<>();
    String ARTIFACT_DIRECTORY = "dir/";
    artifactMetadata.put(ARTIFACTORY_ARTIFACT_PATH, repositoryName + "/" + ARTIFACTORY_PATH);
    artifactMetadata.put(ARTIFACTORY_ARTIFACT_NAME, repositoryName + "/" + ARTIFACTORY_PATH);

    String input = "asfd";
    InputStream inputStream = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
    String artifactPath = Paths
            .get(((ServerlessArtifactoryArtifactConfig) serverlessArtifactConfig).getRepositoryName(),
                    ((ServerlessArtifactoryArtifactConfig) serverlessArtifactConfig).getArtifactPath())
            .toString();

    doReturn(inputStream)
            .when(artifactoryNgService)
            .downloadArtifacts(artifactoryConfigRequest, repositoryName, artifactMetadata, ARTIFACTORY_ARTIFACT_PATH,
                    ARTIFACTORY_ARTIFACT_NAME);
    serverlessTaskHelperBase.fetchArtifact(serverlessArtifactConfig, logCallback, ARTIFACT_DIRECTORY);
    verify(logCallback)
            .saveExecutionLog(color(
                    format("Downloading %s artifact with identifier: %s", serverlessArtifactConfig.getServerlessArtifactType(),
                            ((ServerlessArtifactoryArtifactConfig) serverlessArtifactConfig).getIdentifier()),
                    White, Bold));
    verify(logCallback).saveExecutionLog("Artifactory Artifact Path: " + artifactPath);
    verify(logCallback).saveExecutionLog(color("Successfully downloaded artifact..", White, Bold));
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getRelativePathTest(){
    assertThat(serverlessTaskHelperBase.getRelativePath("dir/path1","dir")).isEqualTo("path1");
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void generateTruncatedFileListForLoggingTest() {
     Path path1 = Paths.get("path1");Path path2 = Paths.get("path2");
     Stream<Path> paths = Stream.of(path1,path2);
     serverlessTaskHelperBase.generateTruncatedFileListForLogging(path1,paths);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void removePluginVersionTest() throws IOException {//// Blocker at Paths
    ServerlessAwsLambdaManifestSchema serverlessManifestSchema = ServerlessAwsLambdaManifestSchema.builder()
                                                      .plugins(new ArrayList<String>(Arrays.asList("plugin@manifest"))).build();
    GitStoreDelegateConfig gitStoreDelegateConfig = GitStoreDelegateConfig.builder()
            .branch("branch").commitId("commitId").connectorName("connector").manifestId("manifest").gitConfigDTO(gitConfigDTO)
            .fetchType(FetchType.BRANCH).paths(new ArrayList<String>(Arrays.asList("path1", "path2"))).optimizedFilesFetch(true).build();
    ServerlessAwsLambdaManifestConfig serverlessManifestConfig = ServerlessAwsLambdaManifestConfig.builder()
            .manifestPath("manifestPath").configOverridePath("path").gitStoreDelegateConfig(gitStoreDelegateConfig).build();
    //serverlessTaskHelperBase.removePluginVersion("manifestContent",serverlessManifestSchema);
    serverlessTaskHelperBase.replaceManifestWithRenderedContent(serverlessDelegateTaskParams,serverlessManifestConfig,
             "manifestContent",serverlessManifestSchema);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getServerlessAwsLambdaServerInstanceInfosTest() {
    List<String> functions = Arrays.asList("fun");
    AwsManualConfigSpecDTO config = AwsManualConfigSpecDTO.builder()
            .accessKey("accessKey").build();
    AwsCredentialDTO awsCredentialDTO =
            AwsCredentialDTO.builder()
                    .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS).config(config)
                    .build();
    AwsConnectorDTO awsConnectorDTO =
            AwsConnectorDTO.builder()
                    .credential(awsCredentialDTO).build();
    ServerlessAwsLambdaInfraConfig serverlessAwsLambdaInfraConfig =
            ServerlessAwsLambdaInfraConfig.builder()
            .awsConnectorDTO(awsConnectorDTO).infraStructureKey("infraKey").stage("stage").build();
    ServerlessAwsLambdaDeploymentReleaseData deploymentReleaseData =
            ServerlessAwsLambdaDeploymentReleaseData.builder()
            .functions(functions).serviceName("service").region("us-east-1")
            .serverlessInfraConfig(serverlessAwsLambdaInfraConfig).build();
    doNothing().when(serverlessInfraConfigHelper).decryptServerlessInfraConfig(serverlessAwsLambdaInfraConfig);
    AwsInternalConfig awsInternalConfig =
            AwsInternalConfig.builder().useIRSA(false).accessKey(new char[]{'a','c','c','e','s','s'}).build();
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(serverlessAwsLambdaInfraConfig.getAwsConnectorDTO());
    AwsLambdaFunctionDetails awsLambdaFunctionDetails =
            AwsLambdaFunctionDetails.builder()
            .functionName("fun").build();
    doReturn(awsLambdaFunctionDetails).when(awsLambdaHelperServiceDelegateNG).getAwsLambdaFunctionDetails(awsInternalConfig, "fun", deploymentReleaseData.getRegion());
    ServerlessAwsLambdaServerInstanceInfo serverInstanceInfo =
            (ServerlessAwsLambdaServerInstanceInfo) serverlessTaskHelperBase
                    .getServerlessAwsLambdaServerInstanceInfos(deploymentReleaseData).get(0);
//    ServerlessAwsLambdaServerInstanceInfo serverInstanceInfoTest = ServerlessAwsLambdaServerInstanceInfo.builder()
//            .serverlessServiceName("service").serverlessStage("stage").region("us-east-1").functionName("fun").infraStructureKey("infraKey").build();
    assertThat(serverInstanceInfo.getServerlessServiceName()).isEqualTo("service");
    assertThat(serverInstanceInfo.getServerlessStage()).isEqualTo("stage");
    assertThat(serverInstanceInfo.getFunctionName()).isEqualTo("fun");
    assertThat(serverInstanceInfo.getRegion()).isEqualTo("us-east-1");
    assertThat(serverInstanceInfo.getInfraStructureKey()).isEqualTo("infraKey");
  }
}