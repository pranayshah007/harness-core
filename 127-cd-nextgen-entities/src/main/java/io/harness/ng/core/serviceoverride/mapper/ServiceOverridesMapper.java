/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.serviceoverride.mapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.beans.ServiceOverrideRequestDTO;
import io.harness.ng.core.serviceoverride.beans.ServiceOverrideResponseDTO;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideConfig;
import io.harness.utils.YamlPipelineUtils;
import io.harness.yaml.core.variables.NGServiceOverrides;

import java.io.IOException;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDC)
@UtilityClass
public class ServiceOverridesMapper {
  public NGServiceOverridesEntity toServiceOverridesEntity(
      String accountId, ServiceOverrideRequestDTO serviceOverrideRequestDTO) {
    NGServiceOverridesEntity serviceOverridesEntity =
        NGServiceOverridesEntity.builder()
            .accountId(accountId)
            .orgIdentifier(serviceOverrideRequestDTO.getOrgIdentifier())
            .projectIdentifier(serviceOverrideRequestDTO.getProjectIdentifier())
            .environmentRef(serviceOverrideRequestDTO.getEnvironmentRef())
            .serviceRef(serviceOverrideRequestDTO.getServiceRef())
            .yaml(serviceOverrideRequestDTO.getYaml())
            .build();

    NGServiceOverrideConfig serviceOverrideConfig =
        NGServiceOverrideEntityConfigMapper.toNGServiceOverrideConfig(serviceOverridesEntity);
    serviceOverridesEntity.setYaml(NGServiceOverrideEntityConfigMapper.toYaml(serviceOverrideConfig));
    return serviceOverridesEntity;
  }

  public ServiceOverrideResponseDTO toResponseWrapper(NGServiceOverridesEntity serviceOverridesEntity) {
    return ServiceOverrideResponseDTO.builder()
        .accountId(serviceOverridesEntity.getAccountId())
        .orgIdentifier(serviceOverridesEntity.getOrgIdentifier())
        .projectIdentifier(serviceOverridesEntity.getProjectIdentifier())
        .environmentRef(serviceOverridesEntity.getEnvironmentRef())
        .serviceRef(serviceOverridesEntity.getServiceRef())
        .yaml(serviceOverridesEntity.getYaml())
        .build();
  }

  public NGServiceOverrides toServiceOverrides(String entityYaml) {
    try {
      NGServiceOverrideConfig serviceOverrideConfig = YamlPipelineUtils.read(entityYaml, NGServiceOverrideConfig.class);
      return NGServiceOverrides.builder()
          .serviceRef(serviceOverrideConfig.getServiceOverrideInfoConfig().getServiceRef())
          .variables(serviceOverrideConfig.getServiceOverrideInfoConfig().getVariables())
          .build();
    } catch (IOException e) {
      throw new InvalidRequestException(String.format("Cannot read serviceOverride yaml %s ", entityYaml));
    }
  }
}
