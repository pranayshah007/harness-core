/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.ecr;

import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.mappers.ArtifactBuildDetailsMapper;
import io.harness.delegate.task.artifacts.mappers.EcrRequestResponseMapper;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDC)
public class EcrArtifactTaskHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private EcrArtifactTaskHandler ecrArtifactTaskHandler;

  @InjectMocks EcrArtifactTaskHelper ecrArtifactTaskHelper;
  private AwsConnectorDTO awsConnectorDTO =
      AwsConnectorDTO.builder()
          .credential(
              AwsCredentialDTO.builder()
                  .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                  .config(AwsManualConfigSpecDTO.builder()
                              .accessKey("access-key")
                              .secretKeyRef(SecretRefData.builder().decryptedValue("secret".toCharArray()).build())
                              .build())
                  .build())
          .build();

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldHandleGetImagesRequestSuccess() {
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        ArtifactTaskExecutionResponse.builder().artifactImages(Arrays.asList("nginx", "todolist")).build();
    EcrArtifactDelegateRequest attributes =
        EcrArtifactDelegateRequest.builder().awsConnectorDTO(awsConnectorDTO).build();
    ArtifactTaskParameters artifactTaskParameters =
        ArtifactTaskParameters.builder().artifactTaskType(ArtifactTaskType.GET_IMAGES).attributes(attributes).build();
    doNothing().when(ecrArtifactTaskHandler).decryptRequestDTOs(any());
    doReturn(artifactTaskExecutionResponse).when(ecrArtifactTaskHandler).getImages(eq(attributes));

    ArtifactTaskResponse response = ecrArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(ecrArtifactTaskHandler, times(1)).decryptRequestDTOs(any());
    verify(ecrArtifactTaskHandler, times(1)).getImages(eq(attributes));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldHandleGetImagesRequestFailure() {
    EcrArtifactDelegateRequest attributes =
        EcrArtifactDelegateRequest.builder().awsConnectorDTO(awsConnectorDTO).build();
    ArtifactTaskParameters artifactTaskParameters =
        ArtifactTaskParameters.builder().artifactTaskType(ArtifactTaskType.GET_IMAGES).attributes(attributes).build();
    doNothing().when(ecrArtifactTaskHandler).decryptRequestDTOs(any());
    doThrow(new InvalidRequestException("Region not specified")).when(ecrArtifactTaskHandler).getImages(eq(attributes));

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> ecrArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters))
        .withMessageContaining("Region not specified");

    verify(ecrArtifactTaskHandler, times(1)).decryptRequestDTOs(any());
    verify(ecrArtifactTaskHandler, times(1)).getImages(eq(attributes));
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void shouldHandleGetLastSuccesfulBuild() {
    EcrArtifactDelegateRequest attributes =
        EcrArtifactDelegateRequest.builder().awsConnectorDTO(awsConnectorDTO).imagePath("imagePath").build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .artifactTaskType(ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD)
                                                        .attributes(attributes)
                                                        .build();

    BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder().build();

    List<EcrArtifactDelegateResponse> artifactDelegateResponse =
        Collections.singletonList(EcrRequestResponseMapper.toEcrResponse(buildDetailsInternal, attributes));

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        ArtifactTaskExecutionResponse.builder().artifactDelegateResponses(artifactDelegateResponse).build();
    doNothing().when(ecrArtifactTaskHandler).decryptRequestDTOs(any());

    doReturn(artifactTaskExecutionResponse).when(ecrArtifactTaskHandler).getLastSuccessfulBuild(eq(attributes));

    ArtifactTaskResponse response = ecrArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);

    assertThat(response).isNotNull();

    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);

    assertThat(response.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(ecrArtifactTaskHandler, times(1)).decryptRequestDTOs(any());

    verify(ecrArtifactTaskHandler, times(1)).getLastSuccessfulBuild(eq(attributes));
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void shouldHandleLastSuccesfulBuildFailure() {
    EcrArtifactDelegateRequest attributes =
        EcrArtifactDelegateRequest.builder().awsConnectorDTO(awsConnectorDTO).imagePath("imagePath").build();

    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .artifactTaskType(ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD)
                                                        .attributes(attributes)
                                                        .build();

    doNothing().when(ecrArtifactTaskHandler).decryptRequestDTOs(any());

    doThrow(new InvalidRequestException("Last Successful Build Failure"))
        .when(ecrArtifactTaskHandler)
        .getLastSuccessfulBuild(eq(attributes));

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> ecrArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters))
        .withMessageContaining("Last Successful Build Failure");
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void shouldHandleGetBuilds() {
    EcrArtifactDelegateRequest attributes =
        EcrArtifactDelegateRequest.builder().awsConnectorDTO(awsConnectorDTO).imagePath("imagePath").build();
    ArtifactTaskParameters artifactTaskParameters =
        ArtifactTaskParameters.builder().artifactTaskType(ArtifactTaskType.GET_BUILDS).attributes(attributes).build();

    BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder().build();

    List<EcrArtifactDelegateResponse> artifactDelegateResponse =
        Collections.singletonList(EcrRequestResponseMapper.toEcrResponse(buildDetailsInternal, attributes));

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        ArtifactTaskExecutionResponse.builder().artifactDelegateResponses(artifactDelegateResponse).build();
    doNothing().when(ecrArtifactTaskHandler).decryptRequestDTOs(any());

    doReturn(artifactTaskExecutionResponse).when(ecrArtifactTaskHandler).getBuilds(eq(attributes));

    ArtifactTaskResponse response = ecrArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);

    assertThat(response).isNotNull();

    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);

    assertThat(response.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(ecrArtifactTaskHandler, times(1)).decryptRequestDTOs(any());

    verify(ecrArtifactTaskHandler, times(1)).getBuilds(eq(attributes));
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void shouldHandleGetBuildsFailure() {
    EcrArtifactDelegateRequest attributes =
        EcrArtifactDelegateRequest.builder().awsConnectorDTO(awsConnectorDTO).imagePath("imagePath").build();

    ArtifactTaskParameters artifactTaskParameters =
        ArtifactTaskParameters.builder().artifactTaskType(ArtifactTaskType.GET_BUILDS).attributes(attributes).build();

    doNothing().when(ecrArtifactTaskHandler).decryptRequestDTOs(any());

    doThrow(new InvalidRequestException("Get Builds Failure")).when(ecrArtifactTaskHandler).getBuilds(eq(attributes));

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> ecrArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters))
        .withMessageContaining("Get Builds Failure");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void validateartifactserverSuccesstest() {
    EcrArtifactDelegateRequest attributes =
        EcrArtifactDelegateRequest.builder().awsConnectorDTO(awsConnectorDTO).imagePath("imagePath").build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .artifactTaskType(ArtifactTaskType.VALIDATE_ARTIFACT_SERVER)
                                                        .attributes(attributes)
                                                        .build();

    BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder().build();

    EcrArtifactDelegateResponse ecrArtifactDelegateResponse =
        new EcrArtifactDelegateResponse(ArtifactBuildDetailsMapper.toBuildDetailsNG(buildDetailsInternal), null,
            "imagePath", "tag", "imageUrl", "authToken", null);

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        ArtifactTaskExecutionResponse.builder().artifactDelegateResponse(ecrArtifactDelegateResponse).build();
    doNothing().when(ecrArtifactTaskHandler).decryptRequestDTOs(any());

    doReturn(artifactTaskExecutionResponse).when(ecrArtifactTaskHandler).validateArtifactServer(eq(attributes));

    ArtifactTaskResponse response = ecrArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);

    assertThat(response).isNotNull();

    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);

    assertThat(response.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(ecrArtifactTaskHandler, times(1)).decryptRequestDTOs(any());

    verify(ecrArtifactTaskHandler, times(1)).validateArtifactServer(eq(attributes));
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void validateartifactserverFailuretest() {
    EcrArtifactDelegateRequest attributes =
        EcrArtifactDelegateRequest.builder().awsConnectorDTO(awsConnectorDTO).imagePath("imagePath").build();

    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .artifactTaskType(ArtifactTaskType.VALIDATE_ARTIFACT_SERVER)
                                                        .attributes(attributes)
                                                        .build();

    doNothing().when(ecrArtifactTaskHandler).decryptRequestDTOs(any());

    doThrow(new InvalidRequestException("Validate Artifact Server Failure"))
        .when(ecrArtifactTaskHandler)
        .validateArtifactServer(eq(attributes));

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> ecrArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters))
        .withMessageContaining("Validate Artifact Server Failure");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void validateartifactsourceSuccesstest() {
    EcrArtifactDelegateRequest attributes =
        EcrArtifactDelegateRequest.builder().awsConnectorDTO(awsConnectorDTO).imagePath("imagePath").build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .artifactTaskType(ArtifactTaskType.VALIDATE_ARTIFACT_SOURCE)
                                                        .attributes(attributes)
                                                        .build();

    BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder().build();

    EcrArtifactDelegateResponse ecrArtifactDelegateResponse =
        new EcrArtifactDelegateResponse(ArtifactBuildDetailsMapper.toBuildDetailsNG(buildDetailsInternal), null,
            "imagePath", "tag", "imageUrl", "authToken", null);

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        ArtifactTaskExecutionResponse.builder().artifactDelegateResponse(ecrArtifactDelegateResponse).build();
    doNothing().when(ecrArtifactTaskHandler).decryptRequestDTOs(any());

    doReturn(artifactTaskExecutionResponse).when(ecrArtifactTaskHandler).validateArtifactImage(eq(attributes));

    ArtifactTaskResponse response = ecrArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);

    assertThat(response).isNotNull();

    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);

    assertThat(response.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(ecrArtifactTaskHandler, times(1)).decryptRequestDTOs(any());

    verify(ecrArtifactTaskHandler, times(1)).validateArtifactImage(eq(attributes));
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void validateartifactimagesourcefailuretest() {
    EcrArtifactDelegateRequest attributes =
        EcrArtifactDelegateRequest.builder().awsConnectorDTO(awsConnectorDTO).imagePath("imagePath").build();

    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .artifactTaskType(ArtifactTaskType.VALIDATE_ARTIFACT_SOURCE)
                                                        .attributes(attributes)
                                                        .build();

    doNothing().when(ecrArtifactTaskHandler).decryptRequestDTOs(any());

    doThrow(new InvalidRequestException("Validate Artifact Source Failure"))
        .when(ecrArtifactTaskHandler)
        .validateArtifactImage(eq(attributes));

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> ecrArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters))
        .withMessageContaining("Validate Artifact Source Failure");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void getimageurlsuccesstest() {
    EcrArtifactDelegateRequest attributes =
        EcrArtifactDelegateRequest.builder().awsConnectorDTO(awsConnectorDTO).imagePath("imagePath").build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .artifactTaskType(ArtifactTaskType.GET_IMAGE_URL)
                                                        .attributes(attributes)
                                                        .build();

    BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder().build();

    EcrArtifactDelegateResponse ecrArtifactDelegateResponse =
        new EcrArtifactDelegateResponse(ArtifactBuildDetailsMapper.toBuildDetailsNG(buildDetailsInternal), null,
            "imagePath", "tag", "imageUrl", "authToken", null);

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        ArtifactTaskExecutionResponse.builder().artifactDelegateResponse(ecrArtifactDelegateResponse).build();
    doNothing().when(ecrArtifactTaskHandler).decryptRequestDTOs(any());

    doReturn(artifactTaskExecutionResponse).when(ecrArtifactTaskHandler).getEcrImageUrl(eq(attributes));

    ArtifactTaskResponse response = ecrArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);

    assertThat(response).isNotNull();

    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);

    assertThat(response.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(ecrArtifactTaskHandler, times(1)).decryptRequestDTOs(any());

    verify(ecrArtifactTaskHandler, times(1)).getEcrImageUrl(eq(attributes));
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void getimageurlfailuretest() {
    EcrArtifactDelegateRequest attributes =
        EcrArtifactDelegateRequest.builder().awsConnectorDTO(awsConnectorDTO).imagePath("imagePath").build();

    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .artifactTaskType(ArtifactTaskType.GET_IMAGE_URL)
                                                        .attributes(attributes)
                                                        .build();

    doNothing().when(ecrArtifactTaskHandler).decryptRequestDTOs(any());

    doThrow(new InvalidRequestException("GET IMAGE URL Failure"))
        .when(ecrArtifactTaskHandler)
        .getEcrImageUrl(eq(attributes));

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> ecrArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters))
        .withMessageContaining("GET IMAGE URL Failure");
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void getauthtokensuccesstest() {
    EcrArtifactDelegateRequest attributes =
        EcrArtifactDelegateRequest.builder().awsConnectorDTO(awsConnectorDTO).imagePath("imagePath").build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .artifactTaskType(ArtifactTaskType.GET_AUTH_TOKEN)
                                                        .attributes(attributes)
                                                        .build();

    BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder().build();

    EcrArtifactDelegateResponse ecrArtifactDelegateResponse =
        new EcrArtifactDelegateResponse(ArtifactBuildDetailsMapper.toBuildDetailsNG(buildDetailsInternal), null,
            "imagePath", "tag", "imageUrl", "authToken", null);

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        ArtifactTaskExecutionResponse.builder().artifactDelegateResponse(ecrArtifactDelegateResponse).build();
    doNothing().when(ecrArtifactTaskHandler).decryptRequestDTOs(any());

    doReturn(artifactTaskExecutionResponse).when(ecrArtifactTaskHandler).getAmazonEcrAuthToken(eq(attributes));

    ArtifactTaskResponse response = ecrArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);

    assertThat(response).isNotNull();

    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);

    assertThat(response.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(ecrArtifactTaskHandler, times(1)).decryptRequestDTOs(any());

    verify(ecrArtifactTaskHandler, times(1)).getAmazonEcrAuthToken(eq(attributes));
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void getauthtokenfailuretest() {
    EcrArtifactDelegateRequest attributes =
        EcrArtifactDelegateRequest.builder().awsConnectorDTO(awsConnectorDTO).imagePath("imagePath").build();

    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .artifactTaskType(ArtifactTaskType.GET_AUTH_TOKEN)
                                                        .attributes(attributes)
                                                        .build();

    doNothing().when(ecrArtifactTaskHandler).decryptRequestDTOs(any());

    doThrow(new InvalidRequestException("GET AUTH TOKEN Failure"))
        .when(ecrArtifactTaskHandler)
        .getAmazonEcrAuthToken(eq(attributes));

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> ecrArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters))
        .withMessageContaining("GET AUTH TOKEN Failure");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void defaultCasetest() {
    EcrArtifactDelegateRequest attributes =
        EcrArtifactDelegateRequest.builder().awsConnectorDTO(awsConnectorDTO).imagePath("imagePath").build();

    ArtifactTaskParameters artifactTaskParameters =
        ArtifactTaskParameters.builder().artifactTaskType(ArtifactTaskType.GET_LABELS).attributes(attributes).build();

    doNothing().when(ecrArtifactTaskHandler).decryptRequestDTOs(any());

    ArtifactTaskResponse response = ecrArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);

    assertThat(response).isNotNull();

    assertThat(response.getCommandExecutionStatus()).isEqualTo(FAILURE);
  }
}
