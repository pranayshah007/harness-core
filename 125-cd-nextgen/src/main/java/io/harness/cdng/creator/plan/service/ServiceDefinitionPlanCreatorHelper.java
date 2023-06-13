/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.service;

import static io.harness.cdng.creator.plan.manifest.ManifestsPlanCreator.SERVICE_ENTITY_DEFINITION_TYPE_KEY;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.cdng.artifact.bean.yaml.ArtifactListConfig;
import io.harness.cdng.azure.config.yaml.ApplicationSettingsConfiguration;
import io.harness.cdng.azure.config.yaml.ConnectionStringsConfiguration;
import io.harness.cdng.azure.config.yaml.StartupCommandConfiguration;
import io.harness.cdng.azure.webapp.ApplicationSettingsParameters;
import io.harness.cdng.azure.webapp.ConnectionStringsParameters;
import io.harness.cdng.azure.webapp.StartupCommandParameters;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.cdng.creator.plan.PlanCreatorConstants;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.service.beans.AzureWebAppServiceSpec;
import io.harness.cdng.service.beans.ServiceConfig;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.steps.ServiceStepOverrideHelper;
import io.harness.cdng.utilities.ArtifactsUtility;
import io.harness.cdng.utilities.AzureConfigsUtility;
import io.harness.cdng.utilities.ManifestsUtility;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.UUIDGenerator;
import io.harness.ng.core.environment.beans.NGEnvironmentGlobalOverride;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideConfig;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.YamlUpdates;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse.PlanCreationResponseBuilder;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.KryoSerializer;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Contains method useful for serviceDefinition plan creator
 */
