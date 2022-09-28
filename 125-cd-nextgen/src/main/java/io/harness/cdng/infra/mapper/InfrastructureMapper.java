/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra.mapper;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.HarnessStringUtils.join;
import static io.harness.ng.core.mapper.TagMapper.convertToList;
import static io.harness.ng.core.mapper.TagMapper.convertToMap;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.yaml.InfrastructureConfig;
import io.harness.cdng.infra.yaml.InfrastructureConfig.InfrastructureConfigKeys;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.infrastructure.dto.InfrastructureRequestDTO;
import io.harness.ng.core.infrastructure.dto.InfrastructureResponse;
import io.harness.ng.core.infrastructure.dto.InfrastructureResponseDTO;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import lombok.experimental.UtilityClass;

@OwnedBy(CDC)
@UtilityClass
public class InfrastructureMapper {
  private static final Validator validator = beanValidator();

  private Validator beanValidator() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    return factory.getValidator();
  }

  public InfrastructureEntity toInfrastructureEntity(
      String accountId, InfrastructureRequestDTO infrastructureRequestDTO) {
    // TODO: refactor code to populate infrastructureEntity from yaml rather than infrastructureRequestDTO
    InfrastructureEntity infrastructureEntity = InfrastructureEntity.builder()
                                                    .identifier(infrastructureRequestDTO.getIdentifier())
                                                    .accountId(accountId)
                                                    .orgIdentifier(infrastructureRequestDTO.getOrgIdentifier())
                                                    .projectIdentifier(infrastructureRequestDTO.getProjectIdentifier())
                                                    .envIdentifier(infrastructureRequestDTO.getEnvironmentRef())
                                                    .name(infrastructureRequestDTO.getName())
                                                    .description(infrastructureRequestDTO.getDescription())
                                                    .tags(convertToList(infrastructureRequestDTO.getTags()))
                                                    .yaml(infrastructureRequestDTO.getYaml())
                                                    .type(infrastructureRequestDTO.getType())
                                                    .build();

    InfrastructureConfig infrastructureConfig =
        InfrastructureEntityConfigMapper.toInfrastructureConfig(infrastructureEntity);

    validate(infrastructureConfig);

    if (isEmpty(infrastructureRequestDTO.getYaml())) {
      infrastructureEntity.setYaml(InfrastructureEntityConfigMapper.toYaml(infrastructureConfig));
    }
    if (infrastructureConfig.getInfrastructureDefinitionConfig().getDeploymentType() != null) {
      infrastructureEntity.setDeploymentType(
          infrastructureConfig.getInfrastructureDefinitionConfig().getDeploymentType());
    }
    return infrastructureEntity;
  }

  private void validate(InfrastructureConfig cfg) {
    Set<ConstraintViolation<InfrastructureConfig>> violations = validator.validate(cfg);
    if (isNotEmpty(violations)) {
      final List<String> messages = violations.stream()
                                        .filter(v -> v.getPropertyPath() != null && isNotEmpty(v.getMessage()))
                                        .map(v
                                            -> v.getPropertyPath().toString().replace(
                                                   InfrastructureConfigKeys.infrastructureDefinitionConfig + ".", "")
                                                + " " + v.getMessage())
                                        .collect(Collectors.toList());
      throw new InvalidRequestException(join(",", messages));
    }
  }

  public InfrastructureResponse toResponseWrapper(InfrastructureEntity infrastructureEntity) {
    return InfrastructureResponse.builder()
        .infrastructure(writeDTO(infrastructureEntity))
        .createdAt(infrastructureEntity.getCreatedAt())
        .lastModifiedAt(infrastructureEntity.getLastModifiedAt())
        .build();
  }

  public InfrastructureResponseDTO writeDTO(InfrastructureEntity infrastructureEntity) {
    return InfrastructureResponseDTO.builder()
        .accountId(infrastructureEntity.getAccountId())
        .orgIdentifier(infrastructureEntity.getOrgIdentifier())
        .projectIdentifier(infrastructureEntity.getProjectIdentifier())
        .environmentRef(infrastructureEntity.getEnvIdentifier())
        .identifier(infrastructureEntity.getIdentifier())
        .name(infrastructureEntity.getName())
        .description(infrastructureEntity.getDescription())
        .tags(convertToMap(infrastructureEntity.getTags()))
        .yaml(infrastructureEntity.getYaml())
        .type(infrastructureEntity.getType())
        .deploymentType(infrastructureEntity.getDeploymentType())
        .build();
  }
}
