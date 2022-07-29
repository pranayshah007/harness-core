/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.preflight.handler;

import static java.lang.String.format;

import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidYamlException;
import io.harness.logging.AutoLogContext;
import io.harness.ng.core.EntityDetail;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.helpers.FQNMapGenerator;
import io.harness.pms.preflight.PreFlightStatus;
import io.harness.pms.preflight.PreflightCommonUtils;
import io.harness.pms.preflight.connector.ConnectorCheckResponse;
import io.harness.pms.preflight.entity.PreFlightEntity;
import io.harness.pms.preflight.service.PreflightService;
import io.harness.pms.yaml.YamlUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Builder
@Slf4j
public class AsyncPreFlightHandler implements Runnable {
  private final PreFlightEntity entity;
  private final List<EntityDetail> entityDetails;
  private final PreflightService preflightService;

  @Override
  public void run() {
    try (AutoLogContext ignore = entity.autoLogContext()) {
      log.info("Handling preflight check with id " + entity.getUuid() + " for pipeline with id "
          + entity.getPipelineIdentifier());
      Map<String, Object> fqnToObjectMapMergedYaml = new HashMap<>();
      try {
        Map<FQN, Object> fqnObjectMap =
            FQNMapGenerator.generateFQNMap(YamlUtils.readTree(entity.getPipelineYaml()).getNode().getCurrJsonNode());
        fqnObjectMap.keySet().forEach(
            fqn -> fqnToObjectMapMergedYaml.put(fqn.getExpressionFqn(), fqnObjectMap.get(fqn)));
      } catch (IOException e) {
        log.error(format("Invalid merged pipeline yaml. Error in node [%s]", YamlUtils.getErrorNodePartialFQN(e)), e);
        throw new InvalidYamlException(
            format("Invalid merged pipeline yaml. Error in node [%s]", YamlUtils.getErrorNodePartialFQN(e)), e);
      }
      // update status to in progress
      preflightService.updateStatus(entity.getUuid(), PreFlightStatus.IN_PROGRESS, null);

      List<EntityDetail> connectorUsages = entityDetails.stream()
                                               .filter(entityDetail -> entityDetail.getType() == EntityType.CONNECTORS)
                                               .collect(Collectors.toList());
      List<ConnectorCheckResponse> connectorCheckResponses =
          preflightService.updateConnectorCheckResponses(entity.getAccountIdentifier(), entity.getOrgIdentifier(),
              entity.getProjectIdentifier(), entity.getUuid(), fqnToObjectMapMergedYaml, connectorUsages);
      PreFlightStatus overallStatus =
          PreflightCommonUtils.getOverallStatus(connectorCheckResponses, entity.getPipelineInputResponse());
      preflightService.updateStatus(entity.getUuid(), overallStatus, null);
      log.info("Preflight Check with id " + entity.getUuid() + " completed with status: " + overallStatus);
    } catch (Exception e) {
      log.error("Error occurred while handling preflight check", e);
      preflightService.updateStatus(
          entity.getUuid(), PreFlightStatus.FAILURE, PreflightCommonUtils.getInternalIssueErrorInfo(e));
    }
  }
}
