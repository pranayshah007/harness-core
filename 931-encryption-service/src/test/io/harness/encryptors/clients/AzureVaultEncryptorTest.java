/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.encryptors.clients;

import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.RAGHAV_MURALI;
import static io.harness.rule.OwnerRule.UTKARSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.azure.AzureEnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.concurrent.HTimeLimiter;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.SecretManagementDelegateException;
import io.harness.helpers.ext.azure.KeyVaultAuthenticator;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionType;

import software.wings.beans.AzureVaultConfig;

import com.azure.core.exception.ResourceNotFoundException;
import com.azure.core.http.rest.Response;
import com.azure.core.http.rest.SimpleResponse;
import com.azure.core.util.Context;
import com.azure.core.util.polling.LongRunningOperationStatus;
import com.azure.core.util.polling.PollResponse;
import com.azure.core.util.polling.SyncPoller;
import com.azure.security.keyvault.administration.implementation.models.KeyVaultError;
import com.azure.security.keyvault.administration.implementation.models.KeyVaultErrorException;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.DeletedSecret;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.aad.msal4j.MsalServiceException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PrepareForTest;

@Slf4j
@PrepareForTest({KeyVaultAuthenticator.class})
public class AzureVaultEncryptorTest extends CategoryTest {
  private AzureVaultEncryptor azureVaultEncryptor;
  private AzureVaultConfig azureVaultConfig;
  private SecretClient keyVaultClient;
  private MockedStatic<KeyVaultAuthenticator> keyVaultAuthenticatorMockedStatic;

  @Before
  public void setup() {
    azureVaultEncryptor = new AzureVaultEncryptor(HTimeLimiter.create());
    azureVaultConfig = AzureVaultConfig.builder()
                           .uuid(UUIDGenerator.generateUuid())
                           .name(UUIDGenerator.generateUuid())
                           .accountId(UUIDGenerator.generateUuid())
                           .clientId(UUIDGenerator.generateUuid())
                           .secretKey(UUIDGenerator.generateUuid())
                           .tenantId(UUIDGenerator.generateUuid())
                           .subscription(UUIDGenerator.generateUuid())
                           .vaultName(UUIDGenerator.generateUuid())
                           .azureEnvironmentType(AzureEnvironmentType.AZURE)
                           .encryptionType(EncryptionType.AZURE_VAULT)
                           .isDefault(false)
                           .build();

    keyVaultClient = mock(SecretClient.class);
    keyVaultAuthenticatorMockedStatic = Mockito.mockStatic(KeyVaultAuthenticator.class);
    keyVaultAuthenticatorMockedStatic.when(() -> KeyVaultAuthenticator.getSecretsClient(anyString(), any(), any()))
        .thenReturn(keyVaultClient);
  }

  @Test
  @Owner(developers = {UTKARSH, MLUKIC})
  @Category(UnitTests.class)
  public void testCreateSecret() {
    String plainText = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    ArgumentCaptor<KeyVaultSecret> captor = ArgumentCaptor.forClass(KeyVaultSecret.class);
    KeyVaultSecret keyVaultSecret = mockKeyVaultSecret(name, plainText);
    Response<KeyVaultSecret> response = new SimpleResponse(null, 200, null, keyVaultSecret);

    when(keyVaultClient.setSecretWithResponse(any(KeyVaultSecret.class), any(Context.class))).thenReturn(response);
    EncryptedRecord encryptedRecord =
        azureVaultEncryptor.createSecret(azureVaultConfig.getAccountId(), name, plainText, azureVaultConfig);
    assertThat(encryptedRecord).isNotNull();
    assertThat(encryptedRecord.getEncryptedValue()).isEqualTo(keyVaultSecret.getId().toCharArray());
    assertThat(encryptedRecord.getEncryptionKey()).isEqualTo(name);
    verify(keyVaultClient, times(1)).setSecretWithResponse(captor.capture(), any(Context.class));
    KeyVaultSecret keyVaultSecretCaptured = captor.getValue();
    assertThat(keyVaultSecretCaptured.getName()).isEqualTo(name);
    assertThat(keyVaultSecretCaptured.getValue()).isEqualTo(plainText);
  }

