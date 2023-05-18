/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.artifactory;

import static io.harness.artifactory.service.ArtifactoryRegistryService.DEFAULT_ARTIFACT_FILTER;
import static io.harness.artifactory.service.ArtifactoryRegistryService.MAX_NO_OF_TAGS_PER_ARTIFACT;
import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifactory.ArtifactoryNgService;
import io.harness.artifactory.service.ArtifactoryRegistryService;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.beans.ArtifactMetaInfo;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthenticationDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;
import io.harness.delegate.task.artifactory.ArtifactoryRequestMapper;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.utils.RepositoryFormat;

import io.fabric8.utils.Lists;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
public class ArtifactoryArtifactTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private static String ARTIFACTORY_URL_HOSTNAME = "artifactory.harness.io";
  private static String ARTIFACTORY_URL = "https://" + ARTIFACTORY_URL_HOSTNAME;
  private static String ARTIFACT_REPO_URL = "test.artifactory.harness.io";
  private static String ARTIFACTORY_USERNAME = "username";
  private static String ARTIFACTORY_PASSWORD = "password";
  private static String REPO_NAME = "repoName";
  private static String ARTIFACT_DIRECTORY = "dir";
  private static String IMAGE_NAME = "imageName";
  private static String IMAGE_TAG = "imageTag";
  private static String IMAGE_TAG_REGEX = "\\*";
  private static String ARTIFACT_PATH = "path";
  private static String COMBINED_ARTIFACT_PATH = ARTIFACT_DIRECTORY + "/" + ARTIFACT_PATH;
  private static int MAX_NO_OF_TAGS_PER_IMAGE = 10000;
  private static final Map<String, String> LABEL = Map.of("K1", "V1");
  private static final String SHA = "sha256:12345";
  private static final String SHA_V2 = "sha256:334534";
  private static final ArtifactMetaInfo ARTIFACT_META_INFO =
      ArtifactMetaInfo.builder().sha(SHA).shaV2(SHA_V2).labels(LABEL).build();

  @Mock ArtifactoryRegistryService artifactoryRegistryService;
  @InjectMocks ArtifactoryArtifactTaskHandler artifactoryArtifactService;
  @Mock ArtifactoryNgService artifactoryNgService;
  @Mock SecretDecryptionService secretDecryptionService;
  @Spy ArtifactoryRequestMapper artifactoryRequestMapper;

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuild() {
    ArtifactoryUsernamePasswordAuthDTO artifactoryUsernamePasswordAuthDTO = createArtifactoryCredentials();
    ArtifactoryConnectorDTO artifactoryConnectorDTO = createArtifactoryConnector(artifactoryUsernamePasswordAuthDTO);
    BuildDetailsInternal buildDetailsInternal =
        BuildDetailsInternal.builder().number(IMAGE_TAG).metadata(createBuildMetadata()).build();
    ArtifactoryConfigRequest artifactoryInternalConfig =
        ArtifactoryConfigRequest.builder()
            .artifactoryUrl(artifactoryConnectorDTO.getArtifactoryServerUrl())
            .username(artifactoryUsernamePasswordAuthDTO.getUsername())
            .password(artifactoryUsernamePasswordAuthDTO.getPasswordRef().getDecryptedValue())
            .artifactRepositoryUrl(ARTIFACT_REPO_URL)
            .build();

    ArtifactoryArtifactDelegateRequest sourceAttributes = ArtifactoryArtifactDelegateRequest.builder()
                                                              .repositoryName(REPO_NAME)
                                                              .artifactPath(IMAGE_NAME)
                                                              .repositoryFormat(RepositoryFormat.docker.name())
                                                              .tag(IMAGE_TAG)
                                                              .artifactRepositoryUrl(ARTIFACT_REPO_URL)
                                                              .artifactoryConnectorDTO(artifactoryConnectorDTO)
                                                              .build();

    doReturn(buildDetailsInternal)
        .when(artifactoryRegistryService)
        .verifyBuildNumber(any(), any(), any(), any(), any());

    ArtifactTaskExecutionResponse lastSuccessfulBuild =
        artifactoryArtifactService.getLastSuccessfulBuild(sourceAttributes);
    assertThat(lastSuccessfulBuild).isNotNull();
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().size()).isEqualTo(1);
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().get(0))
        .isInstanceOf(ArtifactoryArtifactDelegateResponse.class);
    ArtifactoryArtifactDelegateResponse attributes =
        (ArtifactoryArtifactDelegateResponse) lastSuccessfulBuild.getArtifactDelegateResponses().get(0);
    assertThat(attributes.getArtifactPath()).isEqualTo(IMAGE_NAME);
    assertThat(attributes.getTag()).isEqualTo(sourceAttributes.getTag());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildFromRegex() {
    ArtifactoryUsernamePasswordAuthDTO artifactoryUsernamePasswordAuthDTO =
        ArtifactoryUsernamePasswordAuthDTO.builder()
            .username(ARTIFACTORY_USERNAME)
            .passwordRef(SecretRefData.builder().decryptedValue(ARTIFACTORY_PASSWORD.toCharArray()).build())
            .build();

    ArtifactoryConnectorDTO artifactoryConnectorDTO =
        ArtifactoryConnectorDTO.builder()
            .artifactoryServerUrl(ARTIFACTORY_URL)
            .auth(ArtifactoryAuthenticationDTO.builder().credentials(artifactoryUsernamePasswordAuthDTO).build())
            .build();

    BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder().number(IMAGE_TAG).build();
    ArtifactoryConfigRequest artifactoryInternalConfig =
        ArtifactoryConfigRequest.builder()
            .artifactoryUrl(artifactoryConnectorDTO.getArtifactoryServerUrl())
            .username(artifactoryUsernamePasswordAuthDTO.getUsername())
            .password(artifactoryUsernamePasswordAuthDTO.getPasswordRef().getDecryptedValue())
            .hasCredentials(true)
            .artifactRepositoryUrl(ARTIFACT_REPO_URL)
            .build();

    ArtifactoryArtifactDelegateRequest sourceAttributes = ArtifactoryArtifactDelegateRequest.builder()
                                                              .repositoryName(REPO_NAME)
                                                              .artifactPath(IMAGE_NAME)
                                                              .repositoryFormat(RepositoryFormat.docker.name())
                                                              .tagRegex(IMAGE_TAG_REGEX)
                                                              .artifactRepositoryUrl(ARTIFACT_REPO_URL)
                                                              .artifactoryConnectorDTO(artifactoryConnectorDTO)
                                                              .build();

    doReturn(buildDetailsInternal)
        .when(artifactoryRegistryService)
        .getLastSuccessfulBuildFromRegex(
            artifactoryInternalConfig, REPO_NAME, IMAGE_NAME, RepositoryFormat.docker.name(), IMAGE_TAG_REGEX);

    ArtifactTaskExecutionResponse lastSuccessfulBuild =
        artifactoryArtifactService.getLastSuccessfulBuild(sourceAttributes);
    assertThat(lastSuccessfulBuild).isNotNull();
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().size()).isEqualTo(1);
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().get(0))
        .isInstanceOf(ArtifactoryArtifactDelegateResponse.class);
    ArtifactoryArtifactDelegateResponse attributes =
        (ArtifactoryArtifactDelegateResponse) lastSuccessfulBuild.getArtifactDelegateResponses().get(0);
    assertThat(attributes.getArtifactPath()).isEqualTo(IMAGE_NAME);
    assertThat(attributes.getTag()).isEqualTo(IMAGE_TAG);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetBuilds() {
    ArtifactoryUsernamePasswordAuthDTO artifactoryUsernamePasswordAuthDTO =
        ArtifactoryUsernamePasswordAuthDTO.builder()
            .username(ARTIFACTORY_USERNAME)
            .passwordRef(SecretRefData.builder().decryptedValue(ARTIFACTORY_PASSWORD.toCharArray()).build())
            .build();

    ArtifactoryConnectorDTO artifactoryConnectorDTO =
        ArtifactoryConnectorDTO.builder()
            .artifactoryServerUrl(ARTIFACTORY_URL)
            .auth(ArtifactoryAuthenticationDTO.builder().credentials(artifactoryUsernamePasswordAuthDTO).build())
            .build();

    Map<String, String> metadata = new HashMap<>();
    metadata.put(ArtifactMetadataKeys.IMAGE, ARTIFACT_REPO_URL + "/" + IMAGE_NAME + ":" + IMAGE_TAG);
    metadata.put(ArtifactMetadataKeys.TAG, IMAGE_TAG);
    BuildDetailsInternal buildDetailsInternal =
        BuildDetailsInternal.builder().number(IMAGE_TAG).metadata(metadata).build();
    ArtifactoryConfigRequest artifactoryInternalConfig =
        ArtifactoryConfigRequest.builder()
            .artifactoryUrl(artifactoryConnectorDTO.getArtifactoryServerUrl())
            .username(artifactoryUsernamePasswordAuthDTO.getUsername())
            .password(artifactoryUsernamePasswordAuthDTO.getPasswordRef().getDecryptedValue())
            .hasCredentials(true)
            .artifactRepositoryUrl(ARTIFACT_REPO_URL)
            .build();

    ArtifactoryArtifactDelegateRequest sourceAttributes = ArtifactoryArtifactDelegateRequest.builder()
                                                              .repositoryName(REPO_NAME)
                                                              .artifactPath(IMAGE_NAME)
                                                              .repositoryFormat(RepositoryFormat.docker.name())
                                                              .artifactRepositoryUrl(ARTIFACT_REPO_URL)
                                                              .artifactoryConnectorDTO(artifactoryConnectorDTO)
                                                              .build();

    doReturn(Lists.newArrayList(buildDetailsInternal))
        .when(artifactoryRegistryService)
        .getBuilds(artifactoryInternalConfig, REPO_NAME, IMAGE_NAME, RepositoryFormat.docker.name());

    ArtifactTaskExecutionResponse lastSuccessfulBuild = artifactoryArtifactService.getBuilds(sourceAttributes);
    assertThat(lastSuccessfulBuild).isNotNull();
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().size()).isEqualTo(1);
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().get(0))
        .isInstanceOf(ArtifactoryArtifactDelegateResponse.class);
    ArtifactoryArtifactDelegateResponse attributes =
        (ArtifactoryArtifactDelegateResponse) lastSuccessfulBuild.getArtifactDelegateResponses().get(0);
    assertThat(attributes.getArtifactPath()).isEqualTo(IMAGE_NAME);
    assertThat(attributes.getTag()).isEqualTo(IMAGE_TAG);
    assertThat(attributes.getBuildDetails().getMetadata().get(ArtifactMetadataKeys.IMAGE))
        .isEqualTo(ARTIFACT_REPO_URL + "/" + IMAGE_NAME + ":" + IMAGE_TAG);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetBuildsForGeneric() {
    ArtifactoryUsernamePasswordAuthDTO artifactoryUsernamePasswordAuthDTO =
        ArtifactoryUsernamePasswordAuthDTO.builder()
            .username(ARTIFACTORY_USERNAME)
            .passwordRef(SecretRefData.builder().decryptedValue(ARTIFACTORY_PASSWORD.toCharArray()).build())
            .build();

    ArtifactoryConnectorDTO artifactoryConnectorDTO = ArtifactoryConnectorDTO.builder()
                                                          .artifactoryServerUrl(ARTIFACTORY_URL)
                                                          .auth(ArtifactoryAuthenticationDTO.builder()
                                                                    .authType(ArtifactoryAuthType.USER_PASSWORD)
                                                                    .credentials(artifactoryUsernamePasswordAuthDTO)
                                                                    .build())
                                                          .build();

    BuildDetails buildDetailsInternal =
        BuildDetails.Builder.aBuildDetails().withArtifactPath(COMBINED_ARTIFACT_PATH).build();

    ArtifactoryConfigRequest artifactoryInternalConfig =
        ArtifactoryConfigRequest.builder()
            .artifactoryUrl(artifactoryConnectorDTO.getArtifactoryServerUrl())
            .username(artifactoryUsernamePasswordAuthDTO.getUsername())
            .password(artifactoryUsernamePasswordAuthDTO.getPasswordRef().getDecryptedValue())
            .hasCredentials(true)
            .build();

    ArtifactoryGenericArtifactDelegateRequest sourceAttributes = ArtifactoryGenericArtifactDelegateRequest.builder()
                                                                     .repositoryName(REPO_NAME)
                                                                     .repositoryFormat(RepositoryFormat.generic.name())
                                                                     .artifactDirectory(ARTIFACT_DIRECTORY)
                                                                     .artifactoryConnectorDTO(artifactoryConnectorDTO)
                                                                     .build();

    String artifactDirectory = sourceAttributes.getArtifactDirectory();
    String filePath = Paths.get(artifactDirectory, DEFAULT_ARTIFACT_FILTER).toString();

    doReturn(Lists.newArrayList(buildDetailsInternal))
        .when(artifactoryNgService)
        .getArtifactList(
            artifactoryInternalConfig, sourceAttributes.getRepositoryName(), filePath, MAX_NO_OF_TAGS_PER_ARTIFACT);

    ArtifactTaskExecutionResponse lastSuccessfulBuild = artifactoryArtifactService.getBuilds(sourceAttributes);
    assertThat(lastSuccessfulBuild).isNotNull();
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().size()).isEqualTo(1);
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().get(0))
        .isInstanceOf(ArtifactoryGenericArtifactDelegateResponse.class);
    ArtifactoryGenericArtifactDelegateResponse attributes =
        (ArtifactoryGenericArtifactDelegateResponse) lastSuccessfulBuild.getArtifactDelegateResponses().get(0);
    assertThat(attributes.getArtifactPath()).isEqualTo(ARTIFACT_PATH);
    assertThat(lastSuccessfulBuild.getBuildDetails().size()).isEqualTo(1);
    assertThat(lastSuccessfulBuild.getBuildDetails().get(0).getArtifactPath()).isEqualTo(ARTIFACT_PATH);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testValidateArtifactServer() {
    ArtifactoryUsernamePasswordAuthDTO artifactoryUsernamePasswordAuthDTO =
        ArtifactoryUsernamePasswordAuthDTO.builder()
            .username(ARTIFACTORY_USERNAME)
            .passwordRef(SecretRefData.builder().decryptedValue(ARTIFACTORY_PASSWORD.toCharArray()).build())
            .build();

    ArtifactoryConnectorDTO artifactoryConnectorDTO =
        ArtifactoryConnectorDTO.builder()
            .artifactoryServerUrl(ARTIFACTORY_URL)
            .auth(ArtifactoryAuthenticationDTO.builder().credentials(artifactoryUsernamePasswordAuthDTO).build())
            .build();

    ArtifactoryConfigRequest artifactoryInternalConfig =
        ArtifactoryConfigRequest.builder()
            .artifactoryUrl(artifactoryConnectorDTO.getArtifactoryServerUrl())
            .username(artifactoryUsernamePasswordAuthDTO.getUsername())
            .password(artifactoryUsernamePasswordAuthDTO.getPasswordRef().getDecryptedValue())
            .hasCredentials(true)
            .artifactRepositoryUrl(ARTIFACT_REPO_URL)
            .build();

    ArtifactoryArtifactDelegateRequest sourceAttributes = ArtifactoryArtifactDelegateRequest.builder()
                                                              .repositoryName(REPO_NAME)
                                                              .artifactPath(IMAGE_NAME)
                                                              .repositoryFormat(RepositoryFormat.docker.name())
                                                              .tag(IMAGE_TAG)
                                                              .artifactRepositoryUrl(ARTIFACT_REPO_URL)
                                                              .artifactoryConnectorDTO(artifactoryConnectorDTO)
                                                              .build();
    doReturn(true).when(artifactoryRegistryService).validateCredentials(artifactoryInternalConfig);

    ArtifactTaskExecutionResponse lastSuccessfulBuild =
        artifactoryArtifactService.validateArtifactServer(sourceAttributes);
    assertThat(lastSuccessfulBuild).isNotNull();
    assertThat(lastSuccessfulBuild.isArtifactServerValid()).isTrue();
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testIsRegex() {
    ArtifactoryArtifactDelegateRequest sourceAttributes = ArtifactoryArtifactDelegateRequest.builder()
                                                              .repositoryName(REPO_NAME)
                                                              .artifactPath(IMAGE_NAME)
                                                              .repositoryFormat(RepositoryFormat.docker.name())
                                                              .tag(IMAGE_TAG)
                                                              .tagRegex(IMAGE_TAG_REGEX)
                                                              .build();
    boolean regex = artifactoryArtifactService.isRegex(sourceAttributes);
    assertThat(regex).isTrue();

    sourceAttributes = ArtifactoryArtifactDelegateRequest.builder()
                           .repositoryName(REPO_NAME)
                           .artifactPath(IMAGE_NAME)
                           .repositoryFormat(RepositoryFormat.docker.name())
                           .tagRegex(IMAGE_TAG_REGEX)
                           .build();
    regex = artifactoryArtifactService.isRegex(sourceAttributes);
    assertThat(regex).isTrue();

    sourceAttributes = ArtifactoryArtifactDelegateRequest.builder()
                           .repositoryName(REPO_NAME)
                           .artifactPath(IMAGE_NAME)
                           .repositoryFormat(RepositoryFormat.docker.name())
                           .tag(IMAGE_TAG)
                           .build();
    regex = artifactoryArtifactService.isRegex(sourceAttributes);
    assertThat(regex).isFalse();
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testLabel() {
    ArtifactoryUsernamePasswordAuthDTO artifactoryUsernamePasswordAuthDTO = createArtifactoryCredentials();

    ArtifactoryConnectorDTO artifactoryConnectorDTO = createArtifactoryConnector(artifactoryUsernamePasswordAuthDTO);

    BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder()
                                                    .artifactMetaInfo(ARTIFACT_META_INFO)
                                                    .number(IMAGE_TAG)
                                                    .metadata(createBuildMetadata())
                                                    .build();

    ArtifactoryConfigRequest artifactoryInternalConfig =
        ArtifactoryConfigRequest.builder()
            .artifactoryUrl(artifactoryConnectorDTO.getArtifactoryServerUrl())
            .username(artifactoryUsernamePasswordAuthDTO.getUsername())
            .password(artifactoryUsernamePasswordAuthDTO.getPasswordRef().getDecryptedValue())
            .artifactRepositoryUrl(ARTIFACT_REPO_URL)
            .hasCredentials(true)
            .build();

    ArtifactoryArtifactDelegateRequest sourceAttributes = ArtifactoryArtifactDelegateRequest.builder()
                                                              .repositoryName(REPO_NAME)
                                                              .artifactPath(IMAGE_NAME)
                                                              .repositoryFormat(RepositoryFormat.docker.name())
                                                              .tag(IMAGE_TAG)
                                                              .artifactRepositoryUrl(ARTIFACT_REPO_URL)
                                                              .artifactoryConnectorDTO(artifactoryConnectorDTO)
                                                              .build();

    doReturn(buildDetailsInternal)
        .when(artifactoryRegistryService)
        .verifyBuildNumber(any(), any(), any(), any(), any());

    ArtifactTaskExecutionResponse lastSuccessfulBuild =
        artifactoryArtifactService.getLastSuccessfulBuild(sourceAttributes);

    ArtifactoryArtifactDelegateResponse attributes =
        (ArtifactoryArtifactDelegateResponse) lastSuccessfulBuild.getArtifactDelegateResponses().get(0);

    // verifying the labels fetched
    assertThat(attributes.getLabel()).isEqualTo(LABEL);

    Map<String, String> metadata = attributes.getBuildDetails().getMetadata();
    assertThat(metadata.get(ArtifactMetadataKeys.SHA)).isEqualTo(SHA);
    assertThat(metadata.get(ArtifactMetadataKeys.SHAV2)).isEqualTo(SHA_V2);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetLabelsFromRegex() {
    ArtifactoryUsernamePasswordAuthDTO artifactoryUsernamePasswordAuthDTO =
        ArtifactoryUsernamePasswordAuthDTO.builder()
            .username(ARTIFACTORY_USERNAME)
            .passwordRef(SecretRefData.builder().decryptedValue(ARTIFACTORY_PASSWORD.toCharArray()).build())
            .build();

    ArtifactoryConnectorDTO artifactoryConnectorDTO =
        ArtifactoryConnectorDTO.builder()
            .artifactoryServerUrl(ARTIFACTORY_URL)
            .auth(ArtifactoryAuthenticationDTO.builder().credentials(artifactoryUsernamePasswordAuthDTO).build())
            .build();

    BuildDetailsInternal buildDetailsInternal =
        BuildDetailsInternal.builder().artifactMetaInfo(ARTIFACT_META_INFO).number(IMAGE_TAG).build();
    ArtifactoryConfigRequest artifactoryInternalConfig =
        ArtifactoryConfigRequest.builder()
            .artifactoryUrl(artifactoryConnectorDTO.getArtifactoryServerUrl())
            .username(artifactoryUsernamePasswordAuthDTO.getUsername())
            .password(artifactoryUsernamePasswordAuthDTO.getPasswordRef().getDecryptedValue())
            .hasCredentials(true)
            .artifactRepositoryUrl(ARTIFACT_REPO_URL)
            .build();

    ArtifactoryArtifactDelegateRequest sourceAttributes = ArtifactoryArtifactDelegateRequest.builder()
                                                              .repositoryName(REPO_NAME)
                                                              .artifactPath(IMAGE_NAME)
                                                              .repositoryFormat(RepositoryFormat.docker.name())
                                                              .tagRegex(IMAGE_TAG_REGEX)
                                                              .artifactRepositoryUrl(ARTIFACT_REPO_URL)
                                                              .artifactoryConnectorDTO(artifactoryConnectorDTO)
                                                              .build();

    doReturn(buildDetailsInternal)
        .when(artifactoryRegistryService)
        .getLastSuccessfulBuildFromRegex(
            artifactoryInternalConfig, REPO_NAME, IMAGE_NAME, RepositoryFormat.docker.name(), IMAGE_TAG_REGEX);

    ArtifactTaskExecutionResponse lastSuccessfulBuild =
        artifactoryArtifactService.getLastSuccessfulBuild(sourceAttributes);

    ArtifactoryArtifactDelegateResponse attributes =
        (ArtifactoryArtifactDelegateResponse) lastSuccessfulBuild.getArtifactDelegateResponses().get(0);

    // verifying the labels fetched
    assertThat(attributes.getLabel()).isEqualTo(LABEL);

    Map<String, String> metadata = attributes.getBuildDetails().getMetadata();
    assertThat(metadata.get(ArtifactMetadataKeys.SHA)).isEqualTo(SHA);
    assertThat(metadata.get(ArtifactMetadataKeys.SHAV2)).isEqualTo(SHA_V2);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetSuccessTaskExecutionResponseGeneric() {
    List<ArtifactoryGenericArtifactDelegateResponse> artifactDelegateResponses = new ArrayList<>();

    ArtifactoryGenericArtifactDelegateResponse artifactoryGenericArtifactDelegateResponse =
        ArtifactoryGenericArtifactDelegateResponse.builder().build();

    artifactDelegateResponses.add(artifactoryGenericArtifactDelegateResponse);

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        artifactoryArtifactService.getSuccessTaskExecutionResponseGeneric(artifactDelegateResponses);

    assertThat(artifactTaskExecutionResponse.isArtifactSourceValid()).isTrue();

    assertThat(artifactTaskExecutionResponse.isArtifactServerValid()).isTrue();

    assertThat(artifactTaskExecutionResponse.getArtifactDelegateResponses()).isEqualTo(artifactDelegateResponses);
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testdecryptRequestDTOs() {
    ArtifactoryArtifactDelegateRequest artifactoryArtifactDelegateRequest =
        ArtifactoryArtifactDelegateRequest.builder()
            .artifactoryConnectorDTO(
                ArtifactoryConnectorDTO.builder().auth(ArtifactoryAuthenticationDTO.builder().build()).build())
            .build();

    artifactoryArtifactService.decryptRequestDTOs(artifactoryArtifactDelegateRequest);

    verify(secretDecryptionService).decrypt(any(), any());
  }

  private ArtifactoryUsernamePasswordAuthDTO createArtifactoryCredentials() {
    return ArtifactoryUsernamePasswordAuthDTO.builder()
        .username(ARTIFACTORY_USERNAME)
        .passwordRef(SecretRefData.builder().decryptedValue(ARTIFACTORY_PASSWORD.toCharArray()).build())
        .build();
  }

  private ArtifactoryConnectorDTO createArtifactoryConnector(
      ArtifactoryUsernamePasswordAuthDTO artifactoryCredentials) {
    return ArtifactoryConnectorDTO.builder()
        .artifactoryServerUrl(ARTIFACTORY_URL)
        .auth(ArtifactoryAuthenticationDTO.builder().credentials(artifactoryCredentials).build())
        .build();
  }

  private Map<String, String> createBuildMetadata() {
    Map<String, String> metadata = new HashMap<>();
    metadata.put(ArtifactMetadataKeys.IMAGE, ARTIFACTORY_URL_HOSTNAME + "/" + IMAGE_NAME + ":" + IMAGE_TAG);
    metadata.put(ArtifactMetadataKeys.TAG, IMAGE_TAG);
    return metadata;
  }

  private ArtifactoryArtifactDelegateRequest createArtifactoryDelegateRequestWithTag(
      ArtifactoryConnectorDTO artifactoryConnectorDTO) {
    return createArtifactoryDelegateRequest(artifactoryConnectorDTO, IMAGE_TAG, null, ARTIFACTORY_URL_HOSTNAME);
  }

  private ArtifactoryArtifactDelegateRequest createArtifactoryDelegateRequestWithTagRegex(
      ArtifactoryConnectorDTO artifactoryConnectorDTO) {
    return createArtifactoryDelegateRequest(artifactoryConnectorDTO, null, IMAGE_TAG_REGEX, ARTIFACTORY_URL_HOSTNAME);
  }

  private ArtifactoryArtifactDelegateRequest createArtifactoryDelegateRequest(
      ArtifactoryConnectorDTO artifactoryConnectorDTO, String tag, String tagRegex, String artifactRepoUrl) {
    return ArtifactoryArtifactDelegateRequest.builder()
        .repositoryName(REPO_NAME)
        .artifactPath(IMAGE_NAME)
        .repositoryFormat(RepositoryFormat.docker.name())
        .tag(tag)
        .tagRegex(tagRegex)
        .artifactRepositoryUrl(artifactRepoUrl)
        .artifactoryConnectorDTO(artifactoryConnectorDTO)
        .build();
  }

  private ArtifactoryConfigRequest createArtifactoryConfigWithUrl(
      ArtifactoryUsernamePasswordAuthDTO artifactoryUsernamePasswordAuthDTO,
      ArtifactoryConnectorDTO artifactoryConnectorDTO) {
    return ArtifactoryConfigRequest.builder()
        .artifactoryUrl(artifactoryConnectorDTO.getArtifactoryServerUrl())
        .username(artifactoryUsernamePasswordAuthDTO.getUsername())
        .password(artifactoryUsernamePasswordAuthDTO.getPasswordRef().getDecryptedValue())
        .hasCredentials(true)
        .artifactRepositoryUrl(ARTIFACT_REPO_URL)
        .build();
  }
}
