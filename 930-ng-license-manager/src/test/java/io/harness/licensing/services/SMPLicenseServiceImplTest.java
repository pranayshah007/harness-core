/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.licensing.services;

import static io.harness.ModuleType.CD;
import static io.harness.licensing.LicenseConstant.UNLIMITED;
import static io.harness.licensing.LicenseTestConstant.ACCOUNT_IDENTIFIER;
import static io.harness.rule.OwnerRule.KAPIL_GARG;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.account.services.AccountService;
import io.harness.category.element.UnitTests;
import io.harness.ccm.license.remote.CeLicenseClient;
import io.harness.exception.InvalidRequestException;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.beans.modules.CDModuleLicenseDTO;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.beans.modules.SMPEncLicenseDTO;
import io.harness.licensing.checks.LicenseComplianceResolver;
import io.harness.licensing.entities.modules.CDModuleLicense;
import io.harness.licensing.entities.modules.ModuleLicense;
import io.harness.licensing.interfaces.ModuleLicenseInterface;
import io.harness.licensing.jobs.SMPLicenseValidationJob;
import io.harness.licensing.mappers.LicenseObjectConverter;
import io.harness.licensing.mappers.LicenseObjectMapper;
import io.harness.licensing.mappers.SMPLicenseMapper;
import io.harness.licensing.mappers.modules.CDLicenseObjectMapper;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.repositories.ModuleLicenseRepository;
import io.harness.rule.Owner;
import io.harness.smp.license.models.AccountInfo;
import io.harness.smp.license.models.LicenseMeta;
import io.harness.smp.license.models.SMPLicense;
import io.harness.smp.license.models.SMPLicenseValidationResult;
import io.harness.smp.license.v1.LicenseGenerator;
import io.harness.smp.license.v1.LicenseValidator;
import io.harness.telemetry.TelemetryReporter;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.cache.Cache;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

public class SMPLicenseServiceImplTest extends CategoryTest {
  @Mock ModuleLicenseRepository moduleLicenseRepository;
  @Mock ModuleLicenseInterface moduleLicenseInterface;
  @Mock LicenseObjectConverter licenseObjectConverter;
  @Mock AccountService accountService;
  @Mock TelemetryReporter telemetryReporter;
  @Mock CeLicenseClient ceLicenseClient;
  @Mock LicenseComplianceResolver licenseComplianceResolver;
  @Mock Cache<String, List> cache;
  @Mock LicenseGenerator licenseGenerator;
  @Mock LicenseValidator licenseValidator;
  @Mock SMPLicenseMapper smpLicenseMapper;
  @Mock SMPLicenseValidationJob smpLicenseValidationJob;
  @InjectMocks SMPLicenseServiceImpl licenseService;

  @Before
  public void setup() {
    initMocks(this);
  }

  @Test(expected = UnsupportedOperationException.class)
  @Owner(developers = KAPIL_GARG)
  @Category(UnitTests.class)
  public void generateSMPLicense_validInput_UnsupportedException() {
    licenseService.generateSMPLicense(Mockito.anyString(), Mockito.any());
  }

  @Test(expected = UnsupportedOperationException.class)
  @Owner(developers = KAPIL_GARG)
  @Category(UnitTests.class)
  public void validateSMPLicense_validInput_UnsupportedException() {
    licenseService.validateSMPLicense(Mockito.any());
  }

