/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.licensing.services;

import static io.harness.configuration.DeployMode.DEPLOY_MODE;
import static io.harness.configuration.DeployVariant.DEPLOY_VERSION;
import static io.harness.licensing.LicenseModule.LICENSE_CACHE_NAMESPACE;
import static io.harness.licensing.interfaces.ModuleLicenseImpl.TRIAL_DURATION;
import static io.harness.remote.client.CGRestUtils.getResponse;

import static java.lang.String.format;

import io.harness.ModuleType;
import io.harness.account.services.AccountService;
import io.harness.beans.EmbeddedUser;
import io.harness.ccm.license.CeLicenseInfoDTO;
import io.harness.ccm.license.remote.CeLicenseClient;
import io.harness.configuration.DeployMode;
import io.harness.configuration.DeployVariant;
import io.harness.exception.InvalidRequestException;
import io.harness.licensing.Edition;
import io.harness.licensing.EditionAction;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.LicenseType;
import io.harness.licensing.beans.EditionActionDTO;
import io.harness.licensing.beans.modules.AccountLicenseDTO;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.beans.modules.SMPEncLicenseDTO;
import io.harness.licensing.beans.modules.SMPLicenseRequestDTO;
import io.harness.licensing.beans.modules.SMPValidationResultDTO;
import io.harness.licensing.beans.modules.StartTrialDTO;
import io.harness.licensing.beans.response.CheckExpiryResultDTO;
import io.harness.licensing.beans.summary.LicensesWithSummaryDTO;
import io.harness.licensing.checks.LicenseComplianceResolver;
import io.harness.licensing.entities.modules.ModuleLicense;
import io.harness.licensing.helpers.ModuleLicenseHelper;
import io.harness.licensing.helpers.ModuleLicenseSummaryHelper;
import io.harness.licensing.interfaces.ModuleLicenseInterface;
import io.harness.licensing.mappers.LicenseObjectConverter;
import io.harness.licensing.mappers.SMPLicenseMapper;
import io.harness.ng.core.account.DefaultExperience;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.repositories.ModuleLicenseRepository;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.UserPrincipal;
import io.harness.smp.license.models.AccountInfo;
import io.harness.smp.license.models.LibraryVersion;
import io.harness.smp.license.models.LicenseMeta;
import io.harness.smp.license.models.SMPLicense;
import io.harness.smp.license.models.SMPLicenseEnc;
import io.harness.smp.license.models.SMPLicenseValidationResult;
import io.harness.smp.license.v1.LicenseGenerator;
import io.harness.smp.license.v1.LicenseValidator;
import io.harness.telemetry.Category;
import io.harness.telemetry.Destination;
import io.harness.telemetry.TelemetryReporter;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.cache.Cache;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultLicenseServiceImpl implements LicenseService {
  private final ModuleLicenseRepository moduleLicenseRepository;
  private final LicenseObjectConverter licenseObjectConverter;
  private final ModuleLicenseInterface licenseInterface;
  private final TelemetryReporter telemetryReporter;
  private final CeLicenseClient ceLicenseClient;
  private final LicenseComplianceResolver licenseComplianceResolver;
  private final Cache<String, List> cache;
  protected final LicenseGenerator licenseGenerator;
  protected final LicenseValidator licenseValidator;
  protected final SMPLicenseMapper smpLicenseMapper;

  protected final AccountService accountService;

  static final String FAILED_OPERATION = "START_TRIAL_ATTEMPT_FAILED";
  static final String SUCCEED_START_FREE_OPERATION = "FREE_PLAN";
  static final String SUCCEED_START_TRIAL_OPERATION = "NEW_TRIAL";
  static final String SUCCEED_EXTEND_TRIAL_OPERATION = "EXTEND_TRIAL";
  static final String TRIAL_ENDED = "TRIAL_ENDED";

  private static final Set<Edition> TRIAL_SUPPORTED_EDITION = Sets.newHashSet(Edition.ENTERPRISE, Edition.TEAM);

  @Inject
  public DefaultLicenseServiceImpl(ModuleLicenseRepository moduleLicenseRepository,
      LicenseObjectConverter licenseObjectConverter, ModuleLicenseInterface licenseInterface,
      AccountService accountService, TelemetryReporter telemetryReporter, CeLicenseClient ceLicenseClient,
      LicenseComplianceResolver licenseComplianceResolver, @Named(LICENSE_CACHE_NAMESPACE) Cache<String, List> cache,
      LicenseGenerator licenseGenerator, LicenseValidator licenseValidator, SMPLicenseMapper smpLicenseMapper) {
    this.moduleLicenseRepository = moduleLicenseRepository;
    this.licenseObjectConverter = licenseObjectConverter;
    this.licenseInterface = licenseInterface;
    this.accountService = accountService;
    this.telemetryReporter = telemetryReporter;
    this.ceLicenseClient = ceLicenseClient;
    this.licenseComplianceResolver = licenseComplianceResolver;
    this.cache = cache;
    this.licenseGenerator = licenseGenerator;
    this.licenseValidator = licenseValidator;
    this.smpLicenseMapper = smpLicenseMapper;
  }

  @Override
  @Deprecated
  public ModuleLicenseDTO getModuleLicense(String accountIdentifier, ModuleType moduleType) {
    List<ModuleLicense> licenses =
        moduleLicenseRepository.findByAccountIdentifierAndModuleType(accountIdentifier, moduleType);
    if (licenses.isEmpty()) {
      log.debug(String.format(
          "ModuleLicense with ModuleType [%s] and accountIdentifier [%s] not found", moduleType, accountIdentifier));
      return null;
    }
    return licenseObjectConverter.toDTO(licenses.get(0));
  }

  @Override
  public List<ModuleLicenseDTO> getModuleLicenses(String accountIdentifier, ModuleType moduleType) {
    return getModuleLicensesByAccountIdAndModuleType(accountIdentifier, moduleType);
  }

  @Override
  public List<ModuleLicenseDTO> getEnabledModuleLicensesByModuleType(ModuleType moduleType, long expiryTime) {
    List<ModuleLicense> licenses =
        moduleLicenseRepository.findByModuleTypeAndExpiryTimeGreaterThanEqual(moduleType, expiryTime);
    return licenses.stream().map(licenseObjectConverter::<ModuleLicenseDTO>toDTO).collect(Collectors.toList());
  }

  @Override
  public AccountLicenseDTO getAccountLicense(String accountIdentifier) {
    AccountLicenseDTO dto = AccountLicenseDTO.builder().accountId(accountIdentifier).build();
    Map<ModuleType, List<ModuleLicenseDTO>> allModuleLicenses = new HashMap<>();
    for (ModuleType moduleType : ModuleType.getModules()) {
      if (moduleType.isInternal()) {
        continue;
      }

      List<ModuleLicenseDTO> moduleLicenses = getModuleLicenses(accountIdentifier, moduleType);
      allModuleLicenses.put(moduleType, moduleLicenses);
    }
    dto.setAllModuleLicenses(allModuleLicenses);
    return dto;
  }

  @Override
  public ModuleLicenseDTO getModuleLicenseById(String identifier) {
    Optional<ModuleLicense> license = moduleLicenseRepository.findById(identifier);
    if (!license.isPresent()) {
      log.debug(String.format("ModuleLicense with identifier [%s] not found", identifier));
      return null;
    }
    return licenseObjectConverter.toDTO(license.get());
  }

  @Override
  public ModuleLicenseDTO createModuleLicense(ModuleLicenseDTO moduleLicense) {
    ModuleLicense savedEntity = createModuleLicense(licenseObjectConverter.toEntity(moduleLicense));
    return licenseObjectConverter.toDTO(savedEntity);
  }

  @Override
  public ModuleLicenseDTO updateModuleLicense(ModuleLicenseDTO moduleLicense) {
    ModuleLicense updatedLicense = updateModuleLicense(licenseObjectConverter.toEntity(moduleLicense));
    return licenseObjectConverter.toDTO(updatedLicense);
  }

  @Override
  public void deleteModuleLicense(String id) {
    Optional<ModuleLicense> existingEntityOptional = moduleLicenseRepository.findById(id);
    if (!existingEntityOptional.isPresent()) {
      throw new NotFoundException(String.format("ModuleLicense with identifier [%s] not found", id));
    }

    ModuleLicense moduleLicense = existingEntityOptional.get();
    moduleLicenseRepository.deleteById(id);
    evictCache(moduleLicense.getAccountIdentifier(), moduleLicense.getModuleType());
    log.info("Deleted license [{}] for module [{}] in account [{}]", id, moduleLicense.getModuleType(),
        moduleLicense.getAccountIdentifier());
  }

  @Override
  public ModuleLicense getCurrentLicense(String accountId, ModuleType moduleType) {
    List<ModuleLicense> licenses = moduleLicenseRepository.findByAccountIdentifierAndModuleType(accountId, moduleType);
    return ModuleLicenseHelper.getLatestLicense(licenses);
  }

  @Override
  public ModuleLicense createModuleLicense(ModuleLicense moduleLicense) {
    verifyAccountExistence(moduleLicense.getAccountIdentifier());
    // validate license existence
    List<ModuleLicense> existingLicenses = moduleLicenseRepository.findByAccountIdentifierAndModuleType(
        moduleLicense.getAccountIdentifier(), moduleLicense.getModuleType());
    if (existingLicenses.size() != 0) {
      throw new InvalidRequestException(
          String.format("ModuleLicense with accountIdentifier [%s] and moduleType [%s] already exists",
              moduleLicense.getAccountIdentifier(), moduleLicense.getModuleType()));
    }

    // Validate entity
    moduleLicense.setCreatedBy(EmbeddedUser.builder().email(getEmailFromPrincipal()).build());
    ModuleLicense savedEntity = saveLicense(moduleLicense);
    // Send telemetry

    log.info("Created license for module [{}] in account [{}]", savedEntity.getModuleType(),
        savedEntity.getAccountIdentifier());
    return savedEntity;
  }

  @Override
  public ModuleLicense updateModuleLicense(ModuleLicense moduleLicense) {
    // validate the license
    Optional<ModuleLicense> existingEntityOptional = moduleLicenseRepository.findById(moduleLicense.getId());
    if (!existingEntityOptional.isPresent()) {
      throw new NotFoundException(String.format("ModuleLicense with identifier [%s] not found", moduleLicense.getId()));
    }

    ModuleLicense existedLicense = existingEntityOptional.get();
    ModuleLicense updateLicense = ModuleLicenseHelper.compareAndUpdate(existedLicense, moduleLicense);

    updateLicense.setLastUpdatedBy(EmbeddedUser.builder().email(getEmailFromPrincipal()).build());
    ModuleLicense updatedLicense = saveLicense(updateLicense);

    log.info("Updated license for module [{}] in account [{}]", updatedLicense.getModuleType(),
        updatedLicense.getAccountIdentifier());
    return updatedLicense;
  }

  @Override
  public void deleteByAccount(String accountIdentifier) {
    moduleLicenseRepository.deleteAllByAccountIdentifier(accountIdentifier);
  }

  @Override
  public ModuleLicenseDTO startFreeLicense(
      String accountIdentifier, ModuleType moduleType, String referer, String gaClientId) {
    ModuleLicenseDTO trialLicenseDTO = licenseInterface.generateFreeLicense(accountIdentifier, moduleType);
    verifyAccountExistence(accountIdentifier);

    ModuleLicense trialLicense = licenseObjectConverter.toEntity(trialLicenseDTO);
    trialLicense.setCreatedBy(EmbeddedUser.builder().email(getEmailFromPrincipal()).build());

    licenseComplianceResolver.preCheck(trialLicense, EditionAction.START_FREE);
    ModuleLicense savedEntity = saveLicense(trialLicense);
    sendSucceedTelemetryEvents(SUCCEED_START_FREE_OPERATION, savedEntity, accountIdentifier, referer, gaClientId);

    log.info("Free license for module [{}] is started in account [{}]", moduleType, accountIdentifier);

    accountService.updateDefaultExperienceIfApplicable(accountIdentifier, DefaultExperience.NG);
    startTrialInCGIfCE(savedEntity);
    return licenseObjectConverter.toDTO(savedEntity);
  }

  @Override
  public ModuleLicenseDTO startCommunityLicense(String accountIdentifier, ModuleType moduleType) {
    ModuleLicenseDTO trialLicenseDTO = licenseInterface.generateCommunityLicense(accountIdentifier, moduleType);
    verifyAccountExistence(accountIdentifier);

    List<ModuleLicense> existing =
        moduleLicenseRepository.findByAccountIdentifierAndModuleType(accountIdentifier, moduleType);
    if (!existing.isEmpty()) {
      String cause = format("Account [%s] already have licenses for moduleType [%s]", accountIdentifier, moduleType);
      throw new InvalidRequestException(cause);
    }

    ModuleLicense trialLicense = licenseObjectConverter.toEntity(trialLicenseDTO);
    trialLicense.setCreatedBy(EmbeddedUser.builder().email(getEmailFromPrincipal()).build());
    ModuleLicense savedEntity = saveLicense(trialLicense);

    log.info("Free license for module [{}] is started in account [{}]", moduleType, accountIdentifier);

    accountService.updateDefaultExperienceIfApplicable(accountIdentifier, DefaultExperience.NG);
    return licenseObjectConverter.toDTO(savedEntity);
  }

  @Override
  public ModuleLicenseDTO startTrialLicense(
      String accountIdentifier, StartTrialDTO startTrialRequestDTO, String referer) {
    Edition edition = startTrialRequestDTO.getEdition();
    if (!checkTrialSupported(edition)) {
      throw new InvalidRequestException("Edition doesn't support trial");
    }
    verifyAccountExistence(accountIdentifier);

    ModuleLicenseDTO trialLicenseDTO =
        licenseInterface.generateTrialLicense(edition, accountIdentifier, startTrialRequestDTO.getModuleType());
    ModuleLicense trialLicense = licenseObjectConverter.toEntity(trialLicenseDTO);
    trialLicense.setCreatedBy(EmbeddedUser.builder().email(getEmailFromPrincipal()).build());

    licenseComplianceResolver.preCheck(trialLicense, EditionAction.START_TRIAL);
    ModuleLicense savedEntity = saveLicense(trialLicense);
    sendSucceedTelemetryEvents(SUCCEED_START_TRIAL_OPERATION, savedEntity, accountIdentifier, referer, null);

    log.info("Trial license for module [{}] is started in account [{}]", startTrialRequestDTO.getModuleType(),
        accountIdentifier);

    accountService.updateDefaultExperienceIfApplicable(accountIdentifier, DefaultExperience.NG);
    startTrialInCGIfCE(savedEntity);
    return licenseObjectConverter.toDTO(savedEntity);
  }

  @Override
  public ModuleLicenseDTO extendTrialLicense(String accountIdentifier, StartTrialDTO startTrialRequestDTO) {
    ModuleType moduleType = startTrialRequestDTO.getModuleType();

    List<ModuleLicense> existing =
        moduleLicenseRepository.findByAccountIdentifierAndModuleType(accountIdentifier, moduleType);
    if (existing.isEmpty()) {
      throw new InvalidRequestException(String.format("Account [%s] has no trial license", accountIdentifier));
    }

    ModuleLicense licenseToBeExtended = existing.get(0);
    licenseToBeExtended.setTrialExtended(true);
    licenseToBeExtended.setExpiryTime(Instant.now().plus(TRIAL_DURATION, ChronoUnit.DAYS).toEpochMilli());
    licenseToBeExtended.setStatus(LicenseStatus.ACTIVE);
    licenseComplianceResolver.preCheck(licenseToBeExtended, EditionAction.EXTEND_TRIAL);
    ModuleLicense savedEntity = saveLicense(licenseToBeExtended);

    sendSucceedTelemetryEvents(SUCCEED_EXTEND_TRIAL_OPERATION, savedEntity, accountIdentifier, null, null);
    syncLicenseChangeToCGForCE(savedEntity);
    log.info("Trial license for module [{}] is extended in account [{}]", moduleType, accountIdentifier);
    return licenseObjectConverter.toDTO(savedEntity);
  }

  @Override
  public CheckExpiryResultDTO checkExpiry(String accountIdentifier) {
    List<ModuleLicense> licenses = moduleLicenseRepository.findByAccountIdentifier(accountIdentifier);
    long currentTime = Instant.now().toEpochMilli();
    long maxExpiryTime = 0;
    boolean atLeastOneLicenseActive = false;
    boolean isPaidOrFree = false;
    for (ModuleLicense moduleLicense : licenses) {
      if (moduleLicense.isActive() == true) {
        atLeastOneLicenseActive = true;
      }
      if (Edition.FREE.equals(moduleLicense.getEdition())) {
        isPaidOrFree = true;
        continue;
      }
      if (LicenseType.PAID.equals(moduleLicense.getLicenseType())) {
        isPaidOrFree = true;
      }

      if (moduleLicense.checkExpiry(currentTime)) {
        if (moduleLicense.isActive()) {
          // In case need to expire license
          moduleLicense.setStatus(LicenseStatus.EXPIRED);
          saveLicense(moduleLicense);

          if (LicenseType.TRIAL.equals(moduleLicense.getLicenseType())) {
            sendTrialEndEvents(moduleLicense, moduleLicense.getCreatedBy());
          }
        }
      }

      if (maxExpiryTime < moduleLicense.getExpiryTime()) {
        maxExpiryTime = moduleLicense.getExpiryTime();
      }
    }

    log.info("Check for module licenses under account {} complete, used {} milliseconds", accountIdentifier,
        Instant.now().toEpochMilli() - currentTime);
    return CheckExpiryResultDTO.builder()
        .shouldDelete(!isPaidOrFree && (maxExpiryTime <= currentTime))
        .expiryTime(maxExpiryTime)
        .ngAccountActive(atLeastOneLicenseActive)
        .build();
  }

  @Override
  public void softDelete(String accountIdentifier) {}

  @Override
  public LicensesWithSummaryDTO getLicenseSummary(String accountIdentifier, ModuleType moduleType) {
    List<ModuleLicenseDTO> moduleLicenses = getModuleLicenses(accountIdentifier, moduleType);
    if (moduleLicenses.isEmpty()) {
      return null;
    }
    return ModuleLicenseSummaryHelper.generateSummary(moduleType, moduleLicenses);
  }

  @Override
  public Edition calculateAccountEdition(String accountIdentifier) {
    AccountLicenseDTO accountLicense = getAccountLicense(accountIdentifier);
    Map<ModuleType, List<ModuleLicenseDTO>> allModuleLicenses = accountLicense.getAllModuleLicenses();

    Optional<ModuleLicenseDTO> highestEditionLicense =
        allModuleLicenses.values().stream().flatMap(Collection::stream).reduce((compareLicense, currentLicense) -> {
          if (compareLicense.getEdition().compareTo(currentLicense.getEdition()) < 0) {
            return currentLicense;
          }
          return compareLicense;
        });

    if (!highestEditionLicense.isPresent()) {
      Edition edition = Edition.FREE;
      if (DeployMode.isOnPrem(System.getenv().get(DEPLOY_MODE))) {
        if (DeployVariant.isCommunity(System.getenv().get(DEPLOY_VERSION))) {
          edition = Edition.COMMUNITY;
        } else {
          edition = Edition.ENTERPRISE;
        }
      }
      log.warn("Account {} has no highest edition license, fallback to {}", accountIdentifier, edition);
      return edition;
    }
    return highestEditionLicense.get().getEdition();
  }

  @Override
  public Map<Edition, Set<EditionActionDTO>> getEditionActions(String accountIdentifier, ModuleType moduleType) {
    Map<Edition, Set<EditionAction>> editionStates =
        licenseComplianceResolver.getEditionStates(moduleType, accountIdentifier);

    Map<Edition, Set<EditionActionDTO>> result = new HashMap<>();
    for (Map.Entry<Edition, Set<EditionAction>> entry : editionStates.entrySet()) {
      Set<EditionActionDTO> dtos =
          entry.getValue().stream().map(e -> toEditionActionDTO(e)).collect(Collectors.toSet());
      result.put(entry.getKey(), dtos);
    }
    return result;
  }

  @Override
  public Map<ModuleType, Long> getLastUpdatedAtMap(String accountIdentifier) {
    Map<ModuleType, Long> lastUpdatedAtMap = new HashMap<>();
    for (ModuleType moduleType : ModuleType.getModules()) {
      if (moduleType.isInternal()) {
        continue;
      }

      List<ModuleLicenseDTO> moduleLicenses = getModuleLicenses(accountIdentifier, moduleType);
      ModuleLicenseDTO mostRecentUpdatedLicense = ModuleLicenseHelper.getMostRecentUpdatedLicense(moduleLicenses);
      lastUpdatedAtMap.put(
          moduleType, mostRecentUpdatedLicense == null ? 0 : mostRecentUpdatedLicense.getLastModifiedAt());
    }
    return lastUpdatedAtMap;
  }

  @Override
  public List<ModuleLicenseDTO> getAllModuleLicences(String accountIdentifier) {
    List<ModuleLicense> licenses = moduleLicenseRepository.findByAccountIdentifier(accountIdentifier);
    return licenses.stream().map(licenseObjectConverter::<ModuleLicenseDTO>toDTO).collect(Collectors.toList());
  }

  @Override
  public SMPValidationResultDTO validateSMPLicense(SMPEncLicenseDTO licenseDTO) {
    SMPLicenseEnc smpLicenseEnc = smpLicenseMapper.toSMPLicenseEnc(licenseDTO);
    SMPLicenseValidationResult validationResult = licenseValidator.validate(smpLicenseEnc, licenseDTO.isDecrypt());
    return smpLicenseMapper.toSMPValidationResultDTO(validationResult);
  }

  @Override
  public SMPEncLicenseDTO generateSMPLicense(String accountId, SMPLicenseRequestDTO licenseRequest) {
    SMPLicense smpLicense = createSmpLicense(accountId);
    String license = licenseGenerator.generateLicense(smpLicense);
    return SMPEncLicenseDTO.builder().encryptedLicense(license).build();
  }

  protected SMPLicense createSmpLicense(String accountId) {
    AccountLicenseDTO accountLicenseDTO = getAccountLicense(accountId);
    AccountDTO accountDTO = accountService.getAccount(accountId);

    if (Objects.isNull(accountLicenseDTO) || Objects.isNull(accountLicenseDTO.getAllModuleLicenses())
        || accountLicenseDTO.getAllModuleLicenses().isEmpty() || Objects.isNull(accountDTO)) {
      throw new InvalidRequestException("There might be no account or module license present in db");
    }
    List<ModuleLicenseDTO> moduleLicenseDTOS = accountLicenseDTO.getAllModuleLicenses()
                                                   .values()
                                                   .stream()
                                                   .flatMap(Collection::stream)
                                                   .collect(Collectors.toList());
    if (moduleLicenseDTOS.isEmpty()) {
      throw new InvalidRequestException("No module license found for account: " + accountId);
    }
    LicenseMeta licenseMeta = new LicenseMeta();
    licenseMeta.setAccountDTO(AccountInfo.builder()
                                  .name(accountDTO.getName())
                                  .identifier(accountDTO.getIdentifier())
                                  .companyName(accountDTO.getCompanyName())
                                  .build());
    licenseMeta.setIssueDate(new Date());
    licenseMeta.setLicenseVersion(0);
    licenseMeta.setLibraryVersion(LibraryVersion.V1);
    licenseMeta.setAccountOptional(true);
    return SMPLicense.builder().licenseMeta(licenseMeta).moduleLicenses(moduleLicenseDTOS).build();
  }

  @Override
  public void applySMPLicense(SMPEncLicenseDTO encLicenseDTO) {
    throw new UnsupportedOperationException("API only available on Self Managed Platform");
  }

  private EditionActionDTO toEditionActionDTO(EditionAction editionAction) {
    return EditionActionDTO.builder().action(editionAction).reason(editionAction.getReason()).build();
  }

  private void sendSucceedTelemetryEvents(
      String eventName, ModuleLicense moduleLicense, String accountIdentifier, String referer, String gaClientId) {
    String email = getEmailFromPrincipal();
    HashMap<String, Object> properties = new HashMap<>();
    properties.put("email", email);
    properties.put("module", moduleLicense.getModuleType());

    if (moduleLicense.getLicenseType() != null) {
      properties.put("licenseType", moduleLicense.getLicenseType());
    }

    if (moduleLicense.getEdition() != null) {
      properties.put("plan", moduleLicense.getEdition());
    }
    if (referer != null) {
      properties.put("refererURL", referer);
    }
    if (gaClientId != null) {
      properties.put("ga_client_id", gaClientId);
    }
    properties.put("platform", "NG");
    properties.put("startTime", String.valueOf(moduleLicense.getStartTime()));
    properties.put("duration", TRIAL_DURATION);
    properties.put("licenseStatus", moduleLicense.getStatus());
    telemetryReporter.sendTrackEvent(eventName, properties,
        ImmutableMap.<Destination, Boolean>builder().put(Destination.MARKETO, true).build(), Category.SIGN_UP);

    HashMap<String, Object> groupProperties = new HashMap<>();
    String moduleType = moduleLicense.getModuleType().name();
    groupProperties.put("group_id", accountIdentifier);
    groupProperties.put("group_type", "Account");

    if (moduleLicense.getEdition() != null) {
      groupProperties.put(format("%s%s", moduleType, "LicenseEdition"), moduleLicense.getEdition());
    }

    if (moduleLicense.getLicenseType() != null) {
      groupProperties.put(format("%s%s", moduleType, "LicenseType"), moduleLicense.getLicenseType());
    }

    groupProperties.put(format("%s%s", moduleType, "LicenseStartTime"), moduleLicense.getStartTime());
    groupProperties.put(format("%s%s", moduleType, "LicenseDuration"), TRIAL_DURATION);
    groupProperties.put(format("%s%s", moduleType, "LicenseStatus"), moduleLicense.getStatus());
    telemetryReporter.sendGroupEvent(
        accountIdentifier, groupProperties, ImmutableMap.<Destination, Boolean>builder().build());
  }

  private void sendFailedTelemetryEvents(
      String accountIdentifier, ModuleType moduleType, LicenseType licenseType, Edition edition, String cause) {
    HashMap<String, Object> properties = new HashMap<>();
    properties.put("reason", cause);
    properties.put("module", moduleType);

    if (licenseType != null) {
      properties.put("licenseType", licenseType);
    }

    if (edition != null) {
      properties.put("plan", edition);
    }

    telemetryReporter.sendTrackEvent(FAILED_OPERATION, properties, null, Category.SIGN_UP);
  }

  private void sendTrialEndEvents(ModuleLicense moduleLicense, EmbeddedUser user) {
    HashMap<String, Object> properties = new HashMap<>();
    String email = "unknown";
    if (user != null && user.getEmail() != null) {
      email = user.getEmail();
    }
    properties.put("email", email);
    properties.put("module", moduleLicense.getModuleType());
    properties.put("licenseType", moduleLicense.getLicenseType());
    properties.put("plan", moduleLicense.getEdition());
    properties.put("platform", "NG");
    properties.put("startTime", String.valueOf(moduleLicense.getStartTime()));
    properties.put("endTime", String.valueOf(moduleLicense.getExpiryTime()));
    properties.put("duration", TRIAL_DURATION);
    telemetryReporter.sendTrackEvent(TRIAL_ENDED, email, moduleLicense.getAccountIdentifier(), properties,
        ImmutableMap.<Destination, Boolean>builder().put(Destination.MARKETO, true).build(), Category.SIGN_UP);
  }

  private String getEmailFromPrincipal() {
    Principal principal = SourcePrincipalContextBuilder.getSourcePrincipal();
    String email = "";
    if (principal instanceof UserPrincipal) {
      email = ((UserPrincipal) principal).getEmail();
    }
    return email;
  }

  private void startTrialInCGIfCE(ModuleLicense moduleLicense) {
    if (ModuleType.CE.equals(moduleLicense.getModuleType())) {
      try {
        getResponse(ceLicenseClient.createCeTrial(CeLicenseInfoDTO.builder()
                                                      .accountId(moduleLicense.getAccountIdentifier())
                                                      .expiryTime(moduleLicense.getExpiryTime())
                                                      .edition(moduleLicense.getEdition())
                                                      .build()));
      } catch (Exception e) {
        log.error("Unable to sync trial start in CG CCM", e);
      }
    }
  }

  private void syncLicenseChangeToCGForCE(ModuleLicense moduleLicense) {
    if (ModuleType.CE.equals(moduleLicense.getModuleType())) {
      try {
        getResponse(ceLicenseClient.updateCeLicense(CeLicenseInfoDTO.builder()
                                                        .accountId(moduleLicense.getAccountIdentifier())
                                                        .expiryTime(moduleLicense.getExpiryTime())
                                                        .build()));
      } catch (Exception e) {
        log.error("Unable to sync license info in CG CCM", e);
      }
    }
  }

  private boolean checkTrialSupported(Edition edition) {
    return TRIAL_SUPPORTED_EDITION.contains(edition);
  }

  private void verifyAccountExistence(String accountIdentifier) {
    AccountDTO accountDTO = accountService.getAccount(accountIdentifier);
    if (accountDTO == null) {
      throw new InvalidRequestException(String.format("Account [%s] doesn't exists", accountIdentifier));
    }
  }

  private ModuleLicense saveLicense(ModuleLicense moduleLicense) {
    ModuleLicense savedLicense = moduleLicenseRepository.save(moduleLicense);
    evictCache(moduleLicense.getAccountIdentifier(), moduleLicense.getModuleType());
    return savedLicense;
  }

  private List<ModuleLicenseDTO> getModuleLicensesByAccountIdAndModuleType(
      String accountIdentifier, ModuleType moduleType) {
    if (checkExistInCache(accountIdentifier, moduleType)) {
      try {
        return getFromCache(accountIdentifier, moduleType);
      } catch (Exception e) {
        log.error("Unable to get license data from cache", e);
      }
    }

    List<ModuleLicense> licenses =
        moduleLicenseRepository.findByAccountIdentifierAndModuleType(accountIdentifier, moduleType);
    List<ModuleLicenseDTO> result =
        licenses.stream().map(licenseObjectConverter::<ModuleLicenseDTO>toDTO).collect(Collectors.toList());
    setToCache(accountIdentifier, moduleType, result);
    return result;
  }

  private List<ModuleLicenseDTO> getFromCache(String accountIdentifier, ModuleType moduleType) {
    String key = generateCacheKey(accountIdentifier, moduleType);
    return (List<ModuleLicenseDTO>) cache.get(key);
  }

  private boolean checkExistInCache(String accountIdentifier, ModuleType moduleType) {
    String key = generateCacheKey(accountIdentifier, moduleType);
    return cache.containsKey(key);
  }

  private void setToCache(String accountIdentifier, ModuleType moduleType, List<ModuleLicenseDTO> licenses) {
    String key = generateCacheKey(accountIdentifier, moduleType);
    cache.put(key, licenses);
  }

  private void evictCache(String accountIdentifier, ModuleType moduleType) {
    String key = generateCacheKey(accountIdentifier, moduleType);
    cache.remove(key);
  }

  private String generateCacheKey(String accountIdentifier, ModuleType moduleType) {
    return String.format("%s:%s", accountIdentifier, moduleType.name());
  }
}