  @Test
  @Owner(developers = {UTKARSH, MLUKIC})
  @Category(UnitTests.class)
  public void testCreateSecret_shouldThrowException() {
    String plainText = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    when(keyVaultClient.setSecretWithResponse(any(KeyVaultSecret.class), any(Context.class)))
        .thenThrow(new KeyVaultErrorException("Dummy error", null));
    try {
      azureVaultEncryptor.createSecret(azureVaultConfig.getAccountId(), name, plainText, azureVaultConfig);
      fail("An error occurred while creating the secret.");
    } catch (SecretManagementDelegateException e) {
      assertThat(e.getCause()).isOfAnyClassIn(KeyVaultErrorException.class);
    }
  }

  @Test
  @Owner(developers = {RAGHAV_MURALI, MLUKIC})
  @Category(UnitTests.class)
  public void testCreateSecretShouldThrowKeyVaultException() {
    String plainText = UUIDGenerator.generateUuid();
    String name = "My_Azure_Secret";
    ObjectMapper objectMapper = new ObjectMapper();
    String errorJson = "{\"error\" : {\"code\" : \"BadParameter\","
        + " \"message\": \"The request URI contains an invalid name: My_Azure_Secret\"}}";
    try {
      KeyVaultError error = objectMapper.readValue(errorJson, KeyVaultError.class);
      when(keyVaultClient.setSecretWithResponse(any(KeyVaultSecret.class), any(Context.class)))
          .thenThrow(
              new KeyVaultErrorException("The request URI contains an invalid name: My_Azure_Secret", null, error));
    } catch (Exception e) {
      // unable to process json
      fail("Json processing error");
    }

    assertThatThrownBy(
        () -> azureVaultEncryptor.createSecret(azureVaultConfig.getAccountId(), name, plainText, azureVaultConfig))
        .isInstanceOf(SecretManagementDelegateException.class)
        .hasMessageContaining("The request URI contains an invalid name: My_Azure_Secret")
        .hasMessageContaining("BadParameter");
  }

  public void testCreateSecretAuthenticationException() {
    String plainText = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    keyVaultAuthenticatorMockedStatic.when(() -> KeyVaultAuthenticator.getSecretsClient(anyString(), any(), any()))
        .thenThrow(new MsalServiceException(
            "'38fca8d7-4dda-41d5-b106-e5d8712b733b' was not found in the directory 'Harness Inc'.", "AADXXXXXX"));

    assertThatThrownBy(
        () -> azureVaultEncryptor.createSecret(azureVaultConfig.getAccountId(), name, plainText, azureVaultConfig))
        .isInstanceOf(SecretManagementDelegateException.class)
        .hasMessageContaining("'38fca8d7-4dda-41d5-b106-e5d8712b733b' was not found in the directory 'Harness Inc'.")
        .hasCauseInstanceOf(MsalServiceException.class);
  }

