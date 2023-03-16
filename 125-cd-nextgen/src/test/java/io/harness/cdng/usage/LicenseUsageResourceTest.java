/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.usage;

import static io.harness.rule.OwnerRule.IVAN;

import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.licensing.usage.interfaces.LicenseUsageInterface;
import io.harness.licensing.usage.resources.LicenseUsageResource;
import io.harness.rule.Owner;

import java.io.File;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class LicenseUsageResourceTest extends CategoryTest {
  private static final String accountIdentifier = "ACCOUNT_ID";

  @Mock private LicenseUsageInterface licenseUsageInterface;
  @InjectMocks private LicenseUsageResource licenseUsageResource;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetLicenseUsageCSVReportInvalidTimestamp() {
    File file = new File("active-services-csv-report-path");
    long currentTsInMs = System.currentTimeMillis();
    when(licenseUsageInterface.getLicenseUsageCSVReport(accountIdentifier, ModuleType.CD, currentTsInMs))
        .thenReturn(file);
    Response response = licenseUsageResource.downloadActiveServiceCSVReport(accountIdentifier, currentTsInMs);

    assertThat(response).isNotNull();
    assertThat(response.getMetadata().get("Content-Type").get(0).toString()).isEqualTo(APPLICATION_OCTET_STREAM);
    assertThat(response.getMetadata().get("Content-Disposition").get(0).toString())
        .isEqualTo("attachment; filename=ACCOUNT_ID-" + currentTsInMs + ".csv");
  }
}
