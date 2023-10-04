/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.licensing.mappers.modules;

import static io.harness.rule.OwnerRule.SATHISH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.licensing.beans.modules.IDPModuleLicenseDTO;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.entities.modules.IDPModuleLicense;
import io.harness.licensing.entities.modules.ModuleLicense;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class IDPLicenseObjectMapperTest extends CategoryTest {
  @InjectMocks IDPLicenseObjectMapper objectMapper;

  private IDPModuleLicense moduleLicense;
  private IDPModuleLicenseDTO moduleLicenseDTO;
  private static final int DEFAULT_NUMBER_OF_DEVELOPERS = 5;

  @Before
  public void setUp() {
    initMocks(this);
    moduleLicense = IDPModuleLicense.builder().numberOfDevelopers(DEFAULT_NUMBER_OF_DEVELOPERS).build();
    moduleLicenseDTO = IDPModuleLicenseDTO.builder().numberOfDevelopers(DEFAULT_NUMBER_OF_DEVELOPERS).build();
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testToDTO() {
    ModuleLicenseDTO result = objectMapper.toDTO(moduleLicense);
    assertThat(result).isEqualTo(moduleLicenseDTO);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testToEntity() {
    ModuleLicense result = objectMapper.toEntity(moduleLicenseDTO);
    assertThat(result).isEqualTo(moduleLicense);
  }
}
