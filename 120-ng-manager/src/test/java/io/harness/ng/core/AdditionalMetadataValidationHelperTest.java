/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core;

import static io.harness.SecretConstants.EXPIRES_ON;
import static io.harness.SecretConstants.LATEST;
import static io.harness.SecretConstants.REGIONS;
import static io.harness.SecretConstants.VERSION;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.NISHANT;
import static io.harness.rule.OwnerRule.SHREYAS;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.secrets.SecretFileSpecDTO;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.ValueType;
import io.harness.security.encryption.AdditionalMetadata;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class AdditionalMetadataValidationHelperTest extends CategoryTest {
  Map<String, Object> values = new HashMap<>();
  private String regions = randomAlphabetic(10);

  AdditionalMetadataValidationHelper additionalMetadataValidationHelper;
  @Before
  public void setup() {
    additionalMetadataValidationHelper = new AdditionalMetadataValidationHelper();
  }
  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testSecretFileWithoutAdditionalMetadata_forGcpSecretManager_shouldNotThrowException() {
    SecretFileSpecDTO secretFileSpecDTO = SecretFileSpecDTO.builder().build();
    assertThatCode(
        () -> additionalMetadataValidationHelper.validateAdditionalMetadataForGcpSecretManager(secretFileSpecDTO))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testSecretFileWithoutValuesKeys_forGcpSecretManager_shouldThrowException() {
    AdditionalMetadata additionalMetadata = AdditionalMetadata.builder().values(values).build();
    SecretFileSpecDTO secretFileSpecDTO = SecretFileSpecDTO.builder().additionalMetadata(additionalMetadata).build();
    assertThatThrownBy(
        () -> additionalMetadataValidationHelper.validateAdditionalMetadataForGcpSecretManager(secretFileSpecDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Additional metadata must have values map");
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testSecretFileWithRegion_forGcpSecretManager_shouldNotThrowException() {
    values.put(REGIONS, regions);
    AdditionalMetadata additionalMetadata = AdditionalMetadata.builder().values(values).build();
    SecretFileSpecDTO secretFileSpecDTO = SecretFileSpecDTO.builder().additionalMetadata(additionalMetadata).build();
    assertThatCode(
        () -> additionalMetadataValidationHelper.validateAdditionalMetadataForGcpSecretManager(secretFileSpecDTO))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testSecretFileWithoutIncorrectKey_forGcpSecretManager_shouldThrowException() {
    values.put(randomAlphabetic(10), randomAlphabetic(10));
    AdditionalMetadata additionalMetadata = AdditionalMetadata.builder().values(values).build();
    SecretFileSpecDTO secretFileSpecDTO = SecretFileSpecDTO.builder().additionalMetadata(additionalMetadata).build();
    assertThatThrownBy(
        () -> additionalMetadataValidationHelper.validateAdditionalMetadataForGcpSecretManager(secretFileSpecDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Additional metadata values map must have only one key - regions for secrets created using google secret manager.");
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testSecretFileWithNullRegionValue_forGcpSecretManager_shouldThrowException() {
    values.put(REGIONS, null);
    AdditionalMetadata additionalMetadata = AdditionalMetadata.builder().values(values).build();
    SecretFileSpecDTO secretFileSpecDTO = SecretFileSpecDTO.builder().additionalMetadata(additionalMetadata).build();
    assertThatThrownBy(
        () -> additionalMetadataValidationHelper.validateAdditionalMetadataForGcpSecretManager(secretFileSpecDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("regions should not be empty for secret created using google secret manager.");
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testSecretFileWithEmptyRegionValue_forGcpSecretManager_shouldThrowException() {
    values.put(REGIONS, "");
    AdditionalMetadata additionalMetadata = AdditionalMetadata.builder().values(values).build();
    SecretFileSpecDTO secretFileSpecDTO = SecretFileSpecDTO.builder().additionalMetadata(additionalMetadata).build();
    assertThatThrownBy(
        () -> additionalMetadataValidationHelper.validateAdditionalMetadataForGcpSecretManager(secretFileSpecDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("regions should not be empty for secret created using google secret manager.");
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testInlineSecretWithRegion_forGcpSecretManager_shouldNotThrowException() {
    values.put(REGIONS, regions);
    AdditionalMetadata additionalMetadata = AdditionalMetadata.builder().values(values).build();
    SecretTextSpecDTO secretTextSpecDTO =
        SecretTextSpecDTO.builder().valueType(ValueType.Inline).additionalMetadata(additionalMetadata).build();
    assertThatCode(
        () -> additionalMetadataValidationHelper.validateAdditionalMetadataForGcpSecretManager(secretTextSpecDTO))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testInlineSecretWithNullRegion_forGcpSecretManager_shouldThrowException() {
    values.put(REGIONS, null);
    AdditionalMetadata additionalMetadata = AdditionalMetadata.builder().values(values).build();
    SecretTextSpecDTO secretTextSpecDTO =
        SecretTextSpecDTO.builder().valueType(ValueType.Inline).additionalMetadata(additionalMetadata).build();
    assertThatThrownBy(
        () -> additionalMetadataValidationHelper.validateAdditionalMetadataForGcpSecretManager(secretTextSpecDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("regions should not be empty for secret created using google secret manager.");
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testInlineSecretWithEmptyRegion_forGcpSecretManager_shouldThrowException() {
    values.put(REGIONS, "");
    AdditionalMetadata additionalMetadata = AdditionalMetadata.builder().values(values).build();
    SecretTextSpecDTO secretTextSpecDTO =
        SecretTextSpecDTO.builder().valueType(ValueType.Inline).additionalMetadata(additionalMetadata).build();
    assertThatThrownBy(
        () -> additionalMetadataValidationHelper.validateAdditionalMetadataForGcpSecretManager(secretTextSpecDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("regions should not be empty for secret created using google secret manager.");
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testInlineSecretWithoutRegion_forGcpSecretManager_shouldNotThrowException() {
    SecretTextSpecDTO secretTextSpecDTO = SecretTextSpecDTO.builder().valueType(ValueType.Inline).build();
    assertThatCode(
        () -> additionalMetadataValidationHelper.validateAdditionalMetadataForGcpSecretManager(secretTextSpecDTO))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testInlineSecretValidationWithWrongMetadata_forGcpSecretManager_shouldThrowException() {
    String invalidKey = randomAlphabetic(10);
    values.put(invalidKey, regions);
    AdditionalMetadata additionalMetadata = AdditionalMetadata.builder().values(values).build();
    SecretTextSpecDTO secretTextSpecDTO =
        SecretTextSpecDTO.builder().valueType(ValueType.Inline).additionalMetadata(additionalMetadata).build();
    assertThatThrownBy(
        () -> additionalMetadataValidationHelper.validateAdditionalMetadataForGcpSecretManager(secretTextSpecDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Additional metadata values map must have only one key - regions for secrets created using google secret manager.");
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testReferenceSecretValidationWithVersionAsLatest_forGcpSecretManager_shouldNotThrowException() {
    values.put(VERSION, LATEST);
    AdditionalMetadata additionalMetadata = AdditionalMetadata.builder().values(values).build();
    SecretTextSpecDTO secretTextSpecDTO =
        SecretTextSpecDTO.builder().valueType(ValueType.Reference).additionalMetadata(additionalMetadata).build();
    assertThatCode(
        () -> additionalMetadataValidationHelper.validateAdditionalMetadataForGcpSecretManager(secretTextSpecDTO))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testReferenceSecretValidationWithNoVersion_forGcpSecretManager_shouldThrowException() {
    SecretTextSpecDTO secretTextSpecDTO = SecretTextSpecDTO.builder().valueType(ValueType.Reference).build();
    assertThatThrownBy(
        () -> additionalMetadataValidationHelper.validateAdditionalMetadataForGcpSecretManager(secretTextSpecDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Additional metadata must be present for reference secret created using google secret manager connector");
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testReferenceSecretValidationWithWrongMetadata_forGcpSecretManager_shouldThrowException() {
    String invalidKey = randomAlphabetic(10);
    values.put(invalidKey, regions);
    AdditionalMetadata additionalMetadata = AdditionalMetadata.builder().values(values).build();
    SecretTextSpecDTO secretTextSpecDTO =
        SecretTextSpecDTO.builder().valueType(ValueType.Reference).additionalMetadata(additionalMetadata).build();
    assertThatThrownBy(
        () -> additionalMetadataValidationHelper.validateAdditionalMetadataForGcpSecretManager(secretTextSpecDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Additional metadata values map must have only one key - version for secrets created using google secret manager.");
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testReferenceSecretValidationWithEmptyVersion_forGcpSecretManager_shouldThrowException() {
    values.put(VERSION, "");
    AdditionalMetadata additionalMetadata = AdditionalMetadata.builder().values(values).build();
    SecretTextSpecDTO secretTextSpecDTO =
        SecretTextSpecDTO.builder().valueType(ValueType.Reference).additionalMetadata(additionalMetadata).build();
    assertThatThrownBy(
        () -> additionalMetadataValidationHelper.validateAdditionalMetadataForGcpSecretManager(secretTextSpecDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("version should not be empty for secret created using google secret manager.");
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testReferenceSecretValidationWithNullVersion_forGcpSecretManager_shouldThrowException() {
    values.put(VERSION, null);
    AdditionalMetadata additionalMetadata = AdditionalMetadata.builder().values(values).build();
    SecretTextSpecDTO secretTextSpecDTO =
        SecretTextSpecDTO.builder().valueType(ValueType.Reference).additionalMetadata(additionalMetadata).build();
    assertThatThrownBy(
        () -> additionalMetadataValidationHelper.validateAdditionalMetadataForGcpSecretManager(secretTextSpecDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("version should not be empty for secret created using google secret manager.");
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testReferenceSecretValidationWithWrongVersionInfo_forGcpSecretManager_shouldThrowException() {
    values.put(VERSION, randomAlphabetic(10));
    AdditionalMetadata additionalMetadata = AdditionalMetadata.builder().values(values).build();
    SecretTextSpecDTO secretTextSpecDTO =
        SecretTextSpecDTO.builder().valueType(ValueType.Reference).additionalMetadata(additionalMetadata).build();
    assertThatThrownBy(
        () -> additionalMetadataValidationHelper.validateAdditionalMetadataForGcpSecretManager(secretTextSpecDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Version should be either latest or an integer.");
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testValidateAdditionalMetadataForAzureValue_textSecret() {
    SecretTextSpecDTO secretSpecDTO =
        SecretTextSpecDTO.builder()
            .additionalMetadata(AdditionalMetadata.builder().value(EXPIRES_ON, randomAlphabetic(10)).build())
            .build();
    assertThatThrownBy(() -> additionalMetadataValidationHelper.validateAdditionalMetadataForAzureValue(secretSpecDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format("Value of %s should be a valid epoch timestamp but given: [%s]", EXPIRES_ON,
            secretSpecDTO.getAdditionalMetadata().getValues().get(EXPIRES_ON)));
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testValidateAdditionalMetadataForAzureValue_fileSecret() {
    SecretFileSpecDTO secretSpecDTO =
        SecretFileSpecDTO.builder()
            .additionalMetadata(AdditionalMetadata.builder().value(EXPIRES_ON, randomAlphabetic(10)).build())
            .build();
    assertThatThrownBy(() -> additionalMetadataValidationHelper.validateAdditionalMetadataForAzureValue(secretSpecDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format("Value of %s should be a valid epoch timestamp but given: [%s]", EXPIRES_ON,
            secretSpecDTO.getAdditionalMetadata().getValues().get(EXPIRES_ON)));
  }
}
