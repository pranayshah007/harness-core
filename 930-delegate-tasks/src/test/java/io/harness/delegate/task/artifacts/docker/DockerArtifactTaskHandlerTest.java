/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.docker;

import static io.harness.artifacts.docker.service.DockerRegistryService.MAX_NO_OF_TAGS_PER_IMAGE;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.SHIVAM;
import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.docker.beans.DockerInternalConfig;
import io.harness.artifacts.docker.service.DockerRegistryService;
import io.harness.beans.ArtifactMetaInfo;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.docker.DockerAuthenticationDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerUserNamePasswordDTO;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

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

public class DockerArtifactTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock DockerRegistryService dockerRegistryService;
  @InjectMocks DockerArtifactTaskHandler dockerArtifactService;

  private static final String SHA = "sha256:1234";
  private static final String SHA_V2 = "sha256:5678";
  private static final Map<String, String> LABELS = Collections.singletonMap("author", "docker");
  private static final ArtifactMetaInfo ARTIFACT_META_INFO =
      ArtifactMetaInfo.builder().sha(SHA).shaV2(SHA_V2).labels(LABELS).build();

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuild() {
    BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder().number("tag").build();
    DockerInternalConfig dockerInternalConfig =
        DockerInternalConfig.builder().dockerRegistryUrl("URL").username("username").build();
    doReturn(buildDetailsInternal)
        .when(dockerRegistryService)
        .verifyBuildNumber(dockerInternalConfig, "imagePath", "tag");
    DockerArtifactDelegateRequest sourceAttributes =
        DockerArtifactDelegateRequest.builder()
            .imagePath("imagePath")
            .tag("tag")
            .dockerConnectorDTO(DockerConnectorDTO.builder()
                                    .dockerRegistryUrl("URL")
                                    .auth(DockerAuthenticationDTO.builder()
                                              .credentials(DockerUserNamePasswordDTO.builder()
                                                               .username("username")
                                                               .passwordRef(SecretRefData.builder().build())
                                                               .build())
                                              .build())
                                    .build())
            .build();
    ArtifactTaskExecutionResponse lastSuccessfulBuild = dockerArtifactService.getLastSuccessfulBuild(sourceAttributes);
    assertThat(lastSuccessfulBuild).isNotNull();
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().size()).isEqualTo(1);
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().get(0))
        .isInstanceOf(DockerArtifactDelegateResponse.class);
    DockerArtifactDelegateResponse attributes =
        (DockerArtifactDelegateResponse) lastSuccessfulBuild.getArtifactDelegateResponses().get(0);
    assertThat(attributes.getImagePath()).isEqualTo(sourceAttributes.getImagePath());
    assertThat(attributes.getTag()).isEqualTo(sourceAttributes.getTag());
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildWithV2SHA256Digest() {
    BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder().number("tag").build();
    DockerInternalConfig dockerInternalConfig =
        DockerInternalConfig.builder().dockerRegistryUrl("URL").username("username").build();
    doReturn(buildDetailsInternal)
        .when(dockerRegistryService)
        .verifyBuildNumber(dockerInternalConfig, "imagePath", "tag");
    Map<String, String> label = new HashMap<>();
    label.put("label", "label");
    doReturn(Collections.singletonList(label))
        .when(dockerRegistryService)
        .getLabels(dockerInternalConfig, "imagePath", Collections.singletonList("tag"));
    ArtifactMetaInfo artifactMetaInfo = ArtifactMetaInfo.builder().shaV2("V2_DIGEST").build();
    doReturn(artifactMetaInfo)
        .when(dockerRegistryService)
        .getArtifactMetaInfo(dockerInternalConfig, "imagePath", "tag", true);
    DockerArtifactDelegateRequest sourceAttributes =
        DockerArtifactDelegateRequest.builder()
            .imagePath("imagePath")
            .tag("tag")
            .shouldFetchDockerV2DigestSHA256(true)
            .dockerConnectorDTO(DockerConnectorDTO.builder()
                                    .dockerRegistryUrl("URL")
                                    .auth(DockerAuthenticationDTO.builder()
                                              .credentials(DockerUserNamePasswordDTO.builder()
                                                               .username("username")
                                                               .passwordRef(SecretRefData.builder().build())
                                                               .build())
                                              .build())
                                    .build())
            .build();

    ArtifactTaskExecutionResponse lastSuccessfulBuild = dockerArtifactService.getLastSuccessfulBuild(sourceAttributes);
    assertThat(lastSuccessfulBuild).isNotNull();
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().size()).isEqualTo(1);
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().get(0))
        .isInstanceOf(DockerArtifactDelegateResponse.class);
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().get(0).getBuildDetails().getMetadata().get(
                   ArtifactMetadataKeys.SHAV2))
        .isEqualTo("V2_DIGEST");
    DockerArtifactDelegateResponse attributes =
        (DockerArtifactDelegateResponse) lastSuccessfulBuild.getArtifactDelegateResponses().get(0);
    assertThat(attributes.getImagePath()).isEqualTo(sourceAttributes.getImagePath());
    assertThat(attributes.getTag()).isEqualTo(sourceAttributes.getTag());
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildFromRegex() {
    BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder().number("tag").build();
    DockerInternalConfig dockerInternalConfig =
        DockerInternalConfig.builder().dockerRegistryUrl("URL").username("username").build();
    doReturn(buildDetailsInternal)
        .when(dockerRegistryService)
        .getLastSuccessfulBuildFromRegex(dockerInternalConfig, "imagePath", "tagRegex");
    DockerArtifactDelegateRequest sourceAttributes =
        DockerArtifactDelegateRequest.builder()
            .imagePath("imagePath")
            .tagRegex("tagRegex")
            .dockerConnectorDTO(DockerConnectorDTO.builder()
                                    .dockerRegistryUrl("URL")
                                    .auth(DockerAuthenticationDTO.builder()
                                              .credentials(DockerUserNamePasswordDTO.builder()
                                                               .username("username")
                                                               .passwordRef(SecretRefData.builder().build())
                                                               .build())
                                              .build())
                                    .build())
            .build();
    ArtifactTaskExecutionResponse lastSuccessfulBuild = dockerArtifactService.getLastSuccessfulBuild(sourceAttributes);
    assertThat(lastSuccessfulBuild).isNotNull();
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().size()).isEqualTo(1);
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().get(0))
        .isInstanceOf(DockerArtifactDelegateResponse.class);
    DockerArtifactDelegateResponse attributes =
        (DockerArtifactDelegateResponse) lastSuccessfulBuild.getArtifactDelegateResponses().get(0);
    assertThat(attributes.getImagePath()).isEqualTo(sourceAttributes.getImagePath());
    assertThat(attributes.getTag()).isEqualTo("tag");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetBuilds() {
    BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder().number("tag").build();
    DockerInternalConfig dockerInternalConfig =
        DockerInternalConfig.builder().dockerRegistryUrl("URL").username("username").build();
    doReturn(Lists.newArrayList(buildDetailsInternal))
        .when(dockerRegistryService)
        .getBuilds(dockerInternalConfig, "imagePath", MAX_NO_OF_TAGS_PER_IMAGE, "tagRegex");
    DockerArtifactDelegateRequest sourceAttributes =
        DockerArtifactDelegateRequest.builder()
            .imagePath("imagePath")
            .tagRegex("tagRegex")
            .dockerConnectorDTO(DockerConnectorDTO.builder()
                                    .dockerRegistryUrl("URL")
                                    .auth(DockerAuthenticationDTO.builder()
                                              .credentials(DockerUserNamePasswordDTO.builder()
                                                               .username("username")
                                                               .passwordRef(SecretRefData.builder().build())
                                                               .build())
                                              .build())
                                    .build())
            .build();
    ArtifactTaskExecutionResponse lastSuccessfulBuild = dockerArtifactService.getBuilds(sourceAttributes);
    assertThat(lastSuccessfulBuild).isNotNull();
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().size()).isEqualTo(1);
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().get(0))
        .isInstanceOf(DockerArtifactDelegateResponse.class);
    DockerArtifactDelegateResponse attributes =
        (DockerArtifactDelegateResponse) lastSuccessfulBuild.getArtifactDelegateResponses().get(0);
    assertThat(attributes.getImagePath()).isEqualTo(sourceAttributes.getImagePath());
    assertThat(attributes.getTag()).isEqualTo("tag");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetLabels() {
    BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder().number("tag").build();
    DockerInternalConfig dockerInternalConfig =
        DockerInternalConfig.builder().dockerRegistryUrl("URL").username("username").build();
    doReturn(Lists.newArrayList(new HashMap()))
        .when(dockerRegistryService)
        .getLabels(dockerInternalConfig, "imagePath", Lists.newArrayList("tag1"));
    DockerArtifactDelegateRequest sourceAttributes =
        DockerArtifactDelegateRequest.builder()
            .imagePath("imagePath")
            .tagsList(Lists.newArrayList("tag1"))
            .dockerConnectorDTO(DockerConnectorDTO.builder()
                                    .dockerRegistryUrl("URL")
                                    .auth(DockerAuthenticationDTO.builder()
                                              .credentials(DockerUserNamePasswordDTO.builder()
                                                               .username("username")
                                                               .passwordRef(SecretRefData.builder().build())
                                                               .build())
                                              .build())
                                    .build())
            .build();
    ArtifactTaskExecutionResponse lastSuccessfulBuild = dockerArtifactService.getLabels(sourceAttributes);
    assertThat(lastSuccessfulBuild).isNotNull();
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().size()).isEqualTo(1);
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().get(0))
        .isInstanceOf(DockerArtifactDelegateResponse.class);
    DockerArtifactDelegateResponse attributes =
        (DockerArtifactDelegateResponse) lastSuccessfulBuild.getArtifactDelegateResponses().get(0);
    assertThat(attributes.getImagePath()).isEqualTo(sourceAttributes.getImagePath());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testValidateArtifactServer() {
    BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder().number("tag").build();
    DockerInternalConfig dockerInternalConfig =
        DockerInternalConfig.builder().dockerRegistryUrl("URL").username("username").build();
    doReturn(true).when(dockerRegistryService).validateCredentials(dockerInternalConfig);
    DockerArtifactDelegateRequest sourceAttributes =
        DockerArtifactDelegateRequest.builder()
            .imagePath("imagePath")
            .tagsList(Lists.newArrayList("tag1"))
            .dockerConnectorDTO(DockerConnectorDTO.builder()
                                    .dockerRegistryUrl("URL")
                                    .auth(DockerAuthenticationDTO.builder()
                                              .credentials(DockerUserNamePasswordDTO.builder()
                                                               .username("username")
                                                               .passwordRef(SecretRefData.builder().build())
                                                               .build())
                                              .build())
                                    .build())
            .build();
    ArtifactTaskExecutionResponse lastSuccessfulBuild = dockerArtifactService.validateArtifactServer(sourceAttributes);
    assertThat(lastSuccessfulBuild).isNotNull();
    assertThat(lastSuccessfulBuild.isArtifactServerValid()).isTrue();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testValidateArtifactImage() {
    BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder().number("tag").build();
    DockerInternalConfig dockerInternalConfig =
        DockerInternalConfig.builder().dockerRegistryUrl("URL").username("username").build();
    doReturn(true).when(dockerRegistryService).verifyImageName(dockerInternalConfig, "imagePath");
    DockerArtifactDelegateRequest sourceAttributes =
        DockerArtifactDelegateRequest.builder()
            .imagePath("imagePath")
            .tagsList(Lists.newArrayList("tag1"))
            .dockerConnectorDTO(DockerConnectorDTO.builder()
                                    .dockerRegistryUrl("URL")
                                    .auth(DockerAuthenticationDTO.builder()
                                              .credentials(DockerUserNamePasswordDTO.builder()
                                                               .username("username")
                                                               .passwordRef(SecretRefData.builder().build())
                                                               .build())
                                              .build())
                                    .build())
            .build();
    ArtifactTaskExecutionResponse lastSuccessfulBuild = dockerArtifactService.validateArtifactImage(sourceAttributes);
    assertThat(lastSuccessfulBuild).isNotNull();
    assertThat(lastSuccessfulBuild.isArtifactSourceValid()).isTrue();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testIsRegex() {
    DockerArtifactDelegateRequest sourceAttributes =
        DockerArtifactDelegateRequest.builder().imagePath("imagePath").tag("tag").tagRegex("tagRegex").build();
    boolean regex = dockerArtifactService.isRegex(sourceAttributes);
    assertThat(regex).isTrue();
    sourceAttributes = DockerArtifactDelegateRequest.builder().imagePath("imagePath").tagRegex("tagRegex").build();
    regex = dockerArtifactService.isRegex(sourceAttributes);
    assertThat(regex).isTrue();
    sourceAttributes = DockerArtifactDelegateRequest.builder().imagePath("imagePath").tag("tag").build();
    regex = dockerArtifactService.isRegex(sourceAttributes);
    assertThat(regex).isFalse();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetLabelForBuild() {
    BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder().number("tag").build();
    DockerInternalConfig dockerInternalConfig =
        DockerInternalConfig.builder().dockerRegistryUrl("URL").username("username").build();
    doReturn(buildDetailsInternal)
        .when(dockerRegistryService)
        .verifyBuildNumber(dockerInternalConfig, "imagePath", "tag");
    doReturn(ARTIFACT_META_INFO)
        .when(dockerRegistryService)
        .getArtifactMetaInfo(dockerInternalConfig, "imagePath", "tag", true);
    DockerArtifactDelegateRequest sourceAttributes =
        DockerArtifactDelegateRequest.builder()
            .imagePath("imagePath")
            .tag("tag")
            .dockerConnectorDTO(DockerConnectorDTO.builder()
                                    .dockerRegistryUrl("URL")
                                    .auth(DockerAuthenticationDTO.builder()
                                              .credentials(DockerUserNamePasswordDTO.builder()
                                                               .username("username")
                                                               .passwordRef(SecretRefData.builder().build())
                                                               .build())
                                              .build())
                                    .build())
            .build();
    ArtifactTaskExecutionResponse lastSuccessfulBuild = dockerArtifactService.getLastSuccessfulBuild(sourceAttributes);
    assertThat(lastSuccessfulBuild).isNotNull();
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().size()).isEqualTo(1);
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().get(0))
        .isInstanceOf(DockerArtifactDelegateResponse.class);
    DockerArtifactDelegateResponse attributes =
        (DockerArtifactDelegateResponse) lastSuccessfulBuild.getArtifactDelegateResponses().get(0);
    assertThat(attributes.getImagePath()).isEqualTo(sourceAttributes.getImagePath());
    assertThat(attributes.getTag()).isEqualTo(sourceAttributes.getTag());
    assertThat(attributes.getLabel()).isSameAs(LABELS);
    Map<String, String> metadata = attributes.getBuildDetails().getMetadata();
    assertThat(metadata.get(ArtifactMetadataKeys.SHA)).isEqualTo(SHA);
    assertThat(metadata.get(ArtifactMetadataKeys.SHAV2)).isEqualTo(SHA_V2);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetLabelForNull() {
    BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder().number("tag").build();
    DockerInternalConfig dockerInternalConfig =
        DockerInternalConfig.builder().dockerRegistryUrl("URL").username("username").build();
    doReturn(buildDetailsInternal)
        .when(dockerRegistryService)
        .verifyBuildNumber(dockerInternalConfig, "imagePath", "tag");
    doReturn(ArtifactMetaInfo.builder().sha(SHA).shaV2(SHA_V2).build())
        .when(dockerRegistryService)
        .getArtifactMetaInfo(dockerInternalConfig, "imagePath", "tag", true);
    DockerArtifactDelegateRequest sourceAttributes =
        DockerArtifactDelegateRequest.builder()
            .imagePath("imagePath")
            .tag("tag")
            .dockerConnectorDTO(DockerConnectorDTO.builder()
                                    .dockerRegistryUrl("URL")
                                    .auth(DockerAuthenticationDTO.builder()
                                              .credentials(DockerUserNamePasswordDTO.builder()
                                                               .username("username")
                                                               .passwordRef(SecretRefData.builder().build())
                                                               .build())
                                              .build())
                                    .build())
            .build();
    ArtifactTaskExecutionResponse lastSuccessfulBuild = dockerArtifactService.getLastSuccessfulBuild(sourceAttributes);
    assertThat(lastSuccessfulBuild).isNotNull();
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().size()).isEqualTo(1);
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().get(0))
        .isInstanceOf(DockerArtifactDelegateResponse.class);
    DockerArtifactDelegateResponse attributes =
        (DockerArtifactDelegateResponse) lastSuccessfulBuild.getArtifactDelegateResponses().get(0);
    assertThat(attributes.getImagePath()).isEqualTo(sourceAttributes.getImagePath());
    assertThat(attributes.getTag()).isEqualTo(sourceAttributes.getTag());
    assertThat(attributes.getLabel()).isNull();
    Map<String, String> metadata = attributes.getBuildDetails().getMetadata();
    assertThat(metadata.get(ArtifactMetadataKeys.SHA)).isEqualTo(SHA);
    assertThat(metadata.get(ArtifactMetadataKeys.SHAV2)).isEqualTo(SHA_V2);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetLabelForRegex() {
    BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder().number("tag").build();
    DockerInternalConfig dockerInternalConfig =
        DockerInternalConfig.builder().dockerRegistryUrl("URL").username("username").build();
    doReturn(buildDetailsInternal)
        .when(dockerRegistryService)
        .getLastSuccessfulBuildFromRegex(dockerInternalConfig, "imagePath", "tagRegex");
    doReturn(ARTIFACT_META_INFO)
        .when(dockerRegistryService)
        .getArtifactMetaInfo(dockerInternalConfig, "imagePath", "tag", true);
    DockerArtifactDelegateRequest sourceAttributes =
        DockerArtifactDelegateRequest.builder()
            .imagePath("imagePath")
            .tagRegex("tagRegex")
            .dockerConnectorDTO(DockerConnectorDTO.builder()
                                    .dockerRegistryUrl("URL")
                                    .auth(DockerAuthenticationDTO.builder()
                                              .credentials(DockerUserNamePasswordDTO.builder()
                                                               .username("username")
                                                               .passwordRef(SecretRefData.builder().build())
                                                               .build())
                                              .build())
                                    .build())
            .build();
    ArtifactTaskExecutionResponse lastSuccessfulBuild = dockerArtifactService.getLastSuccessfulBuild(sourceAttributes);
    assertThat(lastSuccessfulBuild).isNotNull();
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().size()).isEqualTo(1);
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().get(0))
        .isInstanceOf(DockerArtifactDelegateResponse.class);
    DockerArtifactDelegateResponse attributes =
        (DockerArtifactDelegateResponse) lastSuccessfulBuild.getArtifactDelegateResponses().get(0);
    assertThat(attributes.getImagePath()).isEqualTo(sourceAttributes.getImagePath());
    assertThat(attributes.getTag()).isEqualTo("tag");
    assertThat(attributes.getLabel()).isSameAs(LABELS);
    Map<String, String> metadata = attributes.getBuildDetails().getMetadata();
    assertThat(metadata.get(ArtifactMetadataKeys.SHA)).isEqualTo(SHA);
    assertThat(metadata.get(ArtifactMetadataKeys.SHAV2)).isEqualTo(SHA_V2);
  }
}