  @Test
  @Owner(developers = {UTKARSH, MLUKIC})
  @Category(UnitTests.class)
  public void testUpdateSecret() {
    String plainText = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    ArgumentCaptor<KeyVaultSecret> captor = ArgumentCaptor.forClass(KeyVaultSecret.class);
    KeyVaultSecret keyVaultSecret = mockKeyVaultSecret(name, plainText);
    Response<KeyVaultSecret> response = new SimpleResponse(null, 200, null, keyVaultSecret);
    when(keyVaultClient.setSecretWithResponse(any(KeyVaultSecret.class), any(Context.class))).thenReturn(response);

    SyncPoller<DeletedSecret, Void> syncPoller = mock(SyncPoller.class);
    when(syncPoller.setPollInterval(any())).thenReturn(syncPoller);

    PollResponse<DeletedSecret> pollResponse = mock(PollResponse.class);
    when(syncPoller.waitUntil(any(Duration.class), any(LongRunningOperationStatus.class))).thenReturn(pollResponse);
    when(keyVaultClient.beginDeleteSecret(any())).thenReturn(syncPoller);

    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .name(UUIDGenerator.generateUuid())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .build();

    when(keyVaultClient.getSecret(oldRecord.getName()))
        .thenThrow(new ResourceNotFoundException("404 - resource not found", null));

    EncryptedRecord encryptedRecord =
        azureVaultEncryptor.updateSecret(azureVaultConfig.getAccountId(), name, plainText, oldRecord, azureVaultConfig);
    assertThat(encryptedRecord).isNotNull();
    assertThat(encryptedRecord.getEncryptedValue()).isEqualTo(keyVaultSecret.getId().toCharArray());
    assertThat(encryptedRecord.getEncryptionKey()).isEqualTo(name);
    verify(keyVaultClient, times(1)).setSecretWithResponse(captor.capture(), any());
    KeyVaultSecret keyVaultSecretCaptured = captor.getValue();
    assertThat(keyVaultSecretCaptured.getName()).isEqualTo(name);
    assertThat(keyVaultSecretCaptured.getValue()).isEqualTo(plainText);
    verify(keyVaultClient, times(1)).beginDeleteSecret(oldRecord.getName());
    verify(keyVaultClient, times(1)).getSecret(oldRecord.getName());
  }

  @Test
  @Owner(developers = {UTKARSH, MLUKIC})
  @Category(UnitTests.class)
  public void testUpdateSecret_shouldThrowException() {
    String plainText = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .name(UUIDGenerator.generateUuid())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .build();

    when(keyVaultClient.setSecretWithResponse(any(KeyVaultSecret.class), any(Context.class)))
        .thenThrow(new KeyVaultErrorException("Dummy error", null));
    try {
      azureVaultEncryptor.updateSecret(azureVaultConfig.getAccountId(), name, plainText, oldRecord, azureVaultConfig);
      fail("An error occurred while updating the secret.");
    } catch (SecretManagementDelegateException e) {
      assertThat(e.getCause()).isOfAnyClassIn(KeyVaultErrorException.class);
    }
  }

  @Test
  @Owner(developers = {RAGHAV_MURALI, MLUKIC})
  @Category(UnitTests.class)
  public void testUpdateSecretShouldThrowKeyVaultException() {
    String plainText = UUIDGenerator.generateUuid();
    String name = "My_Azure_Secret";
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .name("MyAzureSecret")
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .build();
    ObjectMapper objectMapper = new ObjectMapper();
    String errorJson = "{\"error\" : {\"code\" : \"BadParameter\","
        + " \"message\": \"The request URI contains an invalid name: My_Azure_Secret\"}}";
    try {
      KeyVaultError error = objectMapper.readValue(errorJson, KeyVaultError.class);
      when(keyVaultClient.setSecretWithResponse(any(KeyVaultSecret.class), any(Context.class)))
          .thenThrow(
              new KeyVaultErrorException("The request URI contains an invalid name: My_Azure_Secret", null, error));
    } catch (Exception e) {
      // unable to process json
      fail("Json processing error");
    }

    assertThatThrownBy(()
                           -> azureVaultEncryptor.updateSecret(
                               azureVaultConfig.getAccountId(), name, plainText, oldRecord, azureVaultConfig))
        .isInstanceOf(SecretManagementDelegateException.class)
        .hasMessageContaining("The request URI contains an invalid name: My_Azure_Secret")
        .hasMessageContaining("BadParameter");
  }

