/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secrets.validation.validators;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.GCP_SECRET_OPERATION_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.exception.WingsException.USER_SRE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EncryptedData;
import io.harness.beans.HarnessSecret;
import io.harness.beans.SecretFile;
import io.harness.beans.SecretManagerConfig;
import io.harness.beans.SecretText;
import io.harness.exception.SecretManagementException;
import io.harness.secrets.SecretsDao;
import io.harness.secrets.validation.BaseSecretValidator;
import io.harness.security.encryption.common.EncryptedRecord;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.regex.Pattern;
import javax.validation.executable.ValidateOnExecution;

@ValidateOnExecution
@Singleton
@OwnedBy(PL)
public class GcpSecretManagerValidator extends BaseSecretValidator {
  private static final Pattern GCP_SECRET_NAME_PATTERN = Pattern.compile("^[\\w-_]+$");
  private static final int GCP_SECRET_CONTENT_SIZE_LIMIT = 65536;
  private static final String GCP_SECRET_NAME_ERROR =
      "Secret names can only contain English letters (A-Z), numbers (0-9), dashes (-), and underscores (_)";
  private static final String GCP_SECRET_FILE_SIZE_ERROR = "File size limit is 64 KiB";
  private static final String GCP_SECRET_CONTENT_SIZE_ERROR =
      "Gcp Secrets Manager limits secret value to " + GCP_SECRET_CONTENT_SIZE_LIMIT + " bytes.";

  @Inject
  public GcpSecretManagerValidator(SecretsDao secretsDao) {
    super(secretsDao);
  }

  private void verifyInlineSecret(SecretText secretText) {
    validateSecretName(secretText.getName());
    verifyValueSizeWithinLimit(secretText.getValue());
  }

  private void validateSecretName(String name) {
    if (!GCP_SECRET_NAME_PATTERN.matcher(name).find()) {
      throw new SecretManagementException(GCP_SECRET_OPERATION_ERROR, GCP_SECRET_NAME_ERROR, USER_SRE);
    }
  }

  private void verifyFileSizeWithinLimit(byte[] fileContent) {
    if (isNotEmpty(fileContent) && fileContent.length > GCP_SECRET_CONTENT_SIZE_LIMIT) {
      throw new SecretManagementException(GCP_SECRET_OPERATION_ERROR, GCP_SECRET_FILE_SIZE_ERROR, USER_SRE);
    }
  }

  private void verifyValueSizeWithinLimit(String secretText) {
    if (isNotEmpty(secretText) && secretText.getBytes().length > GCP_SECRET_CONTENT_SIZE_LIMIT) {
      throw new SecretManagementException(GCP_SECRET_OPERATION_ERROR, GCP_SECRET_CONTENT_SIZE_ERROR, USER_SRE);
    }
  }

  @Override
  public void validateSecretText(String accountId, SecretText secretText, SecretManagerConfig secretManagerConfig) {
    super.validateSecretText(accountId, secretText, secretManagerConfig);
    if (secretText.isInlineSecret()) {
      verifyInlineSecret(secretText);
    }
  }

  @Override
  public void validateSecretTextUpdate(
      SecretText secretText, EncryptedData existingRecord, SecretManagerConfig secretManagerConfig) {
    super.validateSecretTextUpdate(secretText, existingRecord, secretManagerConfig);
    if (secretText.isInlineSecret()) {
      verifyInlineSecret(secretText);
    } else {
      checkIfSecretCanBeUpdated(secretText, existingRecord);
    }
  }

  private void checkIfSecretCanBeUpdated(HarnessSecret secretText, EncryptedRecord existingRecord) {
    String secretName =
        existingRecord.getEncryptionKey() != null ? existingRecord.getEncryptionKey() : existingRecord.getName();
    if (isEmpty(secretText.getName())) {
      throw new SecretManagementException(GCP_SECRET_OPERATION_ERROR, "Null or Empty Secret Name is not allowed", USER);
    } else if (!secretText.getName().equals(secretName)) {
      throw new SecretManagementException(
          GCP_SECRET_OPERATION_ERROR, "Renaming Secrets in GCP Secret Manager is not supported", USER);
    }
  }

  @Override
  public void validateSecretFile(String accountId, SecretFile secretFile, SecretManagerConfig secretManagerConfig) {
    super.validateSecretFile(accountId, secretFile, secretManagerConfig);
    validateSecretName(secretFile.getName());
    verifyFileSizeWithinLimit(secretFile.getFileContent());
  }

  @Override
  public void validateSecretFileUpdate(
      SecretFile secretFile, EncryptedData existingRecord, SecretManagerConfig secretManagerConfig) {
    super.validateSecretFileUpdate(secretFile, existingRecord, secretManagerConfig);
    checkIfSecretCanBeUpdated(secretFile, existingRecord);
    validateSecretName(secretFile.getName());
    verifyFileSizeWithinLimit(secretFile.getFileContent());
  }
}
