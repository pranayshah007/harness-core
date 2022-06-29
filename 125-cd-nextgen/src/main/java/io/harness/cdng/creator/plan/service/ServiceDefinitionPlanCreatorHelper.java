/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.service;

import io.harness.cdng.artifact.bean.yaml.ArtifactListConfig;
import io.harness.cdng.azure.webapp.ApplicationSettingsParameters;
import io.harness.cdng.azure.webapp.ConnectionStringsParameters;
import io.harness.cdng.azure.webapp.StartupScriptParameters;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.cdng.creator.plan.PlanCreatorConstants;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.service.beans.AzureWebAppServiceSpec;
import io.harness.cdng.service.beans.ServiceConfig;
import io.harness.cdng.utilities.ArtifactsUtility;
import io.harness.cdng.utilities.AzureConfigsUtility;
import io.harness.cdng.utilities.ConfigFileUtility;
import io.harness.cdng.utilities.ManifestsUtility;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.YamlUpdates;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse.PlanCreationResponseBuilder;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.serializer.KryoSerializer;

import com.google.protobuf.ByteString;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

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
      if (artifactListConfig.getPrimary() != null || EmptyPredicate.isNotEmpty(artifactListConfig.getSidecars())) {
        return true;
      }
    }

    if (actualServiceConfig.getStageOverrides() != null
        && actualServiceConfig.getStageOverrides().getArtifacts() != null) {
      return actualServiceConfig.getStageOverrides().getArtifacts().getPrimary() != null
          || EmptyPredicate.isNotEmpty(actualServiceConfig.getStageOverrides().getArtifacts().getSidecars());
    }

    return false;
  }

  boolean validateCreatePlanNodeForArtifactsV2(NGServiceV2InfoConfig serviceConfig) {
    ArtifactListConfig artifactListConfig = serviceConfig.getServiceDefinition().getServiceSpec().getArtifacts();

    // Contains either primary artifacts or side-car artifacts
    if (artifactListConfig != null) {
      return artifactListConfig.getPrimary() != null || EmptyPredicate.isNotEmpty(artifactListConfig.getSidecars());
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

  String addDependenciesForManifestsV2(YamlNode serviceV2Node,
      Map<String, PlanCreationResponse> planCreationResponseMap, NGServiceV2InfoConfig serviceV2Config,
      KryoSerializer kryoSerializer) {
    YamlUpdates.Builder yamlUpdates = YamlUpdates.newBuilder();
    YamlField manifestsYamlField =
        ManifestsUtility.fetchManifestsYamlFieldAndSetYamlUpdates(serviceV2Node, false, yamlUpdates);
    String manifestsPlanNodeId = "manifests-" + UUIDGenerator.generateUuid();

    Map<String, ByteString> metadataDependency =
        prepareMetadataV2(manifestsPlanNodeId, serviceV2Config, kryoSerializer);

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

  String addDependenciesForConfigFilesV2(YamlNode serviceV2Node,
      Map<String, PlanCreationResponse> planCreationResponseMap, NGServiceV2InfoConfig serviceV2Config,
      KryoSerializer kryoSerializer) {
    YamlUpdates.Builder yamlUpdates = YamlUpdates.newBuilder();
    YamlField configFilesYamlField =
        ConfigFileUtility.fetchConfigFilesYamlFieldAndSetYamlUpdates(serviceV2Node, false, yamlUpdates);
    String configFilesPlanNodeId = "configFiles-" + UUIDGenerator.generateUuid();

    Map<String, ByteString> metadataDependency =
        prepareMetadataV2(configFilesPlanNodeId, serviceV2Config, kryoSerializer);

    Map<String, YamlField> dependenciesMap = new HashMap<>();
    dependenciesMap.put(configFilesPlanNodeId, configFilesYamlField);

    PlanCreationResponseBuilder configFilesPlanCreationResponse = PlanCreationResponse.builder().dependencies(
        getDependencies(configFilesPlanNodeId, metadataDependency, dependenciesMap));
    if (yamlUpdates.getFqnToYamlCount() > 0) {
      configFilesPlanCreationResponse.yamlUpdates(yamlUpdates.build());
    }
    planCreationResponseMap.put(configFilesPlanNodeId, configFilesPlanCreationResponse.build());

    return configFilesPlanNodeId;
  }

  private Dependencies getDependencies(
      String planNodeId, Map<String, ByteString> metadataDependency, Map<String, YamlField> dependenciesMap) {
    return DependenciesUtils.toDependenciesProto(dependenciesMap)
        .toBuilder()
        .putDependencyMetadata(planNodeId, Dependency.newBuilder().putAllMetadata(metadataDependency).build())
        .build();
  }

  String addDependenciesForStartupScript(YamlNode serviceConfigNode,
      Map<String, PlanCreationResponse> planCreationResponseMap, ServiceConfig serviceConfig,
      KryoSerializer kryoSerializer) {
    YamlUpdates.Builder yamlUpdates = YamlUpdates.newBuilder();
    boolean isUseFromStage = serviceConfig.getUseFromStage() != null;
    YamlField startupScriptYamlField = AzureConfigsUtility.fetchAzureConfigYamlFieldAndSetYamlUpdates(
        serviceConfigNode, isUseFromStage, yamlUpdates, YamlTypes.STARTUP_SCRIPT);
    String startupScriptPlanNodeId = "startupScript-" + UUIDGenerator.generateUuid();

    StoreConfigWrapper startupScript =
        ((AzureWebAppServiceSpec) serviceConfig.getServiceDefinition().getServiceSpec()).getStartupScript();
    Map<String, YamlField> dependenciesMap = new HashMap<>();
    dependenciesMap.put(startupScriptPlanNodeId, startupScriptYamlField);
    PlanCreationResponseBuilder startupScriptPlanCreationResponse = PlanCreationResponse.builder().dependencies(
        DependenciesUtils.toDependenciesProto(dependenciesMap)
            .toBuilder()
            .putDependencyMetadata(startupScriptPlanNodeId,
                AzureConfigsUtility.getDependencyMetadata(startupScriptPlanNodeId,
                    StartupScriptParameters.builder().startupScript(startupScript).build(), kryoSerializer,
                    PlanCreatorConstants.STARTUP_SCRIPT_STEP_PARAMETER))
            .build());
    if (yamlUpdates.getFqnToYamlCount() > 0) {
      startupScriptPlanCreationResponse.yamlUpdates(yamlUpdates.build());
    }
    planCreationResponseMap.put(startupScriptPlanNodeId, startupScriptPlanCreationResponse.build());
    return startupScriptPlanNodeId;
  }

  String addDependenciesForApplicationSettings(YamlNode serviceConfigNode,
      Map<String, PlanCreationResponse> planCreationResponseMap, ServiceConfig serviceConfig,
      KryoSerializer kryoSerializer) {
    YamlUpdates.Builder yamlUpdates = YamlUpdates.newBuilder();
    boolean isUseFromStage = serviceConfig.getUseFromStage() != null;
    YamlField applicationSettingsYamlField = AzureConfigsUtility.fetchAzureConfigYamlFieldAndSetYamlUpdates(
        serviceConfigNode, isUseFromStage, yamlUpdates, YamlTypes.APPLICATION_SETTINGS);
    String applicationSettingsYamlFieldPlanNodeId = "applicationSettings-" + UUIDGenerator.generateUuid();

    StoreConfigWrapper applicationSettings =
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

    StoreConfigWrapper connectionStrings =
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

  String addDependenciesForStartupScriptV2(YamlNode serviceV2Node,
      Map<String, PlanCreationResponse> planCreationResponseMap, NGServiceV2InfoConfig serviceV2Config,
      KryoSerializer kryoSerializer) {
    YamlUpdates.Builder yamlUpdates = YamlUpdates.newBuilder();
    YamlField startupScriptYamlField = AzureConfigsUtility.fetchAzureConfigYamlFieldAndSetYamlUpdates(
        serviceV2Node, false, yamlUpdates, YamlTypes.STARTUP_SCRIPT);
    String startupScriptPlanNodeId = "startupScript-" + UUIDGenerator.generateUuid();

    StoreConfigWrapper startupScript =
        ((AzureWebAppServiceSpec) serviceV2Config.getServiceDefinition().getServiceSpec()).getStartupScript();

    Map<String, YamlField> dependenciesMap = new HashMap<>();
    dependenciesMap.put(startupScriptPlanNodeId, startupScriptYamlField);
    PlanCreationResponseBuilder startupScriptPlanCreationResponse = PlanCreationResponse.builder().dependencies(
        DependenciesUtils.toDependenciesProto(dependenciesMap)
            .toBuilder()
            .putDependencyMetadata(startupScriptPlanNodeId,
                AzureConfigsUtility.getDependencyMetadata(startupScriptPlanNodeId,
                    StartupScriptParameters.builder().startupScript(startupScript).build(), kryoSerializer,
                    PlanCreatorConstants.STARTUP_SCRIPT_STEP_PARAMETER))
            .build());
    if (yamlUpdates.getFqnToYamlCount() > 0) {
      startupScriptPlanCreationResponse.yamlUpdates(yamlUpdates.build());
    }
    planCreationResponseMap.put(startupScriptPlanNodeId, startupScriptPlanCreationResponse.build());
    return startupScriptPlanNodeId;
  }

  String addDependenciesForApplicationSettingsV2(YamlNode serviceV2Node,
      Map<String, PlanCreationResponse> planCreationResponseMap, NGServiceV2InfoConfig serviceV2Config,
      KryoSerializer kryoSerializer) {
    YamlUpdates.Builder yamlUpdates = YamlUpdates.newBuilder();
    YamlField applicationSettingsYamlField = AzureConfigsUtility.fetchAzureConfigYamlFieldAndSetYamlUpdates(
        serviceV2Node, false, yamlUpdates, YamlTypes.APPLICATION_SETTINGS);
    String applicationSettingsPlanNodeId = "applicationSettings-" + UUIDGenerator.generateUuid();

    StoreConfigWrapper applicationSettings =
        ((AzureWebAppServiceSpec) serviceV2Config.getServiceDefinition().getServiceSpec()).getApplicationSettings();

    Map<String, YamlField> dependenciesMap = new HashMap<>();
    dependenciesMap.put(applicationSettingsPlanNodeId, applicationSettingsYamlField);
    PlanCreationResponseBuilder applicationSettingsPlanCreationResponse = PlanCreationResponse.builder().dependencies(
        DependenciesUtils.toDependenciesProto(dependenciesMap)
            .toBuilder()
            .putDependencyMetadata(applicationSettingsPlanNodeId,
                AzureConfigsUtility.getDependencyMetadata(applicationSettingsPlanNodeId,
                    ApplicationSettingsParameters.builder().applicationSettings(applicationSettings).build(),
                    kryoSerializer, PlanCreatorConstants.APPLICATION_SETTINGS_STEP_PARAMETER))
            .build());
    if (yamlUpdates.getFqnToYamlCount() > 0) {
      applicationSettingsPlanCreationResponse.yamlUpdates(yamlUpdates.build());
    }
    planCreationResponseMap.put(applicationSettingsPlanNodeId, applicationSettingsPlanCreationResponse.build());
    return applicationSettingsPlanNodeId;
  }

  String addDependenciesForConnectionStringsV2(YamlNode serviceV2Node,
      Map<String, PlanCreationResponse> planCreationResponseMap, NGServiceV2InfoConfig serviceV2Config,
      KryoSerializer kryoSerializer) {
    YamlUpdates.Builder yamlUpdates = YamlUpdates.newBuilder();
    YamlField connectionStringsYamlField = AzureConfigsUtility.fetchAzureConfigYamlFieldAndSetYamlUpdates(
        serviceV2Node, false, yamlUpdates, YamlTypes.CONNECTION_STRINGS);
    String connectionStringsPlanNodeId = "connectionStrings-" + UUIDGenerator.generateUuid();

    StoreConfigWrapper connectionStrings =
        ((AzureWebAppServiceSpec) serviceV2Config.getServiceDefinition().getServiceSpec()).getConnectionStrings();

    Map<String, YamlField> dependenciesMap = new HashMap<>();
    dependenciesMap.put(connectionStringsPlanNodeId, connectionStringsYamlField);
    PlanCreationResponseBuilder connectionStringsPlanCreationResponse = PlanCreationResponse.builder().dependencies(
        DependenciesUtils.toDependenciesProto(dependenciesMap)
            .toBuilder()
            .putDependencyMetadata(connectionStringsPlanNodeId,
                AzureConfigsUtility.getDependencyMetadata(connectionStringsPlanNodeId,
                    ConnectionStringsParameters.builder().connectionStrings(connectionStrings).build(), kryoSerializer,
                    PlanCreatorConstants.CONNECTION_STRINGS_STEP_PARAMETER))
            .build());
    if (yamlUpdates.getFqnToYamlCount() > 0) {
      connectionStringsPlanCreationResponse.yamlUpdates(yamlUpdates.build());
    }
    planCreationResponseMap.put(connectionStringsPlanNodeId, connectionStringsPlanCreationResponse.build());
    return connectionStringsPlanNodeId;
  }

  boolean shouldCreatePlanNodeForManifests(ServiceConfig actualServiceConfig) {
    List<ManifestConfigWrapper> manifests = actualServiceConfig.getServiceDefinition().getServiceSpec().getManifests();

    // Contains either manifests or overrides or nothing.
    if (EmptyPredicate.isNotEmpty(manifests)) {
      return true;
    }

    return actualServiceConfig.getStageOverrides() != null
        && actualServiceConfig.getStageOverrides().getManifests() != null
        && EmptyPredicate.isNotEmpty(actualServiceConfig.getStageOverrides().getManifests());
  }

  boolean shouldCreatePlanNodeForManifestsV2(NGServiceV2InfoConfig serviceV2InfoConfig) {
    List<ManifestConfigWrapper> manifests = serviceV2InfoConfig.getServiceDefinition().getServiceSpec().getManifests();

    // Contains either manifests or not.
    return EmptyPredicate.isNotEmpty(manifests);
  }

  boolean shouldCreatePlanNodeForConfigFilesV2(NGServiceV2InfoConfig serviceV2InfoConfig) {
    List<ConfigFileWrapper> configFiles = serviceV2InfoConfig.getServiceDefinition().getServiceSpec().getConfigFiles();

    return EmptyPredicate.isNotEmpty(configFiles);
  }
}
