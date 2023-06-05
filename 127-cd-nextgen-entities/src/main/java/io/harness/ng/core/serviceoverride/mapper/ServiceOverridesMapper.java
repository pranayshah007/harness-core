/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.serviceoverride.mapper;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.utils.IdentifierRefHelper.MAX_RESULT_THRESHOLD_FOR_SPLIT;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity.NGServiceOverridesEntityKeys;
import io.harness.ng.core.serviceoverride.beans.ServiceOverrideRequestDTO;
import io.harness.ng.core.serviceoverride.beans.ServiceOverrideResponseDTO;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideConfig;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.scope.ScopeHelper;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.YamlPipelineUtils;
import io.harness.yaml.core.variables.NGServiceOverrides;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDC)
@UtilityClass
public class ServiceOverridesMapper {
  public NGServiceOverridesEntity toServiceOverridesEntity(
      String accountId, ServiceOverrideRequestDTO serviceOverrideRequestDTO) {
    String orgIdentifier = serviceOverrideRequestDTO.getOrgIdentifier();
    String projectIdentifier = serviceOverrideRequestDTO.getProjectIdentifier();
    String environmentIdentifier = serviceOverrideRequestDTO.getEnvironmentIdentifier();
    if (isNotEmpty(environmentIdentifier)) {
      String[] environmentRefSplit = StringUtils.split(environmentIdentifier, ".", MAX_RESULT_THRESHOLD_FOR_SPLIT);
      if (environmentRefSplit.length > 1 && isNotEmpty(projectIdentifier)) {
        throw new InvalidRequestException(
            "Project Identifier should not be passed when environment used in service override is at organisation or account scope");
      }
    }
    NGServiceOverridesEntity serviceOverridesEntity = NGServiceOverridesEntity.builder()
                                                          .accountId(accountId)
                                                          .orgIdentifier(orgIdentifier)
                                                          .projectIdentifier(projectIdentifier)
                                                          .environmentRef(environmentIdentifier)
                                                          .serviceRef(serviceOverrideRequestDTO.getServiceIdentifier())
                                                          .yaml(serviceOverrideRequestDTO.getYaml())
                                                          .build();

    // validating the yaml
    NGServiceOverrideConfig ngServiceOverrideConfig =
        NGServiceOverrideEntityConfigMapper.toNGServiceOverrideConfig(serviceOverridesEntity);

    String updatedYaml =
        getUpdatedYamlIfEnvRefIsNotQualifiedRef(accountId, serviceOverrideRequestDTO, ngServiceOverrideConfig);
    if (isNotBlank(updatedYaml)) {
      serviceOverridesEntity.setYaml(updatedYaml);
    }

    return serviceOverridesEntity;
  }

  private static String getUpdatedYamlIfEnvRefIsNotQualifiedRef(String accountId,
      ServiceOverrideRequestDTO serviceOverrideRequestDTO, NGServiceOverrideConfig ngServiceOverrideConfig) {
    String updatedYaml = StringUtils.EMPTY;

    if (ngServiceOverrideConfig.getServiceOverrideInfoConfig() != null) {
      String environmentRef = ngServiceOverrideConfig.getServiceOverrideInfoConfig().getEnvironmentRef();
      String[] envRefSplit = StringUtils.split(environmentRef, ".", MAX_RESULT_THRESHOLD_FOR_SPLIT);
      Scope scope = ScopeHelper.getScope(
          accountId, serviceOverrideRequestDTO.getOrgIdentifier(), serviceOverrideRequestDTO.getProjectIdentifier());

      if ((envRefSplit == null || envRefSplit.length == 1) && !scope.equals(Scope.PROJECT)) {
        if (isNotBlank(serviceOverrideRequestDTO.getYaml())) {
          try {
            final String qualifiedEnvRef = IdentifierRefHelper.getRefFromIdentifierOrRef(accountId,
                serviceOverrideRequestDTO.getOrgIdentifier(), serviceOverrideRequestDTO.getProjectIdentifier(),
                serviceOverrideRequestDTO.getEnvironmentIdentifier());
            YamlField yamlField = YamlUtils.readTree(serviceOverrideRequestDTO.getYaml());
            JsonNode serviceOverridesJsonNode =
                yamlField.getNode().getField("serviceOverrides").getNode().getCurrJsonNode();
            YamlField envRefYamlField = yamlField.getNode()
                                            .getField("serviceOverrides")
                                            .getNode()
                                            .getField(NGServiceOverridesEntityKeys.environmentRef);
            if (envRefYamlField != null) {
              JsonNode envRefJsonNode = envRefYamlField.getNode().getCurrJsonNode();
              if (envRefJsonNode != null && envRefJsonNode.isTextual()) {
                ((ObjectNode) serviceOverridesJsonNode)
                    .put(NGServiceOverridesEntityKeys.environmentRef, qualifiedEnvRef);
              }
              updatedYaml = YamlUtils.writeYamlString(yamlField.getNode().getCurrJsonNode());
            }
          } catch (Exception ex) {
            throw new InvalidRequestException("Can not update service override due to " + ex.getMessage());
          }
        }
      }
    }
    return updatedYaml;
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

  public NGServiceOverrideConfig toNGServiceOverrideConfig(String entityYaml) {
    try {
      return YamlPipelineUtils.read(entityYaml, NGServiceOverrideConfig.class);
    } catch (IOException e) {
      throw new InvalidRequestException(String.format("Cannot read serviceOverride yaml %s ", entityYaml));
    }
  }
}
