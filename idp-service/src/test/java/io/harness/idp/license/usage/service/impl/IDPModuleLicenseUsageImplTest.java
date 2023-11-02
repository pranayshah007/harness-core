/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.usage.service.impl;

import static io.harness.rule.OwnerRule.SATHISH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.client.NgConnectorManagerClient;
import io.harness.idp.events.producers.IdpServiceMiscRedisProducer;
import io.harness.idp.license.usage.dto.ActiveDevelopersTrendCountDTO;
import io.harness.idp.license.usage.dto.IDPLicenseUsageUserCaptureDTO;
import io.harness.idp.license.usage.entities.ActiveDevelopersDailyCountEntity;
import io.harness.idp.license.usage.entities.ActiveDevelopersEntity;
import io.harness.idp.license.usage.repositories.ActiveDevelopersDailyCountRepository;
import io.harness.idp.license.usage.repositories.ActiveDevelopersRepository;
import io.harness.licensing.usage.params.filter.IDPLicenseDateUsageParams;
import io.harness.licensing.usage.params.filter.LicenseDateUsageReportType;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class IDPModuleLicenseUsageImplTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  static final String TEST_USER_IDENTIFIER = "testUser123";
  static final String TEST_USER_EMAIL = "testEmail123";
  static final String TEST_USER_NAME = "testName123";
  static final long TEST_LAST_ACCESSED_AT = 1698294600000L;
  static final long TEST_USERS_COUNT = 3;
  static final String TEST_DATE_IN_STRING_FORMAT = "2023-10-26";
  static final Date TEST_DATE_IN_DATE_FORMAT = new Date();

  AutoCloseable openMocks;
  @InjectMocks IDPModuleLicenseUsageImpl idpModuleLicenseUsage;
  @Mock IdpServiceMiscRedisProducer idpServiceMiscRedisProducer;
  @Mock NgConnectorManagerClient ngConnectorManagerClient;
  final List<String> internalAccounts = List.of("kmpySmUISimoRrJL6NL73w");
  @Mock ActiveDevelopersRepository activeDevelopersRepository;
  @Mock ActiveDevelopersDailyCountRepository activeDevelopersDailyCountRepository;

  @Before
  public void setUp() throws IllegalAccessException {
    openMocks = MockitoAnnotations.openMocks(this);

    FieldUtils.writeField(idpModuleLicenseUsage, "internalAccounts", internalAccounts, true);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testCheckIfUrlPathCapturesLicenseUsage() {
    boolean result = idpModuleLicenseUsage.checkIfUrlPathCapturesLicenseUsage("v1/status-info");
    assertTrue(result);

    result = idpModuleLicenseUsage.checkIfUrlPathCapturesLicenseUsage("v1/onboarding/test");
    assertTrue(result);

    result = idpModuleLicenseUsage.checkIfUrlPathCapturesLicenseUsage("v1/dummy");
    assertFalse(result);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testCaptureLicenseUsageInRedis() {
    IDPLicenseUsageUserCaptureDTO idpLicenseUsageUserCaptureDTO = buildIDPLicenseUsageUserCaptureDTO();

    doNothing()
        .when(idpServiceMiscRedisProducer)
        .publishIDPLicenseUsageUserCaptureDTOToRedis(
            TEST_ACCOUNT_IDENTIFIER, TEST_USER_IDENTIFIER, TEST_USER_EMAIL, TEST_USER_NAME, TEST_LAST_ACCESSED_AT);

    idpModuleLicenseUsage.captureLicenseUsageInRedis(idpLicenseUsageUserCaptureDTO);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testSaveLicenseUsageInDB() throws IOException {
    IDPLicenseUsageUserCaptureDTO idpLicenseUsageUserCaptureDTO = buildIDPLicenseUsageUserCaptureDTO();
    ActiveDevelopersEntity activeDevelopersEntity = buildActiveDevelopersEntity(TEST_ACCOUNT_IDENTIFIER);

    RestResponse<Boolean> restResponse = new RestResponse<>(false);
    Call<RestResponse<Boolean>> restResponseCall = mock(Call.class);
    when(ngConnectorManagerClient.isHarnessSupportUser(anyString())).thenReturn(restResponseCall);
    when(restResponseCall.execute()).thenReturn(Response.success(restResponse));
    when(activeDevelopersRepository.findByAccountIdentifierAndUserIdentifier(
             TEST_ACCOUNT_IDENTIFIER, TEST_USER_IDENTIFIER))
        .thenReturn(Optional.of(activeDevelopersEntity));
    when(activeDevelopersRepository.save(activeDevelopersEntity)).thenReturn(activeDevelopersEntity);
    idpModuleLicenseUsage.saveLicenseUsageInDB(idpLicenseUsageUserCaptureDTO);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testSaveLicenseUsageInDBSupportUserInCustomerAccount() throws IOException {
    IDPLicenseUsageUserCaptureDTO idpLicenseUsageUserCaptureDTO = buildIDPLicenseUsageUserCaptureDTO();
    ActiveDevelopersEntity activeDevelopersEntity = buildActiveDevelopersEntity(TEST_ACCOUNT_IDENTIFIER);

    RestResponse<Boolean> restResponse = new RestResponse<>(true);
    Call<RestResponse<Boolean>> restResponseCall = mock(Call.class);
    when(ngConnectorManagerClient.isHarnessSupportUser(anyString())).thenReturn(restResponseCall);
    when(restResponseCall.execute()).thenReturn(Response.success(restResponse));
    when(activeDevelopersRepository.findByAccountIdentifierAndUserIdentifier(
             TEST_ACCOUNT_IDENTIFIER, TEST_USER_IDENTIFIER))
        .thenReturn(Optional.of(activeDevelopersEntity));
    when(activeDevelopersRepository.save(activeDevelopersEntity)).thenReturn(activeDevelopersEntity);
    idpModuleLicenseUsage.saveLicenseUsageInDB(idpLicenseUsageUserCaptureDTO);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testSaveLicenseUsageInDBInternalAccount() throws IOException {
    IDPLicenseUsageUserCaptureDTO idpLicenseUsageUserCaptureDTO = buildIDPLicenseUsageUserCaptureDTO();
    idpLicenseUsageUserCaptureDTO.setAccountIdentifier(internalAccounts.get(0));
    ActiveDevelopersEntity activeDevelopersEntity = buildActiveDevelopersEntity(internalAccounts.get(0));

    RestResponse<Boolean> restResponse = new RestResponse<>(true);
    Call<RestResponse<Boolean>> restResponseCall = mock(Call.class);
    when(ngConnectorManagerClient.isHarnessSupportUser(anyString())).thenReturn(restResponseCall);
    when(restResponseCall.execute()).thenReturn(Response.success(restResponse));
    when(activeDevelopersRepository.findByAccountIdentifierAndUserIdentifier(
             internalAccounts.get(0), TEST_USER_IDENTIFIER))
        .thenReturn(Optional.of(activeDevelopersEntity));
    when(activeDevelopersRepository.save(activeDevelopersEntity)).thenReturn(activeDevelopersEntity);
    idpModuleLicenseUsage.saveLicenseUsageInDB(idpLicenseUsageUserCaptureDTO);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testSaveLicenseUsageInDBInternalAccountNotSupportUser() throws IOException {
    IDPLicenseUsageUserCaptureDTO idpLicenseUsageUserCaptureDTO = buildIDPLicenseUsageUserCaptureDTO();
    idpLicenseUsageUserCaptureDTO.setAccountIdentifier(internalAccounts.get(0));
    ActiveDevelopersEntity activeDevelopersEntity = buildActiveDevelopersEntity(internalAccounts.get(0));

    RestResponse<Boolean> restResponse = new RestResponse<>(false);
    Call<RestResponse<Boolean>> restResponseCall = mock(Call.class);
    when(ngConnectorManagerClient.isHarnessSupportUser(anyString())).thenReturn(restResponseCall);
    when(restResponseCall.execute()).thenReturn(Response.success(restResponse));
    when(activeDevelopersRepository.findByAccountIdentifierAndUserIdentifier(
             internalAccounts.get(0), TEST_USER_IDENTIFIER))
        .thenReturn(Optional.of(activeDevelopersEntity));
    when(activeDevelopersRepository.save(activeDevelopersEntity)).thenReturn(activeDevelopersEntity);
    idpModuleLicenseUsage.saveLicenseUsageInDB(idpLicenseUsageUserCaptureDTO);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testLicenseUsageDailyCountAggregationPerAccount() {
    ActiveDevelopersEntity activeDevelopersEntity = buildActiveDevelopersEntity(internalAccounts.get(0));
    ActiveDevelopersDailyCountEntity activeDevelopersDailyCountEntity =
        ActiveDevelopersDailyCountEntity.builder()
            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
            .dateInStringFormat(TEST_DATE_IN_STRING_FORMAT)
            .dateInDateFormat(TEST_DATE_IN_DATE_FORMAT)
            .count(TEST_USERS_COUNT)
            .build();
    List<ActiveDevelopersDailyCountEntity> activeDevelopersDailyCountEntities = new ArrayList<>();
    activeDevelopersDailyCountEntities.add(activeDevelopersDailyCountEntity);

    when(activeDevelopersRepository.findByLastAccessedAtBetween(anyLong(), anyLong()))
        .thenReturn(Collections.singletonList(activeDevelopersEntity));
    when(activeDevelopersDailyCountRepository.saveAll(activeDevelopersDailyCountEntities))
        .thenReturn(activeDevelopersDailyCountEntities);
    idpModuleLicenseUsage.licenseUsageDailyCountAggregationPerAccount();
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetHistoryTrend() {
    IDPLicenseDateUsageParams idpLicenseDateUsageParams = IDPLicenseDateUsageParams.builder()
                                                              .fromDate("2023-10-23")
                                                              .toDate("2023-10-26")
                                                              .reportType(LicenseDateUsageReportType.DAILY)
                                                              .build();

    List<ActiveDevelopersDailyCountEntity> activeDevelopersDailyCountEntities = new ArrayList<>();
    ActiveDevelopersDailyCountEntity activeDevelopersDailyCountEntity =
        ActiveDevelopersDailyCountEntity.builder()
            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
            .dateInStringFormat(TEST_DATE_IN_STRING_FORMAT)
            .dateInDateFormat(TEST_DATE_IN_DATE_FORMAT)
            .count(TEST_USERS_COUNT)
            .build();
    activeDevelopersDailyCountEntities.add(activeDevelopersDailyCountEntity);

    when(activeDevelopersDailyCountRepository.findByAccountIdentifierAndDateInDateFormatBetween(any(), any(), any()))
        .thenReturn(activeDevelopersDailyCountEntities);
    List<ActiveDevelopersTrendCountDTO> activeDevelopersTrendCountDTOList =
        idpModuleLicenseUsage.getHistoryTrend(TEST_ACCOUNT_IDENTIFIER, idpLicenseDateUsageParams);

    assertNotNull(activeDevelopersTrendCountDTOList);
    assertEquals(1, activeDevelopersTrendCountDTOList.size());
    assertEquals(TEST_USERS_COUNT, activeDevelopersTrendCountDTOList.get(0).getCount());
    assertEquals(TEST_DATE_IN_STRING_FORMAT, activeDevelopersTrendCountDTOList.get(0).getDate());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetHistoryTrendMonthly() {
    IDPLicenseDateUsageParams idpLicenseDateUsageParams = IDPLicenseDateUsageParams.builder()
                                                              .fromDate("2023-09-26")
                                                              .toDate("2023-10-26")
                                                              .reportType(LicenseDateUsageReportType.MONTHLY)
                                                              .build();
    List<ActiveDevelopersDailyCountEntity> activeDevelopersDailyCountEntities = new ArrayList<>();
    ActiveDevelopersDailyCountEntity activeDevelopersDailyCountEntity =
        ActiveDevelopersDailyCountEntity.builder()
            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
            .dateInStringFormat(TEST_DATE_IN_STRING_FORMAT)
            .dateInDateFormat(TEST_DATE_IN_DATE_FORMAT)
            .count(TEST_USERS_COUNT)
            .build();
    activeDevelopersDailyCountEntities.add(activeDevelopersDailyCountEntity);

    when(activeDevelopersDailyCountRepository.findByAccountIdentifierAndDateInDateFormatBetween(any(), any(), any()))
        .thenReturn(activeDevelopersDailyCountEntities);
    List<ActiveDevelopersTrendCountDTO> activeDevelopersTrendCountDTOList =
        idpModuleLicenseUsage.getHistoryTrend(TEST_ACCOUNT_IDENTIFIER, idpLicenseDateUsageParams);

    assertNotNull(activeDevelopersTrendCountDTOList);
    assertEquals(1, activeDevelopersTrendCountDTOList.size());
    assertEquals(1, activeDevelopersTrendCountDTOList.get(0).getCount());
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  private IDPLicenseUsageUserCaptureDTO buildIDPLicenseUsageUserCaptureDTO() {
    return IDPLicenseUsageUserCaptureDTO.builder()
        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
        .userIdentifier(TEST_USER_IDENTIFIER)
        .email(TEST_USER_EMAIL)
        .userName(TEST_USER_NAME)
        .accessedAt(TEST_LAST_ACCESSED_AT)
        .build();
  }

  private ActiveDevelopersEntity buildActiveDevelopersEntity(String accountIdentifier) {
    return ActiveDevelopersEntity.builder()
        .accountIdentifier(accountIdentifier)
        .userIdentifier(TEST_USER_IDENTIFIER)
        .email(TEST_USER_EMAIL)
        .userName(TEST_USER_NAME)
        .lastAccessedAt(TEST_LAST_ACCESSED_AT)
        .build();
  }
}
