/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.governance.services;

import static io.harness.cvng.cdng.services.impl.CVNGStepUtils.PIPELINE;
import static io.harness.cvng.cdng.services.impl.CVNGStepUtils.SERVICE_CONFIG_KEY;
import static io.harness.cvng.cdng.services.impl.CVNGStepUtils.STAGE_KEY;
import static io.harness.cvng.cdng.services.impl.CVNGStepUtils.USE_FROM_STAGE_KEY;
import static io.harness.cvng.core.beans.params.ServiceEnvironmentParams.builderWithProjectParams;
import static io.harness.cvng.governance.beans.ExpansionKeysConstants.ENVIRONMENT_REF;
import static io.harness.cvng.governance.beans.ExpansionKeysConstants.IDENTIFIER;
import static io.harness.cvng.governance.beans.ExpansionKeysConstants.INFRASTRUCTURE;
import static io.harness.cvng.governance.beans.ExpansionKeysConstants.SERVICE;
import static io.harness.cvng.governance.beans.ExpansionKeysConstants.SERVICE_CONFIG;
import static io.harness.cvng.governance.beans.ExpansionKeysConstants.SERVICE_REF;

import io.harness.cvng.cdng.services.impl.CVNGStepUtils;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceResponse;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.ServiceEnvironmentParams;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.governance.beans.SLOPolicyDTO;
import io.harness.cvng.governance.beans.SLOPolicyExpandedValue;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator;
import io.harness.cvng.servicelevelobjective.services.api.SLOHealthIndicatorService;
import io.harness.pms.contracts.governance.ExpansionPlacementStrategy;
import io.harness.pms.contracts.governance.ExpansionRequestMetadata;
import io.harness.pms.sdk.core.governance.ExpandedValue;
import io.harness.pms.sdk.core.governance.ExpansionResponse;
import io.harness.pms.sdk.core.governance.JsonExpansionHandler;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SLOPolicyExpansionHandler implements JsonExpansionHandler {
  @Inject SLOHealthIndicatorService sloHealthIndicatorService;
  @Inject MonitoredServiceService monitoredServiceService;

  @Override
  public ExpansionResponse expand(JsonNode fieldValue, ExpansionRequestMetadata metadata, String fqn) {
    String accountId = metadata.getAccountId();
    String orgId = metadata.getOrgId();
    String projectId = metadata.getProjectId();
    ProjectParams projectParams =
        ProjectParams.builder().accountIdentifier(accountId).projectIdentifier(projectId).orgIdentifier(orgId).build();
    String serviceRef = fetchServiceIdentifier(fieldValue, metadata);
    if (Objects.isNull(serviceRef)) {
      return ExpansionResponse.builder()
          .success(false)
          .errorMessage("Invalid yaml. Service config or service config reference from other stage not found.")
          .build();
    }
    String environmentRef = fieldValue.get(INFRASTRUCTURE).get(ENVIRONMENT_REF).asText();
    ServiceEnvironmentParams serviceEnvironmentParams = builderWithProjectParams(projectParams)
                                                            .serviceIdentifier(serviceRef)
                                                            .environmentIdentifier(environmentRef)
                                                            .build();
    MonitoredServiceResponse monitoredServiceResponse =
        monitoredServiceService.getApplicationMonitoredServiceResponse(serviceEnvironmentParams);

    SLOPolicyDTO sloPolicyDTO =
        SLOPolicyDTO.builder().statusOfMonitoredService(SLOPolicyDTO.MonitoredServiceStatus.NOT_CONFIGURED).build();
    if (Objects.nonNull(monitoredServiceResponse)
        && Objects.nonNull(monitoredServiceResponse.getMonitoredServiceDTO())) {
      List<SLOHealthIndicator> sloHealthIndicatorList = sloHealthIndicatorService.getByMonitoredServiceIdentifiers(
          projectParams, Collections.singletonList(monitoredServiceResponse.getMonitoredServiceDTO().getIdentifier()));
      double sloErrorBudgetRemainingPercentage = 100D;

      for (SLOHealthIndicator sloHealthIndicator : sloHealthIndicatorList) {
        if (sloErrorBudgetRemainingPercentage > sloHealthIndicator.getErrorBudgetRemainingPercentage()) {
          sloErrorBudgetRemainingPercentage = sloHealthIndicator.getErrorBudgetRemainingPercentage();
        }
      }
      sloPolicyDTO = SLOPolicyDTO.builder()
                         .sloErrorBudgetRemainingPercentage(sloErrorBudgetRemainingPercentage)
                         .statusOfMonitoredService(SLOPolicyDTO.MonitoredServiceStatus.CONFIGURED)
                         .build();
    }
    ExpandedValue value = SLOPolicyExpandedValue.builder().sloPolicyDTO(sloPolicyDTO).build();

    return ExpansionResponse.builder()
        .success(true)
        .key(value.getKey())
        .value(value)
        .placement(ExpansionPlacementStrategy.APPEND)
        .build();
  }

  private String fetchServiceIdentifier(JsonNode fieldValue, ExpansionRequestMetadata metadata) {
    if (Objects.nonNull(fieldValue.get(SERVICE_CONFIG).get(SERVICE_REF))) {
      return fieldValue.get(SERVICE_CONFIG).get(SERVICE_REF).asText();
    } else if (Objects.nonNull(fieldValue.get(SERVICE_CONFIG).get(SERVICE))
        && Objects.nonNull(fieldValue.get(SERVICE_CONFIG).get(SERVICE).get(IDENTIFIER))) {
      return fieldValue.get(SERVICE_CONFIG).get(SERVICE).get(IDENTIFIER).asText();
    } else {
      try {
        String useFromStageIdentifier =
            fieldValue.get(SERVICE_CONFIG_KEY).get(USE_FROM_STAGE_KEY).get(STAGE_KEY).asText();
        YamlNode yamlNode = YamlUtils.readTree(metadata.getYaml().toStringUtf8()).getNode();
        YamlNode propagateFromPipeline = yamlNode.getField(PIPELINE).getNode();
        YamlNode propagateFromStage =
            CVNGStepUtils.findStageByIdentifier(propagateFromPipeline, useFromStageIdentifier);
        return CVNGStepUtils.getServiceRefNode(propagateFromStage).asText();
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }
  }
}
