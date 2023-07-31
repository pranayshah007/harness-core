/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.bamboo;

import static io.harness.rule.OwnerRule.ABHISHEK;
import static io.harness.rule.OwnerRule.SHIVAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.bamboo.BambooAuthType;
import io.harness.delegate.beans.connector.bamboo.BambooAuthenticationDTO;
import io.harness.delegate.beans.connector.bamboo.BambooConnectorDTO;
import io.harness.delegate.task.artifacts.mappers.BambooRequestResponseMapper;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.exception.HintException;
import io.harness.rule.Owner;

import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.service.intfc.BambooBuildService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

public class BambooArtifactTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Spy @InjectMocks BambooArtifactTaskHandler bambooArtifactTaskHandler;
  @Mock BambooBuildService bambooBuildService;

  private static final String PLAN_KEY = "plan";
  private static final String VERSION_NOT_FOUND =
      "Check if the version exist & check if the right connector chosen for fetching the build.";
  private static final String INVALID_REGEX = "Check if the regex is correct";
  private static final List<String> ARTIFACT_PATH_LIST = Collections.singletonList("artifactPath");

  private static final ArtifactStreamAttributes ARTIFACT_STREAM_ATTRIBUTES =
      ArtifactStreamAttributes.builder()
          .jobName(PLAN_KEY)
          .artifactPaths(ARTIFACT_PATH_LIST)
          .artifactStreamType(ArtifactStreamType.BAMBOO.name())
          .build();

  private static final BambooConnectorDTO BAMBOO_CONNECTOR_DTO =
      BambooConnectorDTO.builder()
          .bambooUrl("https://bamboo.com")
          .auth(BambooAuthenticationDTO.builder().authType(BambooAuthType.USER_PASSWORD).build())
          .build();

  private static final List<BuildDetails> BUILD_DETAILS_LIST = List.of(BuildDetails.Builder.aBuildDetails()
                                                                           .withBuildUrl("https://bamboo.com/test")
                                                                           .withArtifactPath("artifactPath")
                                                                           .withNumber("45")
                                                                           .build(),
      BuildDetails.Builder.aBuildDetails()
          .withBuildUrl("https://bamboo.com/test")
          .withArtifactPath("artifactPath")
          .withNumber("455")
          .build(),
      BuildDetails.Builder.aBuildDetails()
          .withBuildUrl("https://bamboo.com/test")
          .withArtifactPath("artifactPath")
          .withNumber("457")
          .build(),
      BuildDetails.Builder.aBuildDetails()
          .withBuildUrl("https://bamboo.com/test")
          .withArtifactPath("artifactPath")
          .withNumber("475")
          .build());

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void validateArtifactServerTest() {
    doNothing().when(bambooArtifactTaskHandler).decryptRequestDTOs(any());
    BambooConnectorDTO bambooConnectorDTO =
        BambooConnectorDTO.builder()
            .bambooUrl("https://bamboo.com")
            .auth(BambooAuthenticationDTO.builder().authType(BambooAuthType.USER_PASSWORD).build())
            .build();
    BambooArtifactDelegateRequest bambooArtifactDelegateRequest =
        BambooArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .encryptedDataDetails(Collections.EMPTY_LIST)
            .planKey("plan")
            .bambooConnectorDTO(bambooConnectorDTO)
            .buildNumber("45")
            .build();
    when(bambooBuildService.validateArtifactServer(
             BambooRequestResponseMapper.toBambooConfig(bambooArtifactDelegateRequest),
             bambooArtifactDelegateRequest.getEncryptedDataDetails()))
        .thenReturn(true);

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        bambooArtifactTaskHandler.validateArtifactServer(bambooArtifactDelegateRequest);
    assertThat(artifactTaskExecutionResponse).isNotNull();
    assertThat(artifactTaskExecutionResponse.isArtifactServerValid()).isEqualTo(true);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void getBuildsTest() {
    doNothing().when(bambooArtifactTaskHandler).decryptRequestDTOs(any());
    BambooConnectorDTO bambooConnectorDTO =
        BambooConnectorDTO.builder()
            .bambooUrl("https://bamboo.com")
            .auth(BambooAuthenticationDTO.builder().authType(BambooAuthType.USER_PASSWORD).build())
            .build();
    BambooArtifactDelegateRequest bambooArtifactDelegateRequest =
        BambooArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .encryptedDataDetails(Collections.EMPTY_LIST)
            .planKey("plan")
            .bambooConnectorDTO(bambooConnectorDTO)
            .buildNumber("45")
            .build();
    ArtifactStreamAttributes artifactStreamAttributes =
        ArtifactStreamAttributes.builder()
            .jobName(bambooArtifactDelegateRequest.getPlanKey())
            .artifactPaths(bambooArtifactDelegateRequest.getArtifactPaths())
            .artifactStreamType(ArtifactStreamType.BAMBOO.name())
            .build();
    when(bambooBuildService.getBuilds(null, artifactStreamAttributes,
             BambooRequestResponseMapper.toBambooConfig(bambooArtifactDelegateRequest),
             bambooArtifactDelegateRequest.getEncryptedDataDetails()))
        .thenReturn(Collections.singletonList(BuildDetails.Builder.aBuildDetails()
                                                  .withBuildUrl("https://bamboo.com/test")
                                                  .withArtifactPath("artifactPath")
                                                  .build()));

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        bambooArtifactTaskHandler.getBuilds(bambooArtifactDelegateRequest);
    assertThat(artifactTaskExecutionResponse).isNotNull();
    assertThat(artifactTaskExecutionResponse.getBuildDetails()).isNotNull();
    assertThat(artifactTaskExecutionResponse.getBuildDetails().get(0).getBuildUrl())
        .isEqualTo("https://bamboo.com/test");
    assertThat(artifactTaskExecutionResponse.getBuildDetails().get(0).getArtifactPath()).isEqualTo("artifactPath");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void getLastSuccessfulBuildTest_FixedValue() {
    doNothing().when(bambooArtifactTaskHandler).decryptRequestDTOs(any());
    BambooArtifactDelegateRequest bambooArtifactDelegateRequest = BambooArtifactDelegateRequest.builder()
                                                                      .artifactPaths(ARTIFACT_PATH_LIST)
                                                                      .encryptedDataDetails(Collections.EMPTY_LIST)
                                                                      .planKey(PLAN_KEY)
                                                                      .bambooConnectorDTO(BAMBOO_CONNECTOR_DTO)
                                                                      .buildNumber("45")
                                                                      .build();

    when(bambooBuildService.getBuilds(null, ARTIFACT_STREAM_ATTRIBUTES,
             BambooRequestResponseMapper.toBambooConfig(bambooArtifactDelegateRequest),
             bambooArtifactDelegateRequest.getEncryptedDataDetails(), Integer.MAX_VALUE))
        .thenReturn(BUILD_DETAILS_LIST);

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        bambooArtifactTaskHandler.getLastSuccessfulBuild(bambooArtifactDelegateRequest);
    assertThat(artifactTaskExecutionResponse).isNotNull();
    assertThat(artifactTaskExecutionResponse.getBuildDetails()).isNotNull();
    assertThat(artifactTaskExecutionResponse.getBuildDetails().get(0).getBuildUrl())
        .isEqualTo("https://bamboo.com/test");
    assertThat(artifactTaskExecutionResponse.getBuildDetails().get(0).getArtifactPath()).isEqualTo("artifactPath");
    assertThat(artifactTaskExecutionResponse.getBuildDetails().get(0).getNumber()).isEqualTo("45");
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void getLastSuccessfulBuildTest_AllRegex() {
    doNothing().when(bambooArtifactTaskHandler).decryptRequestDTOs(any());
    BambooArtifactDelegateRequest bambooArtifactDelegateRequest = BambooArtifactDelegateRequest.builder()
                                                                      .artifactPaths(ARTIFACT_PATH_LIST)
                                                                      .encryptedDataDetails(Collections.EMPTY_LIST)
                                                                      .planKey(PLAN_KEY)
                                                                      .bambooConnectorDTO(BAMBOO_CONNECTOR_DTO)
                                                                      .buildNumber(".*?")
                                                                      .buildRegex(".*?")
                                                                      .build();

    when(bambooBuildService.getBuilds(null, ARTIFACT_STREAM_ATTRIBUTES,
             BambooRequestResponseMapper.toBambooConfig(bambooArtifactDelegateRequest),
             bambooArtifactDelegateRequest.getEncryptedDataDetails(), Integer.MAX_VALUE))
        .thenReturn(BUILD_DETAILS_LIST);

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        bambooArtifactTaskHandler.getLastSuccessfulBuild(bambooArtifactDelegateRequest);
    assertThat(artifactTaskExecutionResponse).isNotNull();
    assertThat(artifactTaskExecutionResponse.getBuildDetails()).isNotNull();
    assertThat(artifactTaskExecutionResponse.getBuildDetails().get(0).getBuildUrl())
        .isEqualTo("https://bamboo.com/test");
    assertThat(artifactTaskExecutionResponse.getBuildDetails().get(0).getArtifactPath()).isEqualTo("artifactPath");
    assertThat(artifactTaskExecutionResponse.getBuildDetails().get(0).getNumber()).isEqualTo("475");
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void getLastSuccessfulBuildTest_ValueRegex() {
    doNothing().when(bambooArtifactTaskHandler).decryptRequestDTOs(any());
    BambooArtifactDelegateRequest bambooArtifactDelegateRequest = BambooArtifactDelegateRequest.builder()
                                                                      .artifactPaths(ARTIFACT_PATH_LIST)
                                                                      .encryptedDataDetails(Collections.EMPTY_LIST)
                                                                      .planKey(PLAN_KEY)
                                                                      .bambooConnectorDTO(BAMBOO_CONNECTOR_DTO)
                                                                      .buildNumber("45")
                                                                      .buildRegex("45")
                                                                      .build();

    when(bambooBuildService.getBuilds(null, ARTIFACT_STREAM_ATTRIBUTES,
             BambooRequestResponseMapper.toBambooConfig(bambooArtifactDelegateRequest),
             bambooArtifactDelegateRequest.getEncryptedDataDetails(), Integer.MAX_VALUE))
        .thenReturn(BUILD_DETAILS_LIST);

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        bambooArtifactTaskHandler.getLastSuccessfulBuild(bambooArtifactDelegateRequest);
    assertThat(artifactTaskExecutionResponse).isNotNull();
    assertThat(artifactTaskExecutionResponse.getBuildDetails()).isNotNull();
    assertThat(artifactTaskExecutionResponse.getBuildDetails().get(0).getBuildUrl())
        .isEqualTo("https://bamboo.com/test");
    assertThat(artifactTaskExecutionResponse.getBuildDetails().get(0).getArtifactPath()).isEqualTo("artifactPath");
    assertThat(artifactTaskExecutionResponse.getBuildDetails().get(0).getNumber()).isEqualTo("457");
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void getLastSuccessfulBuildTest_EmptyBuildList() {
    doNothing().when(bambooArtifactTaskHandler).decryptRequestDTOs(any());
    BambooArtifactDelegateRequest bambooArtifactDelegateRequest = BambooArtifactDelegateRequest.builder()
                                                                      .artifactPaths(ARTIFACT_PATH_LIST)
                                                                      .encryptedDataDetails(Collections.EMPTY_LIST)
                                                                      .planKey(PLAN_KEY)
                                                                      .bambooConnectorDTO(BAMBOO_CONNECTOR_DTO)
                                                                      .buildNumber("45")
                                                                      .buildRegex("45")
                                                                      .build();

    when(bambooBuildService.getBuilds(null, ARTIFACT_STREAM_ATTRIBUTES,
             BambooRequestResponseMapper.toBambooConfig(bambooArtifactDelegateRequest),
             bambooArtifactDelegateRequest.getEncryptedDataDetails(), Integer.MAX_VALUE))
        .thenReturn(new ArrayList<>());

    assertThatThrownBy(() -> bambooArtifactTaskHandler.getLastSuccessfulBuild(bambooArtifactDelegateRequest))
        .isInstanceOf(HintException.class)
        .hasMessage(VERSION_NOT_FOUND);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void getLastSuccessfulBuildTest_TAGMismatch() {
    doNothing().when(bambooArtifactTaskHandler).decryptRequestDTOs(any());
    BambooArtifactDelegateRequest bambooArtifactDelegateRequest = BambooArtifactDelegateRequest.builder()
                                                                      .artifactPaths(ARTIFACT_PATH_LIST)
                                                                      .encryptedDataDetails(Collections.EMPTY_LIST)
                                                                      .planKey(PLAN_KEY)
                                                                      .bambooConnectorDTO(BAMBOO_CONNECTOR_DTO)
                                                                      .buildNumber("78")
                                                                      .build();

    when(bambooBuildService.getBuilds(null, ARTIFACT_STREAM_ATTRIBUTES,
             BambooRequestResponseMapper.toBambooConfig(bambooArtifactDelegateRequest),
             bambooArtifactDelegateRequest.getEncryptedDataDetails(), Integer.MAX_VALUE))
        .thenReturn(BUILD_DETAILS_LIST);

    assertThatThrownBy(() -> bambooArtifactTaskHandler.getLastSuccessfulBuild(bambooArtifactDelegateRequest))
        .isInstanceOf(HintException.class)
        .hasMessage(VERSION_NOT_FOUND);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void getLastSuccessfulBuildTest_RegexMismatch() {
    doNothing().when(bambooArtifactTaskHandler).decryptRequestDTOs(any());
    BambooArtifactDelegateRequest bambooArtifactDelegateRequest = BambooArtifactDelegateRequest.builder()
                                                                      .artifactPaths(ARTIFACT_PATH_LIST)
                                                                      .encryptedDataDetails(Collections.EMPTY_LIST)
                                                                      .planKey(PLAN_KEY)
                                                                      .bambooConnectorDTO(BAMBOO_CONNECTOR_DTO)
                                                                      .buildNumber("78")
                                                                      .buildRegex("78")
                                                                      .build();

    when(bambooBuildService.getBuilds(null, ARTIFACT_STREAM_ATTRIBUTES,
             BambooRequestResponseMapper.toBambooConfig(bambooArtifactDelegateRequest),
             bambooArtifactDelegateRequest.getEncryptedDataDetails(), Integer.MAX_VALUE))
        .thenReturn(BUILD_DETAILS_LIST);

    assertThatThrownBy(() -> bambooArtifactTaskHandler.getLastSuccessfulBuild(bambooArtifactDelegateRequest))
        .isInstanceOf(HintException.class)
        .hasMessage(VERSION_NOT_FOUND);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void getLastSuccessfulBuildTest_PatternException() {
    doNothing().when(bambooArtifactTaskHandler).decryptRequestDTOs(any());
    BambooArtifactDelegateRequest bambooArtifactDelegateRequest = BambooArtifactDelegateRequest.builder()
                                                                      .artifactPaths(ARTIFACT_PATH_LIST)
                                                                      .encryptedDataDetails(Collections.EMPTY_LIST)
                                                                      .planKey(PLAN_KEY)
                                                                      .bambooConnectorDTO(BAMBOO_CONNECTOR_DTO)
                                                                      .buildNumber("*")
                                                                      .buildRegex("*")
                                                                      .build();

    when(bambooBuildService.getBuilds(null, ARTIFACT_STREAM_ATTRIBUTES,
             BambooRequestResponseMapper.toBambooConfig(bambooArtifactDelegateRequest),
             bambooArtifactDelegateRequest.getEncryptedDataDetails(), Integer.MAX_VALUE))
        .thenReturn(BUILD_DETAILS_LIST);

    assertThatThrownBy(() -> bambooArtifactTaskHandler.getLastSuccessfulBuild(bambooArtifactDelegateRequest))
        .isInstanceOf(HintException.class)
        .hasMessage(INVALID_REGEX);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void getPlansTest() {
    doNothing().when(bambooArtifactTaskHandler).decryptRequestDTOs(any());
    BambooConnectorDTO bambooConnectorDTO =
        BambooConnectorDTO.builder()
            .bambooUrl("https://bamboo.com")
            .auth(BambooAuthenticationDTO.builder().authType(BambooAuthType.USER_PASSWORD).build())
            .build();
    BambooArtifactDelegateRequest bambooArtifactDelegateRequest =
        BambooArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .encryptedDataDetails(Collections.EMPTY_LIST)
            .planKey("plan")
            .bambooConnectorDTO(bambooConnectorDTO)
            .buildNumber("45")
            .build();
    when(bambooBuildService.getPlans(BambooRequestResponseMapper.toBambooConfig(bambooArtifactDelegateRequest),
             bambooArtifactDelegateRequest.getEncryptedDataDetails()))
        .thenReturn(Collections.singletonMap("AW-AW", "aws"));

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        bambooArtifactTaskHandler.getPlans(bambooArtifactDelegateRequest);
    assertThat(artifactTaskExecutionResponse).isNotNull();
    assertThat(artifactTaskExecutionResponse.getPlans()).isNotNull();
    assertThat(artifactTaskExecutionResponse.getPlans().get("AW-AW")).isEqualTo("aws");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void getArtifactPathsTest() {
    doNothing().when(bambooArtifactTaskHandler).decryptRequestDTOs(any());
    BambooConnectorDTO bambooConnectorDTO =
        BambooConnectorDTO.builder()
            .bambooUrl("https://bamboo.com")
            .auth(BambooAuthenticationDTO.builder().authType(BambooAuthType.USER_PASSWORD).build())
            .build();
    BambooArtifactDelegateRequest bambooArtifactDelegateRequest =
        BambooArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .encryptedDataDetails(Collections.EMPTY_LIST)
            .planKey("plan")
            .bambooConnectorDTO(bambooConnectorDTO)
            .buildNumber("45")
            .build();
    when(bambooBuildService.getArtifactPaths(bambooArtifactDelegateRequest.getPlanKey(), null,
             BambooRequestResponseMapper.toBambooConfig(bambooArtifactDelegateRequest),
             bambooArtifactDelegateRequest.getEncryptedDataDetails()))
        .thenReturn(Collections.singletonList("AWS"));

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        bambooArtifactTaskHandler.getArtifactPaths(bambooArtifactDelegateRequest);
    assertThat(artifactTaskExecutionResponse).isNotNull();
    assertThat(artifactTaskExecutionResponse.getArtifactPath()).isNotNull();
    assertThat(artifactTaskExecutionResponse.getArtifactPath().get(0)).isEqualTo("AWS");
  }
}
