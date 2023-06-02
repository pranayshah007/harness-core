/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.governance.services;

import static io.harness.cvng.core.beans.params.ServiceEnvironmentParams.builderWithProjectParams;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.cvng.cdng.services.api.CDStageMetaDataService;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceResponse;
import io.harness.cvng.core.beans.monitoredService.SloHealthIndicatorDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.ServiceEnvironmentParams;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.governance.beans.SLOPolicyDTO;
import io.harness.cvng.governance.beans.SLOPolicyExpandedValue;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator;
import io.harness.cvng.servicelevelobjective.services.api.SLOHealthIndicatorService;
import io.harness.ng.core.dto.CDStageMetaDataDTO;
import io.harness.ng.core.dto.CDStageMetaDataDTO.ServiceEnvRef;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.contracts.governance.ExpansionPlacementStrategy;
import io.harness.pms.contracts.governance.ExpansionRequestMetadata;
import io.harness.pms.sdk.core.governance.ExpandedValue;
import io.harness.pms.sdk.core.governance.ExpansionResponse;
import io.harness.pms.sdk.core.governance.JsonExpansionHandler;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SLOPolicyExpansionHandler implements JsonExpansionHandler {
  @Inject SLOHealthIndicatorService sloHealthIndicatorService;
  @Inject MonitoredServiceService monitoredServiceService;
  @Inject private CDStageMetaDataService cdStageMetaDataService;
  @Override
  public ExpansionResponse expand(JsonNode fieldValue, ExpansionRequestMetadata metadata, String fqn) {
    String accountId = metadata.getAccountId();
    String orgId = metadata.getOrgId();
    String projectId = metadata.getProjectId();
    ProjectParams projectParams =
        ProjectParams.builder().accountIdentifier(accountId).projectIdentifier(projectId).orgIdentifier(orgId).build();
    String stageIdentifier = fieldValue.get("identifier").asText();
    String pipeline = metadata.getYaml().toStringUtf8();
    ResponseDTO<CDStageMetaDataDTO> responseDTO =
        cdStageMetaDataService.getServiceAndEnvironmentRef(stageIdentifier, pipeline);
    List<ServiceEnvRef> serviceEnvRefList = responseDTO.getData().getServiceEnvRefList();
    if (isEmpty(serviceEnvRefList)) {
      return ExpansionResponse.builder()
          .success(false)
          .errorMessage("Invalid yaml. Service config or service config reference from other stage not found.")
          .build();
    }
    SLOPolicyDTO sloPolicyDTO =
        SLOPolicyDTO.builder().statusOfMonitoredService(SLOPolicyDTO.MonitoredServiceStatus.NOT_CONFIGURED).build();
    double sloErrorBudgetRemainingPercentage = 100D;
    Map<String, SloHealthIndicatorDTO> sloMappedToTheirHealthIndicators = new HashMap<>();
    for (ServiceEnvRef serviceEnvRef : serviceEnvRefList) {
      ServiceEnvironmentParams serviceEnvironmentParams = builderWithProjectParams(projectParams)
                                                              .serviceIdentifier(serviceEnvRef.getServiceRef())
                                                              .environmentIdentifier(serviceEnvRef.getEnvironmentRef())
                                                              .build();
      MonitoredServiceResponse monitoredServiceResponse =
          monitoredServiceService.getApplicationMonitoredServiceResponse(serviceEnvironmentParams);

      if (Objects.nonNull(monitoredServiceResponse)
          && Objects.nonNull(monitoredServiceResponse.getMonitoredServiceDTO())) {
        List<SLOHealthIndicator> sloHealthIndicatorList =
            sloHealthIndicatorService.getByMonitoredServiceIdentifiers(projectParams,
                Collections.singletonList(monitoredServiceResponse.getMonitoredServiceDTO().getIdentifier()));
        for (SLOHealthIndicator sloHealthIndicator : sloHealthIndicatorList) {
          if (sloErrorBudgetRemainingPercentage > sloHealthIndicator.getErrorBudgetRemainingPercentage()) {
            sloErrorBudgetRemainingPercentage = sloHealthIndicator.getErrorBudgetRemainingPercentage();
          }
          SloHealthIndicatorDTO sloHealthIndicatorDTO =
              SloHealthIndicatorDTO.builder()
                  .serviceLevelObjectiveIdentifier(sloHealthIndicator.getServiceLevelObjectiveIdentifier())
                  .monitoredServiceIdentifier(sloHealthIndicator.getMonitoredServiceIdentifier())
                  .errorBudgetRisk(sloHealthIndicator.getErrorBudgetRisk())
                  .errorBudgetBurnRate(sloHealthIndicator.getErrorBudgetBurnRate())
                  .errorBudgetRemainingPercentage(sloHealthIndicator.getErrorBudgetRemainingPercentage())
                  .errorBudgetRemainingMinutes(sloHealthIndicator.getErrorBudgetRemainingMinutes())
                  .build();
          sloMappedToTheirHealthIndicators.put(
              sloHealthIndicator.getServiceLevelObjectiveIdentifier(), sloHealthIndicatorDTO);
        }
        if (isNotEmpty(sloMappedToTheirHealthIndicators)) {
          sloPolicyDTO = SLOPolicyDTO.builder()
                             .sloErrorBudgetRemainingPercentage(sloErrorBudgetRemainingPercentage)
                             .statusOfMonitoredService(SLOPolicyDTO.MonitoredServiceStatus.CONFIGURED)
                             .slos(sloMappedToTheirHealthIndicators)
                             .build();
        }
      }
    }
    ExpandedValue value = SLOPolicyExpandedValue.builder().sloPolicyDTO(sloPolicyDTO).build();
    return ExpansionResponse.builder()
        .success(true)
        .key(value.getKey())
        .value(value)
        .fqn(fqn + YamlNode.PATH_SEP + YAMLFieldNameConstants.SPEC)
        .placement(ExpansionPlacementStrategy.APPEND)
        .build();
  }
}
