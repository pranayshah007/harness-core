/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.ecr;

import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.SRIDHAR;
import static io.harness.rule.OwnerRule.vivekveman;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
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
import io.harness.beans.ArtifactMetaInfo;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.helpers.ext.ecr.EcrService;
import software.wings.service.impl.AwsApiHelperService;
import software.wings.service.impl.delegate.AwsEcrApiHelperServiceDelegate;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
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

  private static final String SHA = "sha:12345";
  private static final String SHA_KEY = "SHA";
  private static final String SHA_V2_KEY = "SHAV2";
  private static final String TAG = "tag";
  private static final Map<String, String> LABEL = ImmutableMap.<String, String>builder().put("key1", "val1").build();
  private static final ArtifactMetaInfo ARTIFACT_META_INFO =
      ArtifactMetaInfo.builder().sha(SHA).shaV2(SHA).labels(LABEL).build();

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
                                                                .tag(TAG)
                                                                .build();
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();

    doReturn(AwsInternalConfig.builder().build())
        .when(spyecrtaskhandler)
        .getAwsInternalConfig(ecrArtifactDelegateRequest);

    BuildDetailsInternal buildDetailsInternal =
        BuildDetailsInternal.builder().number(TAG).artifactMetaInfo(ARTIFACT_META_INFO).build();

    doReturn(buildDetailsInternal)
        .when(ecrService)
        .verifyBuildNumber(awsInternalConfig, "EcrImageUrl", region, "imagePath", TAG);

    doReturn("EcrImageUrl").when(awsEcrApiHelperServiceDelegate).getEcrImageUrl(awsInternalConfig, region, "imagePath");

    ArtifactTaskExecutionResponse response = spyecrtaskhandler.getLastSuccessfulBuild(ecrArtifactDelegateRequest);

    EcrArtifactDelegateResponse ecrArtifactDelegateResponse =
        (EcrArtifactDelegateResponse) response.getArtifactDelegateResponses().get(0);

    assertThat(ecrArtifactDelegateResponse.getLabel()).isEqualTo(LABEL);
    ArtifactBuildDetailsNG artifactBuildDetailsNG = ecrArtifactDelegateResponse.getBuildDetails();
    assertThat(artifactBuildDetailsNG.getNumber()).isEqualTo(TAG);
    assertThat(artifactBuildDetailsNG.getMetadata().get(SHA_KEY)).isEqualTo(SHA);
    assertThat(artifactBuildDetailsNG.getMetadata().get(SHA_V2_KEY)).isEqualTo(SHA);
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
                                                                .tag(TAG)
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
                                                                .tag(TAG)
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
                                                                .tag(TAG)
                                                                .build();
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();

    doReturn(AwsInternalConfig.builder().build())
        .when(spyecrtaskhandler)
        .getAwsInternalConfig(ecrArtifactDelegateRequest);

    BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder().number(TAG).build();

    doReturn("EcrImageUrl").when(awsEcrApiHelperServiceDelegate).getEcrImageUrl(awsInternalConfig, region, "imagePath");

    doReturn(Collections.singletonList(buildDetailsInternal))
        .when(ecrService)
        .getBuilds(awsInternalConfig, "EcrImageUrl", region, "imagePath", 10000);
    //

    ArtifactTaskExecutionResponse response = spyecrtaskhandler.getBuilds(ecrArtifactDelegateRequest);

    EcrArtifactDelegateResponse ecrArtifactDelegateResponse =
        (EcrArtifactDelegateResponse) response.getArtifactDelegateResponses().get(0);

    assertThat(ecrArtifactDelegateResponse.getImagePath()).isEqualTo("imagePath");

    assertThat(ecrArtifactDelegateResponse.getBuildDetails().getNumber()).isEqualTo(TAG);
  }
  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testGetBuildsSortingOrder() throws ParseException {
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
                                                                .tag(TAG)
                                                                .build();
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();

    doReturn(AwsInternalConfig.builder().build())
        .when(spyecrtaskhandler)
        .getAwsInternalConfig(ecrArtifactDelegateRequest);

    BuildDetailsInternal buildDetailsTagLatest =
        BuildDetailsInternal.builder().number("latest").imagePushedAt(parseDate("2023-02-02T16:48:55-08:00")).build();
    BuildDetailsInternal buildDetailsTagV1 =
        BuildDetailsInternal.builder().number("v1").imagePushedAt(parseDate("2023-02-02T16:45:50-08:00")).build();
    BuildDetailsInternal buildDetailsTagV2 =
        BuildDetailsInternal.builder().number("v2").imagePushedAt(parseDate("2023-02-02T16:45:50-08:00")).build();
    BuildDetailsInternal buildDetailsTagV3 =
        BuildDetailsInternal.builder().number("v3").imagePushedAt(parseDate("2023-02-02T16:45:50-08:00")).build();
    BuildDetailsInternal buildDetailsTagStable =
        BuildDetailsInternal.builder().number("stable").imagePushedAt(parseDate("2022-11-22T04:18:35-08:00")).build();
    BuildDetailsInternal buildDetailsTagStablePerl = BuildDetailsInternal.builder()
                                                         .number("stable-perl")
                                                         .imagePushedAt(parseDate("2022-11-22T23:03:17-08:00"))
                                                         .build();

    doReturn("EcrImageUrl").when(awsEcrApiHelperServiceDelegate).getEcrImageUrl(awsInternalConfig, region, "imagePath");

    doReturn(Arrays.asList(buildDetailsTagStablePerl, buildDetailsTagV1, buildDetailsTagV2, buildDetailsTagV3,
                 buildDetailsTagLatest, buildDetailsTagStable))
        .when(ecrService)
        .getBuilds(awsInternalConfig, "EcrImageUrl", region, "imagePath", 10000);

    ArtifactTaskExecutionResponse response = spyecrtaskhandler.getBuilds(ecrArtifactDelegateRequest);
    assertEquals(response.getArtifactDelegateResponses().size(), 6);

    EcrArtifactDelegateResponse ecrArtifactDelegateResponse =
        (EcrArtifactDelegateResponse) response.getArtifactDelegateResponses().get(0);
    assertThat(ecrArtifactDelegateResponse.getImagePath()).isEqualTo("imagePath");
    assertThat(ecrArtifactDelegateResponse.getBuildDetails().getNumber()).isEqualTo("latest");

    ecrArtifactDelegateResponse = (EcrArtifactDelegateResponse) response.getArtifactDelegateResponses().get(1);
    assertThat(ecrArtifactDelegateResponse.getImagePath()).isEqualTo("imagePath");
    assertThat(ecrArtifactDelegateResponse.getBuildDetails().getNumber()).isEqualTo("v3");

    ecrArtifactDelegateResponse = (EcrArtifactDelegateResponse) response.getArtifactDelegateResponses().get(5);
    assertThat(ecrArtifactDelegateResponse.getImagePath()).isEqualTo("imagePath");
    assertThat(ecrArtifactDelegateResponse.getBuildDetails().getNumber()).isEqualTo("stable");
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
                                                                .tag(TAG)
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
                                                                .tag(TAG)
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
                                                                .tag(TAG)
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

    BuildDetailsInternal buildDetailsInternal =
        BuildDetailsInternal.builder().number(TAG).artifactMetaInfo(ARTIFACT_META_INFO).build();

    doReturn(buildDetailsInternal)
        .when(ecrService)
        .getLastSuccessfulBuildFromRegex(awsInternalConfig, "EcrImageUrl", region, "imagePath", "t.*");

    doReturn("EcrImageUrl").when(awsEcrApiHelperServiceDelegate).getEcrImageUrl(awsInternalConfig, region, "imagePath");

    ArtifactTaskExecutionResponse response = spyecrtaskhandler.getLastSuccessfulBuild(ecrArtifactDelegateRequest);

    EcrArtifactDelegateResponse ecrArtifactDelegateResponse =
        (EcrArtifactDelegateResponse) response.getArtifactDelegateResponses().get(0);

    assertThat(ecrArtifactDelegateResponse.getLabel()).isEqualTo(LABEL);
    ArtifactBuildDetailsNG artifactBuildDetailsNG = ecrArtifactDelegateResponse.getBuildDetails();
    assertThat(artifactBuildDetailsNG.getNumber()).isEqualTo(TAG);
    assertThat(artifactBuildDetailsNG.getMetadata().get(SHA_KEY)).isEqualTo(SHA);
    assertThat(artifactBuildDetailsNG.getMetadata().get(SHA_V2_KEY)).isEqualTo(SHA);
  }

  private static Date parseDate(String date) throws ParseException {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    return sdf.parse(date);
  }
}