  @Test
  @Owner(developers = KAPIL_GARG)
  @Category(UnitTests.class)
  public void applySMPLicense_validLicenseNoAccountNoLicensePresent_accountCreatedLicensesStored()
      throws NoSuchFieldException, IllegalAccessException {
    setupLicenseObjectConverter();
    String license = "";
    SMPEncLicenseDTO licenseDTO = SMPEncLicenseDTO.builder().encryptedLicense(license).decrypt(true).build();
    AccountInfo accountInfo = AccountInfo.builder().name("testAccount").companyName("testCompany").build();
    AccountDTO mockAccount = Mockito.mock(AccountDTO.class);
    SMPLicenseValidationResult validationResult = getSmpLicenseValidationResult(accountInfo, true);

    when(smpLicenseMapper.toSMPLicenseEnc(licenseDTO)).thenCallRealMethod();
    when(licenseValidator.validate(Mockito.any(), Mockito.eq(licenseDTO.isDecrypt()))).thenReturn(validationResult);
    when(accountService.getOnPremAccount()).thenReturn(null);
    when(accountService.createAccount(Mockito.any(AccountDTO.class))).thenReturn(mockAccount);
    when(mockAccount.getIdentifier()).thenReturn("accountId");
    when(accountService.getAccount("accountId")).thenReturn(mockAccount);
    when(moduleLicenseRepository.findByAccountIdentifierAndModuleType(Mockito.any(), Mockito.any()))
        .thenReturn(new ArrayList<>());
    when(moduleLicenseRepository.findByAccountIdentifier(Mockito.any())).thenReturn(new ArrayList<>());
    when(moduleLicenseRepository.save(Mockito.any())).thenReturn(getModuleLicenses().get(0));

    licenseService.applySMPLicense(licenseDTO);
    verify(moduleLicenseRepository, times(1)).save(Mockito.any());
    verify(accountService, times(1)).createAccount(Mockito.any(AccountDTO.class));
    verify(smpLicenseValidationJob, times(1))
        .scheduleValidation(Mockito.anyString(), Mockito.any(), Mockito.eq(60), Mockito.any());
  }

  @Test
  @Owner(developers = KAPIL_GARG)
  @Category(UnitTests.class)
  public void applySMPLicense_validLicenseAccountPresentNoLicensePresent_licensesStored()
      throws NoSuchFieldException, IllegalAccessException {
    setupLicenseObjectConverter();
    String license = "";
    SMPEncLicenseDTO licenseDTO = SMPEncLicenseDTO.builder().encryptedLicense(license).decrypt(true).build();
    AccountInfo accountInfo = AccountInfo.builder().name("testAccount").companyName("testCompany").build();
    AccountDTO mockAccount = Mockito.mock(AccountDTO.class);
    SMPLicenseValidationResult validationResult = getSmpLicenseValidationResult(accountInfo, true);

    when(smpLicenseMapper.toSMPLicenseEnc(licenseDTO)).thenCallRealMethod();
    when(licenseValidator.validate(Mockito.any(), Mockito.eq(licenseDTO.isDecrypt()))).thenReturn(validationResult);
    when(accountService.getOnPremAccount()).thenReturn(mockAccount);
    when(mockAccount.getIdentifier()).thenReturn("accountId");
    when(accountService.getAccount("accountId")).thenReturn(mockAccount);
    when(moduleLicenseRepository.findByAccountIdentifierAndModuleType(Mockito.any(), Mockito.any()))
        .thenReturn(new ArrayList<>());
    when(moduleLicenseRepository.findByAccountIdentifier("accountId")).thenReturn(new ArrayList<>());
    when(moduleLicenseRepository.save(Mockito.any())).thenReturn(getModuleLicenses().get(0));

    licenseService.applySMPLicense(licenseDTO);
    verify(moduleLicenseRepository, times(1)).save(Mockito.any());
    verify(accountService, times(0)).createAccount(Mockito.any(AccountDTO.class));
    verify(smpLicenseValidationJob, times(1))
        .scheduleValidation(Mockito.anyString(), Mockito.any(), Mockito.eq(60), Mockito.any());
  }

