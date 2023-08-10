/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.service.steps;
import static io.harness.cdng.manifest.ManifestType.SERVICE_OVERRIDE_SUPPORTED_MANIFEST_TYPES;
import static io.harness.cdng.service.steps.constants.ServiceStepConstants.ENVIRONMENT;
import static io.harness.cdng.service.steps.constants.ServiceStepConstants.ENVIRONMENT_GLOBAL_OVERRIDES;
import static io.harness.cdng.service.steps.constants.ServiceStepConstants.OVERRIDES_COMMAND_UNIT;
import static io.harness.cdng.service.steps.constants.ServiceStepConstants.OVERRIDE_IN_REVERSE_PRIORITY;
import static io.harness.cdng.service.steps.constants.ServiceStepConstants.SERVICE;
import static io.harness.cdng.service.steps.constants.ServiceStepConstants.SERVICE_CONFIGURATION_NOT_FOUND;
import static io.harness.cdng.service.steps.constants.ServiceStepConstants.SERVICE_OVERRIDES;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.azure.config.yaml.ApplicationSettingsConfiguration;
import io.harness.cdng.azure.config.yaml.ConnectionStringsConfiguration;
import io.harness.cdng.azure.webapp.steps.NgAppSettingsSweepingOutput;
import io.harness.cdng.azure.webapp.steps.NgConnectionStringsSweepingOutput;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.cdng.configfile.steps.NgConfigFilesMetadataSweepingOutput;
import io.harness.cdng.hooks.ServiceHook;
import io.harness.cdng.hooks.ServiceHookWrapper;
import io.harness.cdng.hooks.steps.ServiceHooksMetadataSweepingOutput;
import io.harness.cdng.manifest.ManifestConfigType;
import io.harness.cdng.manifest.steps.output.NgManifestsMetadataSweepingOutput;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.service.ServiceSpec;
import io.harness.cdng.service.WebAppSpec;
import io.harness.cdng.service.beans.KubernetesServiceSpec;
import io.harness.cdng.service.beans.NativeHelmServiceSpec;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.environment.beans.NGEnvironmentGlobalOverride;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.service.mappers.ManifestFilterHelper;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideConfig;
import io.harness.ng.core.serviceoverridev2.beans.NGServiceOverrideConfigV2;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesSpec;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class ServiceStepOverrideHelper {
  @Inject private ExecutionSweepingOutputService sweepingOutputService;

  public void prepareAndSaveFinalManifestMetadataToSweepingOutput(@NonNull NGServiceConfig serviceV2Config,
      NGServiceOverrideConfig serviceOverrideConfig, NGEnvironmentConfig ngEnvironmentConfig, Ambiance ambiance,
      String manifestsSweepingOutputName) {
    NGServiceV2InfoConfig ngServiceV2InfoConfig = serviceV2Config.getNgServiceV2InfoConfig();
    if (ngServiceV2InfoConfig == null) {
      throw new InvalidRequestException(SERVICE_CONFIGURATION_NOT_FOUND);
    }

    Map<String, List<ManifestConfigWrapper>> finalLocationManifestsMap = new HashMap<>();

    // Processing envGroups. EnvironmentConfig and serviceOverrideConfig is null for envGroup. GitOps Flow
    if (serviceOverrideConfig == null && ngEnvironmentConfig == null) {
      final List<ManifestConfigWrapper> svcManifests = getSvcManifests(serviceV2Config.getNgServiceV2InfoConfig());
      finalLocationManifestsMap.put(SERVICE, svcManifests);
    } else {
      finalLocationManifestsMap = getManifestsFromAllLocations(ngServiceV2InfoConfig, serviceOverrideConfig,
          ngEnvironmentConfig.getNgEnvironmentInfoConfig().getNgEnvironmentGlobalOverride());
    }

    final NgManifestsMetadataSweepingOutput manifestSweepingOutput =
        NgManifestsMetadataSweepingOutput.builder()
            .finalSvcManifestsMap(finalLocationManifestsMap)
            .serviceDefinitionType(ngServiceV2InfoConfig.getServiceDefinition().getType())
            .serviceIdentifier(ngServiceV2InfoConfig.getIdentifier())
            .environmentIdentifier(
                ngEnvironmentConfig == null || ngEnvironmentConfig.getNgEnvironmentInfoConfig() == null
                    ? StringUtils.EMPTY
                    : ngEnvironmentConfig.getNgEnvironmentInfoConfig().getIdentifier())
            .manifestConfigurations(ManifestFilterHelper.getManifestConfigurationsFromKubernetesAndNativeHelm(
                ngServiceV2InfoConfig.getServiceDefinition().getServiceSpec()))
            .build();
    sweepingOutputService.consume(
        ambiance, manifestsSweepingOutputName, manifestSweepingOutput, StepCategory.STAGE.name());
  }

  // This is for overrides V2 design
  public void saveFinalManifestsToSweepingOutputV2(@NonNull NGServiceV2InfoConfig ngServiceV2InfoConfig,
      Ambiance ambiance, String manifestsSweepingOutputName,
      EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2> overrideV2Configs, String environmentRef) {
    if (ngServiceV2InfoConfig == null) {
      throw new InvalidRequestException(SERVICE_CONFIGURATION_NOT_FOUND);
    }
    final List<ManifestConfigWrapper> svcManifests = getSvcManifests(ngServiceV2InfoConfig);
    Map<ServiceOverridesType, List<ManifestConfigWrapper>> manifestsFromOverride = new HashMap<>();
    if (isNotEmpty(overrideV2Configs)) {
      overrideV2Configs.forEach((type, override) -> {
        if (isNotEmpty(override.getSpec().getManifests())) {
          manifestsFromOverride.put(type, emptyIfNull(override.getSpec().getManifests()));
        }
      });
    }

    final NgManifestsMetadataSweepingOutput manifestSweepingOutput =
        NgManifestsMetadataSweepingOutput.builder()
            .serviceDefinitionType(ngServiceV2InfoConfig.getServiceDefinition().getType())
            .serviceIdentifier(ngServiceV2InfoConfig.getIdentifier())
            .environmentIdentifier(environmentRef)
            .svcManifests(svcManifests)
            .manifestsFromOverride(manifestsFromOverride)
            .manifestConfigurations(ManifestFilterHelper.getManifestConfigurationsFromKubernetesAndNativeHelm(
                ngServiceV2InfoConfig.getServiceDefinition().getServiceSpec()))
            .build();
    sweepingOutputService.consume(
        ambiance, manifestsSweepingOutputName, manifestSweepingOutput, StepCategory.STAGE.name());
  }
  @NonNull
  public static List<ManifestConfigWrapper> prepareFinalManifests(NGServiceV2InfoConfig ngServiceV2InfoConfig,
      NGServiceOverrideConfig serviceOverrideConfig, NGEnvironmentGlobalOverride environmentGlobalOverride,
      String envId) {
    final Map<String, List<ManifestConfigWrapper>> finalLocationManifestsMap =
        getManifestsFromAllLocations(ngServiceV2InfoConfig, serviceOverrideConfig, environmentGlobalOverride);
    validateOverridesTypeAndUniqueness(finalLocationManifestsMap, ngServiceV2InfoConfig.getIdentifier(), envId);

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

  public static void validateOverridesTypeAndUniquenessV2(
      Map<ServiceOverridesType, List<ManifestConfigWrapper>> manifestsMapGroupByType,
      List<ManifestConfigWrapper> svcManifests) {
    Set<String> existingUniqueIdentifier = new HashSet<>();

    checkDuplicateIdentifiersAndThrow(svcManifests, existingUniqueIdentifier, SERVICE);

    if (isNotEmpty(manifestsMapGroupByType)) {
      for (Entry<ServiceOverridesType, List<ManifestConfigWrapper>> overrideManifestEntry :
          manifestsMapGroupByType.entrySet()) {
        checkDuplicateIdentifiersAndThrow(
            overrideManifestEntry.getValue(), existingUniqueIdentifier, overrideManifestEntry.getKey().toString());
        validateAllowedManifestTypesInOverrides(
            overrideManifestEntry.getValue(), overrideManifestEntry.getKey().toString());
      }
    }
  }

  private static void checkDuplicateIdentifiersAndThrow(
      List<ManifestConfigWrapper> svcManifests, Set<String> existingUniqueIdentifier, String location) {
    List<String> duplicateIdentifiers = svcManifests.stream()
                                            .filter(manifestWrapper -> manifestWrapper.getManifest() != null)
                                            .map(manifestWrapper -> manifestWrapper.getManifest().getIdentifier())
                                            .filter(identifier -> !existingUniqueIdentifier.add(identifier))
                                            .collect(Collectors.toList());
    if (isNotEmpty(duplicateIdentifiers)) {
      throw new InvalidRequestException(
          String.format("found duplicate identifiers %s in %s", duplicateIdentifiers.toString(), location));
    }

    Set<String> newIdentifiers = svcManifests.stream()
                                     .filter(manifestWrapper -> manifestWrapper.getManifest() != null)
                                     .map(manifestWrapper -> manifestWrapper.getManifest().getIdentifier())
                                     .collect(Collectors.toSet());
    existingUniqueIdentifier.addAll(newIdentifiers);
  }

  @NonNull
  public static Map<String, List<ManifestConfigWrapper>> getManifestsFromAllLocations(
      NGServiceV2InfoConfig serviceV2Config, NGServiceOverrideConfig serviceOverrideConfig,
      NGEnvironmentGlobalOverride environmentGlobalOverride) {
    final List<ManifestConfigWrapper> svcManifests = getSvcManifests(serviceV2Config);
    final List<ManifestConfigWrapper> envGlobalManifests = getEnvGlobalManifests(environmentGlobalOverride);
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
  private static List<ManifestConfigWrapper> getEnvGlobalManifests(
      NGEnvironmentGlobalOverride environmentGlobalOverride) {
    return environmentGlobalOverride == null || environmentGlobalOverride.getManifests() == null
        ? Collections.emptyList()
        : environmentGlobalOverride.getManifests();
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
      throw new InvalidRequestException(SERVICE_CONFIGURATION_NOT_FOUND);
    }
    List<ConfigFileWrapper> finalConfigFiles;
    Map<String, String> configFileLocation = new HashMap<>();
    // Processing envGroups. EnvironmentConfig and serviceOverrideConfig is null for envGroup. GitOps Flow
    if (serviceOverrideConfig == null && ngEnvironmentConfig == null) {
      final Map<String, ConfigFileWrapper> svcConfigFiles =
          getSvcConfigFiles(serviceV2Config.getNgServiceV2InfoConfig());
      Map<String, ConfigFileWrapper> finalConfigFilesMap = new HashMap<>();
      createConfigFileMap(configFileLocation, svcConfigFiles, finalConfigFilesMap, SERVICE);
      finalConfigFiles = new ArrayList<>(finalConfigFilesMap.values());
    } else {
      finalConfigFiles = prepareFinalConfigFiles(ngServiceV2InfoConfig, serviceOverrideConfig,
          ngEnvironmentConfig.getNgEnvironmentInfoConfig().getNgEnvironmentGlobalOverride(), configFileLocation);
    }

    final NgConfigFilesMetadataSweepingOutput configFileSweepingOutput =
        NgConfigFilesMetadataSweepingOutput.builder()
            .finalSvcConfigFiles(finalConfigFiles)
            .serviceIdentifier(ngServiceV2InfoConfig.getIdentifier())
            .configFileLocation(configFileLocation)
            .environmentIdentifier(
                ngEnvironmentConfig == null || ngEnvironmentConfig.getNgEnvironmentInfoConfig() == null
                    ? StringUtils.EMPTY
                    : ngEnvironmentConfig.getNgEnvironmentInfoConfig().getIdentifier())
            .build();
    sweepingOutputService.consume(
        ambiance, configFilesSweepingOutputName, configFileSweepingOutput, StepCategory.STAGE.name());
  }

  private static void createConfigFileMap(Map<String, String> configFileLocation,
      Map<String, ConfigFileWrapper> configFiles, Map<String, ConfigFileWrapper> finalConfigFilesMap,
      String fileLocation) {
    for (Map.Entry<String, ConfigFileWrapper> configFile : configFiles.entrySet()) {
      finalConfigFilesMap.put(configFile.getKey(), configFile.getValue());
      configFileLocation.put(configFile.getKey(), fileLocation);
    }
  }

  // This is for overrides V2
  public void saveFinalConfigFilesToSweepingOutputV2(@NonNull NGServiceV2InfoConfig ngServiceV2InfoConfig,
      EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2> overrideV2Configs, String environmentRef,
      Ambiance ambiance, String configFilesSweepingOutputName) {
    List<ConfigFileWrapper> finalConfigFiles = new ArrayList<>();
    Map<String, String> configFileLocation = new HashMap<>();
    finalConfigFiles = prepareFinalConfigFilesV2(ngServiceV2InfoConfig, overrideV2Configs, configFileLocation);

    final NgConfigFilesMetadataSweepingOutput configFileSweepingOutput =
        NgConfigFilesMetadataSweepingOutput.builder()
            .finalSvcConfigFiles(finalConfigFiles)
            .configFileLocation(configFileLocation)
            .serviceIdentifier(ngServiceV2InfoConfig.getIdentifier())
            .environmentIdentifier(environmentRef)
            .build();
    sweepingOutputService.consume(
        ambiance, configFilesSweepingOutputName, configFileSweepingOutput, StepCategory.STAGE.name());
  }

  private List<ConfigFileWrapper> prepareFinalConfigFilesV2(NGServiceV2InfoConfig serviceV2Config,
      Map<ServiceOverridesType, NGServiceOverrideConfigV2> overrideV2Configs, Map<String, String> configFileLocation) {
    Map<String, ConfigFileWrapper> finalConfigFiles = new HashMap<>(getSvcConfigFiles(serviceV2Config));
    for (ConfigFileWrapper configFileWrapper : finalConfigFiles.values()) {
      configFileLocation.put(configFileWrapper.getConfigFile().getIdentifier(), "Service");
    }

    for (ServiceOverridesType overridesType : OVERRIDE_IN_REVERSE_PRIORITY) {
      if (overrideV2Configs.containsKey(overridesType)
          && isNotEmpty(overrideV2Configs.get(overridesType).getSpec().getConfigFiles())) {
        for (ConfigFileWrapper configFileWrapper : overrideV2Configs.get(overridesType).getSpec().getConfigFiles()) {
          finalConfigFiles.put(configFileWrapper.getConfigFile().getIdentifier(), configFileWrapper);
          configFileLocation.put(configFileWrapper.getConfigFile().getIdentifier(), overridesType.toString());
        }
      }
    }

    return new ArrayList<>(finalConfigFiles.values());
  }

  // This is for overrides V2
  public void saveFinalAppSettingsToSweepingOutputV2(@NonNull NGServiceV2InfoConfig ngServiceV2InfoConfig,
      EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2> overrideV2Configs, Ambiance ambiance,
      String appSettingsSweepingOutputName) {
    List<ApplicationSettingsConfiguration> finalAppSettings;

    finalAppSettings = prepareFinalAppSettingsV2(ngServiceV2InfoConfig, overrideV2Configs);

    final NgAppSettingsSweepingOutput appSettingsSweepingOutput =
        NgAppSettingsSweepingOutput.builder()
            .store(finalAppSettings.stream().findFirst().map(ApplicationSettingsConfiguration::getStore).orElse(null))
            .build();
    sweepingOutputService.consume(
        ambiance, appSettingsSweepingOutputName, appSettingsSweepingOutput, StepCategory.STAGE.name());
  }

  private List<ApplicationSettingsConfiguration> prepareFinalAppSettingsV2(
      NGServiceV2InfoConfig serviceV2Config, Map<ServiceOverridesType, NGServiceOverrideConfigV2> overrideV2Configs) {
    List<ApplicationSettingsConfiguration> applicationSettingsConfiguration = getSvcAppSettings(serviceV2Config);
    ApplicationSettingsConfiguration configuration = null;
    for (ServiceOverridesType overridesType : OVERRIDE_IN_REVERSE_PRIORITY) {
      if (overrideV2Configs.containsKey(overridesType)) {
        ServiceOverridesSpec spec = overrideV2Configs.get(overridesType).getSpec();
        if (spec.getApplicationSettings() != null) {
          configuration = spec.getApplicationSettings();
        }
      }
    }
    if (configuration != null) {
      applicationSettingsConfiguration = Collections.singletonList(configuration);
    }
    return applicationSettingsConfiguration;
  }

  // This is for overrides V2
  public void saveFinalConnectionStringsToSweepingOutputV2(@NonNull NGServiceV2InfoConfig ngServiceV2InfoConfig,
      EnumMap<ServiceOverridesType, NGServiceOverrideConfigV2> overrideV2Configs, Ambiance ambiance,
      String connectionStringsSweepingOutputName) {
    List<ConnectionStringsConfiguration> finalConnectionStrings;

    finalConnectionStrings = prepareFinalConnectionStringsV2(ngServiceV2InfoConfig, overrideV2Configs);

    final NgConnectionStringsSweepingOutput connectionStringsSweepingOutput =
        NgConnectionStringsSweepingOutput.builder()
            .store(
                finalConnectionStrings.stream().findFirst().map(ConnectionStringsConfiguration::getStore).orElse(null))
            .build();
    sweepingOutputService.consume(
        ambiance, connectionStringsSweepingOutputName, connectionStringsSweepingOutput, StepCategory.STAGE.name());
  }

  private List<ConnectionStringsConfiguration> prepareFinalConnectionStringsV2(
      NGServiceV2InfoConfig serviceV2Config, Map<ServiceOverridesType, NGServiceOverrideConfigV2> overrideV2Configs) {
    List<ConnectionStringsConfiguration> connectionStringsConfiguration = getSvcConnectionStrings(serviceV2Config);
    ConnectionStringsConfiguration configuration = null;
    for (ServiceOverridesType overridesType : OVERRIDE_IN_REVERSE_PRIORITY) {
      if (overrideV2Configs.containsKey(overridesType)) {
        ServiceOverridesSpec spec = overrideV2Configs.get(overridesType).getSpec();
        if (spec.getConnectionStrings() != null) {
          configuration = spec.getConnectionStrings();
        }
      }
    }
    if (configuration != null) {
      connectionStringsConfiguration = Collections.singletonList(configuration);
    }
    return connectionStringsConfiguration;
  }

  public void prepareAndSaveFinalServiceHooksMetadataToSweepingOutput(
      @NonNull NGServiceConfig serviceV2Config, Ambiance ambiance, String serviceHooksSweepingOutputName) {
    NGServiceV2InfoConfig ngServiceV2InfoConfig = serviceV2Config.getNgServiceV2InfoConfig();
    if (ngServiceV2InfoConfig == null) {
      throw new InvalidRequestException(SERVICE_CONFIGURATION_NOT_FOUND);
    }
    List<ServiceHookWrapper> finalServiceHooks;
    // No overrides for service Hooks
    ServiceSpec serviceSpec = serviceV2Config.getNgServiceV2InfoConfig().getServiceDefinition().getServiceSpec();
    final Map<String, ServiceHookWrapper> serviceHooks = getServiceHooks(serviceSpec);
    finalServiceHooks = new ArrayList<>(serviceHooks.values());

    final ServiceHooksMetadataSweepingOutput serviceHooksSweepingOutput =
        ServiceHooksMetadataSweepingOutput.builder().finalServiceHooks(finalServiceHooks).build();
    sweepingOutputService.consume(
        ambiance, serviceHooksSweepingOutputName, serviceHooksSweepingOutput, StepCategory.STAGE.name());
  }

  public void prepareAndSaveFinalConnectionStringsMetadataToSweepingOutput(@NonNull NGServiceConfig serviceV2Config,
      NGServiceOverrideConfig serviceOverrideConfig, NGEnvironmentConfig ngEnvironmentConfig, Ambiance ambiance,
      String configFilesSweepingOutputName) {
    NGServiceV2InfoConfig ngServiceV2InfoConfig = serviceV2Config.getNgServiceV2InfoConfig();
    if (ngServiceV2InfoConfig == null) {
      throw new InvalidRequestException(SERVICE_CONFIGURATION_NOT_FOUND);
    }
    final List<ConnectionStringsConfiguration> svcConnectionStrings =
        prepareFinalConnectionStrings(ngServiceV2InfoConfig, serviceOverrideConfig, ngEnvironmentConfig);

    if (isNotEmpty(svcConnectionStrings)) {
      final NgConnectionStringsSweepingOutput connectionStringsSweepingOutput =
          NgConnectionStringsSweepingOutput.builder()
              .store(
                  svcConnectionStrings.stream().findFirst().map(ConnectionStringsConfiguration::getStore).orElse(null))
              .build();
      if (connectionStringsSweepingOutput.getStore() != null) {
        sweepingOutputService.consume(
            ambiance, configFilesSweepingOutputName, connectionStringsSweepingOutput, StepCategory.STAGE.name());
      }
    }
  }

  public void prepareAndSaveFinalAppServiceMetadataToSweepingOutput(@NonNull NGServiceConfig serviceV2Config,
      NGServiceOverrideConfig serviceOverrideConfig, NGEnvironmentConfig ngEnvironmentConfig, Ambiance ambiance,
      String appSettingsSweepingOutputName) {
    NGServiceV2InfoConfig ngServiceV2InfoConfig = serviceV2Config.getNgServiceV2InfoConfig();
    if (ngServiceV2InfoConfig == null) {
      throw new InvalidRequestException(SERVICE_CONFIGURATION_NOT_FOUND);
    }
    final List<ApplicationSettingsConfiguration> finalAppSettings =
        prepareFinalAppSettings(ngServiceV2InfoConfig, serviceOverrideConfig, ngEnvironmentConfig);

    if (isNotEmpty(finalAppSettings)) {
      final NgAppSettingsSweepingOutput appSettingsSweepingOutput =
          NgAppSettingsSweepingOutput.builder()
              .store(finalAppSettings.stream().findFirst().map(ApplicationSettingsConfiguration::getStore).orElse(null))
              .build();
      if (appSettingsSweepingOutput.getStore() != null) {
        sweepingOutputService.consume(
            ambiance, appSettingsSweepingOutputName, appSettingsSweepingOutput, StepCategory.STAGE.name());
      }
    }
  }

  @VisibleForTesting
  public List<ConnectionStringsConfiguration> prepareFinalConnectionStrings(NGServiceV2InfoConfig serviceV2Config,
      NGServiceOverrideConfig serviceOverrideConfig, NGEnvironmentConfig environmentConfig) {
    final List<ConnectionStringsConfiguration> svcOverrideConnectionStrings =
        getSvcOverrideConnectionStrings(serviceOverrideConfig);
    if (isNotEmpty(svcOverrideConnectionStrings)) {
      return svcOverrideConnectionStrings;
    }
    final List<ConnectionStringsConfiguration> envGlobalConnectionStrings =
        getEnvironmentGlobalConnectionStrings(environmentConfig);
    if (isNotEmpty(envGlobalConnectionStrings)) {
      return envGlobalConnectionStrings;
    }
    return getSvcConnectionStrings(serviceV2Config);
  }

  @VisibleForTesting
  public List<ApplicationSettingsConfiguration> prepareFinalAppSettings(NGServiceV2InfoConfig serviceV2Config,
      NGServiceOverrideConfig serviceOverrideConfig, NGEnvironmentConfig environmentConfig) {
    final List<ApplicationSettingsConfiguration> svcOverrideAppSettings =
        getSvcOverrideAppSettings(serviceOverrideConfig);
    if (isNotEmpty(svcOverrideAppSettings)) {
      return svcOverrideAppSettings;
    }
    final List<ApplicationSettingsConfiguration> envGlobalAppSettings =
        getEnvironmentGlobalAppSettings(environmentConfig);
    if (isNotEmpty(envGlobalAppSettings)) {
      return envGlobalAppSettings;
    }
    return getSvcAppSettings(serviceV2Config);
  }

  private List<ConnectionStringsConfiguration> getEnvironmentGlobalConnectionStrings(
      NGEnvironmentConfig environmentConfig) {
    if (isNoEnvGlobalConnectionStringsOverridePresent(environmentConfig)) {
      return emptyList();
    }
    return Collections.singletonList(
        environmentConfig.getNgEnvironmentInfoConfig().getNgEnvironmentGlobalOverride().getConnectionStrings());
  }

  private List<ApplicationSettingsConfiguration> getEnvironmentGlobalAppSettings(
      NGEnvironmentConfig environmentConfig) {
    if (isNoEnvGlobalAppSettingsOverridePresent(environmentConfig)) {
      return emptyList();
    }
    return Collections.singletonList(
        environmentConfig.getNgEnvironmentInfoConfig().getNgEnvironmentGlobalOverride().getApplicationSettings());
  }

  private List<ConnectionStringsConfiguration> getSvcConnectionStrings(NGServiceV2InfoConfig serviceV2Config) {
    if (hasWebAppSpec(serviceV2Config)
        && ((WebAppSpec) serviceV2Config.getServiceDefinition().getServiceSpec()).getConnectionStrings() != null) {
      return Collections.singletonList(
          ((WebAppSpec) serviceV2Config.getServiceDefinition().getServiceSpec()).getConnectionStrings());
    }
    return Collections.emptyList();
  }

  private List<ApplicationSettingsConfiguration> getSvcAppSettings(NGServiceV2InfoConfig serviceV2Config) {
    if (hasWebAppSpec(serviceV2Config)
        && ((WebAppSpec) serviceV2Config.getServiceDefinition().getServiceSpec()).getApplicationSettings() != null) {
      return Collections.singletonList(
          ((WebAppSpec) serviceV2Config.getServiceDefinition().getServiceSpec()).getApplicationSettings());
    }
    return emptyList();
  }

  private boolean hasWebAppSpec(NGServiceV2InfoConfig serviceV2Config) {
    return serviceV2Config != null && serviceV2Config.getServiceDefinition() != null
        && WebAppSpec.class.isAssignableFrom(serviceV2Config.getServiceDefinition().getServiceSpec().getClass());
  }

  private List<ConnectionStringsConfiguration> getSvcOverrideConnectionStrings(
      NGServiceOverrideConfig serviceOverrideConfig) {
    if (serviceOverrideConfig == null || serviceOverrideConfig.getServiceOverrideInfoConfig() == null
        || serviceOverrideConfig.getServiceOverrideInfoConfig().getConnectionStrings() == null) {
      return emptyList();
    }
    return Collections.singletonList(serviceOverrideConfig.getServiceOverrideInfoConfig().getConnectionStrings());
  }

  private List<ApplicationSettingsConfiguration> getSvcOverrideAppSettings(
      NGServiceOverrideConfig serviceOverrideConfig) {
    if (serviceOverrideConfig == null || serviceOverrideConfig.getServiceOverrideInfoConfig() == null
        || serviceOverrideConfig.getServiceOverrideInfoConfig().getApplicationSettings() == null) {
      return emptyList();
    }
    return Collections.singletonList(serviceOverrideConfig.getServiceOverrideInfoConfig().getApplicationSettings());
  }

  @VisibleForTesting
  public static List<ConfigFileWrapper> prepareFinalConfigFiles(NGServiceV2InfoConfig serviceV2Config,
      NGServiceOverrideConfig serviceOverrideConfig, NGEnvironmentGlobalOverride environmentGlobalOverride,
      Map<String, String> configFileLocation) {
    final Map<String, ConfigFileWrapper> svcConfigFiles = getSvcConfigFiles(serviceV2Config);
    final Map<String, ConfigFileWrapper> envGlobalConfigFiles =
        getEnvironmentGlobalConfigFiles(environmentGlobalOverride);
    final Map<String, ConfigFileWrapper> svcOverrideConfigFiles = getSvcOverrideConfigFiles(serviceOverrideConfig);

    Map<String, ConfigFileWrapper> finalConfigFilesMap = new HashMap<>();
    createConfigFileMap(configFileLocation, svcConfigFiles, finalConfigFilesMap, "Service");
    createConfigFileMap(configFileLocation, envGlobalConfigFiles, finalConfigFilesMap, ENVIRONMENT);
    createConfigFileMap(configFileLocation, svcOverrideConfigFiles, finalConfigFilesMap, OVERRIDES_COMMAND_UNIT);
    return new ArrayList<>(finalConfigFilesMap.values());
  }

  private static Map<String, ConfigFileWrapper> getSvcConfigFiles(NGServiceV2InfoConfig serviceV2Config) {
    if (isNotEmpty(serviceV2Config.getServiceDefinition().getServiceSpec().getConfigFiles())) {
      return serviceV2Config.getServiceDefinition().getServiceSpec().getConfigFiles().stream().collect(Collectors.toMap(
          configFileWrapper -> configFileWrapper.getConfigFile().getIdentifier(), Function.identity()));
    }
    return emptyMap();
  }

  private static Map<String, ServiceHookWrapper> getServiceHooks(ServiceSpec serviceSpec) {
    if (serviceSpec instanceof KubernetesServiceSpec) {
      if (isNotEmpty(((KubernetesServiceSpec) serviceSpec).getHooks())) {
        return ((KubernetesServiceSpec) serviceSpec)
            .getHooks()
            .stream()
            .filter(f -> f.getHook() != null)
            .collect(Collectors.toMap(serviceHookWrapper -> {
              String identifier;
              ServiceHook serviceHook = serviceHookWrapper.getHook();
              identifier = serviceHook.getIdentifier();
              return identifier;
            }, Function.identity()));
      }
    } else if (serviceSpec instanceof NativeHelmServiceSpec) {
      if (isNotEmpty(((NativeHelmServiceSpec) serviceSpec).getHooks())) {
        return ((NativeHelmServiceSpec) serviceSpec)
            .getHooks()
            .stream()
            .filter(f -> f.getHook() != null)
            .collect(Collectors.toMap(serviceHookWrapper -> {
              String identifier;
              ServiceHook serviceHook = serviceHookWrapper.getHook();
              identifier = serviceHook.getIdentifier();
              return identifier;
            }, Function.identity()));
      }
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
      NGEnvironmentGlobalOverride environmentGlobalOverride) {
    if (isNoEnvGlobalConfigFileOverridePresent(environmentGlobalOverride)) {
      return emptyMap();
    }
    return environmentGlobalOverride.getConfigFiles().stream().collect(
        Collectors.toMap(configFileWrapper -> configFileWrapper.getConfigFile().getIdentifier(), Function.identity()));
  }

  private static boolean isNoEnvGlobalConfigFileOverridePresent(NGEnvironmentGlobalOverride environmentGlobalOverride) {
    return environmentGlobalOverride == null || environmentGlobalOverride.getConfigFiles() == null;
  }

  private boolean isNoEnvGlobalAppSettingsOverridePresent(NGEnvironmentConfig environmentConfig) {
    return environmentConfig == null || environmentConfig.getNgEnvironmentInfoConfig() == null
        || environmentConfig.getNgEnvironmentInfoConfig().getNgEnvironmentGlobalOverride() == null
        || environmentConfig.getNgEnvironmentInfoConfig().getNgEnvironmentGlobalOverride().getApplicationSettings()
        == null;
  }

  private boolean isNoEnvGlobalConnectionStringsOverridePresent(NGEnvironmentConfig environmentConfig) {
    return environmentConfig == null || environmentConfig.getNgEnvironmentInfoConfig() == null
        || environmentConfig.getNgEnvironmentInfoConfig().getNgEnvironmentGlobalOverride() == null
        || environmentConfig.getNgEnvironmentInfoConfig().getNgEnvironmentGlobalOverride().getConnectionStrings()
        == null;
  }
}
