/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.enforcement.services.impl;

import static io.harness.beans.FeatureName.FEATURE_ENFORCEMENT_ENABLED;
import static io.harness.configuration.DeployMode.DEPLOY_MODE;
import static io.harness.configuration.DeployVariant.DEPLOY_VERSION;
import static io.harness.enforcement.utils.EnforcementUtils.getRestriction;

import io.harness.ModuleType;
import io.harness.account.AccountClient;
import io.harness.configuration.DeployMode;
import io.harness.configuration.DeployVariant;
import io.harness.enforcement.bases.AvailabilityRestriction;
import io.harness.enforcement.bases.FeatureRestriction;
import io.harness.enforcement.bases.Restriction;
import io.harness.enforcement.beans.details.AvailabilityRestrictionDTO;
import io.harness.enforcement.beans.details.FeatureRestrictionDetailsDTO;
import io.harness.enforcement.beans.internal.RestrictionMetadataMapResponseDTO;
import io.harness.enforcement.beans.metadata.FeatureRestrictionMetadataDTO;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.enforcement.constants.RestrictionType;
import io.harness.enforcement.exceptions.FeatureNotSupportedException;
import io.harness.enforcement.handlers.ConversionHandler;
import io.harness.enforcement.handlers.ConversionHandlerFactory;
import io.harness.enforcement.handlers.RestrictionHandler;
import io.harness.enforcement.handlers.RestrictionHandlerFactory;
import io.harness.enforcement.services.EnforcementService;
import io.harness.exception.InvalidRequestException;
import io.harness.licensing.Edition;
import io.harness.licensing.beans.summary.LicensesWithSummaryDTO;
import io.harness.licensing.services.LicenseService;
import io.harness.remote.client.RestClientUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class EnforcementServiceImpl implements EnforcementService {
  private final Map<FeatureRestrictionName, FeatureRestriction> featureRestrictionMap;
  private final RestrictionHandlerFactory restrictionHandlerFactory;
  private final LicenseService licenseService;
  private final AccountClient accountClient;
  private final ConversionHandlerFactory conversionHandlerFactory;

  @Inject
  public EnforcementServiceImpl(LicenseService licenseService, RestrictionHandlerFactory restrictionHandlerFactory,
      AccountClient accountClient, ConversionHandlerFactory conversionHandlerFactory) {
    featureRestrictionMap = new HashMap<>();
    this.licenseService = licenseService;
    this.restrictionHandlerFactory = restrictionHandlerFactory;
    this.accountClient = accountClient;
    this.conversionHandlerFactory = conversionHandlerFactory;
  }

  void registerFeature(FeatureRestrictionName featureRestrictionName, FeatureRestriction feature) {
    if (featureRestrictionMap.containsKey(featureRestrictionName)) {
      throw new IllegalArgumentException(String.format("Feature [%s] has been registered", featureRestrictionName));
    }
    featureRestrictionMap.put(featureRestrictionName, feature);
  }

  @Override
  public boolean isFeatureAvailable(FeatureRestrictionName featureRestrictionName, String accountIdentifier) {
    try {
      checkAvailabilityOrThrow(featureRestrictionName, accountIdentifier);
      return true;
    } catch (Exception e) {
      log.debug(
          String.format("feature [%s] is not available for account [%s]", featureRestrictionName, accountIdentifier),
          e);
      return false;
    }
  }

  @Override
  public void checkAvailabilityOrThrow(FeatureRestrictionName featureRestrictionName, String accountIdentifier) {
    if (!isFeatureRestrictionDefined(featureRestrictionName)) {
      throw new FeatureNotSupportedException(String.format("Feature [%s] is not defined", featureRestrictionName));
    }

    if (!isFeatureFlagEnabled(accountIdentifier)) {
      return;
    }

    FeatureRestriction feature = featureRestrictionMap.get(featureRestrictionName);
    Edition edition = getLicenseEdition(accountIdentifier, feature.getModuleType());

    Restriction restriction = getRestriction(feature, edition);
    RestrictionHandler handler = restrictionHandlerFactory.getHandler(restriction.getRestrictionType());

    handler.check(featureRestrictionName, restriction, accountIdentifier, feature.getModuleType(), edition);
  }

  @Override
  public FeatureRestrictionMetadataDTO getFeatureMetadata(
      FeatureRestrictionName featureRestrictionName, String accountIdentifier) {
    if (!isFeatureRestrictionDefined(featureRestrictionName)) {
      throw new InvalidRequestException(String.format("Feature [%s] is not defined", featureRestrictionName));
    }
    FeatureRestriction featureRestriction = featureRestrictionMap.get(featureRestrictionName);
    Edition edition = getLicenseEdition(accountIdentifier, featureRestriction.getModuleType());

    ConversionHandler conversionHandler =
        conversionHandlerFactory.getConversionHandler(isFeatureFlagEnabled(accountIdentifier));
    return conversionHandler.toFeatureMetadataDTO(featureRestriction, edition, accountIdentifier);
  }

  @Override
  public RestrictionMetadataMapResponseDTO getFeatureRestrictionMetadataMap(
      List<FeatureRestrictionName> featureRestrictionNames, String accountIdentifier) {
    Map<FeatureRestrictionName, FeatureRestrictionMetadataDTO> metadataDTOMap = new HashMap<>();

    ConversionHandler conversionHandler =
        conversionHandlerFactory.getConversionHandler(isFeatureFlagEnabled(accountIdentifier));
    for (FeatureRestrictionName name : featureRestrictionNames) {
      FeatureRestriction featureRestriction = featureRestrictionMap.get(name);
      Edition edition = getLicenseEdition(accountIdentifier, featureRestriction.getModuleType());

      FeatureRestrictionMetadataDTO featureRestrictionMetadataDTO =
          conversionHandler.toFeatureMetadataDTO(featureRestriction, edition, accountIdentifier);
      metadataDTOMap.put(name, featureRestrictionMetadataDTO);
    }
    return RestrictionMetadataMapResponseDTO.builder().metadataMap(metadataDTOMap).build();
  }

  @Override
  public List<FeatureRestrictionMetadataDTO> getAllFeatureRestrictionMetadata(String accountIdentifier) {
    boolean featureFlagEnabled = false;
    if (accountIdentifier != null) {
      featureFlagEnabled = isFeatureFlagEnabled(accountIdentifier);
    }
    ConversionHandler conversionHandler = conversionHandlerFactory.getConversionHandler(featureFlagEnabled);

    return featureRestrictionMap.values()
        .stream()
        .map(featureRestriction -> conversionHandler.toFeatureMetadataDTO(featureRestriction, null, null))
        .collect(Collectors.toList());
  }

  @Override
  public FeatureRestrictionDetailsDTO getFeatureDetail(
      FeatureRestrictionName featureRestrictionName, String accountIdentifier) {
    if (!isFeatureRestrictionDefined(featureRestrictionName)) {
      throw new InvalidRequestException(String.format("Feature [%s] is not defined", featureRestrictionName));
    }
    FeatureRestriction featureRestriction = featureRestrictionMap.get(featureRestrictionName);
    ConversionHandler conversionHandler =
        conversionHandlerFactory.getConversionHandler(isFeatureFlagEnabled(accountIdentifier));
    try {
      Edition edition = getLicenseEdition(accountIdentifier, featureRestriction.getModuleType());
      return conversionHandler.toFeatureDetailsDTO(accountIdentifier, featureRestriction, edition);
    } catch (FeatureNotSupportedException e) {
      log.error(String.format("Invalid license state on account [%s] and moduleType [%s]", accountIdentifier,
                    featureRestriction.getModuleType()),
          e);
      return toDisallowedFeatureDetailsDTO(featureRestriction);
    }
  }

  @Override
  public List<FeatureRestrictionDetailsDTO> getFeatureDetails(
      List<FeatureRestrictionName> featureRestrictionNames, String accountIdentifier) {
    List<FeatureRestrictionDetailsDTO> result = new ArrayList<>();

    ConversionHandler conversionHandler =
        conversionHandlerFactory.getConversionHandler(isFeatureFlagEnabled(accountIdentifier));

    for (FeatureRestrictionName name : featureRestrictionNames) {
      if (!isFeatureRestrictionDefined(name)) {
        throw new InvalidRequestException(String.format("Feature [%s] is not defined", name));
      }
      FeatureRestriction featureRestriction = featureRestrictionMap.get(name);

      try {
        Edition edition = getLicenseEdition(accountIdentifier, featureRestriction.getModuleType());
        result.add(conversionHandler.toFeatureDetailsDTO(accountIdentifier, featureRestriction, edition));
      } catch (FeatureNotSupportedException e) {
        result.add(toDisallowedFeatureDetailsDTO(featureRestriction));
      }
    }
    return result;
  }

  @Override
  public List<FeatureRestrictionDetailsDTO> getEnabledFeatureDetails(String accountIdentifier) {
    List<FeatureRestrictionDetailsDTO> result = new ArrayList<>();
    // check all valid module type features(CD,CCM,FF,CI,CORE)

    boolean featureFlagEnabled = isFeatureFlagEnabled(accountIdentifier);
    ConversionHandler conversionHandler = conversionHandlerFactory.getConversionHandler(featureFlagEnabled);
    for (ModuleType moduleType : ModuleType.getModules()) {
      if (moduleType.isInternal() && !ModuleType.CORE.equals(moduleType)) {
        continue;
      }

      Edition edition;
      try {
        edition = getLicenseEdition(accountIdentifier, moduleType);
      } catch (FeatureNotSupportedException e) {
        log.debug("Not able to get license edition for [{}] under account [{}], ", accountIdentifier, moduleType);
        continue;
      }

      for (FeatureRestriction featureRestriction : featureRestrictionMap.values()) {
        if (featureRestriction.getModuleType().equals(moduleType)) {
          Restriction restriction = getRestriction(featureRestriction, edition);
          if (checkFeatureAvailability(featureFlagEnabled, restriction)) {
            result.add(conversionHandler.toFeatureDetailsDTO(accountIdentifier, featureRestriction, edition));
          }
        }
      }
    }
    return result;
  }

  private boolean isEnabledFeature(Restriction restriction) {
    if (RestrictionType.AVAILABILITY.equals(restriction.getRestrictionType())) {
      AvailabilityRestriction enableDisableRestriction = (AvailabilityRestriction) restriction;
      if (enableDisableRestriction.getEnabled()) {
        return true;
      }
    }
    return false;
  }

  private boolean isFeatureRestrictionDefined(FeatureRestrictionName featureRestrictionName) {
    return featureRestrictionMap.containsKey(featureRestrictionName);
  }

  private Edition getLicenseEdition(String accountIdentifier, ModuleType moduleType) {
    // if PL feature edition check
    if (ModuleType.CORE.equals(moduleType)) {
      return licenseService.calculateAccountEdition(accountIdentifier);
    }

    // other module feature
    LicensesWithSummaryDTO licenseInfo = licenseService.getLicenseSummary(accountIdentifier, moduleType);
    if (licenseInfo == null) {
      Edition edition = Edition.FREE;
      // if licenseInfo is null, and it is on prem + community, fallback on community edition instead of free
      if (DeployMode.isOnPrem(System.getenv().get(DEPLOY_MODE))) {
        if (DeployVariant.isCommunity(System.getenv().get(DEPLOY_VERSION))) {
          edition = Edition.COMMUNITY;
        } else {
          edition = Edition.ENTERPRISE;
        }
      }
      log.warn("Account {} has no license on module {}, fallback to {}", accountIdentifier, moduleType.name(), edition);
      return edition;
    }

    //    Expired license won't block user
    //    if (!isLicenseValid(licenseInfo)) {
    //      throw new FeatureNotSupportedException("Invalid license status");
    //    }
    return licenseInfo.getEdition();
  }

  private FeatureRestrictionDetailsDTO toDisallowedFeatureDetailsDTO(FeatureRestriction feature) {
    return FeatureRestrictionDetailsDTO.builder()
        .name(feature.getName())
        .moduleType(feature.getModuleType())
        .description(feature.getDescription())
        .allowed(false)
        .restrictionType(RestrictionType.AVAILABILITY)
        .restriction(AvailabilityRestrictionDTO.builder().enabled(false).build())
        .build();
  }

  private boolean isFeatureFlagEnabled(String accountId) {
    return RestClientUtils.getResponse(
        accountClient.isFeatureFlagEnabled(FEATURE_ENFORCEMENT_ENABLED.name(), accountId));
  }

  private boolean checkFeatureAvailability(boolean featureFlagEnabled, Restriction restriction) {
    if (!featureFlagEnabled) {
      // Feature always available if feature flag is turned off
      return true;
    }

    return isEnabledFeature(restriction);
  }
}
