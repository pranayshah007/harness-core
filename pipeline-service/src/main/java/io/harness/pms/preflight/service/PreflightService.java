/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.preflight.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.EntityDetail;
import io.harness.pms.preflight.PreFlightDTO;
import io.harness.pms.preflight.PreFlightEntityErrorInfo;
import io.harness.pms.preflight.PreFlightStatus;
import io.harness.pms.preflight.connector.ConnectorCheckResponse;
import io.harness.pms.preflight.entity.PreFlightEntity;
import io.harness.pms.preflight.inputset.PipelineInputResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;

@OwnedBy(HarnessTeam.PIPELINE)
public interface PreflightService {
  void updateStatus(String id, PreFlightStatus status, PreFlightEntityErrorInfo errorInfo);

  List<ConnectorCheckResponse> updateConnectorCheckResponses(String accountId, String orgId, String projectId,
      String preflightEntityId, Map<String, Object> fqnToObjectMapMergedYaml, List<EntityDetail> connectorUsages);

  PreFlightEntity saveInitialPreflightEntity(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String pipelineYaml, List<EntityDetail> entityDetails,
      List<PipelineInputResponse> pipelineInputResponses);

  PreFlightDTO getPreflightCheckResponse(String preflightCheckId);

  String startPreflightCheck(@NotNull String accountId, @NotNull String orgIdentifier,
      @NotNull String projectIdentifier, @NotNull String pipelineIdentifier, String inputSetPipelineYaml)
      throws IOException;
}
