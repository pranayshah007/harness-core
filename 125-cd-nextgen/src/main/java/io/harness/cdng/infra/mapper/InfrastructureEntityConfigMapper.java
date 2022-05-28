/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra.mapper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.mapper.TagMapper.convertToMap;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.infra.yaml.InfrastructureConfig;
import io.harness.cdng.infra.yaml.InfrastructureDefinitionConfig;
import io.harness.cdng.infra.yaml.InfrastructurePlanCreatorConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.utils.YamlPipelineUtils;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@OwnedBy(PIPELINE)
@UtilityClass
public class InfrastructureEntityConfigMapper {
  public String toYaml(InfrastructureConfig infrastructureConfig) {
    try {
      return YamlPipelineUtils.getYamlString(infrastructureConfig);
    } catch (IOException e) {
      throw new InvalidRequestException("Cannot create infrastructure entity due to " + e.getMessage());
    }
  }

  public InfrastructureConfig toInfrastructureConfig(InfrastructureEntity infrastructureEntity) {
    Infrastructure infrastructure = null;
    if (isNotEmpty(infrastructureEntity.getYaml())) {
      try {
        final InfrastructureConfig config =
            YamlPipelineUtils.read(infrastructureEntity.getYaml(), InfrastructureConfig.class);
        infrastructure = config.getInfrastructureDefinitionConfig().getSpec();
      } catch (IOException e) {
        throw new InvalidRequestException("Cannot create service ng service config due to " + e.getMessage());
      }
    }
    return InfrastructureConfig.builder()
        .infrastructureDefinitionConfig(InfrastructureDefinitionConfig.builder()
                                            .name(infrastructureEntity.getName())
                                            .description(infrastructureEntity.getDescription())
                                            .tags(convertToMap(infrastructureEntity.getTags()))
                                            .identifier(infrastructureEntity.getIdentifier())
                                            .orgIdentifier(infrastructureEntity.getOrgIdentifier())
                                            .projectIdentifier(infrastructureEntity.getProjectIdentifier())
                                            .environmentRef(infrastructureEntity.getEnvIdentifier())
                                            .type(infrastructureEntity.getType())
                                            .spec(infrastructure)
                                            .build())
        .build();
  }

  public List<InfrastructurePlanCreatorConfig> toInfrastructurePlanCreatorConfig(
      List<InfrastructureEntity> infrastructureEntity) {
    return infrastructureEntity.stream()
        .map(entity
            -> InfrastructurePlanCreatorConfig.builder()
                   .ref(entity.getIdentifier())
                   .infrastructureDefinitionConfig(toInfrastructureConfig(entity).getInfrastructureDefinitionConfig())
                   .build())
        .collect(Collectors.toList());
  }
}
