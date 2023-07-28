/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.serverless;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.UUIDGenerator.convertBase64UuidToCanonicalForm;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.rule.OwnerRule.ALLU_VAMSI;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;
import static org.apache.commons.io.FileUtils.readFileToString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifactory.ArtifactoryNgService;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.connector.service.git.NGGitServiceImpl;
import io.harness.connector.task.git.GitDecryptionHelper;
import io.harness.connector.task.git.ScmConnectorMapperDelegate;
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
import io.harness.delegate.beans.instancesync.info.ServerlessAwsLambdaServerInstanceInfo;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaManifestSchema;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3StoreDelegateConfig;
import io.harness.delegate.task.artifactory.ArtifactoryRequestMapper;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.git.GitFetchTaskHelper;
import io.harness.delegate.task.git.ScmFetchFilesHelperNG;
import io.harness.encryption.SecretRefData;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.filesystem.FileIo;
import io.harness.git.GitClientV2;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.serverless.model.AwsLambdaFunctionDetails;
import io.harness.serverless.model.ServerlessDelegateTaskParams;
import io.harness.shell.SshSessionConfig;

import software.wings.service.impl.AwsApiHelperService;
import software.wings.service.intfc.aws.delegate.AwsLambdaHelperServiceDelegateNG;

import com.amazonaws.services.s3.model.S3Object;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class ServerlessTaskHelperBaseTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private ArtifactoryNgService artifactoryNgService;
  @Mock private ArtifactoryRequestMapper artifactoryRequestMapper;
  @Mock private SecretDecryptionService secretDecryptionService;
  @Mock private ServerlessInfraConfigHelper serverlessInfraConfigHelper;
  @Mock private AwsNgConfigMapper awsNgConfigMapper;
  @Mock private AwsLambdaHelperServiceDelegateNG awsLambdaHelperServiceDelegateNG;
  @Mock private AwsApiHelperService awsApiHelperService;
  @Mock private DecryptionHelper decryptionHelper;
  @Mock private ScmConnectorMapperDelegate scmConnectorMapperDelegate;
  @InjectMocks @Spy private ServerlessTaskHelperBase serverlessTaskHelperBase;

  private static final String ARTIFACT_DIRECTORY = "./repository/serverless/";
  private static final String ARTIFACTORY_PATH = "asdffasd.zip";
  private static final String ARTIFACTORY_ARTIFACT_PATH = "artifactPath";
  private static final String ARTIFACTORY_ARTIFACT_NAME = "artifactName";

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

  private ServerlessDelegateTaskParams serverlessDelegateTaskParams =
      ServerlessDelegateTaskParams.builder().workingDirectory("/dir/").serverlessClientPath("/scPath").build();
  private GitConfigDTO gitConfigDTO = GitConfigDTO.builder().url("url").build();
  @Mock LogCallback logCallback;
  @Mock GitFetchTaskHelper serverlessGitFetchTaskHelper;
  @Mock ScmFetchFilesHelperNG scmFetchFilesHelper;
  @Mock GitDecryptionHelper gitDecryptionHelper;
  @Mock NGGitServiceImpl ngGitService;
  @Mock SshSessionConfig sshSessionConfig;
  @Mock GitClientV2 gitClientV2;

  @Before
  public void setUp() {
    doReturn(gitConfigDTO).when(scmConnectorMapperDelegate).toGitConfigDTO(any(), any());
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void fetchManifestFilesAndWriteToDirectoryTest() throws IOException {
    GitStoreDelegateConfig gitStoreDelegateConfig = GitStoreDelegateConfig.builder()
                                                        .branch("branch")
                                                        .commitId("commitId")
                                                        .connectorName("connector")
                                                        .manifestId("manifest")
                                                        .gitConfigDTO(gitConfigDTO)
                                                        .fetchType(FetchType.BRANCH)
                                                        .paths(new ArrayList<String>(Arrays.asList("path1", "path2")))
                                                        .optimizedFilesFetch(true)
                                                        .build();
    ServerlessAwsLambdaManifestConfig serverlessManifestConfig = ServerlessAwsLambdaManifestConfig.builder()
                                                                     .manifestPath("manifestPath")
                                                                     .configOverridePath("path")
                                                                     .gitStoreDelegateConfig(gitStoreDelegateConfig)
                                                                     .build();

    doNothing().when(serverlessGitFetchTaskHelper).decryptGitStoreConfig(gitStoreDelegateConfig);
    doReturn(null).when(scmFetchFilesHelper).downloadFilesUsingScm("/dir/", gitStoreDelegateConfig, logCallback);
    doReturn("fileLog")
        .when(serverlessTaskHelperBase)
        .getManifestFileNamesInLogFormat(serverlessDelegateTaskParams.getWorkingDirectory());

    serverlessTaskHelperBase.fetchManifestFilesAndWriteToDirectory(
        serverlessManifestConfig, "accountId", logCallback, serverlessDelegateTaskParams);

    verify(logCallback).saveExecutionLog(color("Successfully fetched following files:", White, Bold));
    verify(logCallback)
        .saveExecutionLog(color(format("Fetching %s files with identifier: %s",
                                    gitStoreDelegateConfig.getManifestType(), gitStoreDelegateConfig.getManifestId()),
            White, Bold));
    verify(logCallback).saveExecutionLog("Using optimized file fetch");
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void downloadFilesFromGitNotOptimizedFilesFetchTest() throws IOException {
    GitStoreDelegateConfig gitStoreDelegateConfig = GitStoreDelegateConfig.builder()
                                                        .branch("branch")
                                                        .commitId("commitId")
                                                        .connectorName("connector")
                                                        .manifestId("manifest")
                                                        .gitConfigDTO(gitConfigDTO)
                                                        .fetchType(FetchType.BRANCH)
                                                        .paths(new ArrayList<String>(Arrays.asList("path1", "path2")))
                                                        .optimizedFilesFetch(false)
                                                        .build();
    ServerlessAwsLambdaManifestConfig serverlessManifestConfig = ServerlessAwsLambdaManifestConfig.builder()
                                                                     .manifestPath("manifestPath")
                                                                     .configOverridePath("path")
                                                                     .gitStoreDelegateConfig(gitStoreDelegateConfig)
                                                                     .build();

    doNothing().when(serverlessGitFetchTaskHelper).decryptGitStoreConfig(gitStoreDelegateConfig);
    doReturn(null).when(scmFetchFilesHelper).downloadFilesUsingScm("/dir/", gitStoreDelegateConfig, logCallback);
    doReturn("fileLog")
        .when(serverlessTaskHelperBase)
        .getManifestFileNamesInLogFormat(serverlessDelegateTaskParams.getWorkingDirectory());
    doNothing()
        .when(gitDecryptionHelper)
        .decryptGitConfig(gitConfigDTO, gitStoreDelegateConfig.getEncryptedDataDetails());
    doNothing()
        .when(ngGitService)
        .downloadFiles(gitStoreDelegateConfig, "/dir/", "acoountId", sshSessionConfig, gitConfigDTO);
    serverlessTaskHelperBase.fetchManifestFilesAndWriteToDirectory(
        serverlessManifestConfig, "accountId", logCallback, serverlessDelegateTaskParams);
  }

  @Test(expected = HintException.class)
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void downloadFilesFromGitExceptionTest() throws Exception {
    GitStoreDelegateConfig gitStoreDelegateConfig = GitStoreDelegateConfig.builder()
                                                        .branch("branch")
                                                        .commitId("commitId")
                                                        .connectorName("connector")
                                                        .manifestId("manifest")
                                                        .gitConfigDTO(gitConfigDTO)
                                                        .fetchType(FetchType.BRANCH)
                                                        .paths(new ArrayList<String>(Arrays.asList("path1", "path2")))
                                                        .optimizedFilesFetch(false)
                                                        .build();
    ServerlessAwsLambdaManifestConfig serverlessManifestConfig = ServerlessAwsLambdaManifestConfig.builder()
                                                                     .manifestPath("manifestPath")
                                                                     .configOverridePath("path")
                                                                     .gitStoreDelegateConfig(gitStoreDelegateConfig)
                                                                     .build();

    doNothing().when(serverlessGitFetchTaskHelper).decryptGitStoreConfig(gitStoreDelegateConfig);
    doReturn(null).when(scmFetchFilesHelper).downloadFilesUsingScm("/dir/", gitStoreDelegateConfig, logCallback);
    doReturn("fileLog")
        .when(serverlessTaskHelperBase)
        .getManifestFileNamesInLogFormat(serverlessDelegateTaskParams.getWorkingDirectory());
    doNothing()
        .when(gitDecryptionHelper)
        .decryptGitConfig(gitConfigDTO, gitStoreDelegateConfig.getEncryptedDataDetails());
    doReturn(sshSessionConfig)
        .when(gitDecryptionHelper)
        .getSSHSessionConfig(
            gitStoreDelegateConfig.getSshKeySpecDTO(), gitStoreDelegateConfig.getEncryptedDataDetails());

    doThrow(IOException.class)
        .when(ngGitService)
        .downloadFiles(gitStoreDelegateConfig, "/dir/", "accountId", sshSessionConfig, gitConfigDTO);

    serverlessTaskHelperBase.fetchManifestFilesAndWriteToDirectory(
        serverlessManifestConfig, "accountId", logCallback, serverlessDelegateTaskParams);
  }

  @Test(expected = HintException.class)
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void fetchArtifactInputStreamNullTest() throws Exception {
    doReturn(artifactoryConfigRequest).when(artifactoryRequestMapper).toArtifactoryRequest(connectorConfigDTO);
    Map<String, String> artifactMetadata = new HashMap<>();
    artifactMetadata.put(ARTIFACTORY_ARTIFACT_PATH, repositoryName + "/" + ARTIFACTORY_PATH);
    artifactMetadata.put(ARTIFACTORY_ARTIFACT_NAME, repositoryName + "/" + ARTIFACTORY_PATH);

    doReturn(null)
        .when(artifactoryNgService)
        .downloadArtifacts(artifactoryConfigRequest, repositoryName, artifactMetadata, ARTIFACTORY_ARTIFACT_PATH,
            ARTIFACTORY_ARTIFACT_NAME);
    serverlessTaskHelperBase.fetchArtifact(
        serverlessArtifactConfig, logCallback, ARTIFACT_DIRECTORY, ARTIFACTORY_ARTIFACT_NAME);
    verify(logCallback)
        .saveExecutionLog("Failed to download artifact from artifactory.ø", ERROR, CommandExecutionStatus.FAILURE);
    Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(new Exception("HintException"));
    verify(logCallback)
        .saveExecutionLog(
            "Failed to download artifact from artifactory. " + ExceptionUtils.getMessage(sanitizedException), ERROR,
            CommandExecutionStatus.FAILURE);
    FileIo.deleteDirectoryAndItsContentIfExists(Paths.get(ARTIFACT_DIRECTORY).toAbsolutePath().toString());
  }

  @Test(expected = HintException.class)
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void fetchArtifactEmptyArtifactPathTest() throws Exception {
    ServerlessArtifactConfig serverlessArtifactConfig =
        ServerlessArtifactoryArtifactConfig.builder().repositoryName(repositoryName).build();
    serverlessTaskHelperBase.fetchArtifact(
        serverlessArtifactConfig, logCallback, ARTIFACT_DIRECTORY, ARTIFACTORY_ARTIFACT_NAME);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getManifestFileNamesInLogFormatTest() throws IOException {
    String workingDir =
        Paths.get("workingDir" + convertBase64UuidToCanonicalForm(generateUuid())).toAbsolutePath().toString();
    FileIo.createDirectoryIfDoesNotExist(workingDir);
    String filePath1 = Paths.get(workingDir, "file1").toString();
    String filePath2 = Paths.get(workingDir, "file2").toString();
    FileIo.writeUtf8StringToFile(filePath1, "fileContent1");
    FileIo.writeUtf8StringToFile(filePath2, "fileContent2");
    serverlessTaskHelperBase.getManifestFileNamesInLogFormat(workingDir);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void generateTruncatedFileListForLoggingTest() {
    Path path1 = Paths.get("path1");
    Path path2 = Paths.get("path2");
    Stream<Path> paths = Stream.of(path1, path2);
    serverlessTaskHelperBase.generateTruncatedFileListForLogging(path1, paths);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getRelativePathTest() {
    assertThat(serverlessTaskHelperBase.getRelativePath("dir/path1", "dir")).isEqualTo("path1");
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void replaceManifestWithRenderedContentTest() throws IOException {
    String workingDir = Paths.get("workingDir", convertBase64UuidToCanonicalForm(generateUuid()))
                            .normalize()
                            .toAbsolutePath()
                            .toString();
    FileIo.createDirectoryIfDoesNotExist(workingDir);
    ServerlessDelegateTaskParams serverlessDelegateTaskParams =
        ServerlessDelegateTaskParams.builder().workingDirectory(workingDir).serverlessClientPath("scPath").build();
    ServerlessAwsLambdaManifestSchema serverlessManifestSchema =
        ServerlessAwsLambdaManifestSchema.builder()
            .plugins(new ArrayList<String>(Arrays.asList("plugin@manifest")))
            .build();
    GitStoreDelegateConfig gitStoreDelegateConfig =
        GitStoreDelegateConfig.builder().fetchType(FetchType.BRANCH).optimizedFilesFetch(true).build();
    ServerlessAwsLambdaManifestConfig serverlessManifestConfig = ServerlessAwsLambdaManifestConfig.builder()
                                                                     .manifestPath("manifestFile")
                                                                     .configOverridePath("path")
                                                                     .gitStoreDelegateConfig(gitStoreDelegateConfig)
                                                                     .build();

    serverlessTaskHelperBase.replaceManifestWithRenderedContent(
        serverlessDelegateTaskParams, serverlessManifestConfig, "manifestContent", serverlessManifestSchema);
    FileIo.deleteFileIfExists(workingDir + "manifestFile");
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getServerlessAwsLambdaServerInstanceInfosTest() {
    List<String> functions = Arrays.asList("fun");
    AwsManualConfigSpecDTO config = AwsManualConfigSpecDTO.builder().accessKey("accessKey").build();
    AwsCredentialDTO awsCredentialDTO =
        AwsCredentialDTO.builder().awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS).config(config).build();
    AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder().credential(awsCredentialDTO).build();
    ServerlessAwsLambdaInfraConfig serverlessAwsLambdaInfraConfig = ServerlessAwsLambdaInfraConfig.builder()
                                                                        .awsConnectorDTO(awsConnectorDTO)
                                                                        .infraStructureKey("infraKey")
                                                                        .stage("stage")
                                                                        .build();
    ServerlessAwsLambdaDeploymentReleaseData deploymentReleaseData =
        ServerlessAwsLambdaDeploymentReleaseData.builder()
            .functions(functions)
            .serviceName("service")
            .region("us-east-1")
            .serverlessInfraConfig(serverlessAwsLambdaInfraConfig)
            .build();
    doNothing().when(serverlessInfraConfigHelper).decryptServerlessInfraConfig(serverlessAwsLambdaInfraConfig);
    AwsInternalConfig awsInternalConfig =
        AwsInternalConfig.builder().useIRSA(false).accessKey(new char[] {'a', 'c', 'c', 'e', 's', 's'}).build();
    doReturn(awsInternalConfig)
        .when(awsNgConfigMapper)
        .createAwsInternalConfig(serverlessAwsLambdaInfraConfig.getAwsConnectorDTO());
    AwsLambdaFunctionDetails awsLambdaFunctionDetails = AwsLambdaFunctionDetails.builder().functionName("fun").build();
    doReturn(awsLambdaFunctionDetails)
        .when(awsLambdaHelperServiceDelegateNG)
        .getAwsLambdaFunctionDetails(awsInternalConfig, "fun", deploymentReleaseData.getRegion());
    ServerlessAwsLambdaServerInstanceInfo serverInstanceInfo =
        (ServerlessAwsLambdaServerInstanceInfo) serverlessTaskHelperBase
            .getServerlessAwsLambdaServerInstanceInfos(deploymentReleaseData)
            .get(0);

    assertThat(serverInstanceInfo.getServerlessServiceName()).isEqualTo("service");
    assertThat(serverInstanceInfo.getServerlessStage()).isEqualTo("stage");
    assertThat(serverInstanceInfo.getFunctionName()).isEqualTo("fun");
    assertThat(serverInstanceInfo.getRegion()).isEqualTo("us-east-1");
    assertThat(serverInstanceInfo.getInfraStructureKey()).isEqualTo("infraKey");
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void downloadFilesFromS3Test() throws IOException {
    AwsCredentialDTO awsCredentialDTO =
        AwsCredentialDTO.builder().awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS).build();
    AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder().credential(awsCredentialDTO).build();
    S3StoreDelegateConfig s3StoreDelegateConfig = S3StoreDelegateConfig.builder()
                                                      .awsConnector(awsConnectorDTO)
                                                      .region("region")
                                                      .bucketName("bucketName")
                                                      .path("zipFilePath")
                                                      .build();
    S3Object s3Object = new S3Object();
    s3Object.setObjectContent(new ByteArrayInputStream("content".getBytes()));
    doReturn(s3Object)
        .when(awsApiHelperService)
        .getObjectFromS3(any(), eq("region"), eq("bucketName"), eq("zipFilePath"));
    String workingDir = "serverless/worDir";
    serverlessTaskHelperBase.downloadFilesFromS3(s3StoreDelegateConfig, logCallback, workingDir);

    verify(awsApiHelperService).getObjectFromS3(any(), eq("region"), eq("bucketName"), eq("zipFilePath"));
  }

  @Test(expected = HintException.class)
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void downloadFilesFromS3ExceptionTest() throws IOException {
    AwsCredentialDTO awsCredentialDTO =
        AwsCredentialDTO.builder().awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS).build();
    AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder().credential(awsCredentialDTO).build();
    S3StoreDelegateConfig s3StoreDelegateConfig = S3StoreDelegateConfig.builder()
                                                      .awsConnector(awsConnectorDTO)
                                                      .region("region")
                                                      .bucketName("bucketName")
                                                      .path("zipFilePath")
                                                      .build();
    S3Object s3Object = new S3Object();
    s3Object.setObjectContent(new ByteArrayInputStream("content".getBytes()));
    Exception e = new InvalidRequestException("error");
    doThrow(e).when(awsApiHelperService).getObjectFromS3(any(), eq("region"), eq("bucketName"), eq("zipFilePath"));
    String workingDir = "serverless/worDir";
    serverlessTaskHelperBase.downloadFilesFromS3(s3StoreDelegateConfig, logCallback, workingDir);

    verify(awsApiHelperService).getObjectFromS3(any(), eq("region"), eq("bucketName"), eq("zipFilePath"));
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void testZipAndUnzipOperations() throws IOException {
    final String destDirPath = "./repository/serverless/dest/testDir";
    final String sourceDirPath = "./repository/serverless/source/manifests";
    final File zipDir = new File("./repository/serverless/zip/testDir");
    final File zipFile = new File(format("%s/destZipFile.zip", zipDir.getPath()));

    FileIo.createDirectoryIfDoesNotExist(destDirPath);
    FileIo.createDirectoryIfDoesNotExist(sourceDirPath);
    Files.createFile(Paths.get(sourceDirPath, "test1.yaml"));
    Files.createFile(Paths.get(sourceDirPath, "test2.yaml"));
    FileIo.createDirectoryIfDoesNotExist(zipDir.getPath());
    Files.write(Paths.get(sourceDirPath, "test1.yaml"), "test script 1".getBytes());
    Files.write(Paths.get(sourceDirPath, "test2.yaml"), "test script 2".getBytes());

    zipManifestDirectory(sourceDirPath, zipFile.getPath());
    File[] resultZippedFiles = zipDir.listFiles(file -> !file.isHidden());
    assertThat(resultZippedFiles).isNotNull();
    assertThat(resultZippedFiles).hasSize(1);
    assertThat(resultZippedFiles[0]).hasName("destZipFile.zip");
    assertThat(FileUtils.openInputStream(new File(resultZippedFiles[0].getPath()))).isNotNull();

    InputStream targetStream = FileUtils.openInputStream(new File(zipFile.getPath()));
    ZipInputStream zipTargetStream = new ZipInputStream(targetStream);

    // test unzip directory operation
    serverlessTaskHelperBase.unzipManifestFiles(destDirPath, zipTargetStream);

    File resultDir = new File(destDirPath);
    File[] resultTestFiles = resultDir.listFiles(file -> !file.isHidden());
    assertThat(resultDir.list()).contains("test1.yaml", "test2.yaml");

    List<String> filesContent = new ArrayList<>();
    filesContent.add(readFileToString(resultTestFiles[0], "UTF-8"));
    filesContent.add(readFileToString(resultTestFiles[1], "UTF-8"));
    assertThat(filesContent).containsExactlyInAnyOrder("test script 1", "test script 2");

    // clean up
    FileIo.deleteDirectoryAndItsContentIfExists(destDirPath);
    FileIo.deleteDirectoryAndItsContentIfExists(sourceDirPath);
    FileIo.deleteDirectoryAndItsContentIfExists(zipDir.getPath());
  }

  public void zipManifestDirectory(String sourceFile, String destManifestFilesDirectoryPath) throws IOException {
    FileOutputStream fos = new FileOutputStream(destManifestFilesDirectoryPath);
    ZipOutputStream zipOut = new ZipOutputStream(fos);
    File fileToZip = new File(sourceFile);

    zipFile(fileToZip, fileToZip.getName(), zipOut);
    zipOut.close();
    fos.close();
  }

  private void zipFile(File fileToZip, String fileName, ZipOutputStream zipOut) throws IOException {
    if (fileToZip.isHidden()) {
      return;
    }
    if (fileToZip.isDirectory()) {
      if (fileName.endsWith("/")) {
        zipOut.putNextEntry(new ZipEntry(fileName));
        zipOut.closeEntry();
      } else {
        zipOut.putNextEntry(new ZipEntry(fileName + "/"));
        zipOut.closeEntry();
      }
      File[] children = fileToZip.listFiles();
      for (File childFile : children) {
        zipFile(childFile, fileName + "/" + childFile.getName(), zipOut);
      }
      return;
    }
    FileInputStream fis = new FileInputStream(fileToZip);
    ZipEntry zipEntry = new ZipEntry(fileName);
    zipOut.putNextEntry(zipEntry);
    byte[] bytes = new byte[1024];
    int length;
    while ((length = fis.read(bytes)) >= 0) {
      zipOut.write(bytes, 0, length);
    }
    fis.close();
  }
}