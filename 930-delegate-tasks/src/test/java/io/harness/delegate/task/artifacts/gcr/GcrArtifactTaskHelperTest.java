/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.gcr;

import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.mappers.GcrRequestResponseMapper;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.encryption.SecretRefData;
import io.harness.eraro.ErrorCode;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class GcrArtifactTaskHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @InjectMocks GcrArtifactTaskHelper gcrArtifactTaskHelper;
  private static final String TEST_PROJECT_ID = "project-a";
  private static final String TEST_ACCESS_TOKEN = String.format("{\"access_token\": \"%s\"}", TEST_PROJECT_ID);
  private final char[] serviceAccountKeyFileContent =
      String.format("{\"project_id\": \"%s\"}", TEST_PROJECT_ID).toCharArray();

  @Mock GcrArtifactTaskHandler gcrArtifactTaskHandler;
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponseGetLastSuccessfulBuild() {
    doNothing().when(gcrArtifactTaskHandler).decryptRequestDTOs(any());

    GcrArtifactDelegateRequest garDelegateRequest =
        GcrArtifactDelegateRequest.builder()
            .gcpConnectorDTO(
                GcpConnectorDTO.builder()
                    .credential(
                        GcpConnectorCredentialDTO.builder()
                            .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                            .config(
                                GcpManualDetailsDTO.builder()
                                    .secretKeyRef(
                                        SecretRefData.builder().decryptedValue(serviceAccountKeyFileContent).build())
                                    .build())
                            .build())
                    .build())
            .tagRegex("v")
            .registryHostname("registryHostname")
            .imagePath("imagePath")
            .build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(garDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD)
                                                        .build();
    BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder().build();

    List<GcrArtifactDelegateResponse> artifactDelegateResponse =
        Collections.singletonList(GcrRequestResponseMapper.toGcrResponse(buildDetailsInternal, garDelegateRequest));

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        ArtifactTaskExecutionResponse.builder().artifactDelegateResponses(artifactDelegateResponse).build();

    when(gcrArtifactTaskHandler.getLastSuccessfulBuild(garDelegateRequest)).thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        gcrArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(gcrArtifactTaskHandler).decryptRequestDTOs(any());
    verify(gcrArtifactTaskHandler).getLastSuccessfulBuild(garDelegateRequest);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponseGetBuilds() {
    doNothing().when(gcrArtifactTaskHandler).decryptRequestDTOs(any());

    GcrArtifactDelegateRequest garDelegateRequest =
        GcrArtifactDelegateRequest.builder()
            .gcpConnectorDTO(
                GcpConnectorDTO.builder()
                    .credential(
                        GcpConnectorCredentialDTO.builder()
                            .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                            .config(
                                GcpManualDetailsDTO.builder()
                                    .secretKeyRef(
                                        SecretRefData.builder().decryptedValue(serviceAccountKeyFileContent).build())
                                    .build())
                            .build())
                    .build())
            .tag("v")
            .registryHostname("registryHostname")
            .imagePath("imagePath")
            .build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(garDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_BUILDS)
                                                        .build();
    BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder().build();

    List<GcrArtifactDelegateResponse> artifactDelegateResponse =
        Collections.singletonList(GcrRequestResponseMapper.toGcrResponse(buildDetailsInternal, garDelegateRequest));

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        ArtifactTaskExecutionResponse.builder().artifactDelegateResponses(artifactDelegateResponse).build();

    when(gcrArtifactTaskHandler.getBuilds(garDelegateRequest)).thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        gcrArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(gcrArtifactTaskHandler).decryptRequestDTOs(any());
    verify(gcrArtifactTaskHandler).getBuilds(garDelegateRequest);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponsevalidateArtifactServer() {
    doNothing().when(gcrArtifactTaskHandler).decryptRequestDTOs(any());

    GcrArtifactDelegateRequest garDelegateRequest =
        GcrArtifactDelegateRequest.builder()
            .gcpConnectorDTO(
                GcpConnectorDTO.builder()
                    .credential(
                        GcpConnectorCredentialDTO.builder()
                            .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                            .config(
                                GcpManualDetailsDTO.builder()
                                    .secretKeyRef(
                                        SecretRefData.builder().decryptedValue(serviceAccountKeyFileContent).build())
                                    .build())
                            .build())
                    .build())
            .tag("v")
            .registryHostname("registryHostname")
            .imagePath("imagePath")
            .build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(garDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.VALIDATE_ARTIFACT_SERVER)
                                                        .build();
    BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder().build();

    List<GcrArtifactDelegateResponse> artifactDelegateResponse =
        Collections.singletonList(GcrRequestResponseMapper.toGcrResponse(buildDetailsInternal, garDelegateRequest));

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        ArtifactTaskExecutionResponse.builder().artifactDelegateResponses(artifactDelegateResponse).build();

    when(gcrArtifactTaskHandler.validateArtifactServer(garDelegateRequest)).thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        gcrArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(gcrArtifactTaskHandler).decryptRequestDTOs(any());
    verify(gcrArtifactTaskHandler).validateArtifactServer(garDelegateRequest);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponsevalidateArtifactImage() {
    doNothing().when(gcrArtifactTaskHandler).decryptRequestDTOs(any());

    GcrArtifactDelegateRequest garDelegateRequest =
        GcrArtifactDelegateRequest.builder()
            .gcpConnectorDTO(
                GcpConnectorDTO.builder()
                    .credential(
                        GcpConnectorCredentialDTO.builder()
                            .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                            .config(
                                GcpManualDetailsDTO.builder()
                                    .secretKeyRef(
                                        SecretRefData.builder().decryptedValue(serviceAccountKeyFileContent).build())
                                    .build())
                            .build())
                    .build())
            .tag("v")
            .registryHostname("registryHostname")
            .imagePath("imagePath")
            .build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(garDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.VALIDATE_ARTIFACT_SOURCE)
                                                        .build();
    BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder().build();

    List<GcrArtifactDelegateResponse> artifactDelegateResponse =
        Collections.singletonList(GcrRequestResponseMapper.toGcrResponse(buildDetailsInternal, garDelegateRequest));

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        ArtifactTaskExecutionResponse.builder().artifactDelegateResponses(artifactDelegateResponse).build();

    when(gcrArtifactTaskHandler.validateArtifactImage(garDelegateRequest)).thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        gcrArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(gcrArtifactTaskHandler).decryptRequestDTOs(any());
    verify(gcrArtifactTaskHandler).validateArtifactImage(garDelegateRequest);
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testDefault() {
    doNothing().when(gcrArtifactTaskHandler).decryptRequestDTOs(any());

    GcrArtifactDelegateRequest garDelegateRequest =
        GcrArtifactDelegateRequest.builder()
            .gcpConnectorDTO(
                GcpConnectorDTO.builder()
                    .credential(
                        GcpConnectorCredentialDTO.builder()
                            .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                            .config(
                                GcpManualDetailsDTO.builder()
                                    .secretKeyRef(
                                        SecretRefData.builder().decryptedValue(serviceAccountKeyFileContent).build())
                                    .build())
                            .build())
                    .build())
            .tag("v")
            .registryHostname("registryHostname")
            .imagePath("imagePath")
            .build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(garDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.JENKINS_BUILD)
                                                        .build();

    ArtifactTaskResponse artifactTaskResponse =
        gcrArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getCommandExecutionStatus()).isEqualTo(FAILURE);
    assertThat(artifactTaskResponse.getErrorCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT);
    verify(gcrArtifactTaskHandler).decryptRequestDTOs(any());
  }
}
