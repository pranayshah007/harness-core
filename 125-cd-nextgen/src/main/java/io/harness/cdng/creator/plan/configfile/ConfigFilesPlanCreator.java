/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.configfile;

import static io.harness.cdng.utilities.ConfigFileUtility.fetchIndividualConfigFileYamlField;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.configfile.ConfigFile;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.cdng.configfile.ConfigFiles;
import io.harness.cdng.configfile.steps.ConfigFileStepParameters;
import io.harness.cdng.configfile.steps.ConfigFileStepParameters.ConfigFileStepParametersBuilder;
import io.harness.cdng.configfile.steps.ConfigFilesStep;
import io.harness.cdng.creator.plan.PlanCreatorConstants;
import io.harness.cdng.service.beans.ServiceConfig;
import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.cdng.service.beans.StageOverridesConfig;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.ChildrenPlanCreator;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.YamlField;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.fork.ForkStepParameters;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@OwnedBy(HarnessTeam.CDP)
public class ConfigFilesPlanCreator extends ChildrenPlanCreator<ConfigFiles> {
  @Inject KryoSerializer kryoSerializer;

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, ConfigFiles config) {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    ServiceConfig serviceConfig = (ServiceConfig) kryoSerializer.asInflatedObject(
        ctx.getDependency().getMetadataMap().get(YamlTypes.SERVICE_CONFIG).toByteArray());

    ConfigFileList configFileList = new ConfigFileListBuilder()
                                        .addServiceDefinition(serviceConfig.getServiceDefinition())
                                        .addStageOverrides(serviceConfig.getStageOverrides())
                                        .build();

    if (isEmpty(configFileList.getConfigFiles())) {
      return planCreationResponseMap;
    }

    YamlField configFilesYamlField = ctx.getCurrentField();

    for (Map.Entry<String, ConfigFileStepParameters> identifierToConfigFileStepParametersEntry :
        configFileList.getConfigFiles().entrySet()) {
      addDependenciesForIndividualConfigFile(identifierToConfigFileStepParametersEntry.getKey(),
          identifierToConfigFileStepParametersEntry.getValue(), configFilesYamlField, planCreationResponseMap);
    }

    return planCreationResponseMap;
  }
  @VisibleForTesting
  void addDependenciesForIndividualConfigFile(final String configFileIdentifier,
      ConfigFileStepParameters stepParameters, YamlField configFilesYamlField,
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap) {
    YamlField individualConfigFileYamlField =
        fetchIndividualConfigFileYamlField(configFileIdentifier, configFilesYamlField);

    String individualConfigFilePlanNodeId = UUIDGenerator.generateUuid();

    PlanCreationResponse individualConfigFilePlanResponse =
        PlanCreationResponse.builder()
            .dependencies(
                DependenciesUtils
                    .toDependenciesProto(getDependencies(individualConfigFilePlanNodeId, individualConfigFileYamlField))
                    .toBuilder()
                    .putDependencyMetadata(individualConfigFilePlanNodeId,
                        getDependencyMetadata(individualConfigFilePlanNodeId, stepParameters))
                    .build())
            .build();

    planCreationResponseMap.put(individualConfigFilePlanNodeId, individualConfigFilePlanResponse);
  }

  private Map<String, YamlField> getDependencies(
      final String individualConfigFilePlanNodeId, YamlField individualConfigFile) {
    Map<String, YamlField> dependenciesMap = new HashMap<>();
    dependenciesMap.put(individualConfigFilePlanNodeId, individualConfigFile);
    return dependenciesMap;
  }

  private Dependency getDependencyMetadata(
      final String individualConfigFilePlanNodeId, ConfigFileStepParameters stepParameters) {
    Map<String, ByteString> metadataDependency =
        prepareMetadataForIndividualConfigFilePlanCreator(individualConfigFilePlanNodeId, stepParameters);
    return Dependency.newBuilder().putAllMetadata(metadataDependency).build();
  }

  public Map<String, ByteString> prepareMetadataForIndividualConfigFilePlanCreator(
      String individualConfigFilePlanNodeId, ConfigFileStepParameters stepParameters) {
    Map<String, ByteString> metadataDependency = new HashMap<>();
    metadataDependency.put(
        YamlTypes.UUID, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(individualConfigFilePlanNodeId)));
    metadataDependency.put(PlanCreatorConstants.CONFIG_FILE_STEP_PARAMETER,
        ByteString.copyFrom(kryoSerializer.asDeflatedBytes(stepParameters)));
    return metadataDependency;
  }

  @Override
  public PlanNode createPlanForParentNode(PlanCreationContext ctx, ConfigFiles config, List<String> childrenNodeIds) {
    String configFilesId = (String) kryoSerializer.asInflatedObject(
        ctx.getDependency().getMetadataMap().get(YamlTypes.UUID).toByteArray());

    ForkStepParameters stepParameters = ForkStepParameters.builder().parallelNodeIds(childrenNodeIds).build();
    return PlanNode.builder()
        .uuid(configFilesId)
        .stepType(ConfigFilesStep.STEP_TYPE)
        .name(PlanCreatorConstants.CONFIG_FILES_NODE_NAME)
        .identifier(YamlTypes.CONFIG_FILES)
        .stepParameters(stepParameters)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILDREN).build())
                .build())
        .skipExpressionChain(false)
        .build();
  }

  @Override
  public Class<ConfigFiles> getFieldClass() {
    return ConfigFiles.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(YamlTypes.CONFIG_FILES, Collections.singleton(PlanCreatorUtils.ANY_TYPE));
  }

  @Data
  @FieldDefaults(level = AccessLevel.PRIVATE)
  private static class ConfigFileListBuilder {
    Map<String, ConfigFileStepParametersBuilder> configFileStepParametersBuilders;

    ConfigFileListBuilder() {
      this.configFileStepParametersBuilders = Collections.emptyMap();
    }

    public ConfigFileListBuilder addServiceDefinition(ServiceDefinition serviceDefinition) {
      List<ConfigFileWrapper> configFileWrappers = serviceDefinition.getServiceSpec().getConfigFiles();
      if (configFileWrappers == null) {
        return this;
      }

      this.configFileStepParametersBuilders = new HashMap<>();
      if (EmptyPredicate.isNotEmpty(configFileWrappers)) {
        configFileWrappers.forEach(configFileWrapper -> {
          ConfigFile configFile = configFileWrapper.getConfigFile();
          if (configFileStepParametersBuilders.containsKey(configFile.getIdentifier())) {
            throw new InvalidRequestException(
                String.format("Duplicate identifier: [%s] in ConfigFiles", configFile.getIdentifier()));
          }

          configFileStepParametersBuilders.put(configFile.getIdentifier(),
              ConfigFileStepParameters.builder()
                  .identifier(configFile.getIdentifier())
                  .spec(configFile.getSpec())
                  .order(configFileStepParametersBuilders.size()));
        });
      }
      return this;
    }

    public ConfigFileListBuilder addStageOverrides(StageOverridesConfig stageOverrides) {
      if (stageOverrides == null || isEmpty(stageOverrides.getConfigFiles())) {
        return this;
      }

      List<ConfigFileWrapper> configFileWrappers = stageOverrides.getConfigFiles();
      for (ConfigFileWrapper configFileWrapper : configFileWrappers) {
        ConfigFile configFile = configFileWrapper.getConfigFile();
        ConfigFileStepParametersBuilder configFileStepParametersBuilder =
            configFileStepParametersBuilders.computeIfAbsent(configFile.getIdentifier(),
                identifier
                -> ConfigFileStepParameters.builder()
                       .identifier(identifier)
                       .order(configFileStepParametersBuilders.size()));
        configFileStepParametersBuilder.stageOverride(configFile.getSpec());
      }

      return this;
    }

    public ConfigFileList build() {
      return new ConfigFileList(configFileStepParametersBuilders.entrySet().stream().collect(
          Collectors.toMap(Map.Entry::getKey, e -> e.getValue().build())));
    }
  }

  @Value
  private static class ConfigFileList {
    Map<String, ConfigFileStepParameters> configFiles;
  }
}
