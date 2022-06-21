/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.envGroup;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.cdng.envGroup.services.EnvironmentGroupService;
import io.harness.cdng.envGroup.yaml.EnvGroupPlanCreatorConfig;
import io.harness.cdng.envgroup.yaml.EnvironmentGroupYaml;
import io.harness.cdng.environment.helper.EnvironmentPlanCreatorConfigMapper;
import io.harness.cdng.environment.yaml.EnvironmentPlanCreatorConfig;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.contracts.plan.YamlUpdates;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.utils.YamlPipelineUtils;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@OwnedBy(CDP)
@Singleton
public class EnvGroupPlanCreatorHelper {
  @Inject private EnvironmentGroupService environmentGroupService;
  @Inject private KryoSerializer kryoSerializer;

  public EnvGroupPlanCreatorConfig createEnvGroupPlanCreatorConfig(
      PlanCreationContextValue metadata, EnvironmentGroupYaml envGroupYaml) {
    final String accountIdentifier = metadata.getAccountIdentifier();
    final String orgIdentifier = metadata.getOrgIdentifier();
    final String projectIdentifier = metadata.getProjectIdentifier();
    final String envGroupIdentifier = envGroupYaml.getEnvGroupRef().getValue();

    final Optional<EnvironmentGroupEntity> entity =
        environmentGroupService.get(accountIdentifier, orgIdentifier, projectIdentifier, envGroupIdentifier, false);

    if (!entity.isPresent()) {
      throw new InvalidRequestException(
          String.format("No environment group found with %s identifier in %s project in %s org", envGroupIdentifier,
              projectIdentifier, orgIdentifier));
    }

    List<EnvironmentPlanCreatorConfig> envConfigs = new ArrayList<>();
    if (!envGroupYaml.isDeployToAll()) {
      String mergedYaml = entity.get().getYaml();
      List<EnvironmentYamlV2> envV2Yamls = envGroupYaml.getEnvGroupConfig();
      for (EnvironmentYamlV2 envYaml : envV2Yamls) {
        envConfigs.add(EnvironmentPlanCreatorConfigMapper.toEnvPlanCreatorConfigWithGitops(mergedYaml, envYaml, null));
      }
    }

    return EnvGroupPlanCreatorConfig.builder()
        .name(entity.get().getName())
        .identifier(entity.get().getIdentifier())
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .environmentGroupRef(envGroupYaml.getEnvGroupRef())
        .deployToAll(envGroupYaml.isDeployToAll())
        .environmentPlanCreatorConfigs(envConfigs)
        .build();
  }

  public void addEnvironmentGroupDependency(LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap,
      EnvGroupPlanCreatorConfig envGroupPlanCreatorConfig, YamlField originalEnvGroupField, boolean gitOpsEnabled,
      String envGroupUuid, String postServiceStepUuid, String serviceSpecNodeUuid) throws IOException {
    final YamlField yamlField;
    try {
      String yamlString = YamlPipelineUtils.getYamlString(envGroupPlanCreatorConfig);
      YamlField withUuid = YamlUtils.injectUuidInYamlField(yamlString);
      yamlField = new YamlField(YamlTypes.ENVIRONMENT_GROUP_YAML,
          new YamlNode(YamlTypes.ENVIRONMENT_GROUP_YAML, withUuid.getNode().getCurrJsonNode(),
              originalEnvGroupField.getNode().getParentNode()));
    } catch (IOException e) {
      throw new InvalidRequestException("Invalid environment group yaml", e);
    }

    Map<String, YamlField> fieldMap = ImmutableMap.of(envGroupUuid, yamlField);

    // preparing meta data
    final Dependency envGroupDependency = Dependency.newBuilder()
                                              .putAllMetadata(prepareMetadata(serviceSpecNodeUuid, postServiceStepUuid,
                                                  envGroupUuid, gitOpsEnabled, kryoSerializer))
                                              .build();

    planCreationResponseMap.put(envGroupUuid,
        PlanCreationResponse.builder()
            .dependencies(DependenciesUtils.toDependenciesProto(fieldMap)
                              .toBuilder()
                              .putDependencyMetadata(envGroupUuid, envGroupDependency)
                              .build())
            .yamlUpdates(
                YamlUpdates.newBuilder()
                    .putFqnToYaml(yamlField.getYamlPath(), YamlUtils.writeYamlString(yamlField).replace("---\n", ""))
                    .build())
            .build());
  }

  private Map<String, ByteString> prepareMetadata(String serviceSpecNodeId, String postServiceStepUuid,
      String environmentGroupUuid, boolean gitOpsEnabled, KryoSerializer kryoSerializer) {
    return ImmutableMap.<String, ByteString>builder()
        .put(YamlTypes.NEXT_UUID, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(serviceSpecNodeId)))
        .put(YamlTypes.POST_SERVICE_SPEC_UUID, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(postServiceStepUuid)))
        .put(YamlTypes.UUID, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(environmentGroupUuid)))
        .put(YAMLFieldNameConstants.GITOPS_ENABLED, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(gitOpsEnabled)))
        .build();
  }
}
