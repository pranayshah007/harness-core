/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.preflight;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectivityStatus;
import io.harness.pms.preflight.connector.ConnectorCheckResponse;
import io.harness.pms.preflight.inputset.PipelineInputResponse;

import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.annotations.VisibleForTesting;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
@Slf4j
public class PreflightCommonUtils {
  public PreFlightEntityErrorInfo getNotFoundErrorInfo() {
    return PreFlightEntityErrorInfo.builder()
        .summary("Connector not found or does not exist")
        .causes(
            Collections.singletonList(PreFlightCause.builder().cause("Connector not found or does not exist").build()))
        .resolution(
            Collections.singletonList(PreFlightResolution.builder().resolution("Create this ConnectorDisconnectHandler").build()))
        .build();
  }

  public PreFlightEntityErrorInfo getInvalidConnectorInfo() {
    return PreFlightEntityErrorInfo.builder()
        .summary("Connector not valid")
        .causes(Collections.singletonList(
            PreFlightCause.builder().cause("The ConnectorDisconnectHandler YAML provided on git is invalid").build()))
        .resolution(
            Collections.singletonList(PreFlightResolution.builder().resolution("Fix the ConnectorDisconnectHandler yaml").build()))
        .build();
  }

  public PreFlightEntityErrorInfo getInternalIssueErrorInfo(Exception exception) {
    return PreFlightEntityErrorInfo.builder()
        .summary(exception.getMessage())
        .causes(Collections.singletonList(PreFlightCause.builder().cause("Internal Server Error").build()))
        .build();
  }

  public String getStageName(Map<String, Object> fqnToObjectMapMergedYaml, String identifier) {
    if (fqnToObjectMapMergedYaml == null
        || fqnToObjectMapMergedYaml.get("pipeline.stages." + identifier + ".name") == null) {
      log.error("FqnToObjectMapMergedYaml found null or given stageIdentifier [{}]  is not present in the pipeline",
          identifier);
      return null;
    }
    TextNode stageNameNode = (TextNode) fqnToObjectMapMergedYaml.get("pipeline.stages." + identifier + ".name");
    return stageNameNode.asText();
  }

  public PreFlightStatus getPreFlightStatus(ConnectivityStatus status) {
    switch (status) {
      case SUCCESS:
        return PreFlightStatus.SUCCESS;
      case FAILURE:
        return PreFlightStatus.FAILURE;
      case PARTIAL:
      default:
        return PreFlightStatus.IN_PROGRESS;
    }
  }

  @VisibleForTesting
  PreFlightStatus getPipelineInputStatus(List<PipelineInputResponse> pipelineInputResponse) {
    for (PipelineInputResponse response : pipelineInputResponse) {
      if (!response.isSuccess()) {
        return PreFlightStatus.FAILURE;
      }
    }
    return PreFlightStatus.SUCCESS;
  }

  @VisibleForTesting
  PreFlightStatus getConnectorCheckStatus(List<ConnectorCheckResponse> connectorCheckResponse) {
    for (ConnectorCheckResponse response : connectorCheckResponse) {
      PreFlightStatus status = response.getStatus();
      if (status != PreFlightStatus.SUCCESS) {
        return status;
      }
    }
    return PreFlightStatus.SUCCESS;
  }

  public PreFlightStatus getOverallStatus(
      List<ConnectorCheckResponse> connectorCheckResponse, List<PipelineInputResponse> pipelineInputResponses) {
    if (getConnectorCheckStatus(connectorCheckResponse) == PreFlightStatus.FAILURE
        || getPipelineInputStatus(pipelineInputResponses) == PreFlightStatus.FAILURE) {
      return PreFlightStatus.FAILURE;
    }
    return PreFlightStatus.SUCCESS;
  }
}
