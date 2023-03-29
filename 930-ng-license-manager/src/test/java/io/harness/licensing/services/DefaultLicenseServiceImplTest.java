/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.licensing.services;

import static io.harness.ModuleType.CD;
import static io.harness.ModuleType.CE;
import static io.harness.ModuleType.CF;
import static io.harness.ModuleType.CHAOS;
import static io.harness.ModuleType.CI;
import static io.harness.ModuleType.CV;
import static io.harness.ModuleType.IACM;
import static io.harness.ModuleType.SRM;
import static io.harness.ModuleType.STO;
import static io.harness.licensing.LicenseConstant.UNLIMITED;
import static io.harness.licensing.LicenseTestConstant.ACCOUNT_IDENTIFIER;
import static io.harness.licensing.LicenseTestConstant.DEFAULT_CI_MODULE_LICENSE;
import static io.harness.licensing.LicenseTestConstant.DEFAULT_CI_MODULE_LICENSE_DTO;
import static io.harness.licensing.LicenseTestConstant.DEFAULT_MODULE_TYPE;
import static io.harness.licensing.services.DefaultLicenseServiceImpl.SUCCEED_EXTEND_TRIAL_OPERATION;
import static io.harness.licensing.services.DefaultLicenseServiceImpl.SUCCEED_START_FREE_OPERATION;
import static io.harness.licensing.services.DefaultLicenseServiceImpl.SUCCEED_START_TRIAL_OPERATION;
import static io.harness.licensing.services.DefaultLicenseServiceImpl.TRIAL_ENDED;
import static io.harness.rule.OwnerRule.KAPIL_GARG;
import static io.harness.rule.OwnerRule.NATHAN;
import static io.harness.rule.OwnerRule.XIN;
import static io.harness.rule.OwnerRule.ZHUO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.account.services.AccountService;
import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.ccm.license.remote.CeLicenseClient;
import io.harness.exception.InvalidRequestException;
import io.harness.licensing.Edition;
import io.harness.licensing.EditionAction;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.LicenseType;
import io.harness.licensing.beans.EditionActionDTO;
import io.harness.licensing.beans.modules.AccountLicenseDTO;
import io.harness.licensing.beans.modules.CDModuleLicenseDTO;
import io.harness.licensing.beans.modules.CEModuleLicenseDTO;
import io.harness.licensing.beans.modules.CIModuleLicenseDTO;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.beans.modules.SMPEncLicenseDTO;
import io.harness.licensing.beans.modules.SMPLicenseRequestDTO;
import io.harness.licensing.beans.modules.SMPValidationResultDTO;
import io.harness.licensing.beans.modules.StartTrialDTO;
import io.harness.licensing.beans.response.CheckExpiryResultDTO;
import io.harness.licensing.checks.LicenseComplianceResolver;
import io.harness.licensing.entities.modules.CDModuleLicense;
import io.harness.licensing.entities.modules.CEModuleLicense;
import io.harness.licensing.entities.modules.CIModuleLicense;
import io.harness.licensing.entities.modules.ModuleLicense;
import io.harness.licensing.interfaces.ModuleLicenseInterface;
import io.harness.licensing.mappers.LicenseObjectConverter;
import io.harness.licensing.mappers.SMPLicenseMapper;
import io.harness.ng.core.account.DefaultExperience;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.repositories.ModuleLicenseRepository;
import io.harness.rule.Owner;
import io.harness.smp.license.models.SMPLicenseValidationResult;
import io.harness.smp.license.v1.LicenseGenerator;
import io.harness.smp.license.v1.LicenseValidator;
import io.harness.telemetry.TelemetryReporter;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.cache.Cache;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

public class DefaultLicenseServiceImplTest extends CategoryTest {
  @Mock ModuleLicenseRepository moduleLicenseRepository;
  @Mock ModuleLicenseInterface moduleLicenseInterface;
  @Mock LicenseObjectConverter licenseObjectConverter;
  @Mock AccountService accountService;
  @Mock TelemetryReporter telemetryReporter;
  @Mock CeLicenseClient ceLicenseClient;
  @Mock LicenseComplianceResolver licenseComplianceResolver;
  @Mock LicenseGenerator licenseGenerator;
  @Mock LicenseValidator licenseValidator;
  @Mock SMPLicenseMapper smpLicenseMapper;
  @Mock Cache<String, List> cache;
  @InjectMocks DefaultLicenseServiceImpl licenseService;

  private StartTrialDTO startTrialRequestDTO;
  private AccountLicenseDTO defaultAccountLicensesDTO;
  private static final long EXPIRY_TIME = 1651518231;
  private static final int HOSTING_CREDITS = 100;
  private static final int NUMBER_OF_COMMITERS = 10;

