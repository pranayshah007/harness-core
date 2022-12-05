/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.SecretConstants.LATEST;
import static io.harness.SecretConstants.VERSION;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.encryption.SecretRefParsedData.SECRET_REFERENCE_DATA_ROOT_PREFIX;
import static io.harness.encryption.SecretRefParsedData.SECRET_REFERENCE_EXPRESSION_DELIMITER;
import static io.harness.eraro.ErrorCode.INVALID_FORMAT;
import static io.harness.eraro.ErrorCode.UNSUPPORTED_OPERATION_EXCEPTION;
import static io.harness.exception.WingsException.USER;

import static software.wings.beans.VaultConfig.PATH_SEPARATOR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.SecretRefParsedData;
import io.harness.encryption.SecretRefParsedData.SecretRefParsedDataBuilder;
import io.harness.eraro.ErrorCode;
import io.harness.exception.SecretManagementException;
import io.harness.exception.UnsupportedOperationException;
import io.harness.security.encryption.AdditionalMetadata;
import io.harness.security.encryption.EncryptionType;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.PL)
@Slf4j
public class DynamicSecretReferenceHelper {
  static final Set<EncryptionType> ENCRYPTION_TYPES_ALLOWED_FOR_DIRECT_SECRET_REFERENCE =
      EnumSet.of(EncryptionType.VAULT, EncryptionType.AZURE_VAULT, EncryptionType.AWS_SECRETS_MANAGER,
          EncryptionType.GCP_SECRETS_MANAGER);

  public SecretRefParsedData validateAndGetSecretRefParsedData(String secretIdentifier) {
    EncryptionType encryptionType = extractEncryptionTypeFromExpression(secretIdentifier);
    String dataRef = extractSecretDetailPathFromExpression(secretIdentifier);
    SecretRefParsedDataBuilder secretRefParsedDataBuilder =
        SecretRefParsedData.builder().encryptionType(encryptionType);
    if (!dataRef.startsWith(SECRET_REFERENCE_DATA_ROOT_PREFIX)) {
      throw logAndGetException(
          String.format("Fully-qualified path expression [%s] has illegal format.", secretIdentifier), INVALID_FORMAT,
          null);
    }
    try {
      String secretManagerIdentifierAndPath = dataRef.substring(2);
      int indexOfFirstPathSeparator = secretManagerIdentifierAndPath.indexOf(PATH_SEPARATOR);
      String secretManagerIdentifier = secretManagerIdentifierAndPath.substring(0, indexOfFirstPathSeparator);
      String relativePath = secretManagerIdentifierAndPath.substring(indexOfFirstPathSeparator);
      fillSecretManagerSpecificDetails(secretRefParsedDataBuilder, encryptionType, relativePath);
      return secretRefParsedDataBuilder.secretManagerIdentifier(secretManagerIdentifier).build();
    } catch (StringIndexOutOfBoundsException exception) {
      throw logAndGetException(
          String.format("Missing secret manger identifier or secret path in fully-qualified path expression [%s].",
              secretIdentifier),
          INVALID_FORMAT, exception);
    }
  }

  private EncryptionType extractEncryptionTypeFromExpression(String expression) {
    if (!expression.contains(":")) {
      throw logAndGetException(
          String.format("Missing secret manager type info in fully-qualified path expression [%s].", expression),
          INVALID_FORMAT, null);
    }
    String[] secretDetails = expression.split(SECRET_REFERENCE_EXPRESSION_DELIMITER);
    String encryptionTypeName = secretDetails[0];
    Map<String, EncryptionType> validEncryptionTypeMap =
        ENCRYPTION_TYPES_ALLOWED_FOR_DIRECT_SECRET_REFERENCE.stream().collect(
            Collectors.toMap(EncryptionType::getYamlName, Function.identity()));
    if (isNotEmpty(encryptionTypeName) && validEncryptionTypeMap.containsKey(encryptionTypeName)) {
      return validEncryptionTypeMap.get(encryptionTypeName);
    } else {
      throw logAndGetException(
          String.format(
              "Encryption type [%s] is not supported in fully-qualified path expression.", encryptionTypeName),
          UNSUPPORTED_OPERATION_EXCEPTION, null);
    }
  }

  private String extractSecretDetailPathFromExpression(String secretIdentifier) {
    String[] secretDetails = secretIdentifier.split(SECRET_REFERENCE_EXPRESSION_DELIMITER);
    if (secretDetails.length < 2) {
      throw logAndGetException(
          String.format("Missing secret details in fully-qualified path expression [%s].", secretIdentifier),
          INVALID_FORMAT, null);
    }
    return secretDetails[1];
  }

  private SecretManagementException logAndGetException(String message, ErrorCode errorCode, Exception exception) {
    if (null != exception) {
      log.error(message, exception);
    } else {
      log.error(message);
    }
    return new SecretManagementException(errorCode, message, USER);
  }

  private void fillSecretManagerSpecificDetails(
      SecretRefParsedDataBuilder secretRefParsedDataBuilder, EncryptionType encryptionType, String fullPath) {
    switch (encryptionType) {
      case AWS_SECRETS_MANAGER:
      case AZURE_VAULT:
        fillAzureVaultDetails(secretRefParsedDataBuilder, fullPath);
        break;
      case GCP_SECRETS_MANAGER:
        fillGCPSecretManagerDetails(secretRefParsedDataBuilder, fullPath);
        break;
      case VAULT:
        fillVaultDetails(secretRefParsedDataBuilder, fullPath);
        break;
      default:
        throw new UnsupportedOperationException(
            String.format("Encryption type [%s] is not supported.", encryptionType));
    }
  }

  private void fillVaultDetails(SecretRefParsedDataBuilder secretRefParsedDataBuilder, String fullPath) {
    secretRefParsedDataBuilder.relativePath(fullPath);
  }

  private void fillAzureVaultDetails(SecretRefParsedDataBuilder secretRefParsedDataBuilder, String fullPath) {
    secretRefParsedDataBuilder.relativePath(StringUtils.stripStart(fullPath, "/"));
  }

  private void fillGCPSecretManagerDetails(SecretRefParsedDataBuilder secretRefParsedDataBuilder, String fullPath) {
    String stripedSecretPath = StringUtils.stripStart(fullPath, "/");
    String[] secretNameAndVersion = stripedSecretPath.split("/");
    if (secretNameAndVersion.length > 2) {
      throw new SecretManagementException(INVALID_FORMAT,
          String.format(
              "Reference path [%s] can only have secret name and version. e.g. [secretName/latest]", stripedSecretPath),
          USER);
    }
    String version = (secretNameAndVersion.length > 1) ? secretNameAndVersion[secretNameAndVersion.length - 1] : LATEST;
    secretRefParsedDataBuilder.relativePath(secretNameAndVersion[0]);
    AdditionalMetadata additionalMetadata = AdditionalMetadata.builder().value(VERSION, version).build();
    secretRefParsedDataBuilder.additionalMetadata(additionalMetadata);
  }
}
