/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.enforcement.client.services.impl;

import static io.harness.configuration.DeployVariant.DEPLOY_VERSION;
import static io.harness.remote.client.NGRestUtils.getResponse;

import io.harness.ModuleType;
import io.harness.configuration.DeployVariant;
import io.harness.enforcement.beans.CustomRestrictionEvaluationDTO;
import io.harness.enforcement.beans.details.FeatureRestrictionDetailListRequestDTO;
import io.harness.enforcement.beans.details.FeatureRestrictionDetailRequestDTO;
import io.harness.enforcement.beans.details.FeatureRestrictionDetailsDTO;
import io.harness.enforcement.beans.internal.RestrictionMetadataMapRequestDTO;
import io.harness.enforcement.beans.internal.RestrictionMetadataMapResponseDTO;
import io.harness.enforcement.beans.metadata.AvailabilityRestrictionMetadataDTO;
import io.harness.enforcement.beans.metadata.FeatureRestrictionMetadataDTO;
import io.harness.enforcement.beans.metadata.RateLimitRestrictionMetadataDTO;
import io.harness.enforcement.beans.metadata.RestrictionMetadataDTO;
import io.harness.enforcement.beans.metadata.StaticLimitRestrictionMetadataDTO;
import io.harness.enforcement.client.EnforcementClient;
import io.harness.enforcement.client.EnforcementClientConfiguration;
import io.harness.enforcement.client.custom.CustomRestrictionInterface;
import io.harness.enforcement.client.services.EnforcementClientService;
import io.harness.enforcement.client.services.EnforcementSdkRegisterService;
import io.harness.enforcement.client.usage.RestrictionUsageInterface;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.enforcement.constants.RestrictionType;
import io.harness.enforcement.exceptions.EnforcementServiceConnectionException;
import io.harness.enforcement.exceptions.FeatureNotSupportedException;
import io.harness.enforcement.exceptions.LimitExceededException;
import io.harness.enforcement.exceptions.WrongFeatureStateException;
import io.harness.enforcement.utils.SuggestionMessageUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.licensing.Edition;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class EnforcementClientServiceImpl implements EnforcementClientService {
  private final EnforcementClient enforcementClient;
  private final EnforcementSdkRegisterService enforcementSdkRegisterService;
  private final EnforcementClientConfiguration enforcementClientConfiguration;

  private static final String ENABLED = "enabled";
  private static final String MESSAGE = "message";
  private static String deployVersion = System.getenv().get(DEPLOY_VERSION);

  @Inject
  public EnforcementClientServiceImpl(EnforcementClient enforcementClient,
      EnforcementSdkRegisterService enforcementSdkRegisterService,
      EnforcementClientConfiguration enforcementClientConfiguration) {
    this.enforcementClient = enforcementClient;
    this.enforcementSdkRegisterService = enforcementSdkRegisterService;
    this.enforcementClientConfiguration = enforcementClientConfiguration;
  }

  @Override
  public boolean isEnforcementEnabled() {
    return enforcementClientConfiguration.isEnforcementCheckEnabled() || DeployVariant.isCommunity(deployVersion);
  }

  @Override
  public boolean isAvailable(FeatureRestrictionName featureRestrictionName, String accountIdentifier) {
    try {
      checkAvailability(featureRestrictionName, accountIdentifier);
    } catch (Exception e) {
      log.error(String.format("Enforcement check on feature [%s] for account [%s] does not succeed",
                    featureRestrictionName, accountIdentifier),
          e);
      return false;
    }
    return true;
  }

  @Override
  public boolean isAvailableWithIncrement(
      FeatureRestrictionName featureRestrictionName, String accountIdentifier, long increment) {
    try {
      checkAvailabilityWithIncrement(featureRestrictionName, accountIdentifier, increment);
    } catch (Exception e) {
      log.error(String.format("Enforcement check on feature [%s] for account [%s] does not succeed",
                    featureRestrictionName, accountIdentifier),
          e);
      return false;
    }
    return true;
  }

  @Override
  public void checkAvailability(FeatureRestrictionName featureRestrictionName, String accountIdentifier) {
    checkAvailabilityWithIncrement(featureRestrictionName, accountIdentifier, 1);
  }

  @Override
  public void checkAvailabilityWithIncrement(
      FeatureRestrictionName featureRestrictionName, String accountIdentifier, long increment) {
    if (!isEnforcementEnabled()) {
      return;
    }

    FeatureRestrictionMetadataDTO featureMetadataDTO;
    try {
      featureMetadataDTO =
          getResponse(enforcementClient.getFeatureRestrictionMetadata(featureRestrictionName, accountIdentifier));
    } catch (UnexpectedException e) {
      log.error("Not able to fetch feature restriction metadata from ng-manager, failover to bypass the check", e);
      return;
    }

    Edition edition = featureMetadataDTO.getEdition();
    ModuleType moduleType = featureMetadataDTO.getModuleType();
    RestrictionMetadataDTO currentRestriction = featureMetadataDTO.getRestrictionMetadata().get(edition);
    try {
      verifyRestriction(featureRestrictionName, accountIdentifier, moduleType, edition, currentRestriction, increment);
    } catch (FeatureNotSupportedException e) {
      if (RestrictionType.AVAILABILITY.equals(currentRestriction.getRestrictionType())) {
        String message = (String) e.getParams().get(MESSAGE);
        String newMessage = SuggestionMessageUtils.generateSuggestionMessage(
            message, edition, featureMetadataDTO.getRestrictionMetadata(), ENABLED);
        e.getParams().put(MESSAGE, newMessage);
        throw e;
      }
    } catch (LimitExceededException e) {
      String message = (String) e.getParams().get(MESSAGE);
      String newMessage = SuggestionMessageUtils.generateSuggestionMessage(
          message, edition, featureMetadataDTO.getRestrictionMetadata(), ENABLED);
      e.getParams().put(MESSAGE, newMessage);
      throw e;
    }
  }

  @Override
  public boolean isRemoteFeatureAvailable(FeatureRestrictionName featureRestrictionName, String accountIdentifier) {
    if (!isEnforcementEnabled()) {
      return true;
    }

    try {
      FeatureRestrictionDetailsDTO response = getResponse(enforcementClient.getFeatureRestrictionDetail(
          FeatureRestrictionDetailRequestDTO.builder().name(featureRestrictionName).build(), accountIdentifier));
      if (response != null && response.isAllowed()) {
        return true;
      } else {
        return false;
      }
    } catch (InvalidRequestException e) {
      log.error("Invalid request on check feature details", e);
      return false;
    } catch (UnexpectedException e) {
      log.error("Not able to fetch feature details", e);
      return true;
    }
  }

  @Override
  public Map<FeatureRestrictionName, Boolean> getAvailabilityForRemoteFeatures(
      List<FeatureRestrictionName> featureRestrictionNames, String accountIdentifier) {
    // initiate result
    Map<FeatureRestrictionName, Boolean> result = new HashMap<>();
    for (FeatureRestrictionName name : featureRestrictionNames) {
      result.put(name, true);
    }
    if (!isEnforcementEnabled()) {
      return result;
    }

    try {
      List<FeatureRestrictionDetailsDTO> response = getResponse(enforcementClient.getFeatureRestrictionMap(
          FeatureRestrictionDetailListRequestDTO.builder().names(featureRestrictionNames).build(), accountIdentifier));
      for (FeatureRestrictionDetailsDTO dto : response) {
        result.put(dto.getName(), dto.isAllowed());
      }
    } catch (InvalidRequestException e) {
      log.error("Invalid request on check feature details", e);
      result.forEach((k, v) -> v = false);
      return result;
    } catch (UnexpectedException e) {
      log.error("Not able to fetch feature details", e);
    }
    return result;
  }

  @Override
  public Map<FeatureRestrictionName, Boolean> getAvailabilityMap(
      Set<FeatureRestrictionName> featureRestrictionNames, String accountIdentifier) {
    // initiate result
    Map<FeatureRestrictionName, Boolean> result = new HashMap<>();
    for (FeatureRestrictionName name : featureRestrictionNames) {
      result.put(name, true);
    }

    if (!isEnforcementEnabled()) {
      return result;
    }

    try {
      RestrictionMetadataMapResponseDTO response = getResponse(enforcementClient.getFeatureRestrictionMetadataMap(
          RestrictionMetadataMapRequestDTO.builder().names(new ArrayList<>(featureRestrictionNames)).build(),
          accountIdentifier));

      Map<FeatureRestrictionName, FeatureRestrictionMetadataDTO> metadataMap = response.getMetadataMap();
      for (Map.Entry<FeatureRestrictionName, FeatureRestrictionMetadataDTO> entry : metadataMap.entrySet()) {
        FeatureRestrictionName featureRestrictionName = entry.getKey();
        FeatureRestrictionMetadataDTO featureMetadata = entry.getValue();

        try {
          Edition edition = featureMetadata.getEdition();
          ModuleType moduleType = featureMetadata.getModuleType();
          RestrictionMetadataDTO restrictionMetadataDTO = featureMetadata.getRestrictionMetadata().get(edition);

          verifyRestriction(featureRestrictionName, accountIdentifier, moduleType, edition, restrictionMetadataDTO, 1);
        } catch (Exception e) {
          result.put(featureRestrictionName, false);
        }
      }

    } catch (InvalidRequestException e) {
      log.error("Can't fetch RestrictionMetadataDTO, return all false", e);
      result.forEach((k, v) -> v = false);
      return result;
    } catch (UnexpectedException e) {
      log.error("Unable to connect to enforcement endpoints", e);
    }
    return result;
  }

  @Override
  public Optional<RestrictionMetadataDTO> getRestrictionMetadata(FeatureRestrictionName featureRestrictionName,
      String accountIdentifier) throws WrongFeatureStateException, EnforcementServiceConnectionException {
    try {
      FeatureRestrictionMetadataDTO response =
          getResponse(enforcementClient.getFeatureRestrictionMetadata(featureRestrictionName, accountIdentifier));
      RestrictionMetadataDTO restrictionMetadataDTO = response.getRestrictionMetadata().get(response.getEdition());
      return Optional.ofNullable(restrictionMetadataDTO);
    } catch (InvalidRequestException invalidRequestException) {
      throw new WrongFeatureStateException(
          String.format("Can't fetch RestrictionMetadataDTO for [%s]", featureRestrictionName), invalidRequestException,
          invalidRequestException.getMessage().contains("Invalid license status"));
    } catch (UnexpectedException e) {
      throw new EnforcementServiceConnectionException("Unable to connect to enforcement endpoints", e);
    }
  }

  @Override
  public Map<FeatureRestrictionName, RestrictionMetadataDTO> getRestrictionMetadataMap(
      List<FeatureRestrictionName> featureRestrictionNames, String accountIdentifier)
      throws WrongFeatureStateException, EnforcementServiceConnectionException {
    try {
      RestrictionMetadataMapResponseDTO response = getResponse(enforcementClient.getFeatureRestrictionMetadataMap(
          RestrictionMetadataMapRequestDTO.builder().names(featureRestrictionNames).build(), accountIdentifier));

      Map<FeatureRestrictionName, FeatureRestrictionMetadataDTO> metadataMap = response.getMetadataMap();
      return convertFeatureMetadataToRestrictionMetadata(metadataMap);
    } catch (InvalidRequestException invalidRequestException) {
      throw new WrongFeatureStateException("Can't fetch multiple restriction metadata", invalidRequestException,
          invalidRequestException.getMessage().contains("Invalid license status"));
    } catch (UnexpectedException e) {
      throw new EnforcementServiceConnectionException("Unable to connect to enforcement endpoints", e);
    }
  }

  private boolean verifyExceedLimit(long limit, long count, long increment) {
    return limit < count + increment;
  }

  private void verifyRestriction(FeatureRestrictionName featureRestrictionName, String accountIdentifier,
      ModuleType moduleType, Edition edition, RestrictionMetadataDTO currentRestriction, long increment) {
    switch (currentRestriction.getRestrictionType()) {
      case AVAILABILITY:
        AvailabilityRestrictionMetadataDTO availabilityRestrictionMetadataDTO =
            (AvailabilityRestrictionMetadataDTO) currentRestriction;
        if (!availabilityRestrictionMetadataDTO.isEnabled()) {
          throw new FeatureNotSupportedException("Feature is not enabled");
        }
        break;
      case STATIC_LIMIT:
        StaticLimitRestrictionMetadataDTO staticLimitRestrictionMetadataDTO =
            (StaticLimitRestrictionMetadataDTO) currentRestriction;
        RestrictionUsageInterface staticUsage =
            enforcementSdkRegisterService.getRestrictionUsageInterface(featureRestrictionName);
        if (verifyExceedLimit(staticLimitRestrictionMetadataDTO.getLimit(),
                staticUsage.getCurrentValue(accountIdentifier, staticLimitRestrictionMetadataDTO), increment)) {
          throw new LimitExceededException(String.format(
              "Exceeded static limitation. Current Limit: %s", staticLimitRestrictionMetadataDTO.getLimit()));
        }
        break;
      case RATE_LIMIT:
        RateLimitRestrictionMetadataDTO rateLimitRestriction = (RateLimitRestrictionMetadataDTO) currentRestriction;
        RestrictionUsageInterface rateUsage =
            enforcementSdkRegisterService.getRestrictionUsageInterface(featureRestrictionName);
        if (verifyExceedLimit(rateLimitRestriction.getLimit(),
                rateUsage.getCurrentValue(accountIdentifier, rateLimitRestriction), increment)) {
          throw new LimitExceededException(
              String.format("Exceeded rate limitation. Current Limit: %s", rateLimitRestriction.getLimit()));
        }
        break;
      case DURATION:
        // DURATION type is always allowed
        break;
      case CUSTOM:
        CustomRestrictionInterface customRestrictionInterface =
            enforcementSdkRegisterService.getCustomRestrictionInterface(featureRestrictionName);
        boolean isAllowed =
            customRestrictionInterface.evaluateCustomRestriction(CustomRestrictionEvaluationDTO.builder()
                                                                     .featureRestrictionName(featureRestrictionName)
                                                                     .accountIdentifier(accountIdentifier)
                                                                     .edition(edition)
                                                                     .moduleType(moduleType)
                                                                     .build());
        if (!isAllowed) {
          throw new FeatureNotSupportedException(
              String.format("Feature [%s] is not enabled", featureRestrictionName.name()));
        }
        break;
      case LICENSE_RATE_LIMIT:
      case LICENSE_STATIC_LIMIT:
        // bypass license type check
        break;
      default:
        throw new IllegalArgumentException("Unknown restriction type");
    }
  }

  private Map<FeatureRestrictionName, RestrictionMetadataDTO> convertFeatureMetadataToRestrictionMetadata(
      Map<FeatureRestrictionName, FeatureRestrictionMetadataDTO> metadataMap) {
    return metadataMap.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> {
      FeatureRestrictionMetadataDTO value = entry.getValue();
      return value.getRestrictionMetadata().get(value.getEdition());
    }));
  }
}
