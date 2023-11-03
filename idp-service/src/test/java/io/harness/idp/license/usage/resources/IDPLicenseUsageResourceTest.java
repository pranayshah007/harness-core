/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.usage.resources;

import static io.harness.rule.OwnerRule.SATHISH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.license.usage.dto.ActiveDevelopersTrendCountDTO;
import io.harness.idp.license.usage.dto.IDPActiveDevelopersDTO;
import io.harness.idp.license.usage.service.IDPModuleLicenseUsage;
import io.harness.licensing.usage.interfaces.LicenseUsageInterface;
import io.harness.licensing.usage.params.filter.ActiveDevelopersFilterParams;
import io.harness.licensing.usage.params.filter.IDPLicenseDateUsageParams;
import io.harness.licensing.usage.params.filter.LicenseDateUsageReportType;
import io.harness.ng.core.Status;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;
import io.harness.utils.PageUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class IDPLicenseUsageResourceTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  static final String TEST_USER_IDENTIFIER = "testUser123";
  static final String TEST_USER_EMAIL = "testEmail123";
  static final String TEST_USER_NAME = "testName123";
  static final String TEST_LAST_ACCESSED_DATE = "10-26-2023";

  AutoCloseable openMocks;
  @InjectMocks IDPLicenseUsageResource idpLicenseUsageResource;
  @Mock LicenseUsageInterface licenseUsageInterface;
  @Mock IDPModuleLicenseUsage idpModuleLicenseUsage;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testListIDPActiveDevelopers() {
    ActiveDevelopersFilterParams activeDevelopersFilterParams =
        ActiveDevelopersFilterParams.builder().userIdentifier("").build();

    List<IDPActiveDevelopersDTO> activeDevelopersDTOList = new ArrayList<>();
    IDPActiveDevelopersDTO idpActiveDevelopersDTO = IDPActiveDevelopersDTO.builder()
                                                        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                                        .identifier(TEST_USER_IDENTIFIER)
                                                        .email(TEST_USER_EMAIL)
                                                        .name(TEST_USER_NAME)
                                                        .lastAccessedAt(TEST_LAST_ACCESSED_DATE)
                                                        .build();
    activeDevelopersDTOList.add(idpActiveDevelopersDTO);

    when(licenseUsageInterface.listLicenseUsage(anyString(), any(), anyLong(), any()))
        .thenReturn(PageUtils.getPage(activeDevelopersDTOList, 0, 30));
    final ResponseDTO<Page<IDPActiveDevelopersDTO>> result = idpLicenseUsageResource.listIDPActiveDevelopers(
        TEST_ACCOUNT_IDENTIFIER, 0, 30, Collections.emptyList(), activeDevelopersFilterParams);

    assertThat(result.getStatus()).isEqualTo(Status.SUCCESS);
    assertThat(new ArrayList<>(result.getData().toList())).isEqualTo(activeDevelopersDTOList);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testListIDPActiveDevelopersHistory() {
    IDPLicenseDateUsageParams idpLicenseDateUsageParams = buildIDPLicenseDateUsageParams();
    List<ActiveDevelopersTrendCountDTO> activeDevelopersTrendCountDTOList = buildActiveDevelopersTrendCountDTOList();
    when(idpModuleLicenseUsage.getHistoryTrend(TEST_ACCOUNT_IDENTIFIER, idpLicenseDateUsageParams))
        .thenReturn(activeDevelopersTrendCountDTOList);
    final ResponseDTO<List<ActiveDevelopersTrendCountDTO>> result =
        idpLicenseUsageResource.listIDPActiveDevelopersHistory(TEST_ACCOUNT_IDENTIFIER, idpLicenseDateUsageParams);
    assertThat(result.getStatus()).isEqualTo(Status.SUCCESS);
    assertThat(new ArrayList<>(result.getData())).isEqualTo(activeDevelopersTrendCountDTOList);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  private IDPLicenseDateUsageParams buildIDPLicenseDateUsageParams() {
    return IDPLicenseDateUsageParams.builder()
        .fromDate("2023-10-23")
        .toDate("2023-10-26")
        .reportType(LicenseDateUsageReportType.DAILY)
        .build();
  }

  private List<ActiveDevelopersTrendCountDTO> buildActiveDevelopersTrendCountDTOList() {
    List<ActiveDevelopersTrendCountDTO> activeDevelopersTrendCountDTOList = new ArrayList<>();
    activeDevelopersTrendCountDTOList.add(buildActiveDevelopersTrendCountDTO("2023-10-23", 98));
    activeDevelopersTrendCountDTOList.add(buildActiveDevelopersTrendCountDTO("2023-10-24", 99));
    activeDevelopersTrendCountDTOList.add(buildActiveDevelopersTrendCountDTO("2023-10-25", 100));
    activeDevelopersTrendCountDTOList.add(buildActiveDevelopersTrendCountDTO("2023-10-26", 101));

    return activeDevelopersTrendCountDTOList;
  }

  private ActiveDevelopersTrendCountDTO buildActiveDevelopersTrendCountDTO(String date, long count) {
    return ActiveDevelopersTrendCountDTO.builder().date(date).count(count).build();
  }
}
