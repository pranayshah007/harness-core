/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.azure;

import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.vivekveman;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.azure.AzureEnvironmentType;
import io.harness.beans.ArtifactMetaInfo;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.azureconnector.AzureAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureClientSecretKeyDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialType;
import io.harness.delegate.beans.connector.azureconnector.AzureInheritFromDelegateDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthUADTO;
import io.harness.delegate.beans.connector.azureconnector.AzureManagedIdentityType;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureSecretType;
import io.harness.delegate.beans.connector.azureconnector.AzureUserAssignedMSIAuthDTO;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;
import io.harness.delegate.task.artifacts.response.ArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.delegatetasks.azure.AzureAsyncTaskHelper;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class AcrArtifactTaskHandlerTest extends CategoryTest {
  private static final String ACR_SECRET_KEY = "secretKey";
  private static final String ACR_CLIENT_ID = "765433-2345-5432-123456";
  private static final String ACR_SECRET_CERT = "certval";
  private static final String ACR_TENANT_ID = "123456-6543-3456-654321";
  private final String SUBSCRIPTION_ID = "123456-6543-3456-654321";
  private final String REGISTRY = "testreg";
  private final String REGISTRY_URL = format("%s.azurecr.io", REGISTRY);
  private final String REPOSITORY = "library/testapp";
  private final String TAG_LABEL = "Tag#";
  @Mock private AzureAsyncTaskHelper azureAsyncTaskHelper;
  @Mock private SecretDecryptionService secretDecryptionService;
  @InjectMocks private AcrArtifactTaskHandler acrArtifactTaskHandler;

  private static final String SHA = "sha256:123456";
  private static final String SHA_V2 = "sha256:341243";
  private static final Map<String, String> LABEL = Map.of("k1", "v1");
  private static final ArtifactMetaInfo ARTIFACT_META_INFO =
      ArtifactMetaInfo.builder().sha(SHA).shaV2(SHA_V2).labels(LABEL).build();

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildFromTagRegexSuccess() {
    AzureConnectorDTO azureConnectorDTO = getSPConnector(AzureSecretType.SECRET_KEY);
    List encryptedDataDetails = Lists.emptyList();

    AcrArtifactDelegateRequest acrArtifactDelegateRequest = AcrArtifactDelegateRequest.builder()
                                                                .azureConnectorDTO(azureConnectorDTO)
                                                                .encryptedDataDetails(encryptedDataDetails)
                                                                .subscription(SUBSCRIPTION_ID)
                                                                .registry(REGISTRY)
                                                                .repository(REPOSITORY)
                                                                .tagRegex("[2].[0]")
                                                                .sourceType(ArtifactSourceType.ACR)
                                                                .build();
    BuildDetailsInternal buildDetailsInternal = createBuildDetailsInternal("2.0");
    when(azureAsyncTaskHelper.getLastSuccessfulBuildFromRegex(any(), any(), any(), any(), any()))
        .thenReturn(buildDetailsInternal);

    ArtifactTaskExecutionResponse artifactTaskResponse =
        acrArtifactTaskHandler.getLastSuccessfulBuild(acrArtifactDelegateRequest);

    List<ArtifactDelegateResponse> responseList = artifactTaskResponse.getArtifactDelegateResponses();

    assertThat(responseList.size()).isEqualTo(1);
    Map<String, String> metadata = responseList.get(0).getBuildDetails().getMetadata();
    assertThat(metadata.get(ArtifactMetadataKeys.TAG)).isEqualTo("2.0");
    assertThat(metadata.get(ArtifactMetadataKeys.SHA)).isEqualTo(SHA);
    assertThat(metadata.get(ArtifactMetadataKeys.SHAV2)).isEqualTo(SHA_V2);
    assertThat(((AcrArtifactDelegateResponse) responseList.get(0)).getLabel()).isEqualTo(LABEL);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildFromTagSuccess() {
    AzureConnectorDTO azureConnectorDTO = getSPConnector(AzureSecretType.SECRET_KEY);
    List encryptedDataDetails = Lists.emptyList();

    AcrArtifactDelegateRequest acrArtifactDelegateRequest = AcrArtifactDelegateRequest.builder()
                                                                .azureConnectorDTO(azureConnectorDTO)
                                                                .encryptedDataDetails(encryptedDataDetails)
                                                                .subscription(SUBSCRIPTION_ID)
                                                                .registry(REGISTRY)
                                                                .repository(REPOSITORY)
                                                                .tag("2.0")
                                                                .sourceType(ArtifactSourceType.ACR)
                                                                .build();
    BuildDetailsInternal buildDetailsInternal = createBuildDetailsInternal("2.0");
    when(azureAsyncTaskHelper.verifyBuildNumber(any(), any(), any(), any(), any())).thenReturn(buildDetailsInternal);

    ArtifactTaskExecutionResponse artifactTaskResponse =
        acrArtifactTaskHandler.getLastSuccessfulBuild(acrArtifactDelegateRequest);

    List<ArtifactDelegateResponse> responseList = artifactTaskResponse.getArtifactDelegateResponses();

    Map<String, String> metadata = responseList.get(0).getBuildDetails().getMetadata();
    assertThat(metadata.get(ArtifactMetadataKeys.TAG)).isEqualTo("2.0");
    assertThat(metadata.get(ArtifactMetadataKeys.SHA)).isEqualTo(SHA);
    assertThat(metadata.get(ArtifactMetadataKeys.SHAV2)).isEqualTo(SHA_V2);
    assertThat(((AcrArtifactDelegateResponse) responseList.get(0)).getLabel()).isEqualTo(LABEL);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetBuildsSuccess() {
    AzureConnectorDTO azureConnectorDTO = getSPConnector(AzureSecretType.SECRET_KEY);
    List encryptedDataDetails = Lists.emptyList();

    AcrArtifactDelegateRequest acrArtifactDelegateRequest = AcrArtifactDelegateRequest.builder()
                                                                .azureConnectorDTO(azureConnectorDTO)
                                                                .encryptedDataDetails(encryptedDataDetails)
                                                                .subscription(SUBSCRIPTION_ID)
                                                                .registry(REGISTRY)
                                                                .repository(REPOSITORY)
                                                                .sourceType(ArtifactSourceType.ACR)
                                                                .build();
    List<BuildDetailsInternal> buildDetailsInternalList = new LinkedList<>();
    buildDetailsInternalList.add(createBuildDetailsInternal("1.0"));
    buildDetailsInternalList.add(createBuildDetailsInternal("2.0"));
    buildDetailsInternalList.add(createBuildDetailsInternal("3.0"));

    when(azureAsyncTaskHelper.getImageTags(any(), any(), any(), any())).thenReturn(buildDetailsInternalList);

    ArtifactTaskExecutionResponse artifactTaskResponse = acrArtifactTaskHandler.getBuilds(acrArtifactDelegateRequest);

    List<ArtifactDelegateResponse> responseList = artifactTaskResponse.getArtifactDelegateResponses();

    assertThat(responseList.size()).isEqualTo(3);
    assertThat(responseList.get(0).getBuildDetails().getMetadata().get(ArtifactMetadataKeys.TAG)).isEqualTo("3.0");
    assertThat(responseList.get(1).getBuildDetails().getMetadata().get(ArtifactMetadataKeys.TAG)).isEqualTo("2.0");
    assertThat(responseList.get(2).getBuildDetails().getMetadata().get(ArtifactMetadataKeys.TAG)).isEqualTo("1.0");
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testdecryptRequestDTOs() {
    AzureConnectorDTO azureConnectorDTO = getSPConnector(AzureSecretType.SECRET_KEY);
    List encryptedDataDetails = Lists.emptyList();
    AcrArtifactDelegateRequest acrArtifactDelegateRequest = AcrArtifactDelegateRequest.builder()
                                                                .azureConnectorDTO(azureConnectorDTO)
                                                                .encryptedDataDetails(encryptedDataDetails)
                                                                .subscription(SUBSCRIPTION_ID)
                                                                .registry(REGISTRY)
                                                                .repository(REPOSITORY)
                                                                .sourceType(ArtifactSourceType.ACR)
                                                                .build();
    acrArtifactTaskHandler.decryptRequestDTOs(acrArtifactDelegateRequest);

    verify(secretDecryptionService).decrypt(any(), any());
  }

  private AcrArtifactDelegateResponse createAcrArtifactDelegateResponse(String tag) {
    String imagePath = generateImagePath(tag);
    Map<String, String> metadata = generateMetadata(tag, imagePath);
    return generateAcrArtifactDelegateResponse(tag, imagePath, metadata);
  }

  private BuildDetailsInternal createBuildDetailsInternal(String tag) {
    String imagePath = generateImagePath(tag);
    Map<String, String> metadata = generateMetadata(tag, imagePath);
    return generateBuildDetailsInternal(tag, imagePath, metadata);
  }

  private String generateImagePath(String tag) {
    return format("%s/%s:%s", REGISTRY_URL, REPOSITORY, tag);
  }

  private Map<String, String> generateMetadata(String tag, String imagePath) {
    Map<String, String> metadata = new HashMap<>();
    metadata.put(ArtifactMetadataKeys.IMAGE, imagePath);
    metadata.put(ArtifactMetadataKeys.TAG, tag);
    metadata.put(ArtifactMetadataKeys.REGISTRY_HOSTNAME, REGISTRY_URL);
    return metadata;
  }

  private AcrArtifactDelegateResponse generateAcrArtifactDelegateResponse(
      String tag, String imagePath, Map<String, String> metadata) {
    return AcrArtifactDelegateResponse.builder()
        .sourceType(ArtifactSourceType.ACR)
        .subscription(SUBSCRIPTION_ID)
        .registry(REGISTRY)
        .repository(REPOSITORY)
        .tag(tag)
        .buildDetails(ArtifactBuildDetailsNG.builder()
                          .number(tag)
                          .uiDisplayName(format("%s %s", TAG_LABEL, tag))
                          .buildUrl(imagePath)
                          .metadata(metadata)
                          .build())
        .build();
  }

  private BuildDetailsInternal generateBuildDetailsInternal(
      String tag, String imagePath, Map<String, String> metadata) {
    return BuildDetailsInternal.builder()
        .number(tag)
        .uiDisplayName(format("%s %s", TAG_LABEL, tag))
        .buildUrl(imagePath)
        .metadata(metadata)
        .artifactMetaInfo(ARTIFACT_META_INFO)
        .build();
  }

  private AzureConnectorDTO getSPConnector(AzureSecretType azureSecretType) {
    if (azureSecretType == AzureSecretType.SECRET_KEY) {
      return AzureConnectorDTO.builder()
          .azureEnvironmentType(AzureEnvironmentType.AZURE)
          .credential(getAzureCredentialsForSPWithSecret())
          .build();
    }

    if (azureSecretType == AzureSecretType.KEY_CERT) {
      return AzureConnectorDTO.builder()
          .azureEnvironmentType(AzureEnvironmentType.AZURE)
          .credential(getAzureCredentialsForSPWithCert())
          .build();
    }

    return null;
  }

  private AzureConnectorDTO getMSIConnector(AzureManagedIdentityType azureManagedIdentityType) {
    if (azureManagedIdentityType == AzureManagedIdentityType.USER_ASSIGNED_MANAGED_IDENTITY) {
      return AzureConnectorDTO.builder()
          .azureEnvironmentType(AzureEnvironmentType.AZURE)
          .credential(getAzureCredentialsForUserAssignedMSI())
          .build();
    }

    if (azureManagedIdentityType == AzureManagedIdentityType.SYSTEM_ASSIGNED_MANAGED_IDENTITY) {
      return AzureConnectorDTO.builder()
          .azureEnvironmentType(AzureEnvironmentType.AZURE)
          .credential(getAzureCredentialsForSystemAssignedMSI())
          .build();
    }

    return null;
  }

  private AzureCredentialDTO getAzureCredentialsForSPWithSecret() {
    AzureAuthDTO azureAuthDTO =
        AzureAuthDTO.builder()
            .azureSecretType(AzureSecretType.SECRET_KEY)
            .credentials(AzureClientSecretKeyDTO.builder()
                             .secretKey(SecretRefData.builder().decryptedValue(ACR_SECRET_KEY.toCharArray()).build())
                             .build())
            .build();

    AzureManualDetailsDTO azureManualDetailsDTO =
        AzureManualDetailsDTO.builder().authDTO(azureAuthDTO).clientId(ACR_CLIENT_ID).tenantId(ACR_TENANT_ID).build();

    return AzureCredentialDTO.builder()
        .azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS)
        .config(azureManualDetailsDTO)
        .build();
  }

  private AzureCredentialDTO getAzureCredentialsForSPWithCert() {
    AzureAuthDTO azureAuthDTO =
        AzureAuthDTO.builder()
            .azureSecretType(AzureSecretType.KEY_CERT)
            .credentials(AzureClientSecretKeyDTO.builder()
                             .secretKey(SecretRefData.builder().decryptedValue(ACR_SECRET_CERT.toCharArray()).build())
                             .build())
            .build();

    AzureManualDetailsDTO azureManualDetailsDTO =
        AzureManualDetailsDTO.builder().authDTO(azureAuthDTO).clientId(ACR_CLIENT_ID).tenantId(ACR_TENANT_ID).build();

    return AzureCredentialDTO.builder()
        .azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS)
        .config(azureManualDetailsDTO)
        .build();
  }

  private AzureCredentialDTO getAzureCredentialsForUserAssignedMSI() {
    AzureUserAssignedMSIAuthDTO azureUserAssignedMSIAuthDTO =
        AzureUserAssignedMSIAuthDTO.builder().clientId(ACR_CLIENT_ID).build();

    AzureMSIAuthDTO azureMSIAuthDTO =
        AzureMSIAuthUADTO.builder()
            .azureManagedIdentityType(AzureManagedIdentityType.USER_ASSIGNED_MANAGED_IDENTITY)
            .credentials(azureUserAssignedMSIAuthDTO)
            .build();

    AzureInheritFromDelegateDetailsDTO azureInheritFromDelegateDetailsDTO =
        AzureInheritFromDelegateDetailsDTO.builder().authDTO(azureMSIAuthDTO).build();

    return AzureCredentialDTO.builder()
        .azureCredentialType(AzureCredentialType.INHERIT_FROM_DELEGATE)
        .config(azureInheritFromDelegateDetailsDTO)
        .build();
  }

  private AzureCredentialDTO getAzureCredentialsForSystemAssignedMSI() {
    AzureMSIAuthDTO azureMSIAuthDTO =
        AzureMSIAuthUADTO.builder()
            .azureManagedIdentityType(AzureManagedIdentityType.SYSTEM_ASSIGNED_MANAGED_IDENTITY)
            .build();

    AzureInheritFromDelegateDetailsDTO azureInheritFromDelegateDetailsDTO =
        AzureInheritFromDelegateDetailsDTO.builder().authDTO(azureMSIAuthDTO).build();

    return AzureCredentialDTO.builder()
        .azureCredentialType(AzureCredentialType.INHERIT_FROM_DELEGATE)
        .config(azureInheritFromDelegateDetailsDTO)
        .build();
  }
}
