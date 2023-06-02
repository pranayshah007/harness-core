/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.mappers;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.mapper.TagMapper.convertToMap;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.ManifestAttributes;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.environment.validator.SvcEnvV2ManifestValidator;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.utils.YamlPipelineUtils;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CDC)
@UtilityClass
public class NGServiceEntityMapper {
  public String toYaml(NGServiceConfig ngServiceConfig) {
    try {
      return YamlPipelineUtils.getYamlString(ngServiceConfig);
    } catch (IOException e) {
      throw new InvalidRequestException("Cannot create service entity due to " + e.getMessage());
    }
  }

  public NGServiceConfig toNGServiceConfig(ServiceEntity serviceEntity) {
    ServiceDefinition sDef = null;
    Boolean gitOpsEnabled = null;
    if (isNotEmpty(serviceEntity.getYaml())) {
      try {
        final NGServiceConfig config = YamlPipelineUtils.read(serviceEntity.getYaml(), NGServiceConfig.class);
        validateFieldsOrThrow(config.getNgServiceV2InfoConfig(), serviceEntity);
        sDef = config.getNgServiceV2InfoConfig().getServiceDefinition();
        gitOpsEnabled = config.getNgServiceV2InfoConfig().getGitOpsEnabled();
      } catch (IOException e) {
        throw new InvalidRequestException("Cannot create service ng service config due to " + e.getMessage());
      }
    }
    return NGServiceConfig.builder()
        .ngServiceV2InfoConfig(NGServiceV2InfoConfig.builder()
                                   .name(serviceEntity.getName())
                                   .identifier(serviceEntity.getIdentifier())
                                   .description(serviceEntity.getDescription())
                                   .tags(convertToMap(serviceEntity.getTags()))
                                   .serviceDefinition(sDef)
                                   .gitOpsEnabled(gitOpsEnabled)
                                   .build())
        .build();
  }

  private void validateFieldsOrThrow(NGServiceV2InfoConfig fromYaml, ServiceEntity requestedService) {
    if (StringUtils.compare(requestedService.getIdentifier(), fromYaml.getIdentifier()) != 0) {
      throw new InvalidRequestException(
          String.format("Service Identifier : %s in service request doesn't match with identifier : %s given in yaml",
              requestedService.getIdentifier(), fromYaml.getIdentifier()));
    }

    // not using StringUtils.compare() here as we replace service name with identifier when name field is empty
    if (isNotEmpty(requestedService.getName()) && isNotEmpty(fromYaml.getName())
        && !requestedService.getName().equals(fromYaml.getName())) {
      throw new InvalidRequestException(
          String.format("Service Name : %s in service request doesn't match with name : %s given in yaml",
              requestedService.getName(), fromYaml.getName()));
    }

    ServiceDefinition serviceDefinition = fromYaml.getServiceDefinition();
    if (serviceDefinition != null) {
      validateManifests(serviceDefinition);
    }
  }

  private static void validateManifests(ServiceDefinition serviceDefinition) {
    List<ManifestConfigWrapper> manifests = serviceDefinition.getServiceSpec().getManifests();

    if (isNotEmpty(manifests)) {
      List<ManifestAttributes> manifestList = manifests.stream()
                                                  .map(ManifestConfigWrapper::getManifest)
                                                  .filter(Objects::nonNull)
                                                  .map(ManifestConfig::getSpec)
                                                  .filter(Objects::nonNull)
                                                  .collect(Collectors.toList());
      SvcEnvV2ManifestValidator.validateManifestList(serviceDefinition.getType(), manifestList);
    }
  }
}
