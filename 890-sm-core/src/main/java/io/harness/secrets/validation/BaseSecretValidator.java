/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secrets.validation;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.SecretManagerCapabilities.CREATE_FILE_SECRET;
import static io.harness.beans.SecretManagerCapabilities.CREATE_INLINE_SECRET;
import static io.harness.beans.SecretManagerCapabilities.CREATE_PARAMETERIZED_SECRET;
import static io.harness.beans.SecretManagerCapabilities.CREATE_REFERENCE_SECRET;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eraro.ErrorCode.FILE_SIZE_EXCEEDS_LIMIT;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.exception.WingsException.USER;

import static software.wings.settings.SettingVariableTypes.CONFIG_FILE;
import static software.wings.settings.SettingVariableTypes.SECRET_TEXT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EncryptedData;
import io.harness.beans.HarnessSecret;
import io.harness.beans.SecretFile;
import io.harness.beans.SecretManagerConfig;
import io.harness.beans.SecretText;
import io.harness.beans.SecretUpdateData;
import io.harness.exception.SecretManagementException;
import io.harness.secrets.SecretsDao;
import io.harness.security.encryption.EncryptionType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.validation.executable.ValidateOnExecution;

@ValidateOnExecution
@Singleton
@Slf4j
@OwnedBy(PL)
public class BaseSecretValidator implements SecretValidator {
  private final String ILLEGAL_CHARACTERS = "[~!@#$%^&*'\"/?<>,;.]";
  private final SecretsDao secretsDao;

  @Inject
  public BaseSecretValidator(SecretsDao secretsDao) {
    this.secretsDao = secretsDao;
  }

  protected void validateSecretText(String accountId, SecretText secretText, SecretManagerConfig secretManagerConfig) {
    encryptedTextEmptyCheck(secretText);
    isSMCapableToEncryptText(secretText, secretManagerConfig);
  }

  protected void validateSecretFile(String accountId, SecretFile secretFile, SecretManagerConfig secretManagerConfig) {
    validateFileIsNotEmpty(secretFile.getFileContent());
    isSMCapabletoEncryptFile(secretManagerConfig);
  }

