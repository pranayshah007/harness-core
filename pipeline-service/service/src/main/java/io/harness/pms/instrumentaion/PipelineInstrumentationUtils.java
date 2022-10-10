/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.instrumentaion;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.ResponseMessage;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
@Slf4j
public class PipelineInstrumentationUtils {
  public String getIdentityFromAmbiance(Ambiance ambiance) {
    if (!ambiance.getMetadata().getTriggerInfo().getTriggeredBy().getExtraInfoMap().get("email").isEmpty()) {
      return ambiance.getMetadata().getTriggerInfo().getTriggeredBy().getExtraInfoMap().get("email");
    }
    return ambiance.getMetadata().getTriggerInfo().getTriggeredBy().getIdentifier();
  }

  public Collection<io.harness.exception.FailureType> getFailureTypesFromPipelineExecutionSummary(
      PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity) {
    if (pipelineExecutionSummaryEntity.getFailureInfo() == null) {
      return Collections.emptyList();
    }
    return pipelineExecutionSummaryEntity.getFailureInfo().getFailureTypeList();
  }

  public Set<String> getErrorMessagesFromPipelineExecutionSummary(
      PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity) {
    if (pipelineExecutionSummaryEntity.getFailureInfo() == null) {
      return Collections.emptySet();
    }
    Set<String> errorMessages = new HashSet<>();
    if (!StringUtils.isEmpty(pipelineExecutionSummaryEntity.getFailureInfo().getMessage())) {
      errorMessages.add(pipelineExecutionSummaryEntity.getFailureInfo().getMessage());
    }
    errorMessages.addAll(pipelineExecutionSummaryEntity.getFailureInfo()
                             .getResponseMessages()
                             .stream()
                             .map(ResponseMessage::getMessage)
                             .collect(Collectors.toList()));
    return errorMessages;
  }

  public String extractExceptionMessage(PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity) {
    if (pipelineExecutionSummaryEntity.getFailureInfo() == null) {
      return "";
    }
    return pipelineExecutionSummaryEntity.getFailureInfo()
        .getResponseMessages()
        .stream()
        .filter(o -> o.getCode() != ErrorCode.HINT && o.getCode() != ErrorCode.EXPLANATION)
        .map(ResponseMessage::getMessage)
        .collect(Collectors.toList())
        .toString();
  }

  public static List<String> getStageTypes(PipelineEntity pipelineEntity) {
    if (pipelineEntity == null || pipelineEntity.getYaml() == null) {
      return Collections.emptyList();
    }

    String yaml = pipelineEntity.getYaml();
    try {
      JsonNode jsonNode = new ObjectMapper(new YAMLFactory()).readTree(yaml);
      JsonNode stagesJsonNode = jsonNode.get("pipeline").get("stages");
      List<String> res = new ArrayList<>();
      if (stagesJsonNode.isArray()) {
        for (JsonNode stageNode : stagesJsonNode) {
          if (stageNode.get("parallel") != null) {
            JsonNode parallelStagesNode = stageNode.get("parallel");
            for (JsonNode stageNodeInsideParallel : parallelStagesNode) {
              res.add(stageNodeInsideParallel.get("stage").get("type").textValue());
            }
          } else {
            res.add(stageNode.get("stage").get("type").textValue());
          }
        }
      }
      return res;
    } catch (Exception ex) {
      log.error(String.format("Unable to parse stage types from Pipeline yaml: %s", yaml), ex);
      return Collections.emptyList();
    }
  }
}
