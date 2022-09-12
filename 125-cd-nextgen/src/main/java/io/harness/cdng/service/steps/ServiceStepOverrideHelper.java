/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.service.steps;

import static io.harness.cdng.manifest.ManifestType.SERVICE_OVERRIDE_SUPPORTED_MANIFEST_TYPES;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;
import static java.util.Collections.EMPTY_LIST;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.cdng.configfile.steps.NgConfigFilesMetadataSweepingOutput;
import io.harness.cdng.manifest.ManifestConfigType;
import io.harness.cdng.manifest.steps.NgManifestsMetadataSweepingOutput;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class ServiceStepOverrideHelper {
  @Inject private ExecutionSweepingOutputService sweepingOutputService;

  public static final String SERVICE = "service";
  public static final String SERVICE_OVERRIDES = "service overrides";
  public static final String ENVIRONMENT_GLOBAL_OVERRIDES = "environment global overrides";

  public void prepareAndSaveFinalManifestMetadataToSweepingOutput(@NonNull NGServiceConfig serviceV2Config,
      NGServiceOverrideConfig serviceOverrideConfig, NGEnvironmentConfig ngEnvironmentConfig, Ambiance ambiance,
      String manifestsSweepingOutputName) {
    NGServiceV2InfoConfig ngServiceV2InfoConfig = serviceV2Config.getNgServiceV2InfoConfig();
    if (ngServiceV2InfoConfig == null) {
      throw new InvalidRequestException("No service configuration found in the service");
    }

    final Map<String, List<ManifestConfigWrapper>> finalLocationManifestsMap =
        getManifestsFromAllLocations(ngServiceV2InfoConfig, serviceOverrideConfig, ngEnvironmentConfig);

    final NgManifestsMetadataSweepingOutput manifestSweepingOutput =
        NgManifestsMetadataSweepingOutput.builder()
            .finalSvcManifestsMap(finalLocationManifestsMap)
            .serviceDefinitionType(ngServiceV2InfoConfig.getServiceDefinition().getType())
            .serviceIdentifier(ngServiceV2InfoConfig.getIdentifier())
            .environmentIdentifier(
                ngEnvironmentConfig == null || ngEnvironmentConfig.getNgEnvironmentInfoConfig() == null
                    ? StringUtils.EMPTY
                    : ngEnvironmentConfig.getNgEnvironmentInfoConfig().getIdentifier())
            .build();
    sweepingOutputService.consume(
        ambiance, manifestsSweepingOutputName, manifestSweepingOutput, StepCategory.STAGE.name());
  }

  @NotNull
  public static List<ManifestConfigWrapper> prepareFinalManifests(NGServiceV2InfoConfig ngServiceV2InfoConfig,
      NGServiceOverrideConfig serviceOverrideConfig, NGEnvironmentConfig ngEnvironmentConfig) {
    final Map<String, List<ManifestConfigWrapper>> finalLocationManifestsMap =
        getManifestsFromAllLocations(ngServiceV2InfoConfig, serviceOverrideConfig, ngEnvironmentConfig);
    validateOverridesTypeAndUniqueness(finalLocationManifestsMap, ngServiceV2InfoConfig.getIdentifier(),
        ngEnvironmentConfig.getNgEnvironmentInfoConfig().getIdentifier());

    List<ManifestConfigWrapper> finalManifests = new ArrayList<>();
    finalManifests.addAll(finalLocationManifestsMap.get(SERVICE));
    finalManifests.addAll(finalLocationManifestsMap.get(ENVIRONMENT_GLOBAL_OVERRIDES));
    finalManifests.addAll(finalLocationManifestsMap.get(SERVICE_OVERRIDES));
    return finalManifests;
  }

  public static void validateOverridesTypeAndUniqueness(
      Map<String, List<ManifestConfigWrapper>> locationManifestsMap, String svcIdentifier, String envIdentifier) {
    final List<ManifestConfigWrapper> svcManifests = locationManifestsMap.get(SERVICE);
    final List<ManifestConfigWrapper> envGlobalManifests = locationManifestsMap.get(ENVIRONMENT_GLOBAL_OVERRIDES);
    final List<ManifestConfigWrapper> svcOverrideManifests = locationManifestsMap.get(SERVICE_OVERRIDES);

    checkCrossLocationDuplicateManifestIdentifiers(
        svcManifests, envGlobalManifests, svcIdentifier, envIdentifier, ENVIRONMENT_GLOBAL_OVERRIDES);
    validateAllowedManifestTypesInOverrides(envGlobalManifests, ENVIRONMENT_GLOBAL_OVERRIDES);
    checkCrossLocationDuplicateManifestIdentifiers(
        svcManifests, svcOverrideManifests, svcIdentifier, envIdentifier, SERVICE_OVERRIDES);
    validateAllowedManifestTypesInOverrides(svcOverrideManifests, SERVICE_OVERRIDES);

    checkCrossLocationDuplicateManifestIdentifiers(
        svcOverrideManifests, envGlobalManifests, svcIdentifier, envIdentifier, SERVICE_OVERRIDES);
  }

  @NotNull
  public static Map<String, List<ManifestConfigWrapper>> getManifestsFromAllLocations(
      NGServiceV2InfoConfig serviceV2Config, NGServiceOverrideConfig serviceOverrideConfig,
      NGEnvironmentConfig ngEnvironmentConfig) {
    final List<ManifestConfigWrapper> svcManifests = getSvcManifests(serviceV2Config);
    final List<ManifestConfigWrapper> envGlobalManifests = getEnvGlobalManifests(ngEnvironmentConfig);
    List<ManifestConfigWrapper> svcOverrideManifests = getSvcOverrideManifests(serviceOverrideConfig);

    final Map<String, List<ManifestConfigWrapper>> finalManifests = new HashMap<>();
    finalManifests.put(SERVICE, svcManifests);
    finalManifests.put(ENVIRONMENT_GLOBAL_OVERRIDES, envGlobalManifests);
    finalManifests.put(SERVICE_OVERRIDES, svcOverrideManifests);
    return finalManifests;
  }

  private static List<ManifestConfigWrapper> getSvcManifests(NGServiceV2InfoConfig serviceV2Config) {
    if (serviceV2Config == null || serviceV2Config.getServiceDefinition() == null
        || serviceV2Config.getServiceDefinition().getServiceSpec() == null) {
      return emptyList();
    }
    return emptyIfNull(serviceV2Config.getServiceDefinition().getServiceSpec().getManifests());
  }

  @NonNull
  private static List<ManifestConfigWrapper> getSvcOverrideManifests(NGServiceOverrideConfig serviceOverrideConfig) {
    if (serviceOverrideConfig == null || serviceOverrideConfig.getServiceOverrideInfoConfig() == null) {
      return emptyList();
    }
    return emptyIfNull(serviceOverrideConfig.getServiceOverrideInfoConfig().getManifests());
  }

  @NonNull
  private static List<ManifestConfigWrapper> getEnvGlobalManifests(NGEnvironmentConfig ngEnvironmentConfig) {
    if (isNoManifestAvailable(ngEnvironmentConfig)) {
      return EMPTY_LIST;
    }
    return ngEnvironmentConfig.getNgEnvironmentInfoConfig().getNgEnvironmentGlobalOverride().getManifests();
  }

  private static boolean isNoManifestAvailable(NGEnvironmentConfig ngEnvironmentConfig) {
    return ngEnvironmentConfig == null || ngEnvironmentConfig.getNgEnvironmentInfoConfig() == null
        || ngEnvironmentConfig.getNgEnvironmentInfoConfig().getNgEnvironmentGlobalOverride() == null
        || ngEnvironmentConfig.getNgEnvironmentInfoConfig().getNgEnvironmentGlobalOverride().getManifests() == null;
  }

  private static void validateAllowedManifestTypesInOverrides(
      List<ManifestConfigWrapper> svcOverrideManifests, String overrideLocation) {
    if (isEmpty(svcOverrideManifests)) {
      return;
    }
    Set<String> unsupportedManifestTypesUsed =
        svcOverrideManifests.stream()
            .map(ManifestConfigWrapper::getManifest)
            .filter(Objects::nonNull)
            .map(ManifestConfig::getType)
            .map(ManifestConfigType::getDisplayName)
            .filter(type -> !SERVICE_OVERRIDE_SUPPORTED_MANIFEST_TYPES.contains(type))
            .collect(Collectors.toSet());
    if (isNotEmpty(unsupportedManifestTypesUsed)) {
      throw new InvalidRequestException(format("Unsupported Manifest Types: [%s] found for %s",
          unsupportedManifestTypesUsed.stream().map(Object::toString).collect(Collectors.joining(",")),
          overrideLocation));
    }
  }

  private static void checkCrossLocationDuplicateManifestIdentifiers(List<ManifestConfigWrapper> manifestsA,
      List<ManifestConfigWrapper> manifestsB, String svcIdentifier, String envIdentifier, String overrideLocation) {
    if (isEmpty(manifestsA) || isEmpty(manifestsB)) {
      return;
    }
    Set<String> overridesIdentifiers = manifestsB.stream()
                                           .map(ManifestConfigWrapper::getManifest)
                                           .map(ManifestConfig::getIdentifier)
                                           .collect(Collectors.toSet());
    List<String> duplicateManifestIds = manifestsA.stream()
                                            .map(ManifestConfigWrapper::getManifest)
                                            .map(ManifestConfig::getIdentifier)
                                            .filter(overridesIdentifiers::contains)
                                            .collect(Collectors.toList());
    if (isNotEmpty(duplicateManifestIds)) {
      throw new InvalidRequestException(
          format("Found duplicate manifest identifiers [%s] in %s for service [%s] and environment [%s]",
              duplicateManifestIds.stream().map(Object::toString).collect(Collectors.joining(",")), overrideLocation,
              svcIdentifier, envIdentifier));
    }
  }

  public void prepareAndSaveFinalConfigFilesMetadataToSweepingOutput(@NonNull NGServiceConfig serviceV2Config,
      NGServiceOverrideConfig serviceOverrideConfig, NGEnvironmentConfig ngEnvironmentConfig, Ambiance ambiance,
      String configFilesSweepingOutputName) {
    NGServiceV2InfoConfig ngServiceV2InfoConfig = serviceV2Config.getNgServiceV2InfoConfig();
    if (ngServiceV2InfoConfig == null) {
      throw new InvalidRequestException("No service configuration found in the service");
    }
    final List<ConfigFileWrapper> finalConfigFiles =
        prepareFinalConfigFiles(ngServiceV2InfoConfig, serviceOverrideConfig, ngEnvironmentConfig);

    final NgConfigFilesMetadataSweepingOutput configFileSweepingOutput =
        NgConfigFilesMetadataSweepingOutput.builder()
            .finalSvcConfigFiles(finalConfigFiles)
            .serviceIdentifier(ngServiceV2InfoConfig.getIdentifier())
            .environmentIdentifier(
                ngEnvironmentConfig == null || ngEnvironmentConfig.getNgEnvironmentInfoConfig() == null
                    ? StringUtils.EMPTY
                    : ngEnvironmentConfig.getNgEnvironmentInfoConfig().getIdentifier())
            .build();
    sweepingOutputService.consume(
        ambiance, configFilesSweepingOutputName, configFileSweepingOutput, StepCategory.STAGE.name());
  }

  @VisibleForTesting
  public static List<ConfigFileWrapper> prepareFinalConfigFiles(NGServiceV2InfoConfig serviceV2Config,
      NGServiceOverrideConfig serviceOverrideConfig, NGEnvironmentConfig ngEnvironmentConfig) {
    final Map<String, ConfigFileWrapper> svcConfigFiles = getSvcConfigFiles(serviceV2Config);
    final Map<String, ConfigFileWrapper> envGlobalConfigFiles = getEnvironmentGlobalConfigFiles(ngEnvironmentConfig);
    final Map<String, ConfigFileWrapper> svcOverrideConfigFiles = getSvcOverrideConfigFiles(serviceOverrideConfig);

    Map<String, ConfigFileWrapper> finalManifestsMap = new HashMap<>();
    finalManifestsMap.putAll(svcConfigFiles);
    finalManifestsMap.putAll(envGlobalConfigFiles);
    finalManifestsMap.putAll(svcOverrideConfigFiles);

    return new ArrayList<>(finalManifestsMap.values());
  }

  private static Map<String, ConfigFileWrapper> getSvcConfigFiles(NGServiceV2InfoConfig serviceV2Config) {
    if (isNotEmpty(serviceV2Config.getServiceDefinition().getServiceSpec().getConfigFiles())) {
      return serviceV2Config.getServiceDefinition().getServiceSpec().getConfigFiles().stream().collect(Collectors.toMap(
          configFileWrapper -> configFileWrapper.getConfigFile().getIdentifier(), Function.identity()));
    }
    return emptyMap();
  }

  private static Map<String, ConfigFileWrapper> getSvcOverrideConfigFiles(
      NGServiceOverrideConfig serviceOverrideConfig) {
    if (serviceOverrideConfig == null || serviceOverrideConfig.getServiceOverrideInfoConfig() == null
        || isEmpty(serviceOverrideConfig.getServiceOverrideInfoConfig().getConfigFiles())) {
      return emptyMap();
    }
    return serviceOverrideConfig.getServiceOverrideInfoConfig().getConfigFiles().stream().collect(
        Collectors.toMap(configFileWrapper -> configFileWrapper.getConfigFile().getIdentifier(), Function.identity()));
  }

  private static Map<String, ConfigFileWrapper> getEnvironmentGlobalConfigFiles(
      NGEnvironmentConfig ngEnvironmentConfig) {
    if (isNoEnvGlobalConfigFileOverridePresent(ngEnvironmentConfig)) {
      return emptyMap();
    }
    return ngEnvironmentConfig.getNgEnvironmentInfoConfig()
        .getNgEnvironmentGlobalOverride()
        .getConfigFiles()
        .stream()
        .collect(Collectors.toMap(
            configFileWrapper -> configFileWrapper.getConfigFile().getIdentifier(), Function.identity()));
  }

  private static boolean isNoEnvGlobalConfigFileOverridePresent(NGEnvironmentConfig ngEnvironmentConfig) {
    return ngEnvironmentConfig == null || ngEnvironmentConfig.getNgEnvironmentInfoConfig() == null
        || ngEnvironmentConfig.getNgEnvironmentInfoConfig().getNgEnvironmentGlobalOverride() == null
        || ngEnvironmentConfig.getNgEnvironmentInfoConfig().getNgEnvironmentGlobalOverride().getConfigFiles() == null;
  }
}
