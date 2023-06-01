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

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.utils.AzureConnectorTestHelper;
import io.harness.delegate.beans.connector.ceazure.CEAzureConnectorDTO;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class CEAzureEntityToDTOTest extends CategoryTest {
  @InjectMocks CEAzureEntityToDTO ceAzureEntityToDTO;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testCreateConnectorDTOAll() {
    final CEAzureConnectorDTO ceAzureConnectorDTO =
        ceAzureEntityToDTO.createConnectorDTO(AzureConnectorTestHelper.createCEAzureConfig());
    assertThat(ceAzureConnectorDTO).isEqualTo(AzureConnectorTestHelper.createCEAzureConnectorDTO());
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testBillingOnly() {
    final CEAzureConnectorDTO ceAzureConnectorDTO =
        ceAzureEntityToDTO.createConnectorDTO(AzureConnectorTestHelper.createCEAzureConfigBillingOnly());
    assertThat(ceAzureConnectorDTO).isEqualTo(AzureConnectorTestHelper.createCEAzureConnectorDTOBillingOnly());
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testGovernanceOnly() {
    final CEAzureConnectorDTO ceAzureConnectorDTO =
        ceAzureEntityToDTO.createConnectorDTO(AzureConnectorTestHelper.createCEAzureConfigGovernanceOnly());
    assertThat(ceAzureConnectorDTO).isEqualTo(AzureConnectorTestHelper.createCEAzureConnectorDTOGovernanceOnly());
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testOptimizationOnly() {
    final CEAzureConnectorDTO ceAzureConnectorDTO =
        ceAzureEntityToDTO.createConnectorDTO(AzureConnectorTestHelper.createCEAzureConfigOptimizationOnly());
    assertThat(ceAzureConnectorDTO).isEqualTo(AzureConnectorTestHelper.createCEAzureConnectorDTOOptimizationOnly());
  }
}