  @Test
  @Owner(developers = {RAGHAV_MURALI, MLUKIC})
  @Category(UnitTests.class)
  public void testUpdateSecretShouldHandleDeleteException() {
    String plainText = UUIDGenerator.generateUuid();
    String name = "My_Azure_Secret";
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .name("MyAzureSecret")
                                    .encryptionKey("MyAzureSecret")
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .build();

    KeyVaultSecret keyVaultSecret = mockKeyVaultSecret(name, plainText);
    Response<KeyVaultSecret> response = new SimpleResponse(null, 200, null, keyVaultSecret);
    when(keyVaultClient.setSecretWithResponse(any(KeyVaultSecret.class), any(Context.class))).thenReturn(response);

    ObjectMapper objectMapper = new ObjectMapper();
    String errorJson = "{\"error\" : {\"code\" : \"BadParameter\","
        + " \"message\": \"The request URI contains an invalid name: My_Azure_Secret\"}}";

    try {
      KeyVaultError error = objectMapper.readValue(errorJson, KeyVaultError.class);
      when(keyVaultClient.beginDeleteSecret(any()))
          .thenThrow(new KeyVaultErrorException("Secret not found", null, error));
    } catch (Exception e) {
      // unable to process json
      fail("Json processing error");
    }

    azureVaultEncryptor.updateSecret(azureVaultConfig.getAccountId(), name, plainText, oldRecord, azureVaultConfig);
  }

  @Test
  @Owner(developers = {RAGHAV_MURALI, MLUKIC})
  @Category(UnitTests.class)
  public void testUpdateSecretAuthenticationException() {
    String plainText = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .name(UUIDGenerator.generateUuid())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .build();
    keyVaultAuthenticatorMockedStatic.when(() -> KeyVaultAuthenticator.getSecretsClient(anyString(), any(), any()))
        .thenThrow(new MsalServiceException(
            "'38fca8d7-4dda-41d5-b106-e5d8712b733b' was not found in the directory 'Harness Inc'.", "AADXXXXXX"));

    assertThatThrownBy(()
                           -> azureVaultEncryptor.updateSecret(
                               azureVaultConfig.getAccountId(), name, plainText, oldRecord, azureVaultConfig))
        .isInstanceOf(SecretManagementDelegateException.class)
        .hasMessageContaining("'38fca8d7-4dda-41d5-b106-e5d8712b733b' was not found in the directory 'Harness Inc'.")
        .hasCauseInstanceOf(MsalServiceException.class);
  }

