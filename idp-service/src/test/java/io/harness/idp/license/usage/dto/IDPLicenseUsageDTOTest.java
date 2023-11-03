/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.usage.dto;

import static io.harness.ModuleType.IDP;
import static io.harness.rule.OwnerRule.SATHISH;

import static junit.framework.TestCase.assertEquals;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.licensing.usage.beans.UsageDataDTO;
import io.harness.rule.Owner;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class IDPLicenseUsageDTOTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  static final long TEST_ACTIVE_DEVELOPERS = 300;

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testIDPLicenseUsageDTO() {
    IDPLicenseUsageDTO idpLicenseUsageDTO =
        IDPLicenseUsageDTO.builder()
            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
            .activeDevelopers(UsageDataDTO.builder().count(TEST_ACTIVE_DEVELOPERS).build())
            .build();

    String moduleType = idpLicenseUsageDTO.getModule();

    assertEquals(IDP.toString(), moduleType);
  }
}
