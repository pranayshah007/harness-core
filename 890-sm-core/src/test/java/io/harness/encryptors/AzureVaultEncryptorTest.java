/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.encryptors;

import static io.harness.rule.OwnerRule.RAGHAV_MURALI;
import static io.harness.rule.OwnerRule.UTKARSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

import io.harness.CategoryTest;
import io.harness.azure.AzureEnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.concurrent.HTimeLimiter;
import io.harness.data.structure.UUIDGenerator;
import io.harness.encryptors.clients.AzureVaultEncryptor;
import io.harness.exception.SecretManagementDelegateException;
import io.harness.helpers.ext.azure.KeyVaultADALAuthenticator;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionType;

import software.wings.beans.AzureVaultConfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.aad.adal4j.AuthenticationException;
import com.microsoft.azure.keyvault.KeyVaultClient;
import com.microsoft.azure.keyvault.models.KeyVaultError;
import com.microsoft.azure.keyvault.models.KeyVaultErrorException;
import com.microsoft.azure.keyvault.models.SecretBundle;
import com.microsoft.azure.keyvault.requests.SetSecretRequest;
import com.microsoft.rest.RestException;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@Slf4j
@RunWith(PowerMockRunner.class)
@PrepareForTest({KeyVaultADALAuthenticator.class, KeyVaultClient.class})
@PowerMockIgnore({"javax.security.*", "org.apache.http.conn.ssl.", "javax.net.ssl.", "javax.crypto.*", "sun.*"})
public class AzureVaultEncryptorTest extends CategoryTest {
  private AzureVaultEncryptor azureVaultEncryptor;
  private AzureVaultConfig azureVaultConfig;
  private KeyVaultClient keyVaultClient;
  private KeyVaultADALAuthenticator keyVaultAuthenticator;

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
    keyVaultClient = PowerMockito.mock(KeyVaultClient.class);
    keyVaultAuthenticator = PowerMockito.mock(KeyVaultADALAuthenticator.class);
    mockStatic(KeyVaultADALAuthenticator.class);
    when(KeyVaultADALAuthenticator.getClient(azureVaultConfig.getClientId(), azureVaultConfig.getSecretKey()))
        .thenAnswer(invocationOnMock -> keyVaultClient);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testCreateSecret() {
    String plainText = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    ArgumentCaptor<SetSecretRequest> captor = ArgumentCaptor.forClass(SetSecretRequest.class);
    SecretBundle secretBundle = new SecretBundle().withId(UUIDGenerator.generateUuid());
    when(keyVaultClient.setSecret(any(SetSecretRequest.class))).thenReturn(secretBundle);
    EncryptedRecord encryptedRecord =
        azureVaultEncryptor.createSecret(azureVaultConfig.getAccountId(), name, plainText, azureVaultConfig);
    assertThat(encryptedRecord).isNotNull();
    assertThat(encryptedRecord.getEncryptedValue()).isEqualTo(secretBundle.id().toCharArray());
    assertThat(encryptedRecord.getEncryptionKey()).isEqualTo(name);
    verify(keyVaultClient, times(1)).setSecret(captor.capture());
    SetSecretRequest setSecretRequest = captor.getValue();
    assertThat(setSecretRequest.vaultBaseUrl()).isEqualTo(azureVaultConfig.getEncryptionServiceUrl());
    assertThat(setSecretRequest.secretName()).isEqualTo(name);
    assertThat(setSecretRequest.value()).isEqualTo(plainText);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testCreateSecret_shouldThrowException() {
    String plainText = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    when(keyVaultClient.setSecret(any(SetSecretRequest.class)))
        .thenThrow(new KeyVaultErrorException("Dummy error", null));
    try {
      azureVaultEncryptor.createSecret(azureVaultConfig.getAccountId(), name, plainText, azureVaultConfig);
      fail("An error occurred while creating the secret.");
    } catch (RestException e) {
      // this catch block is to satisfy the error handling framework
    } catch (SecretManagementDelegateException e) {
      assertThat(e.getCause()).isOfAnyClassIn(KeyVaultErrorException.class);
    }
  }

  @Test
  @Owner(developers = RAGHAV_MURALI)
  @Category(UnitTests.class)
  public void testCreateSecretShouldThrowKeyVaultException() {
    String plainText = UUIDGenerator.generateUuid();
    String name = "My_Azure_Secret";
    ObjectMapper objectMapper = new ObjectMapper();
    String errorJson = "{\"error\" : {\"code\" : \"BadParameter\","
        + " \"message\": \"The request URI contains an invalid name: My_Azure_Secret\"}}";
    try {
      KeyVaultError error = objectMapper.readValue(errorJson, KeyVaultError.class);
      when(keyVaultClient.setSecret(any(SetSecretRequest.class)))
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
    when(keyVaultAuthenticator.getClient(anyString(), anyString()))
        .thenThrow(new AuthenticationException(
            "'38fca8d7-4dda-41d5-b106-e5d8712b733b' was not found in the directory 'Harness Inc'."));

    assertThatThrownBy(
        () -> azureVaultEncryptor.createSecret(azureVaultConfig.getAccountId(), name, plainText, azureVaultConfig))
        .isInstanceOf(SecretManagementDelegateException.class)
        .hasMessageContaining("'38fca8d7-4dda-41d5-b106-e5d8712b733b' was not found in the directory 'Harness Inc'.")
        .hasCauseInstanceOf(AuthenticationException.class);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testUpdateSecret() {
    String plainText = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    ArgumentCaptor<SetSecretRequest> captor = ArgumentCaptor.forClass(SetSecretRequest.class);
    SecretBundle secretBundle = new SecretBundle().withId(UUIDGenerator.generateUuid());
    when(keyVaultClient.setSecret(any(SetSecretRequest.class))).thenReturn(secretBundle);
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .name(UUIDGenerator.generateUuid())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .build();
    EncryptedRecord encryptedRecord =
        azureVaultEncryptor.updateSecret(azureVaultConfig.getAccountId(), name, plainText, oldRecord, azureVaultConfig);
    assertThat(encryptedRecord).isNotNull();
    assertThat(encryptedRecord.getEncryptedValue()).isEqualTo(secretBundle.id().toCharArray());
    assertThat(encryptedRecord.getEncryptionKey()).isEqualTo(name);
    verify(keyVaultClient, times(1)).setSecret(captor.capture());
    SetSecretRequest setSecretRequest = captor.getValue();
    assertThat(setSecretRequest.vaultBaseUrl()).isEqualTo(azureVaultConfig.getEncryptionServiceUrl());
    assertThat(setSecretRequest.secretName()).isEqualTo(name);
    assertThat(setSecretRequest.value()).isEqualTo(plainText);
    verify(keyVaultClient, times(1))
        .deleteSecret(azureVaultConfig.getEncryptionServiceUrl(), oldRecord.getEncryptionKey());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testUpdateSecret_shouldThrowException() {
    String plainText = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .name(UUIDGenerator.generateUuid())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .build();
    when(keyVaultClient.setSecret(any(SetSecretRequest.class)))
        .thenThrow(new KeyVaultErrorException("Dummy error", null));
    try {
      azureVaultEncryptor.updateSecret(azureVaultConfig.getAccountId(), name, plainText, oldRecord, azureVaultConfig);
      fail("An error occurred while updating the secret.");
    } catch (RestException e) {
      // this catch block is to satisfy the error handling framework
    } catch (SecretManagementDelegateException e) {
      assertThat(e.getCause()).isOfAnyClassIn(KeyVaultErrorException.class);
    }
  }

  @Test
  @Owner(developers = RAGHAV_MURALI)
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
      when(keyVaultClient.setSecret(any(SetSecretRequest.class)))
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
  @Owner(developers = RAGHAV_MURALI)
  @Category(UnitTests.class)
  public void testUpdateSecretShouldHandleDeleteException() {
    String plainText = UUIDGenerator.generateUuid();
    String name = "My_Azure_Secret";
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .name("MyAzureSecret")
                                    .encryptionKey("MyAzureSecret")
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .build();
    SecretBundle secretBundle = new SecretBundle().withId(UUIDGenerator.generateUuid());
    when(keyVaultClient.setSecret(any(SetSecretRequest.class))).thenReturn(secretBundle);

    ObjectMapper objectMapper = new ObjectMapper();
    String errorJson = "{\"error\" : {\"code\" : \"BadParameter\","
        + " \"message\": \"The request URI contains an invalid name: My_Azure_Secret\"}}";

    try {
      KeyVaultError error = objectMapper.readValue(errorJson, KeyVaultError.class);
      when(keyVaultClient.deleteSecret(anyString(), anyString()))
          .thenThrow(new KeyVaultErrorException("Secret not found", null, error));
    } catch (Exception e) {
      // unable to process json
      fail("Json processing error");
    }

    azureVaultEncryptor.updateSecret(azureVaultConfig.getAccountId(), name, plainText, oldRecord, azureVaultConfig);
  }

  @Test
  @Owner(developers = RAGHAV_MURALI)
  @Category(UnitTests.class)
  public void testUpdateSecretAuthenticationException() {
    String plainText = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .name(UUIDGenerator.generateUuid())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .build();
    when(keyVaultAuthenticator.getClient(anyString(), anyString()))
        .thenThrow(new AuthenticationException(
            "'38fca8d7-4dda-41d5-b106-e5d8712b733b' was not found in the directory 'Harness Inc'."));

    assertThatThrownBy(()
                           -> azureVaultEncryptor.updateSecret(
                               azureVaultConfig.getAccountId(), name, plainText, oldRecord, azureVaultConfig))
        .isInstanceOf(SecretManagementDelegateException.class)
        .hasMessageContaining("'38fca8d7-4dda-41d5-b106-e5d8712b733b' was not found in the directory 'Harness Inc'.")
        .hasCauseInstanceOf(AuthenticationException.class);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRenameSecret() {
    String plainText = UUIDGenerator.generateUuid();
    String name = UUIDGenerator.generateUuid();
    ArgumentCaptor<SetSecretRequest> captor = ArgumentCaptor.forClass(SetSecretRequest.class);
    SecretBundle secretBundle = new SecretBundle().withId(UUIDGenerator.generateUuid());
    when(keyVaultClient.setSecret(any(SetSecretRequest.class))).thenReturn(secretBundle);
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .name(UUIDGenerator.generateUuid())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .build();
    SecretBundle oldSecretBundle = new SecretBundle().withValue(plainText);
    when(keyVaultClient.getSecret(azureVaultConfig.getEncryptionServiceUrl(), oldRecord.getEncryptionKey(), ""))
        .thenReturn(oldSecretBundle);
    EncryptedRecord encryptedRecord =
        azureVaultEncryptor.renameSecret(azureVaultConfig.getAccountId(), name, oldRecord, azureVaultConfig);
    assertThat(encryptedRecord).isNotNull();
    assertThat(encryptedRecord.getEncryptedValue()).isEqualTo(secretBundle.id().toCharArray());
    assertThat(encryptedRecord.getEncryptionKey()).isEqualTo(name);
    verify(keyVaultClient, times(1)).setSecret(captor.capture());
    SetSecretRequest setSecretRequest = captor.getValue();
    assertThat(setSecretRequest.vaultBaseUrl()).isEqualTo(azureVaultConfig.getEncryptionServiceUrl());
    assertThat(setSecretRequest.secretName()).isEqualTo(name);
    assertThat(setSecretRequest.value()).isEqualTo(plainText);
    verify(keyVaultClient, times(1))
        .deleteSecret(azureVaultConfig.getEncryptionServiceUrl(), oldRecord.getEncryptionKey());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRenameSecret_shouldThrowException() {
    String name = UUIDGenerator.generateUuid();
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .name(UUIDGenerator.generateUuid())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .build();
    when(keyVaultClient.getSecret(azureVaultConfig.getEncryptionServiceUrl(), oldRecord.getEncryptionKey(), ""))
        .thenThrow(new KeyVaultErrorException("error", null));
    assertThatThrownBy(
        () -> azureVaultEncryptor.renameSecret(azureVaultConfig.getAccountId(), name, oldRecord, azureVaultConfig))
        .isInstanceOf(SecretManagementDelegateException.class);
  }

  @Test
  @Owner(developers = RAGHAV_MURALI)
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

      when(keyVaultClient.getSecret(anyString(), anyString(), anyString()))
          .thenThrow(
              new KeyVaultErrorException("The request URI contains an invalid name: My_Azure_Secret", null, error));

      when(keyVaultClient.setSecret(any(SetSecretRequest.class)))
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
  @Owner(developers = RAGHAV_MURALI)
  @Category(UnitTests.class)
  public void testRenameSecretAuthenticationException() {
    String name = UUIDGenerator.generateUuid();
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .name(UUIDGenerator.generateUuid())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .build();
    when(keyVaultAuthenticator.getClient(anyString(), anyString()))
        .thenThrow(new AuthenticationException(
            "'38fca8d7-4dda-41d5-b106-e5d8712b733b' was not found in the directory 'Harness Inc'."));

    assertThatThrownBy(
        () -> azureVaultEncryptor.renameSecret(azureVaultConfig.getAccountId(), name, oldRecord, azureVaultConfig))
        .isInstanceOf(SecretManagementDelegateException.class)
        .hasMessageContaining("'38fca8d7-4dda-41d5-b106-e5d8712b733b' was not found in the directory 'Harness Inc'.")
        .hasCauseInstanceOf(AuthenticationException.class);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testFetchSecret() {
    String plainText = UUIDGenerator.generateUuid();
    EncryptedRecord record =
        EncryptedRecordData.builder().name(UUIDGenerator.generateUuid()).path(UUIDGenerator.generateUuid()).build();
    SecretBundle secretBundle = new SecretBundle().withValue(plainText);
    when(keyVaultClient.getSecret(azureVaultConfig.getEncryptionServiceUrl(), record.getPath(), ""))
        .thenReturn(secretBundle);
    char[] value = azureVaultEncryptor.fetchSecretValue(azureVaultConfig.getAccountId(), record, azureVaultConfig);
    assertThat(value).isEqualTo(plainText.toCharArray());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testFetchSecret_shouldThrowException() {
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .name(UUIDGenerator.generateUuid())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .build();
    when(keyVaultClient.getSecret(azureVaultConfig.getEncryptionServiceUrl(), oldRecord.getEncryptionKey(), ""))
        .thenThrow(new KeyVaultErrorException("error", null));

    assertThatThrownBy(
        () -> azureVaultEncryptor.fetchSecretValue(azureVaultConfig.getAccountId(), oldRecord, azureVaultConfig))
        .isInstanceOf(SecretManagementDelegateException.class);
  }

  @Test
  @Owner(developers = RAGHAV_MURALI)
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

      when(keyVaultClient.getSecret(anyString(), anyString(), anyString()))
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
  @Owner(developers = RAGHAV_MURALI)
  @Category(UnitTests.class)
  public void testFetchSecretAuthenticationException() {
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .name(UUIDGenerator.generateUuid())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .build();
    when(keyVaultAuthenticator.getClient(anyString(), anyString()))
        .thenThrow(new AuthenticationException(
            "'38fca8d7-4dda-41d5-b106-e5d8712b733b' was not found in the directory 'Harness Inc'."));

    assertThatThrownBy(
        () -> azureVaultEncryptor.fetchSecretValue(azureVaultConfig.getAccountId(), oldRecord, azureVaultConfig))
        .isInstanceOf(SecretManagementDelegateException.class)
        .hasMessageContaining("'38fca8d7-4dda-41d5-b106-e5d8712b733b' was not found in the directory 'Harness Inc'.")
        .hasCauseInstanceOf(AuthenticationException.class);
  }

  @Test
  @Owner(developers = RAGHAV_MURALI)
  @Category(UnitTests.class)
  public void testDeleteSecretAuthenticationException() {
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .name(UUIDGenerator.generateUuid())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .build();
    when(keyVaultAuthenticator.getClient(anyString(), anyString()))
        .thenThrow(new AuthenticationException(
            "'38fca8d7-4dda-41d5-b106-e5d8712b733b' was not found in the directory 'Harness Inc'."));

    assertThatThrownBy(
        () -> azureVaultEncryptor.deleteSecret(azureVaultConfig.getAccountId(), oldRecord, azureVaultConfig))
        .isInstanceOf(SecretManagementDelegateException.class)
        .hasMessageContaining("'38fca8d7-4dda-41d5-b106-e5d8712b733b' was not found in the directory 'Harness Inc'.")
        .hasCauseInstanceOf(AuthenticationException.class);
  }

  @Test
  @Owner(developers = RAGHAV_MURALI)
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

      when(keyVaultClient.deleteSecret(anyString(), anyString()))
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
}