  @Test
  @Owner(developers = {UTKARSH, MLUKIC})
  @Category(UnitTests.class)
  public void testRenameSecret() {
    String existingSecretName = UUIDGenerator.generateUuid();
    String existingSecretEncryptedValue = UUIDGenerator.generateUuid();
    EncryptedRecord existingSecretEncryptedRecord =
        EncryptedRecordData.builder().name(existingSecretName).encryptionKey(existingSecretName).build();
    KeyVaultSecret existingSecretKeyVaultSecret =
        mockKeyVaultSecret(existingSecretName, existingSecretName, existingSecretEncryptedValue);
    Response<KeyVaultSecret> existingSecretResponse = new SimpleResponse(null, 200, null, existingSecretKeyVaultSecret);
    when(keyVaultClient.getSecretWithResponse(eq(existingSecretName), any(), any(Context.class)))
        .thenReturn(existingSecretResponse);

    String newSecretName = UUIDGenerator.generateUuid();
    ArgumentCaptor<KeyVaultSecret> captorForSet = ArgumentCaptor.forClass(KeyVaultSecret.class);
    KeyVaultSecret newSecretKeyVaultSecret =
        mockKeyVaultSecret(newSecretName, newSecretName, existingSecretEncryptedValue);
    Response<KeyVaultSecret> newSecretResponse = new SimpleResponse(null, 200, null, newSecretKeyVaultSecret);
    when(keyVaultClient.setSecretWithResponse(any(KeyVaultSecret.class), any(Context.class)))
        .thenReturn(newSecretResponse);

    SyncPoller<DeletedSecret, Void> syncPoller = mock(SyncPoller.class);
    when(syncPoller.setPollInterval(any())).thenReturn(syncPoller);
    PollResponse<DeletedSecret> pollResponse = mock(PollResponse.class);
    when(syncPoller.waitUntil(any(Duration.class), any(LongRunningOperationStatus.class))).thenReturn(pollResponse);
    when(keyVaultClient.beginDeleteSecret(eq(existingSecretName))).thenReturn(syncPoller);
    when(keyVaultClient.getSecret(eq(existingSecretName)))
        .thenThrow(new ResourceNotFoundException("404 - resource not found", null));

    EncryptedRecord encryptedRecord = azureVaultEncryptor.renameSecret(
        azureVaultConfig.getAccountId(), newSecretName, existingSecretEncryptedRecord, azureVaultConfig);
    assertThat(encryptedRecord).isNotNull();
    assertThat(encryptedRecord.getEncryptedValue()).isEqualTo(newSecretName.toCharArray());
    assertThat(encryptedRecord.getEncryptionKey()).isEqualTo(newSecretName);
    verify(keyVaultClient, times(1)).getSecretWithResponse(eq(existingSecretName), any(), any(Context.class));
    verify(keyVaultClient, times(1)).setSecretWithResponse(captorForSet.capture(), any(Context.class));
    KeyVaultSecret keyVaultSecretCaptured = captorForSet.getValue();
    assertThat(keyVaultSecretCaptured.getName()).isEqualTo(newSecretName);
    assertThat(keyVaultSecretCaptured.getValue()).isEqualTo(existingSecretEncryptedValue);
    verify(keyVaultClient, times(1)).beginDeleteSecret(eq(existingSecretName));
    verify(keyVaultClient, times(1)).getSecret(eq(existingSecretName));
  }

  @Test
  @Owner(developers = {UTKARSH, MLUKIC})
  @Category(UnitTests.class)
  public void testRenameSecret_shouldThrowException() {
    String name = UUIDGenerator.generateUuid();
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .name(UUIDGenerator.generateUuid())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .build();
    when(keyVaultClient.getSecretWithResponse(anyString(), anyString(), any(Context.class)))
        .thenThrow(new KeyVaultErrorException("error", null));
    assertThatThrownBy(
        () -> azureVaultEncryptor.renameSecret(azureVaultConfig.getAccountId(), name, oldRecord, azureVaultConfig))
        .isInstanceOf(SecretManagementDelegateException.class);
  }

  @Test
  @Owner(developers = {RAGHAV_MURALI, MLUKIC})
  @Category(UnitTests.class)
  public void testRenameSecretShouldThrowKeyVaultException() {
    String name = "My_Azure_Secret";
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .name("MyAzureSecret")
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .build();
    ObjectMapper objectMapper = new ObjectMapper();
    String errorJson = "{\"error\" : {\"code\" : \"BadParameter\","
        + " \"message\": \"The request URI contains an invalid name: My_Azure_Secret\"}}";
    try {
      KeyVaultError error = objectMapper.readValue(errorJson, KeyVaultError.class);

      when(keyVaultClient.getSecretWithResponse(anyString(), anyString(), any(Context.class)))
          .thenThrow(
              new KeyVaultErrorException("The request URI contains an invalid name: My_Azure_Secret", null, error));

      when(keyVaultClient.setSecretWithResponse(any(KeyVaultSecret.class), any(Context.class)))
          .thenThrow(
              new KeyVaultErrorException("The request URI contains an invalid name: My_Azure_Secret", null, error));
    } catch (Exception e) {
      // unable to process json
      fail("Json processing error");
    }

    assertThatThrownBy(
        () -> azureVaultEncryptor.renameSecret(azureVaultConfig.getAccountId(), name, oldRecord, azureVaultConfig))
        .isInstanceOf(SecretManagementDelegateException.class)
        .hasMessageContaining("The request URI contains an invalid name: My_Azure_Secret")
        .hasMessageContaining("BadParameter");
  }

