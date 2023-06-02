/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.vaultconnector;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.KARAN;
import static io.harness.rule.OwnerRule.SHASHANK;

import static junit.framework.TestCase.fail;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import java.util.HashSet;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class VaultConnectorDTOTest extends CategoryTest {
  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testValidate() {
    VaultConnectorDTO vaultConnectorDTO = getVaultConnectorDTO();
    vaultConnectorDTO.validate();

    try {
      vaultConnectorDTO.setSecretEngineVersion(0);
      vaultConnectorDTO.validate();
      vaultConnectorDTO.setSecretEngineVersion(2);
      fail();
    } catch (InvalidRequestException exception) {
      // continue
    }
    try {
      vaultConnectorDTO.setReadOnly(true);
      vaultConnectorDTO.setDefault(true);
      vaultConnectorDTO.validate();
      vaultConnectorDTO.setReadOnly(false);
      vaultConnectorDTO.setDefault(false);
      fail();
    } catch (InvalidRequestException exception) {
      // continue
    }
    try {
      vaultConnectorDTO.setVaultUrl("^random^");
      vaultConnectorDTO.validate();
      vaultConnectorDTO.setVaultUrl("https://localhost:9090");
      fail();
    } catch (InvalidRequestException exception) {
      // continue
    }
    try {
      vaultConnectorDTO.setRenewalIntervalMinutes(0);
      vaultConnectorDTO.validate();
      vaultConnectorDTO.setRenewalIntervalMinutes(10);
      fail();
    } catch (InvalidRequestException exception) {
      // continue
    }
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testValidateVaultConectorAwsIamMissingVaultXVaultAwsIamServerId() {
    VaultConnectorDTO vaultConnectorDTO = getVaultConnectorDTO();
    vaultConnectorDTO.setUseAwsIam(true);
    vaultConnectorDTO.setVaultAwsIamRole(randomAlphabetic(10));
    vaultConnectorDTO.setAwsRegion(randomAlphabetic(10));
    // Did not set XVaultAwsIamServerId header
    vaultConnectorDTO.validate();
    // no exception should be thrown. Which means this field is optional
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testValidateVaultConectorAwsIamMissingVaultAwsIamRole() {
    VaultConnectorDTO vaultConnectorDTO = getVaultConnectorDTO();
    vaultConnectorDTO.setUseAwsIam(true);
    vaultConnectorDTO.setHeaderAwsIam(SecretRefHelper.createSecretRef(randomAlphabetic(10)));
    vaultConnectorDTO.setAwsRegion(randomAlphabetic(10));
    // Did not set vaultAwsIamRole
    vaultConnectorDTO.validate();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testValidateVaultConectorAwsIamMissingVaultAwsRegion() {
    VaultConnectorDTO vaultConnectorDTO = getVaultConnectorDTO();
    vaultConnectorDTO.setUseAwsIam(true);
    vaultConnectorDTO.setHeaderAwsIam(SecretRefHelper.createSecretRef(randomAlphabetic(10)));
    vaultConnectorDTO.setVaultAwsIamRole(randomAlphabetic(10));
    // Did not set a aws region
    vaultConnectorDTO.validate();
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testValidateVaultConectorAwsIam() {
    VaultConnectorDTO vaultConnectorDTO = getVaultConnectorDTO();
    vaultConnectorDTO.setUseAwsIam(true);
    vaultConnectorDTO.setAwsRegion(randomAlphabetic(10));
    vaultConnectorDTO.setVaultAwsIamRole(randomAlphabetic(10));
    vaultConnectorDTO.setHeaderAwsIam(SecretRefHelper.createSecretRef(randomAlphabetic(10)));
    vaultConnectorDTO.validate();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testValidateVaultConectorUseEitherAwsIamOrVaultAgent_Fail() {
    VaultConnectorDTO vaultConnectorDTO = getVaultConnectorDTO();
    vaultConnectorDTO.setUseAwsIam(true);
    vaultConnectorDTO.setVaultAwsIamRole(randomAlphabetic(10));
    vaultConnectorDTO.setHeaderAwsIam(SecretRefHelper.createSecretRef(randomAlphabetic(10)));
    vaultConnectorDTO.setUseVaultAgent(true);
    vaultConnectorDTO.setSinkPath(randomAlphabetic(10));
    vaultConnectorDTO.validate();
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testValidateVaultConectorUseEitherAwsIamOrVaultAgent_Pass() {
    VaultConnectorDTO vaultConnectorDTO = getVaultConnectorDTO();
    vaultConnectorDTO.setUseAwsIam(true);
    vaultConnectorDTO.setAwsRegion(randomAlphabetic(10));
    vaultConnectorDTO.setVaultAwsIamRole(randomAlphabetic(10));
    vaultConnectorDTO.setHeaderAwsIam(SecretRefHelper.createSecretRef(randomAlphabetic(10)));
    vaultConnectorDTO.setUseVaultAgent(false);
    vaultConnectorDTO.setSinkPath(randomAlphabetic(10));
    vaultConnectorDTO.validate();
    vaultConnectorDTO.setUseAwsIam(false);
    vaultConnectorDTO.setUseVaultAgent(true);
    vaultConnectorDTO.validate();
  }

  private VaultConnectorDTO getVaultConnectorDTO() {
    HashSet<String> delegateSelectors = new HashSet<>();
    delegateSelectors.add(randomAlphabetic(10));
    return VaultConnectorDTO.builder()
        .authToken(SecretRefHelper.createSecretRef(randomAlphabetic(10)))
        .vaultUrl("https://localhost:9090")
        .basePath("harness")
        .isReadOnly(false)
        .isDefault(false)
        .secretEngineManuallyConfigured(false)
        .secretEngineName(randomAlphabetic(10))
        .secretEngineVersion(2)
        .renewalIntervalMinutes(10)
        .delegateSelectors(delegateSelectors)
        .build();
  }
}
