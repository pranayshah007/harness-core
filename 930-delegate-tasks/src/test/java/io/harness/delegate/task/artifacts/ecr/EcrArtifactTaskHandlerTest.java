/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.ecr;

import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.helpers.ext.ecr.EcrService;
import software.wings.service.impl.AwsApiHelperService;
import software.wings.service.impl.delegate.AwsEcrApiHelperServiceDelegate;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.PIPELINE)
public class EcrArtifactTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private EcrService ecrService;
  @Mock private AwsApiHelperService awsApiHelperService;
  @InjectMocks private EcrArtifactTaskHandler ecrArtifactTaskHandler;
  @Mock private AwsEcrApiHelperServiceDelegate awsEcrApiHelperServiceDelegate;
  @Mock SecretDecryptionService secretDecryptionService;

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldListEcrRegistries() {
    String nginx = "nginx";
    String todolist = "todolist";
    AwsConnectorDTO awsConnectorDTO =
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
    String region = "us-east-1";
    EcrArtifactDelegateRequest ecrArtifactDelegateRequest =
        EcrArtifactDelegateRequest.builder().region(region).awsConnectorDTO(awsConnectorDTO).build();
    on(ecrArtifactTaskHandler).set("awsNgConfigMapper", new AwsNgConfigMapper());
    doNothing().when(awsApiHelperService).attachCredentialsAndBackoffPolicy(any(), any());
    doReturn(Arrays.asList(nginx, todolist)).when(ecrService).listEcrRegistry(any(), any());
    ArtifactTaskExecutionResponse response = ecrArtifactTaskHandler.getImages(ecrArtifactDelegateRequest);
    assertThat(response.getArtifactImages()).isNotEmpty();
    assertThat(response.getArtifactImages()).containsExactly(nginx, todolist);

    verify(ecrService, times(1)).listEcrRegistry(any(), any());
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildTag() {
    EcrArtifactTaskHandler spyecrtaskhandler = spy(ecrArtifactTaskHandler);

    AwsConnectorDTO awsConnectorDTO =
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

    String region = "us-east-1";

    EcrArtifactDelegateRequest ecrArtifactDelegateRequest = EcrArtifactDelegateRequest.builder()
                                                                .region(region)
                                                                .awsConnectorDTO(awsConnectorDTO)
                                                                .imagePath("imagePath")
                                                                .tag("tag")
                                                                .build();
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();

    doReturn(AwsInternalConfig.builder().build())
        .when(spyecrtaskhandler)
        .getAwsInternalConfig(ecrArtifactDelegateRequest);

    BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder().number("tag").build();

    doReturn(buildDetailsInternal)
        .when(ecrService)
        .verifyBuildNumber(awsInternalConfig, "EcrImageUrl", region, "imagePath", "tag");

    doReturn("EcrImageUrl").when(awsEcrApiHelperServiceDelegate).getEcrImageUrl(awsInternalConfig, region, "imagePath");

    doReturn(Collections.singletonList(Collections.singletonMap("tag", "label")))
        .when(ecrService)
        .getLabels(awsInternalConfig, "imagePath", region, Collections.singletonList("tag"));

    ArtifactTaskExecutionResponse response = spyecrtaskhandler.getLastSuccessfulBuild(ecrArtifactDelegateRequest);

    EcrArtifactDelegateResponse ecrArtifactDelegateResponse =
        (EcrArtifactDelegateResponse) response.getArtifactDelegateResponses().get(0);

    assertThat(ecrArtifactDelegateResponse.getLabel()).isEqualTo(Collections.singletonMap("tag", "label"));

    assertThat(ecrArtifactDelegateResponse.getBuildDetails().getNumber()).isEqualTo("tag");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testEcrImageUrl() {
    EcrArtifactTaskHandler spyecrtaskhandler = spy(ecrArtifactTaskHandler);

    AwsConnectorDTO awsConnectorDTO =
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

    String region = "us-east-1";

    EcrArtifactDelegateRequest ecrArtifactDelegateRequest = EcrArtifactDelegateRequest.builder()
                                                                .region(region)
                                                                .awsConnectorDTO(awsConnectorDTO)
                                                                .imagePath("imagePath")
                                                                .tag("tag")
                                                                .build();

    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();

    doReturn(awsInternalConfig).when(spyecrtaskhandler).getAwsInternalConfig(ecrArtifactDelegateRequest);

    doReturn("imageUrl").when(awsEcrApiHelperServiceDelegate).getEcrImageUrl(awsInternalConfig, region, "imagePath");

    ArtifactTaskExecutionResponse response = spyecrtaskhandler.getEcrImageUrl(ecrArtifactDelegateRequest);

    EcrArtifactDelegateResponse ecrArtifactDelegateResponse =
        (EcrArtifactDelegateResponse) response.getArtifactDelegateResponses().get(0);

    assertThat(ecrArtifactDelegateResponse.getImageUrl()).isEqualTo("imageUrl");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testgetAmazonEcrAuthToken() {
    EcrArtifactTaskHandler spyecrtaskhandler = spy(ecrArtifactTaskHandler);

    AwsConnectorDTO awsConnectorDTO =
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

    String region = "us-east-1";

    EcrArtifactDelegateRequest ecrArtifactDelegateRequest = EcrArtifactDelegateRequest.builder()
                                                                .region(region)
                                                                .awsConnectorDTO(awsConnectorDTO)
                                                                .imagePath("imagePath")
                                                                .tag("tag")
                                                                .build();

    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();

    doReturn(awsInternalConfig).when(spyecrtaskhandler).getAwsInternalConfig(ecrArtifactDelegateRequest);

    doReturn("imageUrl.").when(awsEcrApiHelperServiceDelegate).getEcrImageUrl(awsInternalConfig, region, "imagePath");

    doReturn("AuthToken")
        .when(awsEcrApiHelperServiceDelegate)
        .getAmazonEcrAuthToken(awsInternalConfig, "imageUrl", region);

    ArtifactTaskExecutionResponse response = spyecrtaskhandler.getAmazonEcrAuthToken(ecrArtifactDelegateRequest);

    EcrArtifactDelegateResponse ecrArtifactDelegateResponse =
        (EcrArtifactDelegateResponse) response.getArtifactDelegateResponses().get(0);

    assertThat(ecrArtifactDelegateResponse.getAuthToken()).isEqualTo("AuthToken");
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetBuilds() {
    EcrArtifactTaskHandler spyecrtaskhandler = spy(ecrArtifactTaskHandler);

    AwsConnectorDTO awsConnectorDTO =
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

    String region = "us-east-1";

    EcrArtifactDelegateRequest ecrArtifactDelegateRequest = EcrArtifactDelegateRequest.builder()
                                                                .region(region)
                                                                .awsConnectorDTO(awsConnectorDTO)
                                                                .imagePath("imagePath")
                                                                .tag("tag")
                                                                .build();
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();

    doReturn(AwsInternalConfig.builder().build())
        .when(spyecrtaskhandler)
        .getAwsInternalConfig(ecrArtifactDelegateRequest);

    BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder().number("tag").build();

    doReturn("EcrImageUrl").when(awsEcrApiHelperServiceDelegate).getEcrImageUrl(awsInternalConfig, region, "imagePath");

    doReturn(Collections.singletonList(buildDetailsInternal))
        .when(ecrService)
        .getBuilds(awsInternalConfig, "EcrImageUrl", region, "imagePath", 10000);
    //

    ArtifactTaskExecutionResponse response = spyecrtaskhandler.getBuilds(ecrArtifactDelegateRequest);

    EcrArtifactDelegateResponse ecrArtifactDelegateResponse =
        (EcrArtifactDelegateResponse) response.getArtifactDelegateResponses().get(0);

    assertThat(ecrArtifactDelegateResponse.getImagePath()).isEqualTo("imagePath");

    assertThat(ecrArtifactDelegateResponse.getBuildDetails().getNumber()).isEqualTo("tag");
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testvalidateArtifactServer() {
    EcrArtifactTaskHandler spyecrtaskhandler = spy(ecrArtifactTaskHandler);

    AwsConnectorDTO awsConnectorDTO =
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

    String region = "us-east-1";

    EcrArtifactDelegateRequest ecrArtifactDelegateRequest = EcrArtifactDelegateRequest.builder()
                                                                .region(region)
                                                                .awsConnectorDTO(awsConnectorDTO)
                                                                .imagePath("imagePath")
                                                                .tag("tag")
                                                                .build();

    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();

    doReturn(awsInternalConfig).when(spyecrtaskhandler).getAwsInternalConfig(ecrArtifactDelegateRequest);

    doReturn("imageUrl").when(awsEcrApiHelperServiceDelegate).getEcrImageUrl(awsInternalConfig, region, "imagePath");
    doReturn(true).when(ecrService).validateCredentials(awsInternalConfig, "imageUrl", region, "imagePath");

    ArtifactTaskExecutionResponse response = spyecrtaskhandler.validateArtifactServer(ecrArtifactDelegateRequest);

    EcrArtifactDelegateResponse ecrArtifactDelegateResponse =
        (EcrArtifactDelegateResponse) response.getArtifactDelegateResponses().get(0);

    assertThat(response.isArtifactServerValid()).isEqualTo(true);

    assertThat(ecrArtifactDelegateResponse.getImageUrl()).isEqualTo("imageUrl");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testValidateArtifactName() {
    EcrArtifactTaskHandler spyecrtaskhandler = spy(ecrArtifactTaskHandler);

    AwsConnectorDTO awsConnectorDTO =
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

    String region = "us-east-1";

    EcrArtifactDelegateRequest ecrArtifactDelegateRequest = EcrArtifactDelegateRequest.builder()
                                                                .region(region)
                                                                .awsConnectorDTO(awsConnectorDTO)
                                                                .imagePath("imagePath")
                                                                .tag("tag")
                                                                .build();

    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();

    doReturn(awsInternalConfig).when(spyecrtaskhandler).getAwsInternalConfig(ecrArtifactDelegateRequest);

    doReturn("EcrImageUrl").when(awsEcrApiHelperServiceDelegate).getEcrImageUrl(awsInternalConfig, region, "imagePath");

    doReturn(true).when(ecrService).verifyImageName(awsInternalConfig, "EcrImageUrl", region, "imagePath");

    ArtifactTaskExecutionResponse response = spyecrtaskhandler.validateArtifactImage(ecrArtifactDelegateRequest);

    assertThat(response.isArtifactSourceValid()).isEqualTo(true);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testdecryptRequestDTOs() throws IOException {
    doReturn(null).when(secretDecryptionService).decrypt(any(), any());
    AwsConnectorDTO awsConnectorDTO =
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

    String region = "us-east-1";

    EcrArtifactDelegateRequest ecrArtifactDelegateRequest = EcrArtifactDelegateRequest.builder()
                                                                .region(region)
                                                                .awsConnectorDTO(awsConnectorDTO)
                                                                .imagePath("imagePath")
                                                                .tag("tag")
                                                                .build();

    ecrArtifactTaskHandler.decryptRequestDTOs(ecrArtifactDelegateRequest);
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildTagRegex() {
    EcrArtifactTaskHandler spyecrtaskhandler = spy(ecrArtifactTaskHandler);

    AwsConnectorDTO awsConnectorDTO =
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

    String region = "us-east-1";

    EcrArtifactDelegateRequest ecrArtifactDelegateRequest = EcrArtifactDelegateRequest.builder()
                                                                .region(region)
                                                                .awsConnectorDTO(awsConnectorDTO)
                                                                .imagePath("imagePath")
                                                                .tagRegex("t.*")
                                                                .build();
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();

    doReturn(AwsInternalConfig.builder().build())
        .when(spyecrtaskhandler)
        .getAwsInternalConfig(ecrArtifactDelegateRequest);

    BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder().number("tag").build();

    doReturn(buildDetailsInternal)
        .when(ecrService)
        .getLastSuccessfulBuildFromRegex(awsInternalConfig, "EcrImageUrl", region, "imagePath", "t.*");

    doReturn("EcrImageUrl").when(awsEcrApiHelperServiceDelegate).getEcrImageUrl(awsInternalConfig, region, "imagePath");

    doReturn(Collections.singletonList(Collections.singletonMap("tag", "label")))
        .when(ecrService)
        .getLabels(awsInternalConfig, "imagePath", region, Collections.singletonList("tag"));

    ArtifactTaskExecutionResponse response = spyecrtaskhandler.getLastSuccessfulBuild(ecrArtifactDelegateRequest);

    EcrArtifactDelegateResponse ecrArtifactDelegateResponse =
        (EcrArtifactDelegateResponse) response.getArtifactDelegateResponses().get(0);

    assertThat(ecrArtifactDelegateResponse.getLabel()).isEqualTo(Collections.singletonMap("tag", "label"));

    assertThat(ecrArtifactDelegateResponse.getBuildDetails().getNumber()).isEqualTo("tag");
  }
}