@UtilityClass
public class ServiceDefinitionPlanCreatorHelper {
  public Map<String, ByteString> prepareMetadata(
      String planNodeId, ServiceConfig actualServiceConfig, KryoSerializer kryoSerializer) {
    Map<String, ByteString> metadataDependency = new HashMap<>();
    metadataDependency.put(YamlTypes.UUID, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(planNodeId)));
    // TODO: Find an efficient way to not pass whole service config
    metadataDependency.put(
        YamlTypes.SERVICE_CONFIG, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(actualServiceConfig)));
    return metadataDependency;
  }

  public Map<String, ByteString> prepareMetadataManifestV2(String planNodeId,
      List<ManifestConfigWrapper> finalManifests, ServiceDefinitionType serviceDefinitionType,
      KryoSerializer kryoSerializer) {
    Map<String, ByteString> metadataDependency = new HashMap<>();
    metadataDependency.put(YamlTypes.UUID, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(planNodeId)));
    metadataDependency.put(
        YamlTypes.MANIFEST_LIST_CONFIG, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(finalManifests)));
    metadataDependency.put(
        SERVICE_ENTITY_DEFINITION_TYPE_KEY, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(serviceDefinitionType)));
    return metadataDependency;
  }

  public Map<String, ByteString> prepareMetadataConfigFilesV2(
      String planNodeId, List<ConfigFileWrapper> finalConfigFiles, KryoSerializer kryoSerializer) {
    Map<String, ByteString> metadataDependency = new HashMap<>();
    metadataDependency.put(YamlTypes.UUID, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(planNodeId)));
    metadataDependency.put(
        YamlTypes.CONFIG_FILES, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(finalConfigFiles)));
    return metadataDependency;
  }

  public Map<String, ByteString> prepareMetadataV2(
      String planNodeId, NGServiceV2InfoConfig serviceV2Config, KryoSerializer kryoSerializer) {
    Map<String, ByteString> metadataDependency = new HashMap<>();
    metadataDependency.put(YamlTypes.UUID, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(planNodeId)));
    // TODO: Find an efficient way to not pass whole service entity
    metadataDependency.put(
        YamlTypes.SERVICE_ENTITY, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(serviceV2Config)));
    return metadataDependency;
  }

  boolean validateCreatePlanNodeForArtifacts(ServiceConfig actualServiceConfig) {
    ArtifactListConfig artifactListConfig = actualServiceConfig.getServiceDefinition().getServiceSpec().getArtifacts();

    // Contains either primary artifacts or side-car artifacts
    if (artifactListConfig != null) {
      if (artifactListConfig.getPrimary() != null || isNotEmpty(artifactListConfig.getSidecars())) {
        return true;
      }
    }

    if (actualServiceConfig.getStageOverrides() != null
        && actualServiceConfig.getStageOverrides().getArtifacts() != null) {
      return actualServiceConfig.getStageOverrides().getArtifacts().getPrimary() != null
          || isNotEmpty(actualServiceConfig.getStageOverrides().getArtifacts().getSidecars());
    }

    return false;
  }

  String addDependenciesForArtifacts(YamlNode serviceConfigNode,
      Map<String, PlanCreationResponse> planCreationResponseMap, ServiceConfig actualServiceConfig,
      KryoSerializer kryoSerializer) {
    YamlUpdates.Builder yamlUpdates = YamlUpdates.newBuilder();
    boolean isUseFromStage = actualServiceConfig.getUseFromStage() != null;
    YamlField artifactYamlField =
        ArtifactsUtility.fetchArtifactYamlFieldAndSetYamlUpdates(serviceConfigNode, isUseFromStage, yamlUpdates);
    String artifactsPlanNodeId = UUIDGenerator.generateUuid();

    Map<String, ByteString> metadataDependency =
        prepareMetadata(artifactsPlanNodeId, actualServiceConfig, kryoSerializer);

    Map<String, YamlField> dependenciesMap = new HashMap<>();
    dependenciesMap.put(artifactsPlanNodeId, artifactYamlField);
    PlanCreationResponseBuilder artifactPlanCreationResponse = PlanCreationResponse.builder().dependencies(
        DependenciesUtils.toDependenciesProto(dependenciesMap)
            .toBuilder()
            .putDependencyMetadata(
                artifactsPlanNodeId, Dependency.newBuilder().putAllMetadata(metadataDependency).build())
            .build());
    if (yamlUpdates.getFqnToYamlCount() > 0) {
      artifactPlanCreationResponse.yamlUpdates(yamlUpdates.build());
    }
    planCreationResponseMap.put(artifactsPlanNodeId, artifactPlanCreationResponse.build());
    return artifactsPlanNodeId;
  }

  String addDependenciesForArtifactsV2(YamlNode serviceV2Node,
      Map<String, PlanCreationResponse> planCreationResponseMap, NGServiceV2InfoConfig serviceV2Config,
      KryoSerializer kryoSerializer) {
    YamlUpdates.Builder yamlUpdates = YamlUpdates.newBuilder();
    YamlField artifactYamlField =
        ArtifactsUtility.fetchArtifactYamlFieldAndSetYamlUpdates(serviceV2Node, false, yamlUpdates);
    String artifactsPlanNodeId = UUIDGenerator.generateUuid();

    Map<String, ByteString> metadataDependency =
        prepareMetadataV2(artifactsPlanNodeId, serviceV2Config, kryoSerializer);

    Map<String, YamlField> dependenciesMap = new HashMap<>();
    dependenciesMap.put(artifactsPlanNodeId, artifactYamlField);
    PlanCreationResponseBuilder artifactPlanCreationResponse = PlanCreationResponse.builder().dependencies(
        DependenciesUtils.toDependenciesProto(dependenciesMap)
            .toBuilder()
            .putDependencyMetadata(
                artifactsPlanNodeId, Dependency.newBuilder().putAllMetadata(metadataDependency).build())
            .build());
    if (yamlUpdates.getFqnToYamlCount() > 0) {
      artifactPlanCreationResponse.yamlUpdates(yamlUpdates.build());
    }
    planCreationResponseMap.put(artifactsPlanNodeId, artifactPlanCreationResponse.build());
    return artifactsPlanNodeId;
  }

  String addDependenciesForManifests(YamlNode serviceConfigNode,
      Map<String, PlanCreationResponse> planCreationResponseMap, ServiceConfig actualServiceConfig,
      KryoSerializer kryoSerializer) {
    YamlUpdates.Builder yamlUpdates = YamlUpdates.newBuilder();
    boolean isUseFromStage = actualServiceConfig.getUseFromStage() != null;
    YamlField manifestsYamlField =
        ManifestsUtility.fetchManifestsYamlFieldAndSetYamlUpdates(serviceConfigNode, isUseFromStage, yamlUpdates);
    String manifestsPlanNodeId = "manifests-" + UUIDGenerator.generateUuid();

    Map<String, ByteString> metadataDependency =
        prepareMetadata(manifestsPlanNodeId, actualServiceConfig, kryoSerializer);

    Map<String, YamlField> dependenciesMap = new HashMap<>();
    dependenciesMap.put(manifestsPlanNodeId, manifestsYamlField);
    PlanCreationResponseBuilder manifestsPlanCreationResponse = PlanCreationResponse.builder().dependencies(
        DependenciesUtils.toDependenciesProto(dependenciesMap)
            .toBuilder()
            .putDependencyMetadata(
                manifestsPlanNodeId, Dependency.newBuilder().putAllMetadata(metadataDependency).build())
            .build());
    if (yamlUpdates.getFqnToYamlCount() > 0) {
      manifestsPlanCreationResponse.yamlUpdates(yamlUpdates.build());
    }
    planCreationResponseMap.put(manifestsPlanNodeId, manifestsPlanCreationResponse.build());
    return manifestsPlanNodeId;
  }

  String addDependenciesForServiceManifestsV2(YamlNode serviceV2Node,
      Map<String, PlanCreationResponse> planCreationResponseMap, NGServiceV2InfoConfig serviceV2Config,
      KryoSerializer kryoSerializer) {
    YamlUpdates.Builder yamlUpdates = YamlUpdates.newBuilder();
    YamlField manifestsYamlField =
        ManifestsUtility.fetchManifestsYamlFieldAndSetYamlUpdates(serviceV2Node, false, yamlUpdates);
    String manifestsPlanNodeId = "manifests-" + UUIDGenerator.generateUuid();

    Map<String, ByteString> metadataDependency =
        prepareMetadataV2(manifestsPlanNodeId, serviceV2Config, kryoSerializer);

    PlanCreationResponseBuilder manifestsPlanCreationResponse =
        preparePlanCreationResponseBuilder(manifestsYamlField, manifestsPlanNodeId, metadataDependency);
    if (yamlUpdates.getFqnToYamlCount() > 0) {
      manifestsPlanCreationResponse.yamlUpdates(yamlUpdates.build());
    }
    planCreationResponseMap.put(manifestsPlanNodeId, manifestsPlanCreationResponse.build());
    return manifestsPlanNodeId;
  }

  @VisibleForTesting
  String addDependenciesForManifestV2(YamlNode serviceV2Node, Map<String, PlanCreationResponse> planCreationResponseMap,
      NGServiceV2InfoConfig serviceV2Config, NGServiceOverrideConfig serviceOverrideConfig,
      NGEnvironmentGlobalOverride envGlobalOverride, KryoSerializer kryoSerializer, String envId) throws IOException {
    if (isSvcOverridesManifestPresent(serviceOverrideConfig)
        || isEnvGlobalManifestOverridesPresent(envGlobalOverride)) {
      return ServiceDefinitionPlanCreatorHelper.addDependenciesForSvcAndSvcOverrideManifestsV2(serviceV2Node,
          planCreationResponseMap, serviceV2Config, serviceOverrideConfig, envGlobalOverride, kryoSerializer, envId);
    } else if (ServiceDefinitionPlanCreatorHelper.shouldCreatePlanNodeForManifestsV2(serviceV2Config)) {
      return ServiceDefinitionPlanCreatorHelper.addDependenciesForServiceManifestsV2(
          serviceV2Node, planCreationResponseMap, serviceV2Config, kryoSerializer);
    } else {
      return StringUtils.EMPTY;
    }
  }

  String addDependenciesForSvcAndSvcOverrideManifestsV2(YamlNode serviceV2Node,
      Map<String, PlanCreationResponse> planCreationResponseMap, NGServiceV2InfoConfig serviceV2Config,
      NGServiceOverrideConfig serviceOverrideConfig, NGEnvironmentGlobalOverride ngEnvironmentConfig,
      KryoSerializer kryoSerializer, String envId) throws IOException {
    YamlUpdates.Builder yamlUpdates = YamlUpdates.newBuilder();

    List<ManifestConfigWrapper> finalManifests = ServiceStepOverrideHelper.prepareFinalManifests(
        serviceV2Config, serviceOverrideConfig, ngEnvironmentConfig, envId);

    // in case no manifest is present no node should be created
    if (isEmpty(finalManifests)) {
      return StringUtils.EMPTY;
    }

    YamlField manifestsYamlField = prepareFinalUuidInjectedManifestYamlField(serviceV2Node, finalManifests);
    PlanCreatorUtils.setYamlUpdate(manifestsYamlField, yamlUpdates);
    String manifestsPlanNodeId = "manifests-" + UUIDGenerator.generateUuid();

    Map<String, ByteString> metadataDependency = prepareMetadataManifestV2(
        manifestsPlanNodeId, finalManifests, serviceV2Config.getServiceDefinition().getType(), kryoSerializer);
    PlanCreationResponseBuilder manifestsPlanCreationResponse =
        preparePlanCreationResponseBuilder(manifestsYamlField, manifestsPlanNodeId, metadataDependency);
    if (yamlUpdates.getFqnToYamlCount() > 0) {
      manifestsPlanCreationResponse.yamlUpdates(yamlUpdates.build());
    }
    planCreationResponseMap.put(manifestsPlanNodeId, manifestsPlanCreationResponse.build());
    return manifestsPlanNodeId;
  }

  private PlanCreationResponseBuilder preparePlanCreationResponseBuilder(
      YamlField manifestsYamlField, String manifestsPlanNodeId, Map<String, ByteString> metadataDependency) {
    Map<String, YamlField> dependenciesMap = new HashMap<>();
    dependenciesMap.put(manifestsPlanNodeId, manifestsYamlField);
    return PlanCreationResponse.builder().dependencies(
        DependenciesUtils.toDependenciesProto(dependenciesMap)
            .toBuilder()
            .putDependencyMetadata(
                manifestsPlanNodeId, Dependency.newBuilder().putAllMetadata(metadataDependency).build())
            .build());
  }

  @NotNull
  private YamlField prepareFinalUuidInjectedManifestYamlField(
      YamlNode serviceV2Node, List<ManifestConfigWrapper> finalManifests) throws IOException {
    YamlField manifestsYamlField = YamlUtils.injectUuidInYamlField(YamlUtils.writeYamlString(finalManifests));
    manifestsYamlField = new YamlField(YamlTypes.MANIFEST_LIST_CONFIG,
        new YamlNode(YamlTypes.MANIFEST_LIST_CONFIG, manifestsYamlField.getNode().getCurrJsonNode(),
            serviceV2Node.getField(YamlTypes.SERVICE_DEFINITION).getNode().getField(YamlTypes.SPEC).getNode()));
    return manifestsYamlField;
  }

  String addDependenciesForStartupCommand(YamlNode serviceConfigNode,
      Map<String, PlanCreationResponse> planCreationResponseMap, ServiceConfig serviceConfig,
      KryoSerializer kryoSerializer) {
    YamlUpdates.Builder yamlUpdates = YamlUpdates.newBuilder();
    boolean isUseFromStage = serviceConfig.getUseFromStage() != null;
    YamlField startupCommandYamlField = AzureConfigsUtility.fetchAzureConfigYamlFieldAndSetYamlUpdates(
        serviceConfigNode, isUseFromStage, yamlUpdates, YamlTypes.STARTUP_COMMAND);
    String startupCommandPlanNodeId = "startupCommnad-" + UUIDGenerator.generateUuid();

    StartupCommandConfiguration startupCommand =
        ((AzureWebAppServiceSpec) serviceConfig.getServiceDefinition().getServiceSpec()).getStartupCommand();
    Map<String, YamlField> dependenciesMap = new HashMap<>();
    dependenciesMap.put(startupCommandPlanNodeId, startupCommandYamlField);
    PlanCreationResponseBuilder startupCommandPlanCreationResponse = PlanCreationResponse.builder().dependencies(
        DependenciesUtils.toDependenciesProto(dependenciesMap)
            .toBuilder()
            .putDependencyMetadata(startupCommandPlanNodeId,
                AzureConfigsUtility.getDependencyMetadata(startupCommandPlanNodeId,
                    StartupCommandParameters.builder().startupCommand(startupCommand).build(), kryoSerializer,
                    PlanCreatorConstants.STARTUP_COMMAND_STEP_PARAMETER))
            .build());
    if (yamlUpdates.getFqnToYamlCount() > 0) {
      startupCommandPlanCreationResponse.yamlUpdates(yamlUpdates.build());
    }
    planCreationResponseMap.put(startupCommandPlanNodeId, startupCommandPlanCreationResponse.build());
    return startupCommandPlanNodeId;
  }

  String addDependenciesForApplicationSettings(YamlNode serviceConfigNode,
      Map<String, PlanCreationResponse> planCreationResponseMap, ServiceConfig serviceConfig,
      KryoSerializer kryoSerializer) {
    YamlUpdates.Builder yamlUpdates = YamlUpdates.newBuilder();
    boolean isUseFromStage = serviceConfig.getUseFromStage() != null;
    YamlField applicationSettingsYamlField = AzureConfigsUtility.fetchAzureConfigYamlFieldAndSetYamlUpdates(
        serviceConfigNode, isUseFromStage, yamlUpdates, YamlTypes.APPLICATION_SETTINGS);
    String applicationSettingsYamlFieldPlanNodeId = "applicationSettings-" + UUIDGenerator.generateUuid();

    ApplicationSettingsConfiguration applicationSettings =
        ((AzureWebAppServiceSpec) serviceConfig.getServiceDefinition().getServiceSpec()).getApplicationSettings();
    Map<String, YamlField> dependenciesMap = new HashMap<>();
    dependenciesMap.put(applicationSettingsYamlFieldPlanNodeId, applicationSettingsYamlField);
    PlanCreationResponseBuilder applicationSettingsPlanCreationResponse = PlanCreationResponse.builder().dependencies(
        DependenciesUtils.toDependenciesProto(dependenciesMap)
            .toBuilder()
            .putDependencyMetadata(applicationSettingsYamlFieldPlanNodeId,
                AzureConfigsUtility.getDependencyMetadata(applicationSettingsYamlFieldPlanNodeId,
                    ApplicationSettingsParameters.builder().applicationSettings(applicationSettings).build(),
                    kryoSerializer, PlanCreatorConstants.APPLICATION_SETTINGS_STEP_PARAMETER))
            .build());
    if (yamlUpdates.getFqnToYamlCount() > 0) {
      applicationSettingsPlanCreationResponse.yamlUpdates(yamlUpdates.build());
    }
    planCreationResponseMap.put(
        applicationSettingsYamlFieldPlanNodeId, applicationSettingsPlanCreationResponse.build());
    return applicationSettingsYamlFieldPlanNodeId;
  }

  String addDependenciesForConnectionStrings(YamlNode serviceConfigNode,
      Map<String, PlanCreationResponse> planCreationResponseMap, ServiceConfig serviceConfig,
      KryoSerializer kryoSerializer) {
    YamlUpdates.Builder yamlUpdates = YamlUpdates.newBuilder();
    boolean isUseFromStage = serviceConfig.getUseFromStage() != null;
    YamlField connectionStringsYamlField = AzureConfigsUtility.fetchAzureConfigYamlFieldAndSetYamlUpdates(
        serviceConfigNode, isUseFromStage, yamlUpdates, YamlTypes.CONNECTION_STRINGS);
    String connectionStringsYamlFieldPlanNodeId = "connectionStrings-" + UUIDGenerator.generateUuid();

    ConnectionStringsConfiguration connectionStrings =
        ((AzureWebAppServiceSpec) serviceConfig.getServiceDefinition().getServiceSpec()).getConnectionStrings();
    Map<String, YamlField> dependenciesMap = new HashMap<>();
    dependenciesMap.put(connectionStringsYamlFieldPlanNodeId, connectionStringsYamlField);
    PlanCreationResponseBuilder connectionStringsPlanCreationResponse = PlanCreationResponse.builder().dependencies(
        DependenciesUtils.toDependenciesProto(dependenciesMap)
            .toBuilder()
            .putDependencyMetadata(connectionStringsYamlFieldPlanNodeId,
                AzureConfigsUtility.getDependencyMetadata(connectionStringsYamlFieldPlanNodeId,
                    ConnectionStringsParameters.builder().connectionStrings(connectionStrings).build(), kryoSerializer,
                    PlanCreatorConstants.CONNECTION_STRINGS_STEP_PARAMETER))
            .build());
    if (yamlUpdates.getFqnToYamlCount() > 0) {
      connectionStringsPlanCreationResponse.yamlUpdates(yamlUpdates.build());
    }
    planCreationResponseMap.put(connectionStringsYamlFieldPlanNodeId, connectionStringsPlanCreationResponse.build());
    return connectionStringsYamlFieldPlanNodeId;
  }

  String addDependenciesForStartupCommandV2(YamlNode serviceV2Node,
      Map<String, PlanCreationResponse> planCreationResponseMap, NGServiceV2InfoConfig serviceV2Config,
      KryoSerializer kryoSerializer) {
    YamlUpdates.Builder yamlUpdates = YamlUpdates.newBuilder();
    YamlField startupCommandYamlField = AzureConfigsUtility.fetchAzureConfigYamlFieldAndSetYamlUpdates(
        serviceV2Node, false, yamlUpdates, YamlTypes.STARTUP_COMMAND);
    String startupCommandPlanNodeId = "startupCommand-" + UUIDGenerator.generateUuid();

    StartupCommandConfiguration startupCommand =
        ((AzureWebAppServiceSpec) serviceV2Config.getServiceDefinition().getServiceSpec()).getStartupCommand();

    Map<String, YamlField> dependenciesMap = new HashMap<>();
    dependenciesMap.put(startupCommandPlanNodeId, startupCommandYamlField);
    PlanCreationResponseBuilder startupCommandPlanCreationResponse = PlanCreationResponse.builder().dependencies(
        DependenciesUtils.toDependenciesProto(dependenciesMap)
            .toBuilder()
            .putDependencyMetadata(startupCommandPlanNodeId,
                AzureConfigsUtility.getDependencyMetadata(startupCommandPlanNodeId,
                    StartupCommandParameters.builder().startupCommand(startupCommand).build(), kryoSerializer,
                    PlanCreatorConstants.STARTUP_COMMAND_STEP_PARAMETER))
            .build());
    if (yamlUpdates.getFqnToYamlCount() > 0) {
      startupCommandPlanCreationResponse.yamlUpdates(yamlUpdates.build());
    }
    planCreationResponseMap.put(startupCommandPlanNodeId, startupCommandPlanCreationResponse.build());
    return startupCommandPlanNodeId;
  }

  String addDependenciesForApplicationSettingsV2(YamlNode serviceV2Node,
      Map<String, PlanCreationResponse> planCreationResponseMap, NGServiceV2InfoConfig serviceV2Config,
      NGServiceOverrideConfig serviceOverrideConfig, NGEnvironmentGlobalOverride environmentGlobalOverride,
      KryoSerializer kryoSerializer) throws IOException {
    if (isSvcOverridesApplicationSettingsPresent(serviceOverrideConfig)
        || isEnvGlobalApplicationSettingsOverridesPresent(environmentGlobalOverride)) {
      return ServiceDefinitionPlanCreatorHelper.addDependenciesForSvcAndSvcOverrideApplicationSettingsV2(
          serviceV2Node, planCreationResponseMap, serviceOverrideConfig, environmentGlobalOverride, kryoSerializer);
    } else if (ServiceDefinitionPlanCreatorHelper.shouldCreatePlanNodeForApplicationSettingsV2(serviceV2Config)) {
      return ServiceDefinitionPlanCreatorHelper.addDependenciesForServiceApplicationSettingsV2(
          serviceV2Node, planCreationResponseMap, serviceV2Config, kryoSerializer);
    } else {
      return StringUtils.EMPTY;
    }
  }

  private String addDependenciesForSvcAndSvcOverrideApplicationSettingsV2(YamlNode serviceV2Node,
      Map<String, PlanCreationResponse> planCreationResponseMap, NGServiceOverrideConfig serviceOverrideConfig,
      NGEnvironmentGlobalOverride ngEnvironmentConfig, KryoSerializer kryoSerializer) throws IOException {
    YamlUpdates.Builder yamlUpdates = YamlUpdates.newBuilder();

    ApplicationSettingsConfiguration finalAppSettingsConfig =
        getFinalApplicationSettingsConfiguration(serviceOverrideConfig, ngEnvironmentConfig);
    if (finalAppSettingsConfig == null) {
      return StringUtils.EMPTY;
    }

    YamlField appSettingsConfigFilesYamlField =
        prepareFinalUuidInjectedApplicationSettingYamlField(serviceV2Node, finalAppSettingsConfig);
    PlanCreatorUtils.setYamlUpdate(appSettingsConfigFilesYamlField, yamlUpdates);

    String applicationSettingsPlanNodeId = "applicationSettings-" + UUIDGenerator.generateUuid();
    PlanCreationResponseBuilder applicationSettingsPlanCreationResponse =
        PlanCreationResponse.builder().dependencies(getApplicationSettingDependencies(
            applicationSettingsPlanNodeId, finalAppSettingsConfig, kryoSerializer, appSettingsConfigFilesYamlField));

    if (yamlUpdates.getFqnToYamlCount() > 0) {
      applicationSettingsPlanCreationResponse.yamlUpdates(yamlUpdates.build());
    }
    planCreationResponseMap.put(applicationSettingsPlanNodeId, applicationSettingsPlanCreationResponse.build());
    return applicationSettingsPlanNodeId;
  }

  private String addDependenciesForServiceApplicationSettingsV2(YamlNode serviceV2Node,
      Map<String, PlanCreationResponse> planCreationResponseMap, NGServiceV2InfoConfig serviceV2Config,
      KryoSerializer kryoSerializer) {
    YamlUpdates.Builder yamlUpdates = YamlUpdates.newBuilder();
    YamlField applicationSettingsYamlField = AzureConfigsUtility.fetchAzureConfigYamlFieldAndSetYamlUpdates(
        serviceV2Node, false, yamlUpdates, YamlTypes.APPLICATION_SETTINGS);
    String applicationSettingsPlanNodeId = "applicationSettings-" + UUIDGenerator.generateUuid();

    ApplicationSettingsConfiguration applicationSettings =
        ((AzureWebAppServiceSpec) serviceV2Config.getServiceDefinition().getServiceSpec()).getApplicationSettings();

    PlanCreationResponseBuilder applicationSettingsPlanCreationResponse =
        PlanCreationResponse.builder().dependencies(getApplicationSettingDependencies(
            applicationSettingsPlanNodeId, applicationSettings, kryoSerializer, applicationSettingsYamlField));
    if (yamlUpdates.getFqnToYamlCount() > 0) {
      applicationSettingsPlanCreationResponse.yamlUpdates(yamlUpdates.build());
    }
    planCreationResponseMap.put(applicationSettingsPlanNodeId, applicationSettingsPlanCreationResponse.build());
    return applicationSettingsPlanNodeId;
  }

  private Dependencies getApplicationSettingDependencies(String applicationSettingsPlanNodeId,
      ApplicationSettingsConfiguration appSettingsConfig, KryoSerializer kryoSerializer,
      YamlField appSettingsConfigFilesYamlField) {
    Map<String, YamlField> dependenciesMap = new HashMap<>();
    dependenciesMap.put(applicationSettingsPlanNodeId, appSettingsConfigFilesYamlField);

    return DependenciesUtils.toDependenciesProto(dependenciesMap)
        .toBuilder()
        .putDependencyMetadata(applicationSettingsPlanNodeId,
            AzureConfigsUtility.getDependencyMetadata(applicationSettingsPlanNodeId,
                ApplicationSettingsParameters.builder().applicationSettings(appSettingsConfig).build(), kryoSerializer,
                PlanCreatorConstants.APPLICATION_SETTINGS_STEP_PARAMETER))
        .build();
  }

  ApplicationSettingsConfiguration getFinalApplicationSettingsConfiguration(
      NGServiceOverrideConfig serviceOverrideConfig, NGEnvironmentGlobalOverride environmentGlobalOverride) {
    if (isSvcOverridesApplicationSettingsPresent(serviceOverrideConfig)) {
      return serviceOverrideConfig.getServiceOverrideInfoConfig().getApplicationSettings();
    } else if (isEnvGlobalApplicationSettingsOverridesPresent(environmentGlobalOverride)) {
      return environmentGlobalOverride.getApplicationSettings();
    } else {
      return null;
    }
  }

  private YamlField prepareFinalUuidInjectedApplicationSettingYamlField(
      YamlNode serviceV2Node, ApplicationSettingsConfiguration appSettingsConfig) throws IOException {
    YamlField appSettingsYamlField = YamlUtils.injectUuidInYamlField(YamlUtils.writeYamlString(appSettingsConfig));
    appSettingsYamlField = new YamlField(YamlTypes.APPLICATION_SETTINGS,
        new YamlNode(YamlTypes.APPLICATION_SETTINGS, appSettingsYamlField.getNode().getCurrJsonNode(),
            serviceV2Node.getField(YamlTypes.SERVICE_DEFINITION).getNode().getField(YamlTypes.SPEC).getNode()));
    return appSettingsYamlField;
  }

  String addDependenciesForConnectionStringsV2(YamlNode serviceV2Node,
      Map<String, PlanCreationResponse> planCreationResponseMap, NGServiceV2InfoConfig serviceV2Config,
      NGServiceOverrideConfig serviceOverrideConfig, NGEnvironmentGlobalOverride environmentGlobalOverride,
      KryoSerializer kryoSerializer) throws IOException {
    if (isSvcOverridesConnectionStringsPresent(serviceOverrideConfig)
        || isEnvGlobalConnectionStringsOverridesPresent(environmentGlobalOverride)) {
      return ServiceDefinitionPlanCreatorHelper.addDependenciesForSvcAndSvcOverrideConnectionStringsV2(
          serviceV2Node, planCreationResponseMap, serviceOverrideConfig, environmentGlobalOverride, kryoSerializer);
    } else if (ServiceDefinitionPlanCreatorHelper.shouldCreatePlanNodeForConnectionStringsV2(serviceV2Config)) {
      return ServiceDefinitionPlanCreatorHelper.addDependenciesForConnectionStringsV2(
          serviceV2Node, planCreationResponseMap, serviceV2Config, kryoSerializer);
    } else {
      return StringUtils.EMPTY;
    }
  }

  private String addDependenciesForSvcAndSvcOverrideConnectionStringsV2(YamlNode serviceV2Node,
      Map<String, PlanCreationResponse> planCreationResponseMap, NGServiceOverrideConfig serviceOverrideConfig,
      NGEnvironmentGlobalOverride environmentGlobalOverride, KryoSerializer kryoSerializer) throws IOException {
    YamlUpdates.Builder yamlUpdates = YamlUpdates.newBuilder();

    ConnectionStringsConfiguration finalConnectionStringsConfig =
        getFinalConnectionStringsConfiguration(serviceOverrideConfig, environmentGlobalOverride);
    if (finalConnectionStringsConfig == null) {
      return StringUtils.EMPTY;
    }

    YamlField connectionStringsYamlField =
        prepareFinalUuidInjectedConnectionStringYamlField(serviceV2Node, finalConnectionStringsConfig);
    PlanCreatorUtils.setYamlUpdate(connectionStringsYamlField, yamlUpdates);

    String connectionStringsPlanNodeId = "connectionStrings-" + UUIDGenerator.generateUuid();
    PlanCreationResponseBuilder connectionStringsPlanCreationResponse =
        PlanCreationResponse.builder().dependencies(getConnectionStringsDependencies(
            connectionStringsPlanNodeId, finalConnectionStringsConfig, kryoSerializer, connectionStringsYamlField));
    if (yamlUpdates.getFqnToYamlCount() > 0) {
      connectionStringsPlanCreationResponse.yamlUpdates(yamlUpdates.build());
    }
    planCreationResponseMap.put(connectionStringsPlanNodeId, connectionStringsPlanCreationResponse.build());
    return connectionStringsPlanNodeId;
  }

  private String addDependenciesForConnectionStringsV2(YamlNode serviceV2Node,
      Map<String, PlanCreationResponse> planCreationResponseMap, NGServiceV2InfoConfig serviceV2Config,
      KryoSerializer kryoSerializer) {
    YamlUpdates.Builder yamlUpdates = YamlUpdates.newBuilder();
    YamlField connectionStringsYamlField = AzureConfigsUtility.fetchAzureConfigYamlFieldAndSetYamlUpdates(
        serviceV2Node, false, yamlUpdates, YamlTypes.CONNECTION_STRINGS);
    String connectionStringsPlanNodeId = "connectionStrings-" + UUIDGenerator.generateUuid();

    ConnectionStringsConfiguration connectionStrings =
        ((AzureWebAppServiceSpec) serviceV2Config.getServiceDefinition().getServiceSpec()).getConnectionStrings();

    PlanCreationResponseBuilder connectionStringsPlanCreationResponse =
        PlanCreationResponse.builder().dependencies(getConnectionStringsDependencies(
            connectionStringsPlanNodeId, connectionStrings, kryoSerializer, connectionStringsYamlField));
    if (yamlUpdates.getFqnToYamlCount() > 0) {
      connectionStringsPlanCreationResponse.yamlUpdates(yamlUpdates.build());
    }
    planCreationResponseMap.put(connectionStringsPlanNodeId, connectionStringsPlanCreationResponse.build());
    return connectionStringsPlanNodeId;
  }

  private Dependencies getConnectionStringsDependencies(String connectionStringsPlanNodeId,
      ConnectionStringsConfiguration connectionStringsConfiguration, KryoSerializer kryoSerializer,
      YamlField appSettingsConfigFilesYamlField) {
    Map<String, YamlField> dependenciesMap = new HashMap<>();
    dependenciesMap.put(connectionStringsPlanNodeId, appSettingsConfigFilesYamlField);

    return DependenciesUtils.toDependenciesProto(dependenciesMap)
        .toBuilder()
        .putDependencyMetadata(connectionStringsPlanNodeId,
            AzureConfigsUtility.getDependencyMetadata(connectionStringsPlanNodeId,
                ConnectionStringsParameters.builder().connectionStrings(connectionStringsConfiguration).build(),
                kryoSerializer, PlanCreatorConstants.CONNECTION_STRINGS_STEP_PARAMETER))
        .build();
  }

  private ConnectionStringsConfiguration getFinalConnectionStringsConfiguration(
      NGServiceOverrideConfig serviceOverrideConfig, NGEnvironmentGlobalOverride environmentGlobalOverride) {
    if (isSvcOverridesConnectionStringsPresent(serviceOverrideConfig)) {
      return serviceOverrideConfig.getServiceOverrideInfoConfig().getConnectionStrings();
    } else if (isEnvGlobalConnectionStringsOverridesPresent(environmentGlobalOverride)) {
      return environmentGlobalOverride.getConnectionStrings();
    } else {
      return null;
    }
  }

  private YamlField prepareFinalUuidInjectedConnectionStringYamlField(
      YamlNode serviceV2Node, ConnectionStringsConfiguration finalConnectionStringsConfig) throws IOException {
    YamlField connectionStringsYamlField =
        YamlUtils.injectUuidInYamlField(YamlUtils.writeYamlString(finalConnectionStringsConfig));
    connectionStringsYamlField = new YamlField(YamlTypes.CONNECTION_STRINGS,
        new YamlNode(YamlTypes.CONNECTION_STRINGS, connectionStringsYamlField.getNode().getCurrJsonNode(),
            serviceV2Node.getField(YamlTypes.SERVICE_DEFINITION).getNode().getField(YamlTypes.SPEC).getNode()));
    return connectionStringsYamlField;
  }

  boolean shouldCreatePlanNodeForManifests(ServiceConfig actualServiceConfig) {
    List<ManifestConfigWrapper> manifests = actualServiceConfig.getServiceDefinition().getServiceSpec().getManifests();

    // Contains either manifests or overrides or nothing.
    if (isNotEmpty(manifests)) {
      return true;
    }

    return actualServiceConfig.getStageOverrides() != null
        && actualServiceConfig.getStageOverrides().getManifests() != null
        && isNotEmpty(actualServiceConfig.getStageOverrides().getManifests());
  }

  boolean shouldCreatePlanNodeForManifestsV2(NGServiceV2InfoConfig serviceV2InfoConfig) {
    List<ManifestConfigWrapper> manifests = serviceV2InfoConfig.getServiceDefinition().getServiceSpec().getManifests();

    // Contains either manifests or not.
    return isNotEmpty(manifests);
  }

  private boolean isServiceOverridePresent(NGServiceOverrideConfig serviceOverrideConfig) {
    return serviceOverrideConfig != null && serviceOverrideConfig.getServiceOverrideInfoConfig() != null;
  }

  private boolean isEnvGlobalManifestOverridesPresent(NGEnvironmentGlobalOverride environmentGlobalOverride) {
    if (environmentGlobalOverride == null) {
      return false;
    }
    return isNotEmpty(environmentGlobalOverride.getManifests());
  }

  private boolean isSvcOverridesManifestPresent(NGServiceOverrideConfig serviceOverrideConfig) {
    if (!isServiceOverridePresent(serviceOverrideConfig)) {
      return false;
    }
    return isNotEmpty(serviceOverrideConfig.getServiceOverrideInfoConfig().getManifests());
  }

  private boolean isSvcOverridesApplicationSettingsPresent(NGServiceOverrideConfig serviceOverrideConfig) {
    if (!isServiceOverridePresent(serviceOverrideConfig)) {
      return false;
    }
    return serviceOverrideConfig.getServiceOverrideInfoConfig().getApplicationSettings() != null;
  }

  private boolean isEnvGlobalApplicationSettingsOverridesPresent(
      NGEnvironmentGlobalOverride environmentGlobalOverride) {
    if (environmentGlobalOverride == null) {
      return false;
    }
    return environmentGlobalOverride.getApplicationSettings() != null;
  }

  private boolean shouldCreatePlanNodeForApplicationSettingsV2(NGServiceV2InfoConfig serviceV2InfoConfig) {
    AzureWebAppServiceSpec azureWebAppServiceSpec =
        (AzureWebAppServiceSpec) serviceV2InfoConfig.getServiceDefinition().getServiceSpec();
    ApplicationSettingsConfiguration applicationSettings = azureWebAppServiceSpec.getApplicationSettings();
    return applicationSettings != null;
  }

  private boolean isSvcOverridesConnectionStringsPresent(NGServiceOverrideConfig serviceOverrideConfig) {
    if (!isServiceOverridePresent(serviceOverrideConfig)) {
      return false;
    }
    return serviceOverrideConfig.getServiceOverrideInfoConfig().getConnectionStrings() != null;
  }

  private boolean isEnvGlobalConnectionStringsOverridesPresent(NGEnvironmentGlobalOverride environmentGlobalOverride) {
    if (environmentGlobalOverride == null) {
      return false;
    }
    return environmentGlobalOverride.getConnectionStrings() != null;
  }

  private boolean shouldCreatePlanNodeForConnectionStringsV2(NGServiceV2InfoConfig serviceV2InfoConfig) {
    AzureWebAppServiceSpec azureWebAppServiceSpec =
        (AzureWebAppServiceSpec) serviceV2InfoConfig.getServiceDefinition().getServiceSpec();
    ConnectionStringsConfiguration connectionStrings = azureWebAppServiceSpec.getConnectionStrings();
    return connectionStrings != null;
  }
}
