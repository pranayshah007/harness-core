/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.impl;

import static io.harness.walktree.visitor.utilities.VisitorParentPathUtils.PATH_CONNECTOR;

import io.harness.cvng.cdng.beans.DefaultAndConfiguredMonitoredServiceNode;
import io.harness.cvng.cdng.beans.MonitoredServiceNode;
import io.harness.cvng.cdng.beans.MonitoredServiceSpecType;
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

  public static YamlField getExecutionNodeField(YamlNode stageYaml) {
    return stageYaml.getField(SPEC_KEY).getNode().getField(EXECUTION_KEY);
  }

  public static MonitoredServiceSpecType getMonitoredServiceSpecType(MonitoredServiceNode monitoredServiceNode) {
    return Objects.nonNull(monitoredServiceNode) ? MonitoredServiceSpecType.getByName(monitoredServiceNode.getType())
                                                 : MonitoredServiceSpecType.DEFAULT;
  }

  public static MonitoredServiceSpecType getMonitoredServiceSpecType(
      DefaultAndConfiguredMonitoredServiceNode monitoredServiceNode) {
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