  @Test
  @Owner(developers = {RAGHAV_MURALI, MLUKIC})
  @Category(UnitTests.class)
  public void testRenameSecretAuthenticationException() {
    String name = UUIDGenerator.generateUuid();
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .name(UUIDGenerator.generateUuid())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .build();
    keyVaultAuthenticatorMockedStatic.when(() -> KeyVaultAuthenticator.getSecretsClient(anyString(), any(), any()))
        .thenThrow(new MsalServiceException(
            "'38fca8d7-4dda-41d5-b106-e5d8712b733b' was not found in the directory 'Harness Inc'.", "AADXXXXXX"));

    assertThatThrownBy(
        () -> azureVaultEncryptor.renameSecret(azureVaultConfig.getAccountId(), name, oldRecord, azureVaultConfig))
        .isInstanceOf(SecretManagementDelegateException.class)
        .hasMessageContaining("'38fca8d7-4dda-41d5-b106-e5d8712b733b' was not found in the directory 'Harness Inc'.")
        .hasCauseInstanceOf(MsalServiceException.class);
  }

  @Test
  @Owner(developers = {UTKARSH, MLUKIC})
  @Category(UnitTests.class)
  public void testFetchSecret() {
    String name = UUIDGenerator.generateUuid();
    String plainText = UUIDGenerator.generateUuid();
    EncryptedRecord record = EncryptedRecordData.builder().name(name).path(UUIDGenerator.generateUuid()).build();
    KeyVaultSecret keyVaultSecret = mockKeyVaultSecret(name, plainText);
    Response<KeyVaultSecret> response = new SimpleResponse(null, 200, null, keyVaultSecret);
    when(keyVaultClient.getSecretWithResponse(any(), any(), any(Context.class))).thenReturn(response);
    char[] value = azureVaultEncryptor.fetchSecretValue(azureVaultConfig.getAccountId(), record, azureVaultConfig);
    assertThat(value).isEqualTo(plainText.toCharArray());
  }

  @Test
  @Owner(developers = {UTKARSH, MLUKIC})
  @Category(UnitTests.class)
  public void testFetchSecret_shouldThrowException() {
    String name = UUIDGenerator.generateUuid();
    String plainText = UUIDGenerator.generateUuid();
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .name(name)
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .encryptedValue(plainText.toCharArray())
                                    .build();
    when(keyVaultClient.getSecretWithResponse(any(), any(), any(Context.class)))
        .thenThrow(new KeyVaultErrorException("error", null));

    assertThatThrownBy(
        () -> azureVaultEncryptor.fetchSecretValue(azureVaultConfig.getAccountId(), oldRecord, azureVaultConfig))
        .isInstanceOf(SecretManagementDelegateException.class);
  }

  @Test
  @Owner(developers = {RAGHAV_MURALI, MLUKIC})
  @Category(UnitTests.class)
  public void testFetchSecretShouldThrowKeyVaultException() {
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .name("MyAzureSecret")
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .build();
    ObjectMapper objectMapper = new ObjectMapper();
    String errorJson = "{\"error\" : {\"code\" : \"BadParameter\","
        + " \"message\": \"The request URI contains an invalid name: My_Azure_Secret\"}}";
    try {
      KeyVaultError error = objectMapper.readValue(errorJson, KeyVaultError.class);

      when(keyVaultClient.getSecretWithResponse(any(), any(), any(Context.class)))
          .thenThrow(
              new KeyVaultErrorException("The request URI contains an invalid name: My_Azure_Secret", null, error));
    } catch (Exception e) {
      // unable to process json
      fail("Json processing error");
    }

    assertThatThrownBy(
        () -> azureVaultEncryptor.fetchSecretValue(azureVaultConfig.getAccountId(), oldRecord, azureVaultConfig))
        .isInstanceOf(SecretManagementDelegateException.class)
        .hasMessageContaining("The request URI contains an invalid name: My_Azure_Secret")
        .hasMessageContaining("BadParameter");
  }

