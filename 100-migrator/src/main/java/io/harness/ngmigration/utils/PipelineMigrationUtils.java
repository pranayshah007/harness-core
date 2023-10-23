/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PipelineMigrationUtils {
  public static void fixBarrierInputs(JsonNode templateInputs) {
    fixBarrierInputsForExecution(templateInputs);
    fixBarrierInputsForRollback(templateInputs);
  }

  private static void fixBarrierInputsForExecution(JsonNode templateInputs) {
    ArrayNode stepGroups = (ArrayNode) templateInputs.at("/spec/execution/steps");
    if (stepGroups == null) {
      log.warn("StepGroup is null, cant fix barrier identifiers");
      return;
    }
    stepGroups.forEach(stepGroupNode -> {
      ArrayNode stepsArray = (ArrayNode) stepGroupNode.get("stepGroup").get("steps");

      stepsArray.elements().forEachRemaining(stepNode -> {
        Optional<String> optionalStepType =
            Optional.ofNullable(stepNode.get("step")).map(step -> step.get("type")).map(JsonNode::asText);
        if (optionalStepType.isPresent()) {
          if ("Barrier".equals(optionalStepType.get())) {
            ObjectNode specNode = (ObjectNode) stepNode.get("step").get("spec");
            if (specNode != null && specNode.has("barrierRef")) {
              String barrierRef = specNode.get("barrierRef").asText();
              if (barrierRef.contains("<+input>.default")) {
                String contentInsideDefault = barrierRef.replace("<+input>.default('", "").replace("')", "");

                specNode.put("barrierRef", contentInsideDefault);
              }
            }
          }
        }
      });
    });
  }

  private static void fixBarrierInputsForRollback(JsonNode templateInputs) {
    ArrayNode stepGroups = (ArrayNode) templateInputs.at("/spec/execution/rollbackSteps");
    if (stepGroups == null) {
      log.warn("StepGroup is null, cant fix barrier identifiers");
      return;
    }
    stepGroups.forEach(stepGroupNode -> {
      ArrayNode stepsArray = (ArrayNode) stepGroupNode.get("stepGroup").get("steps");

      stepsArray.elements().forEachRemaining(stepNode -> {
        Optional<String> optionalStepType =
            Optional.ofNullable(stepNode.get("step")).map(step -> step.get("type")).map(JsonNode::asText);
        if (optionalStepType.isPresent()) {
          if ("Barrier".equals(optionalStepType.get())) {
            ObjectNode specNode = (ObjectNode) stepNode.get("step").get("spec");
            if (specNode != null && specNode.has("barrierRef")) {
              String barrierRef = specNode.get("barrierRef").asText();
              if (barrierRef.contains("<+input>.default")) {
                String contentInsideDefault = barrierRef.replace("<+input>.default('", "").replace("')", "");

                specNode.put("barrierRef", contentInsideDefault);
              }
            }
          }
        }
      });
    });
  }
}
