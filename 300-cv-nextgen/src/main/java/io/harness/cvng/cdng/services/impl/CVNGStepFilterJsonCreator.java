/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.impl;

import static io.harness.cvng.cdng.services.impl.CVNGStepUtils.SERVICE_CONFIG_KEY;
import static io.harness.cvng.cdng.services.impl.CVNGStepUtils.SPEC_KEY;
import static io.harness.cvng.cdng.services.impl.CVNGStepUtils.STAGE_KEY;
import static io.harness.cvng.cdng.services.impl.CVNGStepUtils.USE_FROM_STAGE_KEY;
import static io.harness.walktree.visitor.utilities.VisitorParentPathUtils.PATH_CONNECTOR;

import io.harness.common.NGExpressionUtils;
import io.harness.cvng.cdng.beans.CVNGStepInfo;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.params.ServiceEnvironmentParams;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.filters.FilterCreatorHelper;
import io.harness.filters.GenericStepPMSFilterJsonCreator;
import io.harness.plancreator.steps.StepElementConfig;
import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
@Slf4j
public class CVNGStepFilterJsonCreator extends GenericStepPMSFilterJsonCreator {
  @Inject private MonitoredServiceService monitoredServiceService;
  @Override
  public Set<String> getSupportedStepTypes() {
    return CVNGPlanCreatorV2.CVNG_SUPPORTED_TYPES;
  }

  @Override
  public FilterCreationResponse handleNode(FilterCreationContext filterCreationContext, StepElementConfig yamlField) {
    Preconditions.checkState(yamlField.getStepSpecType() instanceof CVNGStepInfo);
    String accountIdentifier = filterCreationContext.getSetupMetadata().getAccountId();
    String orgIdentifier = filterCreationContext.getSetupMetadata().getOrgId();
    String projectIdentifier = filterCreationContext.getSetupMetadata().getProjectId();
    CVNGStepInfo cvngStepInfo = (CVNGStepInfo) yamlField.getStepSpecType();
    cvngStepInfo.validate();
    List<EntityDetailProtoDTO> result = new ArrayList<>();
    // This is handling the case when the monitoring service is defined. Runtime case needs to be handled separately
    // https://harness.atlassian.net/browse/CDNG-10512
    YamlNode stageLevelYamlNode = getStageSpecYamlNode(filterCreationContext.getCurrentField().getNode());
    if (stageLevelYamlNode == null) {
      return FilterCreationResponse.builder().build();
    }
    String serviceIdentifier = parseServiceIdentifier(stageLevelYamlNode);
    String envIdentifier = CVNGStepUtils.getEnvRefNode(stageLevelYamlNode).asText();

    ServiceEnvironmentParams serviceEnvironmentParams = ServiceEnvironmentParams.builder()
                                                            .accountIdentifier(accountIdentifier)
                                                            .orgIdentifier(orgIdentifier)
                                                            .projectIdentifier(projectIdentifier)
                                                            .serviceIdentifier(serviceIdentifier)
                                                            .environmentIdentifier(envIdentifier)
                                                            .build();

    if (!(NGExpressionUtils.isRuntimeOrExpressionField(serviceIdentifier)
            || NGExpressionUtils.isRuntimeOrExpressionField(envIdentifier))) {
      MonitoredServiceDTO monitoredServiceDTO =
          monitoredServiceService.getApplicationMonitoredServiceDTO(serviceEnvironmentParams);
      Preconditions.checkNotNull(monitoredServiceDTO, "MonitoredService does not exist for service %s and env %s",
          serviceIdentifier, envIdentifier);
      Preconditions.checkState(!monitoredServiceDTO.getSources().getHealthSources().isEmpty(),
          "No health sources exists for monitoredService for service %s and env %s", serviceIdentifier, envIdentifier);
      monitoredServiceDTO.getSources().getHealthSources().forEach(healthSource -> {
        String connectorIdentifier = healthSource.getSpec().getConnectorRef();
        String fullQualifiedDomainName =
            YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode()) + PATH_CONNECTOR
            + YAMLFieldNameConstants.SPEC + PATH_CONNECTOR + "monitoredService.healthSources.connectorRef";
        result.add(FilterCreatorHelper.convertToEntityDetailProtoDTO(accountIdentifier, orgIdentifier,
            projectIdentifier, fullQualifiedDomainName, ParameterField.createValueField(connectorIdentifier),
            EntityTypeProtoEnum.CONNECTORS));
      });
    }
    return FilterCreationResponse.builder().referredEntities(result).build();
  }

  private String parseServiceIdentifier(YamlNode stageLevelYamlNode) {
    // Service can be either selected from existing stage or directly provided.
    // propagating service from multiple unknown stages is not supported yet.
    if (CVNGStepUtils.hasServiceIdentifier(stageLevelYamlNode)) {
      return CVNGStepUtils.getServiceRefNode(stageLevelYamlNode).asText();
    } else {
      String useFromStageIdentifier = stageLevelYamlNode.getField(SPEC_KEY)
                                          .getNode()
                                          .getField(SERVICE_CONFIG_KEY)
                                          .getNode()
                                          .getField(USE_FROM_STAGE_KEY)
                                          .getNode()
                                          .getField(STAGE_KEY)
                                          .getNode()
                                          .asText();
      YamlNode propagateFromStage = CVNGStepUtils.findStageByIdentifier(stageLevelYamlNode, useFromStageIdentifier);
      return CVNGStepUtils.getServiceRefNode(propagateFromStage).asText();
    }
  }

  private YamlNode getStageSpecYamlNode(YamlNode yamlNode) {
    if (yamlNode == null) {
      return null;
    }
    if (yamlNode.getField(CVNGStepUtils.STAGE_KEY) != null) {
      return yamlNode.getField(CVNGStepUtils.STAGE_KEY).getNode();
    } else {
      return getStageSpecYamlNode(yamlNode.getParentNode());
    }
  }
}
