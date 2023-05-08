/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.encryptors.clients;

import static io.harness.eraro.ErrorCode.GCP_SECRET_OPERATION_ERROR;
import static io.harness.govern.IgnoreThrowable.ignoredOnPurpose;
import static io.harness.rule.OwnerRule.NISHANT;
import static io.harness.rule.OwnerRule.PIYUSH;
import static io.harness.rule.OwnerRule.SHREYAS;
import static io.harness.rule.OwnerRule.TEJAS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.SecretText;
import io.harness.category.element.UnitTests;
import io.harness.exception.SecretManagementException;
import io.harness.rule.Owner;
import io.harness.security.encryption.AdditionalMetadata;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptedRecordData;

import software.wings.beans.GcpSecretsManagerConfig;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.ProjectName;
import com.google.cloud.secretmanager.v1.Secret;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretName;
import com.google.cloud.secretmanager.v1.SecretPayload;
import com.google.cloud.secretmanager.v1.SecretVersion;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;

public class GcpSecretsManagerEncryptorTest extends CategoryTest {
  private GcpSecretsManagerEncryptor gcpSecretsManagerEncryptor;
  private SecretManagerServiceClient secretManagerServiceClient;
  private GcpSecretsManagerConfig gcpSecretsManagerConfig;
  private GoogleCredentials googleCredentials;

  @Rule public ExpectedException expectedException = ExpectedException.none();

  String mockedProjectId = "MockProjectName";
  String secretName = "MockedSecret";
  String plaintext = "MockedValue";
  String secretVersionName = "MockVersionName";
  String accountId = "MockedAccountId";
  SecretPayload payload = SecretPayload.newBuilder().setData(ByteString.copyFromUtf8(plaintext)).build();
  Secret mockedSecret = Secret.newBuilder().setName(secretName).build();
  SecretVersion mockedSecrectVersion = SecretVersion.newBuilder().setName(secretVersionName).build();
  AccessSecretVersionResponse accessedSecretVersionResponse =
      AccessSecretVersionResponse.newBuilder().setPayload(payload).build();
  String mockVersionFullString =
      "projects/" + mockedProjectId + "/secrets/" + secretName + "/versions/" + secretVersionName;
  SecretManagerServiceClient.ListSecretsPagedResponse listSecretsPagedResponse;

