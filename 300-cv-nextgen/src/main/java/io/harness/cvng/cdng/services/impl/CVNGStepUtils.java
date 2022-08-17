/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.impl;

import static io.harness.walktree.visitor.utilities.VisitorParentPathUtils.PATH_CONNECTOR;

import io.harness.cvng.cdng.beans.MonitoredServiceNode;
import io.harness.cvng.cdng.beans.MonitoredServiceSpec.MonitoredServiceSpecType;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.filters.FilterCreatorHelper;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Preconditions;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CVNGStepUtils {
  public static final String INFRASTRUCTURE_KEY = "infrastructure";

  public static final String ENVIRONMENT_KEY = "environment";
  public static final String SERVICE_CONFIG_KEY = "serviceConfig";

  public static final String SERVICE_KEY = "service";
  public static final String SERVICE_REF_KEY = "serviceRef";
  public static final String ENVIRONMENT_REF_KEY = "environmentRef";
  public static final String SPEC_KEY = "spec";
  public static final String STAGE_KEY = "stage";
  public static final String STAGES_KEY = "stages";
  public static final String EXECUTION_KEY = "execution";
  public static final String USE_FROM_STAGE_KEY = "useFromStage";
  public static final String PIPELINE = "pipeline";

  public static final String IDENTIFIER_KEY = "identifier";

  public static YamlNode getServiceRefNode(YamlNode stageYaml) {
    YamlField serviceConfigKey = stageYaml.getField(SPEC_KEY).getNode().getField(SERVICE_CONFIG_KEY);
    YamlField serviceKey = stageYaml.getField(SPEC_KEY).getNode().getField(SERVICE_KEY);
    try {
      if (serviceKey != null) {
        return serviceKey.getNode().getField(SERVICE_REF_KEY).getNode();
      } else {
        return serviceConfigKey.getNode().getField(SERVICE_REF_KEY).getNode();
      }
    } catch (Exception e) {
      log.error("Exception: " + e.getMessage() + ", Incorrect Service Ref in pipeline Yaml for verify step.");
      throw e;
    }
  }

  public static JsonNode getServiceRefNode(JsonNode fieldValue) {
    JsonNode serviceConfigKey = fieldValue.get(SERVICE_CONFIG_KEY);
    JsonNode serviceKey = fieldValue.get(SERVICE_KEY);
    try {
      if (serviceKey != null) {
        return serviceKey.get(SERVICE_REF_KEY);
      } else {
        if (serviceConfigKey.get(SERVICE_REF_KEY) != null) {
          return serviceConfigKey.get(SERVICE_REF_KEY);
        } else {
          return serviceConfigKey.get(SERVICE_KEY).get(IDENTIFIER_KEY);
        }
      }
    } catch (Exception e) {
      log.error("Exception: " + e.getMessage() + ", Incorrect Service Ref in pipeline Yaml for SLO policy.");
      throw e;
    }
  }

  public static boolean hasServiceIdentifier(YamlNode stageYaml) {
    YamlField serviceConfigKey = stageYaml.getField(SPEC_KEY).getNode().getField(SERVICE_CONFIG_KEY);
    YamlField serviceKey = stageYaml.getField(SPEC_KEY).getNode().getField(SERVICE_KEY);
    if (serviceKey != null) {
      return serviceKey.getNode().getField(SERVICE_REF_KEY) != null;
    } else {
      return serviceConfigKey.getNode().getField(SERVICE_REF_KEY) != null;
    }
  }

  public static boolean hasServiceIdentifier(JsonNode fieldValue) {
    JsonNode serviceConfigKey = fieldValue.get(SERVICE_CONFIG_KEY);
    JsonNode serviceKey = fieldValue.get(SERVICE_KEY);
    if (serviceKey != null) {
      return serviceKey.get(SERVICE_REF_KEY) != null;
    } else {
      return (serviceConfigKey.get(SERVICE_REF_KEY) != null)
          || (serviceConfigKey.get(SERVICE_KEY) != null
              && serviceConfigKey.get(SERVICE_KEY).get(IDENTIFIER_KEY) != null);
    }
  }

  public static YamlNode getEnvRefNode(YamlNode stageYaml) {
    YamlField environmentKey = stageYaml.getField(SPEC_KEY).getNode().getField(ENVIRONMENT_KEY);
    YamlField infrastructureKey = stageYaml.getField(SPEC_KEY).getNode().getField(INFRASTRUCTURE_KEY);
    try {
      if (environmentKey != null) {
        return environmentKey.getNode().getField(ENVIRONMENT_REF_KEY).getNode();
      } else {
        return infrastructureKey.getNode().getField(ENVIRONMENT_REF_KEY).getNode();
      }
    } catch (Exception e) {
      log.error("Exception: " + e.getMessage() + ", Incorrect Environment Ref in pipeline Yaml for verify step.");
      throw e;
    }
  }

  public static YamlField getExecutionNodeField(YamlNode stageYaml) {
    return stageYaml.getField(SPEC_KEY).getNode().getField(EXECUTION_KEY);
  }

  public static YamlNode findStageByIdentifier(YamlNode yamlNode, String identifier) {
    Preconditions.checkNotNull(yamlNode, "Invalid yaml. Can't find stage spec.");
    if (yamlNode.getField(CVNGStepUtils.STAGES_KEY) != null) {
      for (YamlNode stageNode : yamlNode.getField(CVNGStepUtils.STAGES_KEY).getNode().asArray()) {
        if (identifier.equals(stageNode.getField(CVNGStepUtils.STAGE_KEY).getNode().getIdentifier())) {
          return stageNode.getField(CVNGStepUtils.STAGE_KEY).getNode();
        }
      }
      throw new IllegalStateException("Could not find stage with identifier: " + identifier);
    } else {
      return findStageByIdentifier(yamlNode.getParentNode(), identifier);
    }
  }

  public static MonitoredServiceSpecType getMonitoredServiceSpecType(MonitoredServiceNode monitoredServiceNode) {
    return Objects.nonNull(monitoredServiceNode) ? MonitoredServiceSpecType.getByName(monitoredServiceNode.getType())
                                                 : MonitoredServiceSpecType.DEFAULT;
  }
  public static void addReferredEntities(MonitoredServiceDTO monitoredServiceDTO, List<EntityDetailProtoDTO> result,
      FilterCreationContext filterCreationContext, ProjectParams projectParams) {
    monitoredServiceDTO.getSources().getHealthSources().forEach(healthSource -> {
      String connectorIdentifier = healthSource.getSpec().getConnectorRef();
      String fullQualifiedDomainName =
          YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode()) + PATH_CONNECTOR
          + YAMLFieldNameConstants.SPEC + PATH_CONNECTOR + "monitoredService.healthSources.connectorRef";
      result.add(FilterCreatorHelper.convertToEntityDetailProtoDTO(projectParams.getAccountIdentifier(),
          projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), fullQualifiedDomainName,
          ParameterField.createValueField(connectorIdentifier), EntityTypeProtoEnum.CONNECTORS));
    });
  }
}
