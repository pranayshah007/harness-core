/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.ceazure;

import static io.harness.rule.OwnerRule.ANMOL;
import static io.harness.rule.OwnerRule.UTSAV;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.entities.embedded.ceazure.CEAzureConfig;
import io.harness.connector.utils.AzureConnectorTestHelper;
import io.harness.delegate.beans.connector.ceazure.CEAzureConnectorDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class CEAzureDTOToEntityTest extends CategoryTest {
  @InjectMocks CEAzureDTOToEntity ceAzureDTOToEntity;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testToConnectorEntity() {
    final CEAzureConfig ceAzureConfig =
        ceAzureDTOToEntity.toConnectorEntity(AzureConnectorTestHelper.createCEAzureConnectorDTO());
    assertThat(ceAzureConfig).isEqualTo(AzureConnectorTestHelper.createCEAzureConfig());
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testBillingOnly() {
    final CEAzureConfig ceAzureConfig =
        ceAzureDTOToEntity.toConnectorEntity(AzureConnectorTestHelper.createCEAzureConnectorDTOBillingOnly());
    assertThat(ceAzureConfig).isEqualTo(AzureConnectorTestHelper.createCEAzureConfigBillingOnly());
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testGovernanceOnly() {
    final CEAzureConfig ceAzureConfig =
        ceAzureDTOToEntity.toConnectorEntity(AzureConnectorTestHelper.createCEAzureConnectorDTOGovernanceOnly());
    assertThat(ceAzureConfig).isEqualTo(AzureConnectorTestHelper.createCEAzureConfigGovernanceOnly());
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testOptimizationOnly() {
    final CEAzureConfig ceAzureConfig =
        ceAzureDTOToEntity.toConnectorEntity(AzureConnectorTestHelper.createCEAzureConnectorDTOOptimizationOnly());
    assertThat(ceAzureConfig).isEqualTo(AzureConnectorTestHelper.createCEAzureConfigOptimizationOnly());
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testThrowOnBillingExportMissing() {
    final CEAzureConnectorDTO ceAzureConnectorDTO = AzureConnectorTestHelper.createCEAzureConnectorDTO();
    ceAzureConnectorDTO.setBillingExportSpec(null);
    assertThatThrownBy(() -> ceAzureDTOToEntity.toConnectorEntity(ceAzureConnectorDTO))
        .isExactlyInstanceOf(InvalidRequestException.class);
  }
}
