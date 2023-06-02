/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.lambda;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ALLU_VAMSI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.outcome.ArtifactoryGenericArtifactOutcome;
import io.harness.cdng.artifact.outcome.CustomArtifactOutcome;
import io.harness.cdng.artifact.outcome.JenkinsArtifactOutcome;
import io.harness.cdng.artifact.outcome.NexusArtifactOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.impl.DefaultConnectorServiceImpl;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthenticationDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsAuthenticationDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.task.aws.lambda.AwsLambdaArtifactoryArtifactConfig;
import io.harness.delegate.task.aws.lambda.AwsLambdaCustomArtifactConfig;
import io.harness.delegate.task.aws.lambda.AwsLambdaJenkinsArtifactConfig;
import io.harness.delegate.task.aws.lambda.AwsLambdaNexusArtifactConfig;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class AwsLambdaEntityHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @InjectMocks @Spy AwsLambdaEntityHelper awsLambdaEntityHelper;
  @Mock private DefaultConnectorServiceImpl connectorService;
  Ambiance ambiance = getAmbiance();

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getAwsLambdaNexusArtifactConfigTest() {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("url", "nexus.url");
    NexusArtifactOutcome nexusArtifactOutcome = NexusArtifactOutcome.builder()
                                                    .identifier("id")
                                                    .connectorRef("connectorRef")
                                                    .artifactPath("path")
                                                    .primaryArtifact(true)
                                                    .image("image")
                                                    .repositoryFormat("maven")
                                                    .metadata(metadata)
                                                    .build();
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    NexusConnectorDTO connectorDTO = NexusConnectorDTO.builder().version("2.x").build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder().connectorConfig(connectorDTO).build();
    doReturn(null).when(awsLambdaEntityHelper).getEncryptionDataDetails(any(), any());
    doReturn(connectorInfoDTO).when(awsLambdaEntityHelper).getConnectorInfoDTO(anyString(), any());
    AwsLambdaNexusArtifactConfig artifactConfig =
        (AwsLambdaNexusArtifactConfig) awsLambdaEntityHelper.getAwsLambdaArtifactConfig(nexusArtifactOutcome, ngAccess);

    assertThat(artifactConfig.getIdentifier()).isEqualTo(nexusArtifactOutcome.getIdentifier());
    assertThat(artifactConfig.getRepositoryFormat()).isEqualTo(nexusArtifactOutcome.getRepositoryFormat());
    assertThat(artifactConfig.getArtifactUrl()).isEqualTo(nexusArtifactOutcome.getMetadata().get("url"));
    assertThat(artifactConfig.getMetadata()).isEqualTo(nexusArtifactOutcome.getMetadata());
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getAwsLambdaJenkinsArtifactConfigTest() {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("url", "nexus.url");
    JenkinsArtifactOutcome jenkinsArtifactOutcome = JenkinsArtifactOutcome.builder()
                                                        .identifier("id")
                                                        .connectorRef("connectorRef")
                                                        .artifactPath("path")
                                                        .primaryArtifact(true)
                                                        .jobName("jobName")
                                                        .build("build")
                                                        .metadata(metadata)
                                                        .build();
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    JenkinsAuthenticationDTO auth = JenkinsAuthenticationDTO.builder().build();
    JenkinsConnectorDTO connectorDTO = JenkinsConnectorDTO.builder().auth(auth).build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder().connectorConfig(connectorDTO).build();
    doReturn(null).when(awsLambdaEntityHelper).getEncryptionDataDetails(any(), any());
    doReturn(connectorInfoDTO).when(awsLambdaEntityHelper).getConnectorInfoDTO(anyString(), any());
    AwsLambdaJenkinsArtifactConfig artifactConfig =
        (AwsLambdaJenkinsArtifactConfig) awsLambdaEntityHelper.getAwsLambdaArtifactConfig(
            jenkinsArtifactOutcome, ngAccess);

    assertThat(artifactConfig.getIdentifier()).isEqualTo(jenkinsArtifactOutcome.getIdentifier());
    assertThat(artifactConfig.getArtifactPath()).isEqualTo(jenkinsArtifactOutcome.getArtifactPath());
    assertThat(artifactConfig.getConnectorConfig()).isEqualTo(connectorInfoDTO.getConnectorConfig());
    assertThat(artifactConfig.getBuild()).isEqualTo(jenkinsArtifactOutcome.getBuild());
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getAwsLambdaArtifactoryArtifactConfigTest() {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("url", "nexus.url");
    ArtifactoryGenericArtifactOutcome artifactoryArtifactOutcome = ArtifactoryGenericArtifactOutcome.builder()
                                                                       .identifier("id")
                                                                       .connectorRef("connectorRef")
                                                                       .artifactPath("path")
                                                                       .repositoryFormat("format")
                                                                       .primaryArtifact(true)
                                                                       .type("type")
                                                                       .metadata(metadata)
                                                                       .build();
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    ArtifactoryAuthenticationDTO auth = ArtifactoryAuthenticationDTO.builder().build();
    ArtifactoryConnectorDTO connectorDTO = ArtifactoryConnectorDTO.builder().auth(auth).build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder().connectorConfig(connectorDTO).build();
    doReturn(null).when(awsLambdaEntityHelper).getEncryptionDataDetails(any(), any());
    doReturn(connectorInfoDTO).when(awsLambdaEntityHelper).getConnectorInfoDTO(anyString(), any());
    AwsLambdaArtifactoryArtifactConfig artifactConfig =
        (AwsLambdaArtifactoryArtifactConfig) awsLambdaEntityHelper.getAwsLambdaArtifactConfig(
            artifactoryArtifactOutcome, ngAccess);

    assertThat(artifactConfig.getIdentifier()).isEqualTo(artifactoryArtifactOutcome.getIdentifier());
    assertThat(artifactConfig.getArtifactPath()).isEqualTo(artifactoryArtifactOutcome.getArtifactPath());
    assertThat(artifactConfig.getConnectorConfig()).isEqualTo(connectorInfoDTO.getConnectorConfig());
    assertThat(artifactConfig.getRepositoryFormat()).isEqualTo(artifactoryArtifactOutcome.getRepositoryFormat());
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getAwsLambdaCustomArtifactConfigTest() {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("bucketName", "bucket");
    metadata.put("key", "path");
    CustomArtifactOutcome customArtifactOutcome = CustomArtifactOutcome.builder()
                                                      .identifier("id")
                                                      .artifactPath("path")
                                                      .version("001")
                                                      .image("image")
                                                      .primaryArtifact(true)
                                                      .metadata(metadata)
                                                      .build();
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    AwsLambdaCustomArtifactConfig artifactConfig =
        (AwsLambdaCustomArtifactConfig) awsLambdaEntityHelper.getAwsLambdaArtifactConfig(
            customArtifactOutcome, ngAccess);

    assertThat(artifactConfig.getIdentifier()).isEqualTo(customArtifactOutcome.getIdentifier());
    assertThat(artifactConfig.getVersion()).isEqualTo(customArtifactOutcome.getVersion());
  }

  @Test(expected = UnsupportedOperationException.class)
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getAwsLambdaCustomArtifactInvalidMetaDataConfigTest() {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("key", "path");
    CustomArtifactOutcome customArtifactOutcome = CustomArtifactOutcome.builder()
                                                      .identifier("id")
                                                      .artifactPath("path")
                                                      .version("001")
                                                      .image("image")
                                                      .primaryArtifact(true)
                                                      .metadata(metadata)
                                                      .build();
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    AwsLambdaCustomArtifactConfig artifactConfig =
        (AwsLambdaCustomArtifactConfig) awsLambdaEntityHelper.getAwsLambdaArtifactConfig(
            customArtifactOutcome, ngAccess);

    assertThat(artifactConfig.getIdentifier()).isEqualTo(customArtifactOutcome.getIdentifier());
    assertThat(artifactConfig.getVersion()).isEqualTo(customArtifactOutcome.getVersion());
  }

  private Ambiance getAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions("accountId", "test-account")
        .putSetupAbstractions("projectIdentifier", "test-project")
        .putSetupAbstractions("orgIdentifier", "test-org")
        .putSetupAbstractions("identifier", "id")
        .build();
  }
}
