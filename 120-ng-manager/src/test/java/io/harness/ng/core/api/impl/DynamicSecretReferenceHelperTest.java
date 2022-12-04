/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.rule.OwnerRule.NISHANT;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.encryption.SecretRefParsedData;
import io.harness.exception.SecretManagementException;
import io.harness.rule.Owner;
import io.harness.security.encryption.AdditionalMetadata;
import io.harness.security.encryption.EncryptionType;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;

public class DynamicSecretReferenceHelperTest extends CategoryTest {
  public static final String FULLY_QUALIFIED_PATH_EXPRESSION_FORMAT_ERROR =
      "Fully-qualified path expression [%s] has illegal format.";
  public static final String HASHICORP_VAULT_ENCRYPTION_TYPE_PREFIX = "hashicorpvault://";
  public static final String SECRET_RELATIVE_PATH = "/this/is/some/path#key";

  private DynamicSecretReferenceHelper dynamicSecretReferenceHelper;

  @Rule public ExpectedException exceptionRule = ExpectedException.none();

  @Before
  public void setup() {
    this.dynamicSecretReferenceHelper = new DynamicSecretReferenceHelper();
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testValidateAndGetSecretRefParsedDataForInvalidFormat() {
    String secretManagerIdentifier = randomAlphabetic(16);
    String identifier = "hashicorpvault//" + secretManagerIdentifier + SECRET_RELATIVE_PATH;
    exceptionRule.expect(SecretManagementException.class);
    exceptionRule.expectMessage(
        String.format("Missing secret manager type info in fully-qualified path expression [%s].", identifier));
    dynamicSecretReferenceHelper.validateAndGetSecretRefParsedData(identifier);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testValidateAndGetSecretRefParsedDataForUnsupportedEncryptionType() {
    String secretManagerIdentifier = randomAlphabetic(16);
    String encryptionTypeName = randomAlphabetic(10);
    String identifier = encryptionTypeName + "://" + secretManagerIdentifier + SECRET_RELATIVE_PATH;
    exceptionRule.expect(SecretManagementException.class);
    exceptionRule.expectMessage(
        String.format("Encryption type [%s] is not supported in fully-qualified path expression.", encryptionTypeName));
    dynamicSecretReferenceHelper.validateAndGetSecretRefParsedData(identifier);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testValidateAndGetSecretRefParsedDataForInvalidFormatMissingRootPrefix() {
    String secretManagerIdentifier = randomAlphabetic(16);
    String identifier = "hashicorpvault:/" + secretManagerIdentifier + SECRET_RELATIVE_PATH;
    exceptionRule.expect(SecretManagementException.class);
    exceptionRule.expectMessage(String.format(FULLY_QUALIFIED_PATH_EXPRESSION_FORMAT_ERROR, identifier));
    dynamicSecretReferenceHelper.validateAndGetSecretRefParsedData(identifier);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testValidateAndGetSecretRefParsedDataForInvalidFormatMissingSecretManagerIdentifier() {
    String identifier = HASHICORP_VAULT_ENCRYPTION_TYPE_PREFIX;
    exceptionRule.expect(SecretManagementException.class);
    exceptionRule.expectMessage(String.format(
        "Missing secret manger identifier or secret path in fully-qualified path expression [%s].", identifier));
    dynamicSecretReferenceHelper.validateAndGetSecretRefParsedData(identifier);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testValidateAndGetSecretRefParsedDataForInvalidFormatMissingSecretRelativePath() {
    String secretManagerIdentifier = randomAlphabetic(16);
    String identifier = HASHICORP_VAULT_ENCRYPTION_TYPE_PREFIX + secretManagerIdentifier;
    exceptionRule.expect(SecretManagementException.class);
    exceptionRule.expectMessage(String.format(
        "Missing secret manger identifier or secret path in fully-qualified path expression [%s].", identifier));
    dynamicSecretReferenceHelper.validateAndGetSecretRefParsedData(identifier);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testValidateAndGetSecretRefParsedDataForInvalidFormatForGcpSm() {
    String secretManagerIdentifier = randomAlphabetic(16);
    String relativePath = "secretName/extraInfo/5";
    String identifier = "gcpsecretsmanager://" + secretManagerIdentifier + "/" + relativePath;
    exceptionRule.expect(SecretManagementException.class);
    exceptionRule.expectMessage(String.format(
        "Reference path [%s] can only have secret name and version. e.g. [secretName/latest]", relativePath));
    dynamicSecretReferenceHelper.validateAndGetSecretRefParsedData(identifier);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testValidateAndGetSecretRefParsedDataForAzureSM() {
    String secretManagerIdentifier = randomAlphabetic(16);
    String secretPath = randomAlphabetic(5) + "/" + randomAlphabetic(7);
    String identifier = "awssecretsmanager://" + secretManagerIdentifier + "/" + secretPath;
    SecretRefParsedData secretRefParsedData =
        dynamicSecretReferenceHelper.validateAndGetSecretRefParsedData(identifier);
    SecretRefParsedData expectedSecretRefParsedData = SecretRefParsedData.builder()
                                                          .secretManagerIdentifier(secretManagerIdentifier)
                                                          .encryptionType(EncryptionType.AWS_SECRETS_MANAGER)
                                                          .relativePath(secretPath)
                                                          .build();
    assertThat(secretRefParsedData).usingRecursiveComparison().isEqualTo(expectedSecretRefParsedData);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testValidateAndGetSecretRefParsedDataForAzureVault() {
    String secretManagerIdentifier = randomAlphabetic(16);
    String secretName = randomAlphabetic(10);
    String identifier = "azurevault://" + secretManagerIdentifier + "/" + secretName;
    SecretRefParsedData secretRefParsedData =
        dynamicSecretReferenceHelper.validateAndGetSecretRefParsedData(identifier);
    SecretRefParsedData expectedSecretRefParsedData = SecretRefParsedData.builder()
                                                          .secretManagerIdentifier(secretManagerIdentifier)
                                                          .encryptionType(EncryptionType.AZURE_VAULT)
                                                          .relativePath(secretName)
                                                          .build();
    assertThat(secretRefParsedData).usingRecursiveComparison().isEqualTo(expectedSecretRefParsedData);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testValidateAndGetSecretRefParsedDataForGcpSmLatest() {
    String secretManagerIdentifier = randomAlphabetic(16);
    String secretName = randomAlphabetic(10);
    String identifier = "gcpsecretsmanager://" + secretManagerIdentifier + "/" + secretName;
    SecretRefParsedData secretRefParsedData =
        dynamicSecretReferenceHelper.validateAndGetSecretRefParsedData(identifier);
    SecretRefParsedData expectedSecretRefParsedData =
        SecretRefParsedData.builder()
            .secretManagerIdentifier(secretManagerIdentifier)
            .encryptionType(EncryptionType.GCP_SECRETS_MANAGER)
            .relativePath(secretName)
            .additionalMetadata(AdditionalMetadata.builder().value("version", "latest").build())
            .build();
    assertThat(secretRefParsedData).usingRecursiveComparison().isEqualTo(expectedSecretRefParsedData);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testValidateAndGetSecretRefParsedDataForGcpSmVersion() {
    String secretManagerIdentifier = randomAlphabetic(16);
    String secretName = randomAlphabetic(10);
    String version = "7";
    String identifier = "gcpsecretsmanager://" + secretManagerIdentifier + "/" + secretName + "/" + version;
    SecretRefParsedData secretRefParsedData =
        dynamicSecretReferenceHelper.validateAndGetSecretRefParsedData(identifier);
    SecretRefParsedData expectedSecretRefParsedData =
        SecretRefParsedData.builder()
            .secretManagerIdentifier(secretManagerIdentifier)
            .encryptionType(EncryptionType.GCP_SECRETS_MANAGER)
            .relativePath(secretName)
            .additionalMetadata(AdditionalMetadata.builder().value("version", version).build())

            .build();
    assertThat(secretRefParsedData).usingRecursiveComparison().isEqualTo(expectedSecretRefParsedData);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testValidateAndGetSecretRefParsedDataForHashicorpVault() {
    String secretManagerIdentifier = randomAlphabetic(16);
    String identifier = HASHICORP_VAULT_ENCRYPTION_TYPE_PREFIX + secretManagerIdentifier + SECRET_RELATIVE_PATH;
    SecretRefParsedData secretRefParsedData =
        dynamicSecretReferenceHelper.validateAndGetSecretRefParsedData(identifier);
    SecretRefParsedData expectedSecretRefParsedData = SecretRefParsedData.builder()
                                                          .secretManagerIdentifier(secretManagerIdentifier)
                                                          .encryptionType(EncryptionType.VAULT)
                                                          .relativePath(SECRET_RELATIVE_PATH)
                                                          .build();
    assertThat(secretRefParsedData).usingRecursiveComparison().isEqualTo(expectedSecretRefParsedData);
  }
}