  protected void validateSecretTextUpdate(
      SecretText secretText, EncryptedData existingRecord, SecretManagerConfig secretManagerConfig) {
    if ((existingRecord.isInlineSecret() && !secretText.isInlineSecret())
        || (existingRecord.isReferencedSecret() && !secretText.isReferencedSecret())
        || (existingRecord.isParameterizedSecret() && !secretText.isParameterizedSecret())) {
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, "Cannot change the type of secret", USER);
    }
  }

  protected void validateSecretFileUpdate(
      SecretFile secretFile, EncryptedData existingRecord, SecretManagerConfig secretManagerConfig) {
    // Method for extensions to override
  }

  private void checkForDuplicateName(String accountId, String name) {
    if (secretsDao.getSecretByName(accountId, name).isPresent()) {
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR,
          "A secret exists with the proposed secret name in your account. Please choose a different name", USER);
    }
  }

  private void isSMCapabletoEncryptFile(SecretManagerConfig secretManagerConfig) {
    if (!secretManagerConfig.getSecretManagerCapabilities().contains(CREATE_FILE_SECRET)) {
      throw new SecretManagementException(
          SECRET_MANAGEMENT_ERROR, "Cannot create an encrypted file with the selected secret manager", USER);
    }
  }

  private void isSMCapableToEncryptText(SecretText secretText, SecretManagerConfig secretManagerConfig) {
    if (secretText.isReferencedSecret()
        && !secretManagerConfig.getSecretManagerCapabilities().contains(CREATE_REFERENCE_SECRET)) {
      throw new SecretManagementException(
          SECRET_MANAGEMENT_ERROR, "Cannot create a referenced secret with the selected secret manager", USER);
    }
    if (secretText.isInlineSecret()
        && !secretManagerConfig.getSecretManagerCapabilities().contains(CREATE_INLINE_SECRET)) {
      throw new SecretManagementException(
          SECRET_MANAGEMENT_ERROR, "Cannot create an inline secret with the selected secret manager", USER);
    }
    if (secretText.isParameterizedSecret()
        && !secretManagerConfig.getSecretManagerCapabilities().contains(CREATE_PARAMETERIZED_SECRET)) {
      throw new SecretManagementException(
          SECRET_MANAGEMENT_ERROR, "Cannot create a parameterized secret with the selected secret manager", USER);
    }
  }

  private void encryptedTextEmptyCheck(SecretText secretText) {
    if (!secretText.isReferencedSecret() && !secretText.isParameterizedSecret() && isEmpty(secretText.getValue())) {
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, "Cannot create empty secret", USER);
    }
  }

  private void validateSecretName(String name) {
    if (isEmpty(name)) {
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, "Secret name cannot be empty", USER);
    }
    String[] parts = name.split(ILLEGAL_CHARACTERS, 2);
    if (parts.length > 1) {
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR,
          "Secret name should not have any of the following characters " + ILLEGAL_CHARACTERS, USER);
    }
  }

  private void validateFileIsNotEmpty(byte[] fileContent) {
    if (isEmpty(fileContent)) {
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, "Encrypted file is empty", USER);
    }
  }

  private void isValidSecretUpdate(SecretUpdateData secretUpdateData) {
    HarnessSecret harnessSecret = secretUpdateData.getUpdatedSecret();
    EncryptedData existingRecord = secretUpdateData.getExistingRecord();

    if (existingRecord.getEncryptionType() == EncryptionType.LOCAL
        && !harnessSecret.getKmsId().equals(existingRecord.getAccountId())) {
      throw new SecretManagementException(
          SECRET_MANAGEMENT_ERROR, "Cannot change secret manager while updating secret", USER);
    }

    if (existingRecord.getEncryptionType() != EncryptionType.LOCAL
        && !harnessSecret.getKmsId().equals(existingRecord.getKmsId())) {
      throw new SecretManagementException(
          SECRET_MANAGEMENT_ERROR, "Cannot change secret manager while updating secret", USER);
    }

    if (existingRecord.getType() == CONFIG_FILE && harnessSecret instanceof SecretText) {
      throw new SecretManagementException(
          SECRET_MANAGEMENT_ERROR, "Cannot convert encrypted file to encrypted text.", USER);
    }

    if (existingRecord.getType() == SECRET_TEXT && harnessSecret instanceof SecretFile) {
      throw new SecretManagementException(
          SECRET_MANAGEMENT_ERROR, "Cannot convert encrypted text to encrypted file.", USER);
    }
  }

  private void validateScopes(HarnessSecret secret) {
    if (secret.isInheritScopesFromSM() && (secret.isScopedToAccount() || secret.getUsageRestrictions() != null)) {
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR,
          "Invalid scopes, please either select to inherit scopes from Secret Manager or provide your own scopes. You cannot do both.",
          USER);
    }
  }

  @Override
  public void validateSecret(String accountId, HarnessSecret secret, SecretManagerConfig secretManagerConfig) {
    log.info("[JEN]Secret name is {}", secret.getName());
    validateSecretName(secret.getName());
    checkForDuplicateName(accountId, secret.getName());
    validateScopes(secret);
    if (secret instanceof SecretText) {
      SecretText secretText = (SecretText) secret;
      validateSecretText(accountId, secretText, secretManagerConfig);
    } else {
      SecretFile secretFile = (SecretFile) secret;
      validateSecretFile(accountId, secretFile, secretManagerConfig);
    }
  }

  @Override
  public void validateSecretUpdate(
      HarnessSecret secret, EncryptedData existingRecord, SecretManagerConfig secretManagerConfig) {
    SecretUpdateData secretUpdateData = new SecretUpdateData(secret, existingRecord);
    validateScopes(secret);
    isValidSecretUpdate(secretUpdateData);
    if (secretUpdateData.isNameChanged()) {
      validateSecretName(secretUpdateData.getUpdatedSecret().getName());
      checkForDuplicateName(existingRecord.getAccountId(), secretUpdateData.getUpdatedSecret().getName());
    }
    if (secret instanceof SecretText) {
      SecretText secretText = (SecretText) secret;
      validateSecretTextUpdate(secretText, existingRecord, secretManagerConfig);
    } else {
      SecretFile secretFile = (SecretFile) secret;
      validateSecretFileUpdate(secretFile, existingRecord, secretManagerConfig);
    }
  }

  public static void validateFileWithinSizeLimit(long requestContentLength, long maximumFileSize) {
    if (maximumFileSize < requestContentLength) {
      Map<String, String> params = new HashMap<>();
      params.put("size", maximumFileSize / (1024 * 1024) + " MB");
      throw new SecretManagementException(FILE_SIZE_EXCEEDS_LIMIT, null, USER, Collections.unmodifiableMap(params));
    }
  }
}