  @Before
  public void setUp() {
    initMocks(this);
    startTrialRequestDTO = StartTrialDTO.builder().moduleType(DEFAULT_MODULE_TYPE).edition(Edition.ENTERPRISE).build();
    when(licenseObjectConverter.toDTO(DEFAULT_CI_MODULE_LICENSE)).thenReturn(DEFAULT_CI_MODULE_LICENSE_DTO);
    when(licenseObjectConverter.toEntity(DEFAULT_CI_MODULE_LICENSE_DTO)).thenReturn(DEFAULT_CI_MODULE_LICENSE);

    defaultAccountLicensesDTO =
        AccountLicenseDTO.builder()
            .accountId(ACCOUNT_IDENTIFIER)
            .moduleLicenses(null)
            .allModuleLicenses(ImmutableMap.<ModuleType, List<ModuleLicenseDTO>>builder()
                                   .put(DEFAULT_MODULE_TYPE, Lists.newArrayList(DEFAULT_CI_MODULE_LICENSE_DTO))
                                   .put(CD, Lists.newArrayList())
                                   .put(CE, Lists.newArrayList())
                                   .put(CF, Lists.newArrayList())
                                   .put(CHAOS, Lists.newArrayList())
                                   .put(CV, Lists.newArrayList())
                                   .put(STO, Lists.newArrayList())
                                   .put(SRM, Lists.newArrayList())
                                   .put(IACM, Lists.newArrayList())
                                   .build())
            .build();

    when(cache.containsKey(ACCOUNT_IDENTIFIER + ":" + DEFAULT_MODULE_TYPE)).thenReturn(false);
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testGetModuleLicenses() {
    when(moduleLicenseRepository.findByAccountIdentifierAndModuleType(ACCOUNT_IDENTIFIER, DEFAULT_MODULE_TYPE))
        .thenReturn(Lists.newArrayList(DEFAULT_CI_MODULE_LICENSE));
    List<ModuleLicenseDTO> moduleLicense = licenseService.getModuleLicenses(ACCOUNT_IDENTIFIER, DEFAULT_MODULE_TYPE);

    assertThat(moduleLicense).isEqualTo(Lists.newArrayList(DEFAULT_CI_MODULE_LICENSE_DTO));
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testGetModuleLicenseWithNoResult() {
    when(moduleLicenseRepository.findByAccountIdentifierAndModuleType(ACCOUNT_IDENTIFIER, DEFAULT_MODULE_TYPE))
        .thenReturn(new ArrayList<>());
    ModuleLicenseDTO moduleLicense = licenseService.getModuleLicense(ACCOUNT_IDENTIFIER, DEFAULT_MODULE_TYPE);
    assertThat(moduleLicense).isNull();
  }

  @Test
  @Owner(developers = XIN)
  @Category(UnitTests.class)
  public void testGetModuleLicensesByModuleTypeAndExpiryTime() {
    when(moduleLicenseRepository.findByModuleTypeAndExpiryTimeGreaterThanEqual(DEFAULT_MODULE_TYPE, EXPIRY_TIME))
        .thenReturn(Lists.newArrayList(DEFAULT_CI_MODULE_LICENSE));
    List<ModuleLicenseDTO> moduleLicense =
        licenseService.getEnabledModuleLicensesByModuleType(DEFAULT_MODULE_TYPE, EXPIRY_TIME);
    assertThat(moduleLicense).isEqualTo(Lists.newArrayList(DEFAULT_CI_MODULE_LICENSE_DTO));
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testGetAccountLicense() {
    when(moduleLicenseRepository.findByAccountIdentifierAndModuleType(ACCOUNT_IDENTIFIER, DEFAULT_MODULE_TYPE))
        .thenReturn(ImmutableList.<ModuleLicense>builder().add(DEFAULT_CI_MODULE_LICENSE).build());
    AccountLicenseDTO accountLicenseDTO = licenseService.getAccountLicense(ACCOUNT_IDENTIFIER);

    assertThat(accountLicenseDTO).isEqualTo(defaultAccountLicensesDTO);
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testGetAccountLicenseWithNoResult() {
    when(moduleLicenseRepository.findByAccountIdentifierAndModuleType(eq(ACCOUNT_IDENTIFIER), any()))
        .thenReturn(Collections.emptyList());
    AccountLicenseDTO accountLicenseDTO = licenseService.getAccountLicense(ACCOUNT_IDENTIFIER);

    assertThat(accountLicenseDTO.getAccountId()).isEqualTo(ACCOUNT_IDENTIFIER);
    assertThat(accountLicenseDTO.getAllModuleLicenses().get(CI).size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testGetModuleLicenseById() {
    when(moduleLicenseRepository.findById(any())).thenReturn(Optional.of(DEFAULT_CI_MODULE_LICENSE));
    ModuleLicenseDTO moduleLicense = licenseService.getModuleLicenseById("1");

    assertThat(moduleLicense).isEqualTo(DEFAULT_CI_MODULE_LICENSE_DTO);
  }

  @Test
  @Owner(developers = NATHAN)
  @Category(UnitTests.class)
  public void testStartFreeLicense() {
    CIModuleLicense ciModuleLicense =
        CIModuleLicense.builder().numberOfCommitters(NUMBER_OF_COMMITERS).hostingCredits(HOSTING_CREDITS).build();
    ciModuleLicense.setId("id");
    ciModuleLicense.setAccountIdentifier(ACCOUNT_IDENTIFIER);
    ciModuleLicense.setModuleType(DEFAULT_MODULE_TYPE);
    ciModuleLicense.setEdition(Edition.FREE);
    ciModuleLicense.setStatus(LicenseStatus.ACTIVE);
    ciModuleLicense.setStartTime(1);
    ciModuleLicense.setExpiryTime(Long.valueOf(UNLIMITED));
    ciModuleLicense.setCreatedAt(0L);
    ciModuleLicense.setLastUpdatedAt(0L);

    CIModuleLicenseDTO ciModuleLicenseDTO = CIModuleLicenseDTO.builder()
                                                .id("id")
                                                .numberOfCommitters(NUMBER_OF_COMMITERS)
                                                .hostingCredits(HOSTING_CREDITS)
                                                .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                .moduleType(DEFAULT_MODULE_TYPE)
                                                .edition(Edition.FREE)
                                                .status(LicenseStatus.ACTIVE)
                                                .startTime(1)
                                                .expiryTime(Long.valueOf(UNLIMITED))
                                                .createdAt(0L)
                                                .lastModifiedAt(0L)
                                                .build();

    when(licenseObjectConverter.toDTO(ciModuleLicense)).thenReturn(ciModuleLicenseDTO);
    when(licenseObjectConverter.toEntity(ciModuleLicenseDTO)).thenReturn(ciModuleLicense);
    when(moduleLicenseRepository.save(ciModuleLicense)).thenReturn(ciModuleLicense);
    when(moduleLicenseInterface.generateFreeLicense(eq(ACCOUNT_IDENTIFIER), eq(DEFAULT_MODULE_TYPE)))
        .thenReturn(ciModuleLicenseDTO);
    when(accountService.getAccount(ACCOUNT_IDENTIFIER)).thenReturn(AccountDTO.builder().build());
    ModuleLicenseDTO result = licenseService.startFreeLicense(ACCOUNT_IDENTIFIER, CI, null, null);
    verify(accountService, times(1)).updateDefaultExperienceIfApplicable(ACCOUNT_IDENTIFIER, DefaultExperience.NG);
    verify(telemetryReporter, times(1)).sendGroupEvent(eq(ACCOUNT_IDENTIFIER), any(), any());
    verify(telemetryReporter, times(1))
        .sendTrackEvent(eq(SUCCEED_START_FREE_OPERATION), any(), any(), eq(io.harness.telemetry.Category.SIGN_UP));
    verify(cache, times(1)).remove(any());
    assertThat(result).isEqualTo(ciModuleLicenseDTO);
  }

  @Test
  @Owner(developers = NATHAN)
  @Category(UnitTests.class)
  public void testStartCommunityLicense() {
    CIModuleLicense ciModuleLicense =
        CIModuleLicense.builder().numberOfCommitters(NUMBER_OF_COMMITERS).hostingCredits(HOSTING_CREDITS).build();
    ciModuleLicense.setId("id");
    ciModuleLicense.setAccountIdentifier(ACCOUNT_IDENTIFIER);
    ciModuleLicense.setModuleType(DEFAULT_MODULE_TYPE);
    ciModuleLicense.setEdition(Edition.FREE);
    ciModuleLicense.setStatus(LicenseStatus.ACTIVE);
    ciModuleLicense.setStartTime(1);
    ciModuleLicense.setExpiryTime(Long.valueOf(UNLIMITED));
    ciModuleLicense.setCreatedAt(0L);
    ciModuleLicense.setLastUpdatedAt(0L);

    CIModuleLicenseDTO ciModuleLicenseDTO = CIModuleLicenseDTO.builder()
                                                .id("id")
                                                .numberOfCommitters(NUMBER_OF_COMMITERS)
                                                .hostingCredits(HOSTING_CREDITS)
                                                .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                .moduleType(DEFAULT_MODULE_TYPE)
                                                .edition(Edition.FREE)
                                                .status(LicenseStatus.ACTIVE)
                                                .startTime(1)
                                                .expiryTime(Long.valueOf(UNLIMITED))
                                                .createdAt(0L)
                                                .lastModifiedAt(0L)
                                                .build();

    when(licenseObjectConverter.toDTO(ciModuleLicense)).thenReturn(ciModuleLicenseDTO);
    when(licenseObjectConverter.toEntity(ciModuleLicenseDTO)).thenReturn(ciModuleLicense);
    when(moduleLicenseRepository.save(ciModuleLicense)).thenReturn(ciModuleLicense);
    when(moduleLicenseInterface.generateFreeLicense(eq(ACCOUNT_IDENTIFIER), eq(DEFAULT_MODULE_TYPE)))
        .thenReturn(ciModuleLicenseDTO);
    when(accountService.getAccount(ACCOUNT_IDENTIFIER)).thenReturn(AccountDTO.builder().build());
    ModuleLicenseDTO result = licenseService.startFreeLicense(ACCOUNT_IDENTIFIER, CI, null, null);
    verify(accountService, times(1)).updateDefaultExperienceIfApplicable(ACCOUNT_IDENTIFIER, DefaultExperience.NG);
    verify(telemetryReporter, times(1)).sendGroupEvent(eq(ACCOUNT_IDENTIFIER), any(), any());
    verify(telemetryReporter, times(1))
        .sendTrackEvent(eq(SUCCEED_START_FREE_OPERATION), any(), any(), eq(io.harness.telemetry.Category.SIGN_UP));
    assertThat(result).isEqualTo(ciModuleLicenseDTO);
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testStartTrial() {
    when(moduleLicenseRepository.save(DEFAULT_CI_MODULE_LICENSE)).thenReturn(DEFAULT_CI_MODULE_LICENSE);
    when(moduleLicenseInterface.generateTrialLicense(any(), eq(ACCOUNT_IDENTIFIER), eq(DEFAULT_MODULE_TYPE)))
        .thenReturn(DEFAULT_CI_MODULE_LICENSE_DTO);
    when(accountService.getAccount(ACCOUNT_IDENTIFIER)).thenReturn(AccountDTO.builder().build());

    ModuleLicenseDTO result = licenseService.startTrialLicense(ACCOUNT_IDENTIFIER, startTrialRequestDTO, null);
    verify(accountService, times(1)).updateDefaultExperienceIfApplicable(ACCOUNT_IDENTIFIER, DefaultExperience.NG);
    verify(telemetryReporter, times(1)).sendGroupEvent(eq(ACCOUNT_IDENTIFIER), any(), any());
    verify(telemetryReporter, times(1))
        .sendTrackEvent(eq(SUCCEED_START_TRIAL_OPERATION), any(), any(), eq(io.harness.telemetry.Category.SIGN_UP));
    verifyZeroInteractions(ceLicenseClient);
    assertThat(result).isEqualTo(DEFAULT_CI_MODULE_LICENSE_DTO);
    verify(cache, times(1)).remove(any());
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testStartTrialForCE() {
    CEModuleLicense ceModuleLicense = CEModuleLicense.builder().spendLimit(-1L).build();
    ceModuleLicense.setAccountIdentifier(ACCOUNT_IDENTIFIER);
    ceModuleLicense.setModuleType(CE);
    ceModuleLicense.setLicenseType(LicenseType.TRIAL);
    ceModuleLicense.setEdition(Edition.ENTERPRISE);

    CEModuleLicenseDTO ceModuleLicenseDTO = CEModuleLicenseDTO.builder()
                                                .spendLimit(-1L)
                                                .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                .edition(Edition.ENTERPRISE)
                                                .licenseType(LicenseType.TRIAL)
                                                .moduleType(CE)
                                                .build();

    StartTrialDTO startTrialDTO = StartTrialDTO.builder().moduleType(CE).edition(Edition.ENTERPRISE).build();
    when(licenseObjectConverter.toEntity(ceModuleLicenseDTO)).thenReturn(ceModuleLicense);
    when(moduleLicenseRepository.save(ceModuleLicense)).thenReturn(ceModuleLicense);
    when(moduleLicenseInterface.generateTrialLicense(any(), eq(ACCOUNT_IDENTIFIER), eq(ModuleType.CE)))
        .thenReturn(ceModuleLicenseDTO);
    when(accountService.getAccount(ACCOUNT_IDENTIFIER)).thenReturn(AccountDTO.builder().build());
    licenseService.startTrialLicense(ACCOUNT_IDENTIFIER, startTrialDTO, null);
    verify(ceLicenseClient, times(1)).createCeTrial(any());
    verify(cache, times(1)).remove(any());
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testExtendTrial() {
    when(moduleLicenseInterface.generateTrialLicense(any(), eq(ACCOUNT_IDENTIFIER), eq(DEFAULT_MODULE_TYPE)))
        .thenReturn(DEFAULT_CI_MODULE_LICENSE_DTO);

    CIModuleLicense expiredTrial =
        CIModuleLicense.builder().numberOfCommitters(NUMBER_OF_COMMITERS).hostingCredits(HOSTING_CREDITS).build();
    expiredTrial.setAccountIdentifier(ACCOUNT_IDENTIFIER);
    expiredTrial.setModuleType(DEFAULT_MODULE_TYPE);
    expiredTrial.setLicenseType(LicenseType.TRIAL);
    expiredTrial.setEdition(Edition.ENTERPRISE);
    expiredTrial.setExpiryTime(Instant.now().minus(5, ChronoUnit.DAYS).toEpochMilli());
    expiredTrial.setStatus(LicenseStatus.EXPIRED);

    when(moduleLicenseRepository.save(any())).thenReturn(DEFAULT_CI_MODULE_LICENSE);
    when(moduleLicenseRepository.findByAccountIdentifierAndModuleType(eq(ACCOUNT_IDENTIFIER), eq(DEFAULT_MODULE_TYPE)))
        .thenReturn(Lists.newArrayList(expiredTrial));

    ModuleLicenseDTO result = licenseService.extendTrialLicense(ACCOUNT_IDENTIFIER, startTrialRequestDTO);
    verify(telemetryReporter, times(1))
        .sendTrackEvent(eq(SUCCEED_EXTEND_TRIAL_OPERATION), any(), any(), eq(io.harness.telemetry.Category.SIGN_UP));
    verifyZeroInteractions(ceLicenseClient);
    assertThat(result).isEqualTo(DEFAULT_CI_MODULE_LICENSE_DTO);
    verify(cache, times(1)).remove(any());

    ArgumentCaptor<ModuleLicense> extendedLicense = ArgumentCaptor.forClass(ModuleLicense.class);
    verify(moduleLicenseRepository, times(1)).save(extendedLicense.capture());
    assertThat(extendedLicense.getValue().getStatus()).isEqualTo(LicenseStatus.ACTIVE);
    assertThat(extendedLicense.getValue().getTrialExtended()).isTrue();
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testCheckExpiryWithRegularTrial() {
    ModuleLicense moduleLicense =
        CIModuleLicense.builder().numberOfCommitters(NUMBER_OF_COMMITERS).hostingCredits(HOSTING_CREDITS).build();
    moduleLicense.setStatus(LicenseStatus.ACTIVE);
    moduleLicense.setModuleType(ModuleType.CI);
    moduleLicense.setEdition(Edition.ENTERPRISE);
    moduleLicense.setLicenseType(LicenseType.TRIAL);
    moduleLicense.setExpiryTime(Long.MAX_VALUE);
    when(moduleLicenseRepository.findByAccountIdentifier(ACCOUNT_IDENTIFIER))
        .thenReturn(ImmutableList.<ModuleLicense>builder().add(moduleLicense).build());

    CheckExpiryResultDTO checkExpiryResultDTO = licenseService.checkExpiry(ACCOUNT_IDENTIFIER);
    assertThat(checkExpiryResultDTO.isShouldDelete()).isFalse();
    assertThat(checkExpiryResultDTO.getExpiryTime()).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testCheckExpiryWithTrialExpire() {
    ModuleLicense expiryModuleLicense = CDModuleLicense.builder().build();
    expiryModuleLicense.setStatus(LicenseStatus.ACTIVE);
    expiryModuleLicense.setModuleType(CD);
    expiryModuleLicense.setEdition(Edition.ENTERPRISE);
    expiryModuleLicense.setLicenseType(LicenseType.TRIAL);
    expiryModuleLicense.setExpiryTime(0);
    expiryModuleLicense.setCreatedBy(EmbeddedUser.builder().email("test").build());

    when(moduleLicenseRepository.findByAccountIdentifier(ACCOUNT_IDENTIFIER))
        .thenReturn(ImmutableList.<ModuleLicense>builder().add(expiryModuleLicense).build());

    CheckExpiryResultDTO checkExpiryResultDTO = licenseService.checkExpiry(ACCOUNT_IDENTIFIER);
    assertThat(checkExpiryResultDTO.isShouldDelete()).isTrue();
    assertThat(checkExpiryResultDTO.getExpiryTime()).isEqualTo(0);
    verify(moduleLicenseRepository, times(1)).save(any());
    verify(telemetryReporter, times(1)).sendTrackEvent(eq(TRIAL_ENDED), eq("test"), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testCheckExpiryWithAlreadyExpiredTrial() {
    ModuleLicense expiredModuleLicense = CDModuleLicense.builder().build();
    expiredModuleLicense.setStatus(LicenseStatus.EXPIRED);
    expiredModuleLicense.setModuleType(CD);
    expiredModuleLicense.setEdition(Edition.ENTERPRISE);
    expiredModuleLicense.setLicenseType(LicenseType.TRIAL);
    expiredModuleLicense.setExpiryTime(0);

    when(moduleLicenseRepository.findByAccountIdentifier(ACCOUNT_IDENTIFIER))
        .thenReturn(ImmutableList.<ModuleLicense>builder().add(expiredModuleLicense).build());

    CheckExpiryResultDTO checkExpiryResultDTO = licenseService.checkExpiry(ACCOUNT_IDENTIFIER);
    assertThat(checkExpiryResultDTO.isShouldDelete()).isTrue();
    assertThat(checkExpiryResultDTO.getExpiryTime()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testCheckExpiryWithFreeEdition() {
    ModuleLicense moduleLicense =
        CIModuleLicense.builder().numberOfCommitters(NUMBER_OF_COMMITERS).hostingCredits(HOSTING_CREDITS).build();
    moduleLicense.setStatus(LicenseStatus.ACTIVE);
    moduleLicense.setModuleType(ModuleType.CI);
    moduleLicense.setEdition(Edition.FREE);
    moduleLicense.setLicenseType(LicenseType.TRIAL);

    when(moduleLicenseRepository.findByAccountIdentifier(ACCOUNT_IDENTIFIER))
        .thenReturn(ImmutableList.<ModuleLicense>builder().add(moduleLicense).build());

    CheckExpiryResultDTO checkExpiryResultDTO = licenseService.checkExpiry(ACCOUNT_IDENTIFIER);
    assertThat(checkExpiryResultDTO.isShouldDelete()).isFalse();
    assertThat(checkExpiryResultDTO.getExpiryTime()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testCheckExpiryWithPaid() {
    ModuleLicense moduleLicense =
        CIModuleLicense.builder().numberOfCommitters(NUMBER_OF_COMMITERS).hostingCredits(HOSTING_CREDITS).build();
    moduleLicense.setStatus(LicenseStatus.ACTIVE);
    moduleLicense.setModuleType(ModuleType.CI);
    moduleLicense.setEdition(Edition.ENTERPRISE);
    moduleLicense.setLicenseType(LicenseType.PAID);

    when(moduleLicenseRepository.findByAccountIdentifier(ACCOUNT_IDENTIFIER))
        .thenReturn(ImmutableList.<ModuleLicense>builder().add(moduleLicense).build());

    CheckExpiryResultDTO checkExpiryResultDTO = licenseService.checkExpiry(ACCOUNT_IDENTIFIER);
    assertThat(checkExpiryResultDTO.isShouldDelete()).isFalse();
    assertThat(checkExpiryResultDTO.getExpiryTime()).isEqualTo(0);
    verify(moduleLicenseRepository, times(1)).save(any());
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testGetEditionActions() {
    when(licenseComplianceResolver.getEditionStates(any(), eq(ACCOUNT_IDENTIFIER)))
        .thenReturn(ImmutableMap.<Edition, Set<EditionAction>>builder()
                        .put(Edition.FREE, Sets.newHashSet(EditionAction.START_FREE))
                        .put(Edition.TEAM, Sets.newHashSet(EditionAction.DISABLED_BY_ENTERPRISE))
                        .put(Edition.ENTERPRISE, Sets.newHashSet(EditionAction.CONTACT_SALES))
                        .build());
    Map<Edition, Set<EditionActionDTO>> editionActions = licenseService.getEditionActions(ACCOUNT_IDENTIFIER, CD);

    assertThat(editionActions.get(Edition.FREE).size()).isEqualTo(1);
    assertThat(editionActions.get(Edition.TEAM))
        .isEqualTo(Sets.newHashSet(EditionActionDTO.builder()
                                       .action(EditionAction.DISABLED_BY_ENTERPRISE)
                                       .reason(EditionAction.DISABLED_BY_ENTERPRISE.getReason())
                                       .build()));
    assertThat(editionActions.get(Edition.ENTERPRISE).size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testGtLastUpdatedAtMap() {
    CDModuleLicense cdModuleLicense = CDModuleLicense.builder().workloads(Integer.valueOf(UNLIMITED)).build();
    cdModuleLicense.setId("id");
    cdModuleLicense.setAccountIdentifier(ACCOUNT_IDENTIFIER);
    cdModuleLicense.setModuleType(DEFAULT_MODULE_TYPE);
    cdModuleLicense.setEdition(Edition.FREE);
    cdModuleLicense.setStatus(LicenseStatus.ACTIVE);
    cdModuleLicense.setStartTime(1);
    cdModuleLicense.setExpiryTime(Long.valueOf(UNLIMITED));
    cdModuleLicense.setCreatedAt(0L);
    cdModuleLicense.setLastUpdatedAt(1000L);

    CDModuleLicenseDTO cdModuleLicenseDTO = CDModuleLicenseDTO.builder()
                                                .id("id")
                                                .workloads(Integer.valueOf(UNLIMITED))
                                                .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                .moduleType(DEFAULT_MODULE_TYPE)
                                                .edition(Edition.FREE)
                                                .status(LicenseStatus.ACTIVE)
                                                .startTime(1)
                                                .expiryTime(Long.valueOf(UNLIMITED))
                                                .createdAt(0L)
                                                .lastModifiedAt(1000L)
                                                .build();

    when(licenseObjectConverter.toDTO(cdModuleLicense)).thenReturn(cdModuleLicenseDTO);
    when(moduleLicenseRepository.findByAccountIdentifierAndModuleType(ACCOUNT_IDENTIFIER, CD))
        .thenReturn(Lists.newArrayList(cdModuleLicense));

    Map<ModuleType, Long> lastUpdatedAtMap = licenseService.getLastUpdatedAtMap(ACCOUNT_IDENTIFIER);
    assertThat(lastUpdatedAtMap.get(CD)).isEqualTo(1000L);
    assertThat(lastUpdatedAtMap.get(CI)).isEqualTo(0);
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testDeleteModuleLicense() {
    CDModuleLicense cdModuleLicense = CDModuleLicense.builder().workloads(Integer.valueOf(UNLIMITED)).build();
    cdModuleLicense.setId("id");
    cdModuleLicense.setAccountIdentifier(ACCOUNT_IDENTIFIER);
    cdModuleLicense.setModuleType(DEFAULT_MODULE_TYPE);
    cdModuleLicense.setEdition(Edition.FREE);
    cdModuleLicense.setStatus(LicenseStatus.ACTIVE);
    cdModuleLicense.setStartTime(1);
    cdModuleLicense.setExpiryTime(Long.valueOf(UNLIMITED));
    cdModuleLicense.setCreatedAt(0L);
    cdModuleLicense.setLastUpdatedAt(1000L);

    when(moduleLicenseRepository.findById("id")).thenReturn(Optional.of(cdModuleLicense));

    licenseService.deleteModuleLicense("id");
    verify(moduleLicenseRepository, times(1)).deleteById("id");
    verify(cache, times(1)).remove(any());
  }

  @Test
  @Owner(developers = KAPIL_GARG)
  @Category(UnitTests.class)
  public void testGenerateSMPLicense() {
    List<ModuleLicense> moduleLicenses = getModuleLicenses();
    AccountDTO accountDTO = getAccountDTO();

    when(accountService.getAccount(accountDTO.getIdentifier())).thenReturn(accountDTO);
    when(moduleLicenseRepository.findByAccountIdentifierAndModuleType(
             Mockito.eq(accountDTO.getIdentifier()), Mockito.any()))
        .thenReturn(moduleLicenses);
    when(licenseGenerator.generateLicense(Mockito.any())).thenReturn("somelicense");

    SMPLicenseRequestDTO licenseRequestDTO = new SMPLicenseRequestDTO();
    licenseRequestDTO.setAccountOptional(false);

    SMPEncLicenseDTO encLicenseDTO = licenseService.generateSMPLicense(accountDTO.getIdentifier(), licenseRequestDTO);
    assertThat(encLicenseDTO).isNotNull();
    assertThat(encLicenseDTO.getEncryptedLicense()).isNotNull();
    assertThat(encLicenseDTO.getEncryptedLicense().length()).isGreaterThan(0);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = KAPIL_GARG)
  @Category(UnitTests.class)
  public void testGenerateSMPLicenseNoAccountLicenseFound() {
    AccountDTO accountDTO = getAccountDTO();

    when(accountService.getAccount(accountDTO.getIdentifier())).thenReturn(accountDTO);
    when(moduleLicenseRepository.findByAccountIdentifierAndModuleType(
             Mockito.eq(accountDTO.getIdentifier()), Mockito.any()))
        .thenReturn(new ArrayList<>());

    SMPLicenseRequestDTO licenseRequestDTO = new SMPLicenseRequestDTO();
    licenseRequestDTO.setAccountOptional(false);

    licenseService.generateSMPLicense(accountDTO.getIdentifier(), licenseRequestDTO);
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = KAPIL_GARG)
  @Category(UnitTests.class)
  public void testGenerateSMPLicenseLibraryInternalError() {
    List<ModuleLicense> moduleLicenses = getModuleLicenses();
    AccountDTO accountDTO = getAccountDTO();

    when(accountService.getAccount(accountDTO.getIdentifier())).thenReturn(null);
    when(moduleLicenseRepository.findByAccountIdentifierAndModuleType(
             Mockito.eq(accountDTO.getIdentifier()), Mockito.any()))
        .thenReturn(moduleLicenses);
    when(licenseGenerator.generateLicense(Mockito.any())).thenThrow(RuntimeException.class);

    SMPLicenseRequestDTO licenseRequestDTO = new SMPLicenseRequestDTO();
    licenseRequestDTO.setAccountOptional(false);

    licenseService.generateSMPLicense(accountDTO.getIdentifier(), licenseRequestDTO);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = KAPIL_GARG)
  @Category(UnitTests.class)
  public void testGenerateSMPLicenseNoAccountFound() {
    List<ModuleLicense> moduleLicenses = getModuleLicenses();

    when(accountService.getAccount("test")).thenReturn(null);
    when(moduleLicenseRepository.findByAccountIdentifierAndModuleType(Mockito.eq("test"), Mockito.any()))
        .thenReturn(moduleLicenses);

    SMPLicenseRequestDTO licenseRequestDTO = new SMPLicenseRequestDTO();
    licenseRequestDTO.setAccountOptional(false);

    licenseService.generateSMPLicense("test", licenseRequestDTO);
  }

  @Test
  @Owner(developers = KAPIL_GARG)
  @Category(UnitTests.class)
  public void testValidateSMPLicense() {
    String license = "some valid license";
    SMPEncLicenseDTO licenseDTO = SMPEncLicenseDTO.builder().encryptedLicense(license).decrypt(true).build();
    SMPLicenseValidationResult validationResult = SMPLicenseValidationResult.builder().isValid(true).build();

    when(smpLicenseMapper.toSMPLicenseEnc(licenseDTO)).thenCallRealMethod();
    when(smpLicenseMapper.toSMPValidationResultDTO(validationResult)).thenCallRealMethod();
    when(licenseValidator.validate(Mockito.any(), Mockito.eq(licenseDTO.isDecrypt()))).thenReturn(validationResult);

    SMPValidationResultDTO smpValidationResultDTO = licenseService.validateSMPLicense(licenseDTO);
    assertThat(smpValidationResultDTO).isNotNull();
    assertThat(smpValidationResultDTO.isValid()).isTrue();
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = KAPIL_GARG)
  @Category(UnitTests.class)
  public void testValidateSMPLicenseInternalLibraryError() {
    String license = "";
    SMPEncLicenseDTO licenseDTO = SMPEncLicenseDTO.builder().encryptedLicense(license).decrypt(true).build();
    SMPLicenseValidationResult validationResult = SMPLicenseValidationResult.builder().isValid(true).build();

    when(smpLicenseMapper.toSMPValidationResultDTO(validationResult)).thenCallRealMethod();
    when(licenseValidator.validate(Mockito.any(), Mockito.eq(licenseDTO.isDecrypt())))
        .thenThrow(RuntimeException.class);

    licenseService.validateSMPLicense(licenseDTO);
  }

  private AccountDTO getAccountDTO() {
    return AccountDTO.builder().identifier("test").name("test-account").companyName("test-company").build();
  }

  private List<ModuleLicense> getModuleLicenses() {
    CDModuleLicense cdModuleLicense = CDModuleLicense.builder().workloads(Integer.valueOf(UNLIMITED)).build();
    cdModuleLicense.setId("id");
    cdModuleLicense.setAccountIdentifier(ACCOUNT_IDENTIFIER);
    cdModuleLicense.setModuleType(ModuleType.CD);
    cdModuleLicense.setEdition(Edition.FREE);
    cdModuleLicense.setStatus(LicenseStatus.ACTIVE);
    cdModuleLicense.setStartTime(1);
    cdModuleLicense.setExpiryTime(Long.valueOf(UNLIMITED));
    cdModuleLicense.setCreatedAt(0L);
    cdModuleLicense.setLastUpdatedAt(1000L);
    List<ModuleLicense> moduleLicenses = new ArrayList<>();
    moduleLicenses.add(cdModuleLicense);
    return moduleLicenses;
  }
}
