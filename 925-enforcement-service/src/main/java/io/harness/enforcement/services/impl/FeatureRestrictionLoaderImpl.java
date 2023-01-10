/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.enforcement.services.impl;

import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.enforcement.bases.AvailabilityRestriction;
import io.harness.enforcement.bases.CustomRestriction;
import io.harness.enforcement.bases.DurationRestriction;
import io.harness.enforcement.bases.FeatureRestriction;
import io.harness.enforcement.bases.LicenseRateLimitRestriction;
import io.harness.enforcement.bases.LicenseStaticLimitRestriction;
import io.harness.enforcement.bases.RateLimitRestriction;
import io.harness.enforcement.bases.Restriction;
import io.harness.enforcement.bases.StaticLimitRestriction;
import io.harness.enforcement.configs.ClientInfo;
import io.harness.enforcement.configs.FeatureRestrictionConfig;
import io.harness.enforcement.interfaces.EnforcementSdkSupportInterface;
import io.harness.enforcement.services.FeatureRestrictionLoader;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.WingsException;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

@OwnedBy(HarnessTeam.GTM)
@Slf4j
@Singleton
public class FeatureRestrictionLoaderImpl implements FeatureRestrictionLoader {
  private final EnforcementServiceImpl enforcementService;
  private final List<FeatureRestrictionConfig> featureRestrictionConfigs;
  private final KryoConverterFactory kryoConverterFactory;
  private Map<String, EnforcementSdkClient> clientMap = new HashMap<>();

  private static final String FEATURE_RESOURCE_PATH = "classpath:features/*.yml";

  @Inject
  public FeatureRestrictionLoaderImpl(
      EnforcementServiceImpl enforcementService, KryoConverterFactory kryoConverterFactory) {
    featureRestrictionConfigs = new ArrayList<>();
    this.enforcementService = enforcementService;
    this.kryoConverterFactory = kryoConverterFactory;
  }

  public void run(Object applicationConfiguration) {
    loadYmlToFeaturesConfig(featureRestrictionConfigs);

    for (FeatureRestrictionConfig featuresConfig : featureRestrictionConfigs) {
      initiateClientMap(featuresConfig, applicationConfiguration);

      for (FeatureRestriction featureRestriction : featuresConfig.getFeatures()) {
        try {
          validAndCompleteFeature(featureRestriction, featuresConfig.getModuleType());
          enforcementService.registerFeature(featureRestriction.getName(), featureRestriction);
        } catch (Exception e) {
          log.error(String.format("Got exception during generate feature [%s], uncover and continue",
                        featureRestriction.getName()),
              e);
        }
      }
    }
  }

  private EnforcementSdkClientFactory generateUsageClientFactory(
      io.harness.enforcement.configs.ClientInfo clientInfo, Object applicationConfiguration) {
    ServiceHttpClientConfig serviceHttpClientConfig;
    String secret;
    try {
      String[] serverFields = clientInfo.getClientConfig().split("\\.");
      serviceHttpClientConfig = lookForField(serverFields, applicationConfiguration, ServiceHttpClientConfig.class);
    } catch (IllegalAccessException | NoSuchFieldException e) {
      log.error(String.format("Failed when looking for client config [%s]", clientInfo.getClientConfig()), e);
      return null;
    }

    try {
      String[] secretFields = clientInfo.getSecretConfig().split("\\.");
      secret = lookForField(secretFields, applicationConfiguration, String.class);
    } catch (IllegalAccessException | NoSuchFieldException e) {
      log.error(String.format("Failed when looking for secret config [%s]", clientInfo.getSecretConfig()), e);
      return null;
    }

    if (secret == null || serviceHttpClientConfig == null) {
      return null;
    }
    return new EnforcementSdkClientFactory(
        serviceHttpClientConfig, secret, new ServiceTokenGenerator(), kryoConverterFactory, NG_MANAGER.getServiceId());
  }

  private <T> T lookForField(String[] fields, Object applicationConfiguration, Class<T> clazz)
      throws IllegalAccessException, NoSuchFieldException {
    Field targetField = applicationConfiguration.getClass().getDeclaredField(fields[0]);
    targetField.setAccessible(true);
    Object targetObject = targetField.get(applicationConfiguration);
    targetField.setAccessible(false);
    for (int i = 1; i < fields.length; i++) {
      if (targetObject == null) {
        return null;
      }
      targetField = targetObject.getClass().getDeclaredField(fields[i]);
      targetField.setAccessible(true);
      targetObject = targetField.get(targetObject);
      targetField.setAccessible(false);
    }
    if (clazz.isInstance(targetObject)) {
      return (T) targetObject;
    }
    return null;
  }

  private void validAndCompleteFeature(FeatureRestriction featureRestriction, ModuleType moduleType) {
    validFeatureInfo(featureRestriction);
    featureRestriction.setModuleType(moduleType);

    featureRestriction.getRestrictions().values().forEach(v -> {
      try {
        validRestriction(v, moduleType);
        loadUsageClientToLimitRestriction(v);
      } catch (Exception e) {
        throw new InvalidArgumentsException(
            String.format("Failed to generate restriction for feature [%s]", featureRestriction.getName()), e,
            WingsException.USER_SRE);
      }
    });
  }