  @Before
  public void setup() throws IOException {
    gcpSecretsManagerEncryptor = mock(GcpSecretsManagerEncryptor.class);
    googleCredentials = mock(GoogleCredentials.class);
    gcpSecretsManagerConfig = mock(GcpSecretsManagerConfig.class);
    secretManagerServiceClient = mock(SecretManagerServiceClient.class);
    listSecretsPagedResponse = mock(SecretManagerServiceClient.ListSecretsPagedResponse.class);
    when(gcpSecretsManagerEncryptor.getGoogleCredentials(gcpSecretsManagerConfig)).thenReturn(googleCredentials);
    when(gcpSecretsManagerEncryptor.getGcpSecretsManagerClient(any(GcpSecretsManagerConfig.class)))
        .thenReturn(secretManagerServiceClient);

    when(gcpSecretsManagerEncryptor.getProjectId(any(GoogleCredentials.class))).thenReturn(mockedProjectId);
    when(secretManagerServiceClient.createSecret(anyString(), anyString(), any(Secret.class))).thenReturn(mockedSecret);

    // mock calls fro SecretMangerClient
    doReturn(mockedSecret)
        .when(secretManagerServiceClient)
        .createSecret(any(ProjectName.class), anyString(), any(Secret.class));
    doReturn(mockedSecret).when(secretManagerServiceClient).getSecret(any(SecretName.class));
    doReturn(mockedSecrectVersion)
        .when(secretManagerServiceClient)
        .addSecretVersion(anyString(), any(SecretPayload.class));
    doReturn(accessedSecretVersionResponse)
        .when(secretManagerServiceClient)
        .accessSecretVersion(any(SecretVersionName.class));
    doNothing().when(secretManagerServiceClient).deleteSecret(any(SecretName.class));
    doReturn(listSecretsPagedResponse).when(secretManagerServiceClient).listSecrets(any(ProjectName.class));

    // real method invocations for testing
    when(gcpSecretsManagerEncryptor.createSecret(anyString(), any(), any())).thenCallRealMethod();
    when(gcpSecretsManagerEncryptor.updateSecret(anyString(), any(), any(), any())).thenCallRealMethod();
    when(gcpSecretsManagerEncryptor.deleteSecret(anyString(), any(), any())).thenCallRealMethod();
    when(gcpSecretsManagerEncryptor.validateReference(anyString(), any(SecretText.class), any())).thenCallRealMethod();
    when(gcpSecretsManagerEncryptor.fetchSecretValue(anyString(), any(EncryptedRecord.class), any()))
        .thenCallRealMethod();
    when(gcpSecretsManagerEncryptor.validateSecretManagerConfiguration(any(), any())).thenCallRealMethod();
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void test_itShouldCreateSecretWithoutRegionSuccessFully() throws IOException {
    EncryptedRecord expectedEncryptedData = EncryptedRecordData.builder()
                                                .encryptionKey(mockedSecret.getName())
                                                .encryptedValue(mockedSecrectVersion.getName().toCharArray())
                                                .build();
    EncryptedRecord actualEncryptedData = gcpSecretsManagerEncryptor.createSecret(
        accountId, SecretText.builder().name(secretName).value(plaintext).build(), gcpSecretsManagerConfig);
    assertThat(expectedEncryptedData).isEqualTo(actualEncryptedData);
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void test_itShouldCreateSecretWithRegionSuccessFully() throws IOException {
    AdditionalMetadata metadata = AdditionalMetadata.builder().value("regions", "us-east1").build();
    EncryptedRecord expectedEncryptedData = EncryptedRecordData.builder()
                                                .encryptionKey(mockedSecret.getName())
                                                .encryptedValue(mockedSecrectVersion.getName().toCharArray())
                                                .additionalMetadata(metadata)
                                                .build();
    EncryptedRecord actualEncryptedData = gcpSecretsManagerEncryptor.createSecret(accountId,
        SecretText.builder().name(secretName).value(plaintext).additionalMetadata(metadata).build(),
        gcpSecretsManagerConfig);
    assertThat(expectedEncryptedData.getAdditionalMetadata()).isEqualTo(actualEncryptedData.getAdditionalMetadata());
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void test_itShouldUpdateSecretVersionOnly() throws IOException {
    String updatedVersionName = "UpdatedVersionName";
    AdditionalMetadata metadata = AdditionalMetadata.builder().value("regions", "us-east1").build();
    SecretVersion internalMockedSecretVersion = SecretVersion.newBuilder().setName(updatedVersionName).build();
    doReturn(internalMockedSecretVersion)
        .when(secretManagerServiceClient)
        .addSecretVersion(anyString(), any(SecretPayload.class));
    EncryptedRecord existingEncryptedData = EncryptedRecordData.builder()
                                                .encryptionKey(mockedSecret.getName())
                                                .encryptedValue(mockedSecrectVersion.getName().toCharArray())
                                                .additionalMetadata(metadata)
                                                .build();
    EncryptedRecord actualEncryptedData = gcpSecretsManagerEncryptor.updateSecret(accountId,
        SecretText.builder().name(secretName).value(plaintext).additionalMetadata(metadata).build(),
        existingEncryptedData, gcpSecretsManagerConfig);
    assertThat(updatedVersionName.toCharArray()).isEqualTo(actualEncryptedData.getEncryptedValue());
    assertThat(existingEncryptedData.getEncryptionKey()).isEqualTo(actualEncryptedData.getEncryptionKey());
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void test_itShouldThrowExceptionIfNameIsUpdated() throws IOException {
    AdditionalMetadata metadata = AdditionalMetadata.builder().value("regions", "us-east1").build();
    EncryptedRecord existingEncryptedRecord = EncryptedRecordData.builder()
                                                  .encryptionKey(mockedSecret.getName())
                                                  .encryptedValue(mockedSecrectVersion.getName().toCharArray())
                                                  .additionalMetadata(metadata)
                                                  .build();
    try {
      gcpSecretsManagerEncryptor.updateSecret(accountId,
          SecretText.builder().name("Updating Name").value(plaintext).additionalMetadata(metadata).build(),
          existingEncryptedRecord, gcpSecretsManagerConfig);
      Assert.fail("Secret Name got updated");
    } catch (SecretManagementException se) {
      assertThat(se.getCode()).isEqualTo(GCP_SECRET_OPERATION_ERROR);
    }
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void test_itShouldValidateReferenceSuccessfullyForReferencedSecret() throws IOException {
    boolean validReference = gcpSecretsManagerEncryptor.validateReference(
        accountId, SecretText.builder().name(secretName).path(secretVersionName).build(), gcpSecretsManagerConfig);
    assertThat(validReference).isTrue();
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void test_itShouldNotValidateReferenceSuccessfully() throws IOException {
    try {
      boolean validReference = gcpSecretsManagerEncryptor.validateReference(accountId,
          SecretText.builder().name(secretName).value(mockVersionFullString).build(), gcpSecretsManagerConfig);
      Assert.fail("Validated reference without path");
    } catch (SecretManagementException se) {
      ignoredOnPurpose(se);
    }
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void test_itShouldFetchSecretValueByKeyAndPath() throws IOException {
    EncryptedRecord existingEncryptedRecord = EncryptedRecordData.builder()
                                                  .encryptionKey(mockedSecret.getName())
                                                  .path(mockedSecrectVersion.getName())
                                                  .encryptedValue(mockedSecrectVersion.getName().toCharArray())
                                                  .build();
    char[] fetchedSecretValue =
        gcpSecretsManagerEncryptor.fetchSecretValue(accountId, existingEncryptedRecord, gcpSecretsManagerConfig);
    assertThat(plaintext.toCharArray()).isEqualTo(fetchedSecretValue);
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void test_itShouldFetchSecretValueByKeyAndValue() throws IOException {
    EncryptedRecord existingEncryptedRecord = EncryptedRecordData.builder()
                                                  .encryptionKey(mockedSecret.getName())
                                                  .encryptedValue(mockVersionFullString.toCharArray())
                                                  .build();
    char[] fetchedSecretValue =
        gcpSecretsManagerEncryptor.fetchSecretValue(accountId, existingEncryptedRecord, gcpSecretsManagerConfig);
    assertThat(plaintext.toCharArray()).isEqualTo(fetchedSecretValue);
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void test_itShouldThrowExceptionWhileFetchingSecretIfPathIsSetButNameIsNotSet() throws IOException {
    EncryptedRecord existingEncryptedRecord =
        EncryptedRecordData.builder().path(mockedSecrectVersion.getName()).build();
    try {
      char[] fetchedSecretValue =
          gcpSecretsManagerEncryptor.fetchSecretValue(accountId, existingEncryptedRecord, gcpSecretsManagerConfig);
    } catch (SecretManagementException e) {
      assertThat(e.getMessage())
          .isEqualTo("Secret Referencing Failed - Cannot Reference Secret in Gcp Secret Manager Without Name");
    }
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void test_itShouldDeleteSecretSuccessFully() throws IOException {
    EncryptedRecord existingEncryptedRecord = EncryptedRecordData.builder().encryptionKey(secretName).build();
    boolean deleted =
        gcpSecretsManagerEncryptor.deleteSecret(accountId, existingEncryptedRecord, gcpSecretsManagerConfig);
    assertThat(deleted).isTrue();
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void test_itShouldThrowExceptionForDeletingIfEncryptionKeyIsEmpty() throws IOException {
    EncryptedRecord existingEncryptedRecord = EncryptedRecordData.builder().build();
    try {
      boolean isDeleted =
          gcpSecretsManagerEncryptor.deleteSecret(accountId, existingEncryptedRecord, gcpSecretsManagerConfig);
    } catch (SecretManagementException e) {
      assertThat(e.getMessage()).isEqualTo("Cannot delete secret for Empty ProjectId or SecretId");
    }
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void test_whenThereAreNoSecretsShouldPass() {
    assertThat(gcpSecretsManagerEncryptor.validateSecretManagerConfiguration(accountId, gcpSecretsManagerConfig))
        .isTrue();
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void test_whenThereAreMultipleSecretsShouldPass() {
    List<Secret> secrets = new LinkedList<>();
    secrets.add(Secret.newBuilder().build());
    secrets.add(Secret.newBuilder().build());
    when(listSecretsPagedResponse.iterateAll()).thenReturn(secrets);
    assertThat(gcpSecretsManagerEncryptor.validateSecretManagerConfiguration(accountId, gcpSecretsManagerConfig))
        .isTrue();
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void test_RenameSecret_NoChange() {
    when(gcpSecretsManagerEncryptor.renameSecret(any(), any(SecretText.class), any(), any())).thenCallRealMethod();
    EncryptedRecord existingRecord = EncryptedRecordData.builder().name(secretName).build();
    EncryptedRecord updatedRecord = gcpSecretsManagerEncryptor.renameSecret(
        accountId, SecretText.builder().name(secretName).build(), existingRecord, gcpSecretsManagerConfig);
    assertThat(updatedRecord).isEqualTo(existingRecord);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGetGoogleCredentialsForMissingConnectorCredentials() {
    GcpSecretsManagerConfig gcpSecretsManagerConfig = GcpSecretsManagerConfig.builder().build();
    expectedException.expect(SecretManagementException.class);
    expectedException.expectMessage(
        "GCP Secret Manager credentials are missing. Please check if the credentials secret exists.");
    when(gcpSecretsManagerEncryptor.getGoogleCredentials(gcpSecretsManagerConfig)).thenCallRealMethod();
    gcpSecretsManagerEncryptor.getGoogleCredentials(gcpSecretsManagerConfig);
  }
}
