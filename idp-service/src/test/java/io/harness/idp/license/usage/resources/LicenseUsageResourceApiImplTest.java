/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.usage.resources;

import static io.harness.rule.OwnerRule.SATHISH;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.license.usage.dto.IDPLicenseUsageUserCaptureDTO;
import io.harness.idp.license.usage.service.IDPModuleLicenseUsage;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.LicenseUsageSaveRequest;
import io.harness.spec.server.idp.v1.model.LicenseUsageSaveResponse;

import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class LicenseUsageResourceApiImplTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  static final String TEST_USER_IDENTIFIER = "testUser123";
  static final String TEST_USER_EMAIL = "testEmail123";
  static final String TEST_USER_NAME = "testName123";
  static final long TEST_LAST_ACCESSED_AT = 1698294600000L;

  AutoCloseable openMocks;
  @InjectMocks LicenseUsageResourceApiImpl licenseUsageResourceApi;
  @Mock IDPModuleLicenseUsage idpModuleLicenseUsage;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testIdpLicenseUsageSave() {
    IDPLicenseUsageUserCaptureDTO idpLicenseUsageUserCaptureDTO = IDPLicenseUsageUserCaptureDTO.builder()
                                                                      .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                                                      .userIdentifier(TEST_USER_IDENTIFIER)
                                                                      .email(TEST_USER_EMAIL)
                                                                      .userName(TEST_USER_NAME)
                                                                      .accessedAt(TEST_LAST_ACCESSED_AT)
                                                                      .build();
    doNothing().when(idpModuleLicenseUsage).captureLicenseUsageInRedis(idpLicenseUsageUserCaptureDTO);

    LicenseUsageSaveRequest licenseUsageSaveRequest = buildLicenseUsageSaveRequest();
    final Response response =
        licenseUsageResourceApi.idpLicenseUsageSave(licenseUsageSaveRequest, TEST_ACCOUNT_IDENTIFIER);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    LicenseUsageSaveResponse licenseUsageSaveResponse = (LicenseUsageSaveResponse) response.getEntity();
    assertEquals(licenseUsageSaveResponse.getStatus(), "Saved");
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  private LicenseUsageSaveRequest buildLicenseUsageSaveRequest() {
    return new LicenseUsageSaveRequest()
        .userIdentifier(TEST_USER_IDENTIFIER)
        .email(TEST_USER_EMAIL)
        .userName(TEST_USER_NAME)
        .accessedAt(TEST_LAST_ACCESSED_AT);
  }
}