  @Test
  @Owner(developers = {RAGHAV_MURALI, MLUKIC})
  @Category(UnitTests.class)
  public void testFetchSecretAuthenticationException() {
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .name(UUIDGenerator.generateUuid())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .build();

    keyVaultAuthenticatorMockedStatic.when(() -> KeyVaultAuthenticator.getSecretsClient(anyString(), any(), any()))
        .thenThrow(new MsalServiceException(
            "'38fca8d7-4dda-41d5-b106-e5d8712b733b' was not found in the directory 'Harness Inc'.", "AADXXXXXX"));

    assertThatThrownBy(
        () -> azureVaultEncryptor.fetchSecretValue(azureVaultConfig.getAccountId(), oldRecord, azureVaultConfig))
        .isInstanceOf(SecretManagementDelegateException.class)
        .hasMessageContaining("'38fca8d7-4dda-41d5-b106-e5d8712b733b' was not found in the directory 'Harness Inc'.")
        .hasCauseInstanceOf(MsalServiceException.class);
  }

  @Test
  @Owner(developers = {RAGHAV_MURALI, MLUKIC})
  @Category(UnitTests.class)
  public void testDeleteSecretAuthenticationException() {
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .name(UUIDGenerator.generateUuid())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .build();
    keyVaultAuthenticatorMockedStatic.when(() -> KeyVaultAuthenticator.getSecretsClient(anyString(), any(), any()))
        .thenThrow(new MsalServiceException(
            "'38fca8d7-4dda-41d5-b106-e5d8712b733b' was not found in the directory 'Harness Inc'.", "AADXXXXXX"));

    assertThatThrownBy(
        () -> azureVaultEncryptor.deleteSecret(azureVaultConfig.getAccountId(), oldRecord, azureVaultConfig))
        .isInstanceOf(SecretManagementDelegateException.class)
        .hasMessageContaining("'38fca8d7-4dda-41d5-b106-e5d8712b733b' was not found in the directory 'Harness Inc'.")
        .hasCauseInstanceOf(MsalServiceException.class);
  }

  @Test
  @Owner(developers = {RAGHAV_MURALI, MLUKIC})
  @Category(UnitTests.class)
  public void testDeleteSecretOtherException() {
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .name(UUIDGenerator.generateUuid())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .build();

    ObjectMapper objectMapper = new ObjectMapper();
    String errorJson = "{\"error\" : {\"code\" : \"NotFound\","
        + " \"message\": \"The secret with name My_Azure_Secret doesn't exist. \"}}";
    try {
      KeyVaultError error = objectMapper.readValue(errorJson, KeyVaultError.class);

      when(keyVaultClient.beginDeleteSecret(any()))
          .thenThrow(new KeyVaultErrorException("Secret not found", null, error));
    } catch (Exception e) {
      // unable to process json
      fail("Json processing error");
    }

    assertThatThrownBy(
        () -> azureVaultEncryptor.deleteSecret(azureVaultConfig.getAccountId(), oldRecord, azureVaultConfig))
        .isInstanceOf(SecretManagementDelegateException.class)
        .hasMessageContaining("Secret not found")
        .hasCauseInstanceOf(KeyVaultErrorException.class);
  }

  private KeyVaultSecret mockKeyVaultSecret(String name, String value) {
    return mockKeyVaultSecret(name, name, value);
  }

  private KeyVaultSecret mockKeyVaultSecret(String name, String key, String value) {
    KeyVaultSecret keyVaultSecret = spy(KeyVaultSecret.class);
    doReturn(name).when(keyVaultSecret).getName();
    doReturn(value).when(keyVaultSecret).getValue();
    doReturn(key).when(keyVaultSecret).getId();
    return keyVaultSecret;
  }
}
