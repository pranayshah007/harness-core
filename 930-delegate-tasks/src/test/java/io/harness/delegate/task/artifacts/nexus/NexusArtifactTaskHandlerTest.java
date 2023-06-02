/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.nexus;

import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.SHIVAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.nexusconnector.NexusAuthenticationDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusUsernamePasswordAuthDTO;
import io.harness.delegate.task.artifacts.mappers.NexusRequestResponseMapper;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.encryption.SecretRefData;
import io.harness.nexus.NexusRequest;
import io.harness.nexus.service.NexusRegistryService;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.utils.RepositoryFormat;

import io.fabric8.utils.Lists;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
public class NexusArtifactTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private static String NEXUS_URL_HOSTNAME = "nexus.harness.io";
  private static String NEXUS_URL = "https://" + NEXUS_URL_HOSTNAME;
  private static String DOCKER_REPO_URL = "test.harness.io:8181";
  private static String NEXUS_USERNAME = "username";
  private static String NEXUS_PASSWORD = "password";
  private static String REPO_NAME = "repoName";
  private static String REPO_PORT = "8181";
  private static String IMAGE_NAME = "imageName";
  private static String IMAGE_TAG = "imageTag";
  private static String IMAGE_TAG_REGEX = "\\*";
  private static Integer MAX_NO_OF_TAGS_PER_IMAGE = 10000;

  @Mock NexusRegistryService nexusRegistryService;
  @Mock SecretDecryptionService secretDecryptionService;
  @InjectMocks NexusArtifactTaskHandler nexusArtifactService;

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuild() {
    NexusUsernamePasswordAuthDTO nexusUsernamePasswordAuthDTO = createNexusCredentials();
    NexusConnectorDTO nexusConnectorDTO = createNexusConnector(nexusUsernamePasswordAuthDTO);
    BuildDetailsInternal buildDetailsInternal =
        BuildDetailsInternal.builder().number(IMAGE_TAG).metadata(createBuildMetadataWithUrl()).build();
    NexusRequest nexusInternalConfig = createNexusConfigWithUrl(nexusUsernamePasswordAuthDTO, nexusConnectorDTO);
    NexusArtifactDelegateRequest sourceAttributes = createNexusDelegateRequestWithTagAndUrl(nexusConnectorDTO);

    doReturn(buildDetailsInternal)
        .when(nexusRegistryService)
        .verifyBuildNumber(nexusInternalConfig, REPO_NAME, null, IMAGE_NAME, RepositoryFormat.docker.name(), IMAGE_TAG,
            null, null, null, null, null, null, Integer.MAX_VALUE);

    ArtifactTaskExecutionResponse lastSuccessfulBuild = nexusArtifactService.getLastSuccessfulBuild(sourceAttributes);
    assertThat(lastSuccessfulBuild).isNotNull();
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().size()).isEqualTo(1);
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().get(0))
        .isInstanceOf(NexusArtifactDelegateResponse.class);
    NexusArtifactDelegateResponse attributes =
        (NexusArtifactDelegateResponse) lastSuccessfulBuild.getArtifactDelegateResponses().get(0);
    assertThat(attributes.getArtifactPath()).isEqualTo(IMAGE_NAME);
    assertThat(attributes.getTag()).isEqualTo(sourceAttributes.getTag());
    assertThat(attributes.getBuildDetails().getMetadata().get(ArtifactMetadataKeys.IMAGE))
        .isEqualTo(DOCKER_REPO_URL + "/" + IMAGE_NAME + ":" + IMAGE_TAG);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildFromRegex() {
    NexusUsernamePasswordAuthDTO nexusUsernamePasswordAuthDTO = createNexusCredentials();
    NexusConnectorDTO nexusConnectorDTO = createNexusConnector(nexusUsernamePasswordAuthDTO);
    BuildDetailsInternal buildDetailsInternal =
        BuildDetailsInternal.builder().number(IMAGE_TAG).metadata(createBuildMetadataWithUrl()).build();
    NexusRequest nexusInternalConfig = createNexusConfigWithUrl(nexusUsernamePasswordAuthDTO, nexusConnectorDTO);
    NexusArtifactDelegateRequest sourceAttributes = createNexusDelegateRequestWithTagRegexAndUrl(nexusConnectorDTO);

    doReturn(buildDetailsInternal)
        .when(nexusRegistryService)
        .getLastSuccessfulBuildFromRegex(nexusInternalConfig, REPO_NAME, null, IMAGE_NAME,
            RepositoryFormat.docker.name(), IMAGE_TAG_REGEX, null, null, null, null, null, null, Integer.MAX_VALUE);

    ArtifactTaskExecutionResponse lastSuccessfulBuild = nexusArtifactService.getLastSuccessfulBuild(sourceAttributes);
    assertThat(lastSuccessfulBuild).isNotNull();
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().size()).isEqualTo(1);
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().get(0))
        .isInstanceOf(NexusArtifactDelegateResponse.class);
    NexusArtifactDelegateResponse attributes =
        (NexusArtifactDelegateResponse) lastSuccessfulBuild.getArtifactDelegateResponses().get(0);
    assertThat(attributes.getArtifactPath()).isEqualTo(IMAGE_NAME);
    assertThat(attributes.getTag()).isEqualTo(IMAGE_TAG);
    assertThat(attributes.getBuildDetails().getMetadata().get(ArtifactMetadataKeys.IMAGE))
        .isEqualTo(DOCKER_REPO_URL + "/" + IMAGE_NAME + ":" + IMAGE_TAG);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetBuilds() {
    NexusUsernamePasswordAuthDTO nexusUsernamePasswordAuthDTO = createNexusCredentials();
    NexusConnectorDTO nexusConnectorDTO = createNexusConnector(nexusUsernamePasswordAuthDTO);
    BuildDetailsInternal buildDetailsInternal =
        BuildDetailsInternal.builder().number(IMAGE_TAG).metadata(createBuildMetadataWithUrl()).build();
    NexusRequest nexusInternalConfig = createNexusConfigWithUrl(nexusUsernamePasswordAuthDTO, nexusConnectorDTO);
    NexusArtifactDelegateRequest sourceAttributes = createNexusDelegateRequestWithTagAndUrl(nexusConnectorDTO);

    doReturn(Lists.newArrayList(buildDetailsInternal))
        .when(nexusRegistryService)
        .getBuilds(nexusInternalConfig, REPO_NAME, null, IMAGE_NAME, RepositoryFormat.docker.name(), null, null, null,
            null, null, null, Integer.MAX_VALUE);

    ArtifactTaskExecutionResponse lastSuccessfulBuild = nexusArtifactService.getBuilds(sourceAttributes);
    assertThat(lastSuccessfulBuild).isNotNull();
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().size()).isEqualTo(1);
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().get(0))
        .isInstanceOf(NexusArtifactDelegateResponse.class);
    NexusArtifactDelegateResponse attributes =
        (NexusArtifactDelegateResponse) lastSuccessfulBuild.getArtifactDelegateResponses().get(0);
    assertThat(attributes.getArtifactPath()).isEqualTo(IMAGE_NAME);
    assertThat(attributes.getTag()).isEqualTo(IMAGE_TAG);
    assertThat(attributes.getBuildDetails().getMetadata().get(ArtifactMetadataKeys.IMAGE))
        .isEqualTo(DOCKER_REPO_URL + "/" + IMAGE_NAME + ":" + IMAGE_TAG);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testValidateArtifactServer() {
    NexusUsernamePasswordAuthDTO nexusUsernamePasswordAuthDTO = createNexusCredentials();
    NexusConnectorDTO nexusConnectorDTO = createNexusConnector(nexusUsernamePasswordAuthDTO);
    NexusRequest nexusInternalConfig = createNexusConfigWithUrl(nexusUsernamePasswordAuthDTO, nexusConnectorDTO);
    NexusArtifactDelegateRequest sourceAttributes = createNexusDelegateRequestWithTagAndUrl(nexusConnectorDTO);
    doReturn(true).when(nexusRegistryService).validateCredentials(nexusInternalConfig);
    ArtifactTaskExecutionResponse lastSuccessfulBuild = nexusArtifactService.validateArtifactServer(sourceAttributes);
    assertThat(lastSuccessfulBuild).isNotNull();
    assertThat(lastSuccessfulBuild.isArtifactServerValid()).isTrue();
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testIsRegex() {
    NexusUsernamePasswordAuthDTO nexusUsernamePasswordAuthDTO = createNexusCredentials();
    NexusConnectorDTO nexusConnectorDTO = createNexusConnector(nexusUsernamePasswordAuthDTO);
    NexusArtifactDelegateRequest sourceAttributes =
        createNexusDelegateRequest(nexusConnectorDTO, IMAGE_TAG, IMAGE_TAG_REGEX, null, DOCKER_REPO_URL);
    boolean regex = nexusArtifactService.isRegex(sourceAttributes);
    assertThat(regex).isTrue();

    sourceAttributes = createNexusDelegateRequest(nexusConnectorDTO, null, IMAGE_TAG_REGEX, null, DOCKER_REPO_URL);
    regex = nexusArtifactService.isRegex(sourceAttributes);
    assertThat(regex).isTrue();

    sourceAttributes = createNexusDelegateRequest(nexusConnectorDTO, IMAGE_TAG, null, null, DOCKER_REPO_URL);
    regex = nexusArtifactService.isRegex(sourceAttributes);
    assertThat(regex).isFalse();
  }

  private NexusUsernamePasswordAuthDTO createNexusCredentials() {
    return NexusUsernamePasswordAuthDTO.builder()
        .username(NEXUS_USERNAME)
        .passwordRef(SecretRefData.builder().decryptedValue(NEXUS_PASSWORD.toCharArray()).build())
        .build();
  }

  private NexusConnectorDTO createNexusConnector(NexusUsernamePasswordAuthDTO nexusCredentials) {
    return NexusConnectorDTO.builder()
        .nexusServerUrl(NEXUS_URL)
        .auth(NexusAuthenticationDTO.builder().credentials(nexusCredentials).build())
        .build();
  }

  private Map<String, String> createBuildMetadataWithPort() {
    return createBuildMetadata(NEXUS_URL_HOSTNAME + ":" + REPO_PORT);
  }

  private Map<String, String> createBuildMetadataWithUrl() {
    return createBuildMetadata(DOCKER_REPO_URL);
  }

  private Map<String, String> createBuildMetadata(String hostname) {
    Map<String, String> metadata = new HashMap<>();
    metadata.put(ArtifactMetadataKeys.IMAGE, hostname + "/" + IMAGE_NAME + ":" + IMAGE_TAG);
    metadata.put(ArtifactMetadataKeys.TAG, IMAGE_TAG);
    return metadata;
  }

  private NexusArtifactDelegateRequest createNexusDelegateRequestWithTagAndPort(NexusConnectorDTO nexusConnectorDTO) {
    return createNexusDelegateRequest(nexusConnectorDTO, IMAGE_TAG, null, REPO_PORT, null);
  }

  private NexusArtifactDelegateRequest createNexusDelegateRequestWithTagAndUrl(NexusConnectorDTO nexusConnectorDTO) {
    return createNexusDelegateRequest(nexusConnectorDTO, IMAGE_TAG, null, null, DOCKER_REPO_URL);
  }

  private NexusArtifactDelegateRequest createNexusDelegateRequestWithTagRegexAndPort(
      NexusConnectorDTO nexusConnectorDTO) {
    return createNexusDelegateRequest(nexusConnectorDTO, null, IMAGE_TAG_REGEX, REPO_PORT, null);
  }

  private NexusArtifactDelegateRequest createNexusDelegateRequestWithTagRegexAndUrl(
      NexusConnectorDTO nexusConnectorDTO) {
    return createNexusDelegateRequest(nexusConnectorDTO, null, IMAGE_TAG_REGEX, null, DOCKER_REPO_URL);
  }

  private NexusArtifactDelegateRequest createNexusDelegateRequest(
      NexusConnectorDTO nexusConnectorDTO, String tag, String tagRegex, String port, String dockerRepoServer) {
    return NexusArtifactDelegateRequest.builder()
        .repositoryName(REPO_NAME)
        .artifactPath(IMAGE_NAME)
        .repositoryFormat(RepositoryFormat.docker.name())
        .tag(tag)
        .tagRegex(tagRegex)
        .repositoryPort(port)
        .artifactRepositoryUrl(dockerRepoServer)
        .nexusConnectorDTO(nexusConnectorDTO)
        .maxBuilds(Integer.MAX_VALUE)
        .build();
  }

  private NexusRequest createNexusConfigWithPort(
      NexusUsernamePasswordAuthDTO nexusUsernamePasswordAuthDTO, NexusConnectorDTO nexusConnectorDTO) {
    return NexusRequest.builder()
        .nexusUrl(nexusConnectorDTO.getNexusServerUrl())
        .username(nexusUsernamePasswordAuthDTO.getUsername())
        .password(nexusUsernamePasswordAuthDTO.getPasswordRef().getDecryptedValue())
        .hasCredentials(true)
        .build();
  }

  private NexusRequest createNexusConfigWithUrl(
      NexusUsernamePasswordAuthDTO nexusUsernamePasswordAuthDTO, NexusConnectorDTO nexusConnectorDTO) {
    return NexusRequest.builder()
        .nexusUrl(nexusConnectorDTO.getNexusServerUrl())
        .username(nexusUsernamePasswordAuthDTO.getUsername())
        .password(nexusUsernamePasswordAuthDTO.getPasswordRef().getDecryptedValue())
        .hasCredentials(true)
        .artifactRepositoryUrl(DOCKER_REPO_URL)
        .build();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetRepositories() {
    NexusUsernamePasswordAuthDTO nexusUsernamePasswordAuthDTO = createNexusCredentials();
    NexusConnectorDTO nexusConnectorDTO = createNexusConnector(nexusUsernamePasswordAuthDTO);
    BuildDetailsInternal buildDetailsInternal =
        BuildDetailsInternal.builder().number(IMAGE_TAG).metadata(createBuildMetadataWithUrl()).build();
    NexusRequest nexusInternalConfig = createNexusConfigWithUrl(nexusUsernamePasswordAuthDTO, nexusConnectorDTO);
    NexusArtifactDelegateRequest sourceAttributes = createNexusDelegateRequestWithTagAndUrl(nexusConnectorDTO);

    doReturn(Collections.singletonMap("test", "test"))
        .when(nexusRegistryService)
        .getRepository(
            NexusRequestResponseMapper.toNexusInternalConfig(sourceAttributes), sourceAttributes.getRepositoryFormat());

    ArtifactTaskExecutionResponse repositories = nexusArtifactService.getRepositories(sourceAttributes);
    assertThat(repositories).isNotNull();
    assertThat(repositories.getRepositories().size()).isEqualTo(1);
  }
}
