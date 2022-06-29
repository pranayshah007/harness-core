/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.impl;

import static io.harness.cvng.cdng.services.impl.CVNGStepUtils.SERVICE_CONFIG_KEY;
import static io.harness.cvng.cdng.services.impl.CVNGStepUtils.SPEC_KEY;
import static io.harness.cvng.cdng.services.impl.CVNGStepUtils.STAGE_KEY;
import static io.harness.cvng.cdng.services.impl.CVNGStepUtils.USE_FROM_STAGE_KEY;

import io.harness.common.NGExpressionUtils;
import io.harness.cvng.cdng.beans.CVNGStepInfo;
import io.harness.cvng.cdng.beans.MonitoredServiceNode;
import io.harness.cvng.cdng.beans.ResolvedCVConfigInfo;
import io.harness.cvng.cdng.beans.ResolvedCVConfigInfo.ResolvedCVConfigInfoBuilder;
import io.harness.cvng.cdng.services.api.VerifyStepMonitoredServiceResolutionService;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.ServiceEnvironmentParams;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.YamlNode;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.collections.CollectionUtils;

public class DefaultVerifyStepMonitoredServiceResolutionServiceImpl
    implements VerifyStepMonitoredServiceResolutionService {
  @Inject private CVConfigService cvConfigService;
  @Inject private MonitoredServiceService monitoredServiceService;

  @Override
  public ResolvedCVConfigInfo getResolvedCVConfigInfo(
      ServiceEnvironmentParams serviceEnvironmentParams, MonitoredServiceNode monitoredServiceNode) {
    ResolvedCVConfigInfoBuilder resolvedCVConfigInfoBuilder = ResolvedCVConfigInfo.builder();
    Optional<MonitoredService> monitoredService = getMonitoredService(serviceEnvironmentParams);
    monitoredService.ifPresent(service
        -> resolvedCVConfigInfoBuilder.monitoredServiceIdentifier(service.getIdentifier())
               .cvConfigs(getCVConfigs(serviceEnvironmentParams, service)));
    return resolvedCVConfigInfoBuilder.build();
  }

  @Override
  public List<EntityDetailProtoDTO> getReferredEntities(
      FilterCreationContext filterCreationContext, CVNGStepInfo cvngStepInfo, ProjectParams projectParams) {
    YamlNode stageLevelYamlNode = getStageSpecYamlNode(filterCreationContext.getCurrentField().getNode());
    List<EntityDetailProtoDTO> result = new ArrayList<>();

    if (stageLevelYamlNode == null) {
      return result;
    }
    String serviceIdentifier = parseServiceIdentifier(stageLevelYamlNode);
    String envIdentifier = CVNGStepUtils.getEnvRefNode(stageLevelYamlNode).asText();

    ServiceEnvironmentParams serviceEnvironmentParams = ServiceEnvironmentParams.builder()
                                                            .accountIdentifier(projectParams.getAccountIdentifier())
                                                            .orgIdentifier(projectParams.getOrgIdentifier())
                                                            .projectIdentifier(projectParams.getProjectIdentifier())
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
      CVNGStepUtils.addReferredEntities(monitoredServiceDTO, result, filterCreationContext, projectParams);
    }
    return result;
  }

  private List<CVConfig> getCVConfigs(
      ServiceEnvironmentParams serviceEnvironmentParams, MonitoredService monitoredService) {
    if (Objects.nonNull(monitoredService)
        && CollectionUtils.isNotEmpty(monitoredService.getHealthSourceIdentifiers())) {
      return getCvConfigsFromMonitoredService(serviceEnvironmentParams, monitoredService.getIdentifier());
    } else {
      return Collections.emptyList();
    }
  }

  private List<CVConfig> getCvConfigsFromMonitoredService(
      ServiceEnvironmentParams serviceEnvironmentParams, String monitoredServiceIdentifier) {
    return cvConfigService.getCVConfigs(MonitoredServiceParams.builder()
                                            .monitoredServiceIdentifier(monitoredServiceIdentifier)
                                            .accountIdentifier(serviceEnvironmentParams.getAccountIdentifier())
                                            .orgIdentifier(serviceEnvironmentParams.getOrgIdentifier())
                                            .projectIdentifier(serviceEnvironmentParams.getProjectIdentifier())
                                            .build());
  }

  private Optional<MonitoredService> getMonitoredService(ServiceEnvironmentParams serviceEnvironmentParams) {
    return monitoredServiceService.getApplicationMonitoredService(serviceEnvironmentParams);
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
