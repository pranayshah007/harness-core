/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.serverless;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.amazonaws.services.s3.model.S3Object;
import com.google.inject.Inject;
import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifactory.ArtifactoryNgService;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthCredentialsDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthenticationDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;
import io.harness.delegate.task.artifactory.ArtifactoryRequestMapper;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.encryption.SecretRefData;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.wings.service.impl.AwsApiHelperService;

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
  @Mock private AwsInternalConfig awsInternalConfig;
  @Mock private AwsNgConfigMapper awsNgConfigMapper;
  @Mock protected AwsApiHelperService awsApiHelperService;
  @InjectMocks @Spy private ServerlessTaskHelperBase serverlessTaskHelperBase;

  private static final String ARTIFACTORY_PATH = "asdffasd.zip";
  private static final String BUCKET_NAME = "bucket";
  private static final String FILE_PATH = "artifact/aws.zip";
  private String ARTIFACT_DIRECTORY;
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

  @Mock LogCallback logCallback;

  @Before
  public void setUp() {
    ARTIFACT_DIRECTORY = "./repository/serverless/" + RandomStringUtils.random(5, 0, 0, true, true, null, new SecureRandom());
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void fetchArtifactoryArtifactTest() throws Exception {
    ServerlessArtifactConfig serverlessArtifactConfig = ServerlessArtifactoryArtifactConfig.builder()
            .artifactPath(ARTIFACTORY_PATH)
            .connectorDTO(connectorInfoDTO)
            .encryptedDataDetails(encryptedDataDetailsList)
            .repositoryName(repositoryName)
            .build();
    doReturn(artifactoryConfigRequest).when(artifactoryRequestMapper).toArtifactoryRequest(connectorConfigDTO);
    ServerlessInfraConfig serverlessInfraConfig = ServerlessAwsLambdaInfraConfig.builder().region("us-east-1").build();
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
    serverlessTaskHelperBase.fetchArtifact(serverlessArtifactConfig, logCallback, ARTIFACT_DIRECTORY, serverlessInfraConfig);
    verify(logCallback)
        .saveExecutionLog(color(
            format("Downloading %s artifact with identifier: %s", serverlessArtifactConfig.getServerlessArtifactType(),
                ((ServerlessArtifactoryArtifactConfig) serverlessArtifactConfig).getIdentifier()),
            White, Bold));
    verify(logCallback).saveExecutionLog("Artifactory Artifact Path: " + artifactPath);
    verify(logCallback).saveExecutionLog(color("Successfully downloaded artifact..", White, Bold));
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void fetchS3ArtifactTest() throws Exception {
    ServerlessArtifactConfig serverlessArtifactConfig = ServerlessS3ArtifactConfig.builder()
            .bucketName(BUCKET_NAME)
            .filePath(FILE_PATH)
            .build();
    ServerlessAwsLambdaInfraConfig serverlessInfraConfig = ServerlessAwsLambdaInfraConfig.builder().region("us-east-1").build();

    String input = "asfd";
    InputStream inputStream = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
    String artifactPath = Paths
            .get(((ServerlessS3ArtifactConfig) serverlessArtifactConfig).getBucketName(),
                    ((ServerlessS3ArtifactConfig) serverlessArtifactConfig).getFilePath())
            .toString();

    S3Object s3Object = new S3Object();
    s3Object.setObjectContent(inputStream);

    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(serverlessInfraConfig.getAwsConnectorDTO());
    doReturn(s3Object)
            .when(awsApiHelperService)
            .getObjectFromS3(awsInternalConfig, serverlessInfraConfig.getRegion(), BUCKET_NAME, FILE_PATH);
    serverlessTaskHelperBase.fetchArtifact(serverlessArtifactConfig, logCallback, ARTIFACT_DIRECTORY, serverlessInfraConfig);
    verify(logCallback)
            .saveExecutionLog(color(
                    format("Downloading %s artifact with identifier: %s", serverlessArtifactConfig.getServerlessArtifactType(),
                            ((ServerlessS3ArtifactConfig) serverlessArtifactConfig).getIdentifier()),
                    White, Bold));
    verify(logCallback).saveExecutionLog("S3 Object Path: " + artifactPath);
    verify(logCallback).saveExecutionLog(color("Successfully downloaded artifact..", White, Bold));
  }

  @After
  public void cleanUp() throws IOException {
    File file = new File(ARTIFACT_DIRECTORY);
    FileUtils.deleteDirectory(file);
    file.delete();
  }
}
