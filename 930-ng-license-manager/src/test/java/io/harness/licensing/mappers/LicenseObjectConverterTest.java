/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.licensing.mappers;

import static io.harness.licensing.LicenseTestConstant.DEFAULT_CI_MODULE_LICENSE;
import static io.harness.licensing.LicenseTestConstant.DEFAULT_CI_MODULE_LICENSE_DTO;
import static io.harness.licensing.LicenseTestConstant.HOSTING_CREDITS;
import static io.harness.licensing.LicenseTestConstant.TOTAL_DEVELOPER;
import static io.harness.rule.OwnerRule.ZHUO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.category.element.UnitTests;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.LicenseType;
import io.harness.licensing.beans.modules.CIModuleLicenseDTO;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.entities.modules.CIModuleLicense;
import io.harness.licensing.entities.modules.ModuleLicense;
import io.harness.rule.Owner;

import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class LicenseObjectConverterTest extends CategoryTest {
  @InjectMocks LicenseObjectConverter licenseObjectMapper;
  @Mock LicenseObjectMapper CIMapper;
  @Mock Map<ModuleType, LicenseObjectMapper> mapperMap;
  private ModuleLicenseDTO defaultModueLicenseDTO;
  private ModuleLicense expectedModuleLicense;
  private ModuleLicense defaultModuleLicense;
  private static final String ACCOUNT_IDENTIFIER = "account";
  private static final ModuleType DEFAULT_MODULE_TYPE = ModuleType.CI;

  @Before
  public void setUp() {
    initMocks(this);
    defaultModueLicenseDTO = DEFAULT_CI_MODULE_LICENSE_DTO;
    defaultModuleLicense = DEFAULT_CI_MODULE_LICENSE;

    expectedModuleLicense =
        CIModuleLicense.builder().numberOfCommitters(TOTAL_DEVELOPER).hostingCredits(HOSTING_CREDITS).build();
    expectedModuleLicense.setId("id");
    expectedModuleLicense.setAccountIdentifier(ACCOUNT_IDENTIFIER);
    expectedModuleLicense.setModuleType(DEFAULT_MODULE_TYPE);
    expectedModuleLicense.setEdition(Edition.ENTERPRISE);
    expectedModuleLicense.setStatus(LicenseStatus.ACTIVE);
    expectedModuleLicense.setLicenseType(LicenseType.TRIAL);
    expectedModuleLicense.setStartTime(1);
    expectedModuleLicense.setExpiryTime(1);

    when(CIMapper.toEntity(any()))
        .thenReturn(
            CIModuleLicense.builder().numberOfCommitters(TOTAL_DEVELOPER).hostingCredits(HOSTING_CREDITS).build());
    when(CIMapper.toDTO(any()))
        .thenReturn(
            CIModuleLicenseDTO.builder().numberOfCommitters(TOTAL_DEVELOPER).hostingCredits(HOSTING_CREDITS).build());
    when(mapperMap.get(DEFAULT_MODULE_TYPE)).thenReturn(CIMapper);
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testToDTO() {
    ModuleLicenseDTO moduleLicenseDTO = licenseObjectMapper.toDTO(defaultModuleLicense);
    // temporary set due to legacy module license
    moduleLicenseDTO.setCreatedAt(0L);
    moduleLicenseDTO.setLastModifiedAt(0L);
    assertThat(moduleLicenseDTO).isEqualTo(defaultModueLicenseDTO);
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testToEntity() {
    ModuleLicense moduleLicense = licenseObjectMapper.toEntity(defaultModueLicenseDTO);
    assertThat(moduleLicense).isEqualTo(expectedModuleLicense);
  }
}
