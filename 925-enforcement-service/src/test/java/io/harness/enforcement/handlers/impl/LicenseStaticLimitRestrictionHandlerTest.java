/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.enforcement.handlers.impl;

import static io.harness.rule.OwnerRule.ZHUO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.category.element.UnitTests;
import io.harness.enforcement.bases.LicenseStaticLimitRestriction;
import io.harness.enforcement.beans.FeatureRestrictionUsageDTO;
import io.harness.enforcement.beans.details.FeatureRestrictionDetailsDTO;
import io.harness.enforcement.beans.metadata.LicenseStaticLimitRestrictionMetadataDTO;
import io.harness.enforcement.beans.metadata.RestrictionMetadataDTO;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.enforcement.constants.RestrictionType;
import io.harness.enforcement.services.impl.EnforcementSdkClient;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseType;
import io.harness.licensing.beans.summary.CDLicenseSummaryDTO;
import io.harness.licensing.services.LicenseService;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
@PrepareForTest({NGRestUtils.class})
public class LicenseStaticLimitRestrictionHandlerTest extends CategoryTest {
  private LicenseStaticLimitRestrictionHandler handler;
  private LicenseService licenseService;
  private FeatureRestrictionName featureRestrictionName = FeatureRestrictionName.TEST1;
  private LicenseStaticLimitRestriction restriction;
  private EnforcementSdkClient client;
  private String accountIdentifier = "accountId";
  private ModuleType moduleType = ModuleType.CD;
  private Edition edition = Edition.ENTERPRISE;

  @Before
  public void setup() throws IOException {
    licenseService = mock(LicenseService.class);
    when(licenseService.getLicenseSummary(accountIdentifier, moduleType))
        .thenReturn(CDLicenseSummaryDTO.builder()
                        .totalWorkload(10)
                        .edition(Edition.ENTERPRISE)
                        .maxExpiryTime(Integer.MAX_VALUE)
                        .licenseType(LicenseType.TRIAL)
                        .build());

    handler = new LicenseStaticLimitRestrictionHandler(licenseService);
    client = mock(EnforcementSdkClient.class);
    restriction = new LicenseStaticLimitRestriction(RestrictionType.LICENSE_STATIC_LIMIT, "totalWorkload", client);
    Mockito.mockStatic(NGRestUtils.class);
    when(NGRestUtils.getResponse(any())).thenReturn(FeatureRestrictionUsageDTO.builder().count(10).build());
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testCheck() {
    handler.check(featureRestrictionName, restriction, accountIdentifier, moduleType, edition);
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testFillRestrictionDTO() {
    FeatureRestrictionDetailsDTO dto = FeatureRestrictionDetailsDTO.builder().moduleType(ModuleType.CD).build();
    handler.fillRestrictionDTO(featureRestrictionName, restriction, accountIdentifier, edition, dto);

    assertThat(dto.getRestrictionType()).isEqualTo(RestrictionType.LICENSE_STATIC_LIMIT);
    assertThat(dto.getRestriction()).isNotNull();
    assertThat(dto.isAllowed()).isTrue();
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testGetMetadataDTO() {
    RestrictionMetadataDTO metadataDTO = handler.getMetadataDTO(restriction, accountIdentifier, moduleType);

    LicenseStaticLimitRestrictionMetadataDTO dto = (LicenseStaticLimitRestrictionMetadataDTO) metadataDTO;
    assertThat(dto.getRestrictionType()).isEqualTo(RestrictionType.LICENSE_STATIC_LIMIT);
    assertThat(dto.getLimit()).isEqualTo(10);
  }
}
