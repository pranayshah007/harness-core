/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.usage.dto;

import static io.harness.rule.OwnerRule.SATHISH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.LicenseUsageSaveRequest;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class IDPLicenseUsageUserCaptureDTOTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  static final String TEST_USER_IDENTIFIER = "testUser123";
  static final String TEST_USER_EMAIL = "testEmail123";
  static final String TEST_USER_NAME = "testName123";
  static final long TEST_LAST_ACCESSED_AT = 1698294600000L;

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testFromLicenseUsageSaveRequest() {
    LicenseUsageSaveRequest licenseUsageSaveRequest = new LicenseUsageSaveRequest()
                                                          .userIdentifier(TEST_USER_IDENTIFIER)
                                                          .email(TEST_USER_EMAIL)
                                                          .userName(TEST_USER_NAME)
                                                          .accessedAt(TEST_LAST_ACCESSED_AT);

    IDPLicenseUsageUserCaptureDTO idpLicenseUsageUserCaptureDTOActual =
        IDPLicenseUsageUserCaptureDTO.fromLicenseUsageSaveRequest(TEST_ACCOUNT_IDENTIFIER, licenseUsageSaveRequest);

    assertNotNull(idpLicenseUsageUserCaptureDTOActual);
    assertEquals(TEST_ACCOUNT_IDENTIFIER, idpLicenseUsageUserCaptureDTOActual.getAccountIdentifier());
    assertEquals(TEST_USER_IDENTIFIER, idpLicenseUsageUserCaptureDTOActual.getUserIdentifier());
    assertEquals(TEST_USER_EMAIL, idpLicenseUsageUserCaptureDTOActual.getEmail());
    assertEquals(TEST_USER_NAME, idpLicenseUsageUserCaptureDTOActual.getUserName());
    assertEquals(TEST_LAST_ACCESSED_AT, idpLicenseUsageUserCaptureDTOActual.getAccessedAt());
  }
}
