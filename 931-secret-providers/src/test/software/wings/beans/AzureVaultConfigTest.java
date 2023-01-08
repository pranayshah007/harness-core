/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.rule.OwnerRule.ANSHUL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.azure.AzureEnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AzureVaultConfigTest extends CategoryTest {
  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetValidationCriteria() {
    AzureVaultConfig azureVaultConfig = AzureVaultConfig.builder().vaultName("test-vault").build();
    String validationCriteria = azureVaultConfig.getValidationCriteria();
    assertThat(validationCriteria).isEqualTo("https://test-vault.vault.azure.net");

    azureVaultConfig.setAzureEnvironmentType(AzureEnvironmentType.AZURE_US_GOVERNMENT);
    validationCriteria = azureVaultConfig.getValidationCriteria();
    assertThat(validationCriteria).isEqualTo("https://test-vault.vault.usgovcloudapi.net");

    azureVaultConfig.setAzureEnvironmentType(AzureEnvironmentType.AZURE);
    validationCriteria = azureVaultConfig.getValidationCriteria();
    assertThat(validationCriteria).isEqualTo("https://test-vault.vault.azure.net");
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetEncryptionServiceUrl() {
    AzureVaultConfig azureVaultConfig = AzureVaultConfig.builder().vaultName("test-vault").build();
    String validationCriteria = azureVaultConfig.getEncryptionServiceUrl();
    assertThat(validationCriteria).isEqualTo("https://test-vault.vault.azure.net");

    azureVaultConfig.setAzureEnvironmentType(AzureEnvironmentType.AZURE_US_GOVERNMENT);
    validationCriteria = azureVaultConfig.getEncryptionServiceUrl();
    assertThat(validationCriteria).isEqualTo("https://test-vault.vault.usgovcloudapi.net");

    azureVaultConfig.setAzureEnvironmentType(AzureEnvironmentType.AZURE);
    validationCriteria = azureVaultConfig.getEncryptionServiceUrl();
    assertThat(validationCriteria).isEqualTo("https://test-vault.vault.azure.net");
  }
}
