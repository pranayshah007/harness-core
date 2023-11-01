/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.usage.service.impl;

import static io.harness.rule.OwnerRule.SATHISH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.license.usage.dto.IDPActiveDevelopersDTO;
import io.harness.idp.license.usage.dto.IDPLicenseUsageDTO;
import io.harness.idp.license.usage.entities.ActiveDevelopersEntity;
import io.harness.idp.license.usage.repositories.ActiveDevelopersRepository;
import io.harness.licensing.usage.params.DefaultPageableUsageRequestParams;
import io.harness.licensing.usage.params.UsageRequestParams;
import io.harness.licensing.usage.params.filter.ActiveDevelopersFilterParams;
import io.harness.licensing.usage.utils.PageableUtils;
import io.harness.rule.Owner;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class IDPLicenseUsageImplTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  static final String TEST_USER_IDENTIFIER_1 = "testUser123";
  static final String TEST_USER_EMAIL_1 = "testEmail123";
  static final String TEST_USER_NAME_1 = "testName123";
  static final long TEST_LAST_ACCESSED_AT_1 = 1698294600000L;
  static final String TEST_USER_IDENTIFIER_2 = "testUser223";
  static final String TEST_USER_EMAIL_2 = "testEmail223";
  static final String TEST_USER_NAME_2 = "testName223";
  static final long TEST_LAST_ACCESSED_AT_2 = 1698294600002L;

  AutoCloseable openMocks;
  @InjectMocks IDPLicenseUsageImpl idpLicenseUsage;
  @Mock ActiveDevelopersRepository activeDevelopersRepository;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetLicenseUsage() {
    List<ActiveDevelopersEntity> activeDevelopersEntityList = activeDevelopersEntityList();

    when(activeDevelopersRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(activeDevelopersEntityList);

    IDPLicenseUsageDTO idpLicenseUsageDTO = idpLicenseUsage.getLicenseUsage(
        TEST_ACCOUNT_IDENTIFIER, ModuleType.IDP, System.currentTimeMillis(), UsageRequestParams.builder().build());

    assertNotNull(idpLicenseUsageDTO);
    assertEquals(TEST_ACCOUNT_IDENTIFIER, idpLicenseUsageDTO.getAccountIdentifier());
    assertEquals(ModuleType.IDP.toString(), idpLicenseUsageDTO.getModule());
    assertEquals(2, idpLicenseUsageDTO.getActiveDevelopers().getCount());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testListLicenseUsage() {
    Pageable pageRequest =
        PageableUtils.getPageRequest(0, 30, Collections.emptyList(), Sort.by(Sort.Direction.DESC, "lastAccessedAt"));
    ActiveDevelopersFilterParams activeDevelopersFilterParams =
        ActiveDevelopersFilterParams.builder().userIdentifier("").build();
    DefaultPageableUsageRequestParams usageRequest = DefaultPageableUsageRequestParams.builder()
                                                         .filterParams(activeDevelopersFilterParams)
                                                         .pageRequest(pageRequest)
                                                         .build();

    List<ActiveDevelopersEntity> activeDevelopersEntityList = activeDevelopersEntityList();

    when(activeDevelopersRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(activeDevelopersEntityList);

    Page<IDPActiveDevelopersDTO> idpActiveDevelopersDTOPage = idpLicenseUsage.listLicenseUsage(
        TEST_ACCOUNT_IDENTIFIER, ModuleType.IDP, System.currentTimeMillis(), usageRequest);

    assertEquals(2, idpActiveDevelopersDTOPage.getTotalElements());
    assertEquals(1, idpActiveDevelopersDTOPage.getTotalPages());
    assertEquals(2, idpActiveDevelopersDTOPage.getContent().size());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testListLicenseUsageWithUserIdentifierAndSortAsc() {
    Pageable pageRequest =
        PageableUtils.getPageRequest(0, 30, Collections.emptyList(), Sort.by(Sort.Direction.ASC, "lastAccessedAt"));
    ActiveDevelopersFilterParams activeDevelopersFilterParams =
        ActiveDevelopersFilterParams.builder().userIdentifier(TEST_USER_IDENTIFIER_1).build();
    DefaultPageableUsageRequestParams usageRequest = DefaultPageableUsageRequestParams.builder()
                                                         .filterParams(activeDevelopersFilterParams)
                                                         .pageRequest(pageRequest)
                                                         .build();

    ActiveDevelopersEntity activeDevelopersEntity1 = ActiveDevelopersEntity.builder()
                                                         .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                                         .userIdentifier(TEST_USER_IDENTIFIER_1)
                                                         .email(TEST_USER_EMAIL_1)
                                                         .userName(TEST_USER_NAME_1)
                                                         .lastAccessedAt(TEST_LAST_ACCESSED_AT_1)
                                                         .build();

    when(activeDevelopersRepository.findByAccountIdentifierAndUserIdentifier(
             TEST_ACCOUNT_IDENTIFIER, TEST_USER_IDENTIFIER_1))
        .thenReturn(Optional.of(activeDevelopersEntity1));

    Page<IDPActiveDevelopersDTO> idpActiveDevelopersDTOPage = idpLicenseUsage.listLicenseUsage(
        TEST_ACCOUNT_IDENTIFIER, ModuleType.IDP, System.currentTimeMillis(), usageRequest);

    assertEquals(1, idpActiveDevelopersDTOPage.getTotalElements());
    assertEquals(1, idpActiveDevelopersDTOPage.getTotalPages());
    assertThat(idpActiveDevelopersDTOPage.getContent())
        .isEqualTo(idpActiveDevelopersDTOList(Collections.singletonList(activeDevelopersEntity1)));
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetLicenseUsageCSVReport() {
    File file =
        idpLicenseUsage.getLicenseUsageCSVReport(TEST_ACCOUNT_IDENTIFIER, ModuleType.IDP, System.currentTimeMillis());
    assertNull(file);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  private List<ActiveDevelopersEntity> activeDevelopersEntityList() {
    ActiveDevelopersEntity activeDevelopersEntity1 = ActiveDevelopersEntity.builder()
                                                         .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                                         .userIdentifier(TEST_USER_IDENTIFIER_1)
                                                         .email(TEST_USER_EMAIL_1)
                                                         .userName(TEST_USER_NAME_1)
                                                         .lastAccessedAt(TEST_LAST_ACCESSED_AT_1)
                                                         .build();
    ActiveDevelopersEntity activeDevelopersEntity2 = ActiveDevelopersEntity.builder()
                                                         .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                                         .userIdentifier(TEST_USER_IDENTIFIER_2)
                                                         .email(TEST_USER_EMAIL_2)
                                                         .userName(TEST_USER_NAME_2)
                                                         .lastAccessedAt(TEST_LAST_ACCESSED_AT_2)
                                                         .build();

    return List.of(activeDevelopersEntity1, activeDevelopersEntity2);
  }

  private List<IDPActiveDevelopersDTO> idpActiveDevelopersDTOList(
      List<ActiveDevelopersEntity> activeDevelopersEntityList) {
    List<IDPActiveDevelopersDTO> activeDevelopersDTOList = new ArrayList<>();
    activeDevelopersEntityList.forEach(activeDevelopersEntity
        -> activeDevelopersDTOList.add(IDPActiveDevelopersDTO.fromActiveDevelopersEntity(activeDevelopersEntity)));
    return activeDevelopersDTOList;
  }
}