  @Test
  @Owner(developers = KAPIL_GARG)
  @Category(UnitTests.class)
  public void applySMPLicense_validLicenseAccountPresentLicensePresent_licensesUpdated()
      throws NoSuchFieldException, IllegalAccessException {
    setupLicenseObjectConverter();
    String license = "";
    SMPEncLicenseDTO licenseDTO = SMPEncLicenseDTO.builder().encryptedLicense(license).decrypt(true).build();
    AccountInfo accountInfo = AccountInfo.builder().name("testAccount").companyName("testCompany").build();
    AccountDTO mockAccount = Mockito.mock(AccountDTO.class);
    SMPLicenseValidationResult validationResult = getSmpLicenseValidationResult(accountInfo, true);

    when(smpLicenseMapper.toSMPLicenseEnc(licenseDTO)).thenCallRealMethod();
    when(licenseValidator.validate(Mockito.any(), Mockito.eq(licenseDTO.isDecrypt()))).thenReturn(validationResult);
    when(accountService.getOnPremAccount()).thenReturn(mockAccount);
    when(mockAccount.getIdentifier()).thenReturn("accountId");
    when(accountService.getAccount("accountId")).thenReturn(mockAccount);
    when(moduleLicenseRepository.findByAccountIdentifierAndModuleType("accountId", ModuleType.CD))
        .thenReturn(getModuleLicenses());
    when(moduleLicenseRepository.findByAccountIdentifier("accountId")).thenReturn(getModuleLicenses());
    when(moduleLicenseRepository.findById(Mockito.eq(getModuleLicenses().get(0).getId())))
        .thenReturn(Optional.of(getModuleLicenses().get(0)));
    when(moduleLicenseRepository.save(Mockito.any())).thenReturn(getModuleLicenses().get(0));

    licenseService.applySMPLicense(licenseDTO);
    verify(moduleLicenseRepository, times(1)).save(Mockito.any());
    verify(accountService, times(0)).createAccount(Mockito.any(AccountDTO.class));
    verify(smpLicenseValidationJob, times(1))
        .scheduleValidation(Mockito.anyString(), Mockito.any(), Mockito.eq(60), Mockito.any());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = KAPIL_GARG)
  @Category(UnitTests.class)
  public void applySMPLicense_invalidLicenseNoAccountNoLicensePresent_exceptionRaised()
      throws NoSuchFieldException, IllegalAccessException {
    setupLicenseObjectConverter();
    String license = "";
    SMPEncLicenseDTO licenseDTO = SMPEncLicenseDTO.builder().encryptedLicense(license).decrypt(true).build();
    AccountInfo accountInfo = AccountInfo.builder().name("testAccount").companyName("testCompany").build();
    SMPLicenseValidationResult validationResult = getSmpLicenseValidationResult(accountInfo, false);

    when(smpLicenseMapper.toSMPLicenseEnc(licenseDTO)).thenCallRealMethod();
    when(licenseValidator.validate(Mockito.any(), Mockito.eq(licenseDTO.isDecrypt()))).thenReturn(validationResult);

    licenseService.applySMPLicense(licenseDTO);
  }

  private SMPLicenseValidationResult getSmpLicenseValidationResult(AccountInfo accountInfo, boolean isValid) {
    LicenseMeta licenseMeta = new LicenseMeta();
    licenseMeta.setAccountOptional(true);
    licenseMeta.setAccountDTO(accountInfo);
    SMPLicenseValidationResult validationResult =
        SMPLicenseValidationResult.builder()
            .smpLicense(SMPLicense.builder().moduleLicenses(getModuleLicenseDTOs()).licenseMeta(licenseMeta).build())
            .isValid(isValid)
            .build();
    return validationResult;
  }

  // required the actual mapper instead of mock because some transformation is done to intermediate licenses which
  // need to be retained
  private void setupLicenseObjectConverter() throws NoSuchFieldException, IllegalAccessException {
    LicenseObjectConverter licenseObjectConverter = new LicenseObjectConverter();
    Map<ModuleType, LicenseObjectMapper> mapperMap = new HashMap<>();
    mapperMap.put(CD, new CDLicenseObjectMapper());
    Field mapperField = licenseObjectConverter.getClass().getDeclaredField("mapperMap");
    mapperField.setAccessible(true);
    mapperField.set(licenseObjectConverter, mapperMap);
    Field declaredField = licenseService.getClass().getSuperclass().getDeclaredField("licenseObjectConverter");
    declaredField.setAccessible(true);
    declaredField.set(licenseService, licenseObjectConverter);
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

  private List<ModuleLicenseDTO> getModuleLicenseDTOs() {
    CDModuleLicenseDTO cdModuleLicense = CDModuleLicenseDTO.builder().workloads(Integer.valueOf(UNLIMITED)).build();
    cdModuleLicense.setId("id");
    cdModuleLicense.setAccountIdentifier(ACCOUNT_IDENTIFIER);
    cdModuleLicense.setModuleType(ModuleType.CD);
    cdModuleLicense.setEdition(Edition.FREE);
    cdModuleLicense.setStatus(LicenseStatus.ACTIVE);
    cdModuleLicense.setStartTime(1);
    cdModuleLicense.setExpiryTime(Long.valueOf(UNLIMITED));
    cdModuleLicense.setCreatedAt(0L);
    List<ModuleLicenseDTO> moduleLicenses = new ArrayList<>();
    moduleLicenses.add(cdModuleLicense);
    return moduleLicenses;
  }
}