  private void loadUsageClientToLimitRestriction(Restriction restriction) {
    if (EnforcementSdkSupportInterface.class.isAssignableFrom(restriction.getClass())) {
      EnforcementSdkSupportInterface enforcementSdkSupport = (EnforcementSdkSupportInterface) restriction;
      enforcementSdkSupport.setEnforcementSdkClient(findFeatureClient(enforcementSdkSupport.getClientName()));
    }
  }

  private EnforcementSdkClient findFeatureClient(String clientName) {
    EnforcementSdkClient featureUsageClient = clientMap.get(clientName);
    if (featureUsageClient == null) {
      throw new InvalidArgumentsException(String.format("Client [%s] is not defined", clientName));
    }
    return featureUsageClient;
  }

  void validFeatureInfo(FeatureRestriction feature) {
    if (feature.getName() == null) {
      throw new InvalidArgumentsException("Feature is missing name");
    }

    if (feature.getRestrictions() == null) {
      throw new InvalidArgumentsException(
          String.format("Missing mandatory information for feature [%s]", feature.getName()));
    }
  }

  void validRestriction(Restriction restriction, ModuleType moduleType) {
    if (restriction.getRestrictionType() == null) {
      throw new InvalidArgumentsException("Restriction type is missing");
    }

    switch (restriction.getRestrictionType()) {
      case AVAILABILITY:
        AvailabilityRestriction enableDisableRestriction = (AvailabilityRestriction) restriction;

        if (enableDisableRestriction.getEnabled() == null) {
          throw new InvalidArgumentsException("EnableDisableRestriction is missing enabled value");
        }
        break;
      case RATE_LIMIT:
        RateLimitRestriction rateLimitRestriction = (RateLimitRestriction) restriction;

        if (rateLimitRestriction.getClientName() == null || rateLimitRestriction.getLimit() == null
            || rateLimitRestriction.getTimeUnit() == null) {
          throw new InvalidArgumentsException("RateLimitRestriction is missing necessary config");
        }
        break;
      case STATIC_LIMIT:
        StaticLimitRestriction staticLimitRestriction = (StaticLimitRestriction) restriction;

        if (staticLimitRestriction.getClientName() == null || staticLimitRestriction.getLimit() == null) {
          throw new InvalidArgumentsException("StaticLimitRestriction is missing necessary config");
        }
        break;
      case CUSTOM:
        CustomRestriction customRestriction = (CustomRestriction) restriction;
        if (customRestriction.getClientName() == null) {
          throw new InvalidArgumentsException("CustomRestriction is missing necessary config");
        }
        break;
      case DURATION:
        DurationRestriction durationRestriction = (DurationRestriction) restriction;
        if (durationRestriction.getTimeUnit() == null || durationRestriction.getTimeUnit().getUnit() == null) {
          throw new InvalidArgumentsException("DurationRestriction is missing necessary config");
        }
        break;
      case LICENSE_RATE_LIMIT:
        LicenseRateLimitRestriction licenseRateLimitRestriction = (LicenseRateLimitRestriction) restriction;
        if (ModuleType.CORE.equals(moduleType)) {
          throw new InvalidArgumentsException("CORE feature can't have LicenseRateLimitRestriction");
        }
        if (licenseRateLimitRestriction.getClientName() == null || licenseRateLimitRestriction.getFieldName() == null
            || licenseRateLimitRestriction.getTimeUnit() == null
            || licenseRateLimitRestriction.getTimeUnit().getUnit() == null) {
          throw new InvalidArgumentsException("LicenseRateLimitRestriction is missing necessary config");
        }
        break;
      case LICENSE_STATIC_LIMIT:
        LicenseStaticLimitRestriction licenseStaticLimitRestriction = (LicenseStaticLimitRestriction) restriction;
        if (ModuleType.CORE.equals(moduleType)) {
          throw new InvalidArgumentsException("CORE feature can't have LicenseRateLimitRestriction");
        }
        if (licenseStaticLimitRestriction.getClientName() == null
            || licenseStaticLimitRestriction.getFieldName() == null) {
          throw new InvalidArgumentsException("LicenseRateLimitRestriction is missing necessary config");
        }
        break;
      default:
        throw new InvalidArgumentsException("Unknown restriction type");
    }
  }

  void loadYmlToFeaturesConfig(List<FeatureRestrictionConfig> configs) {
    ClassLoader cl = this.getClass().getClassLoader();
    ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(cl);
    try {
      Resource[] resources = resolver.getResources(FEATURE_RESOURCE_PATH);

      ObjectMapper om = new ObjectMapper(new YAMLFactory());
      for (Resource resource : resources) {
        byte[] bytes = Resources.toByteArray(resource.getURL());
        FeatureRestrictionConfig featuresConfig = om.readValue(bytes, FeatureRestrictionConfig.class);
        configs.add(featuresConfig);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load feature yaml file.", e);
    }
  }

  private void initiateClientMap(FeatureRestrictionConfig featuresConfig, Object applicationConfiguration) {
    for (ClientInfo clientInfo : featuresConfig.getClients()) {
      EnforcementSdkClientFactory featureUsageClientFactory =
          generateUsageClientFactory(clientInfo, applicationConfiguration);
      if (featureUsageClientFactory != null) {
        clientMap.put(clientInfo.getName(), featureUsageClientFactory.get());
      }
    }
  }
}
