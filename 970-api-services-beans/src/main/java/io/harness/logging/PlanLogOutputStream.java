/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logging;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static com.google.common.base.Joiner.on;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.provision.model.ResourceAction;
import io.harness.provision.model.TerraformPlan;
import io.harness.provision.model.TerraformResourceChange;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.zeroturnaround.exec.stream.LogOutputStream;

@EqualsAndHashCode(callSuper = false)
@Slf4j
@OwnedBy(CDP)
public class PlanLogOutputStream extends LogOutputStream {
  private static String PLAN_REF_POINT = "Plan:";
  private static String NO_CHANGES_REF_POINT = "No changes.";
  private static String PLAN_CHANGE_TYPES_SPLITTER_REGEX = "[,]";
  private static String CHANGE_TYPE_SPLITTER_REGEX = "[ ]";
  private static String TEXT_FORMAT_REGEX = "\\x1b\\[[0-9;]*[mGKHF]";
  private List<String> logs;
  private int numOfAdd;
  private int numOfChange;
  private int numOfDestroy;
  private boolean planProcessed;

  @Override
  public void processLine(String line) {
    if (logs == null) {
      logs = new ArrayList<>();
    }
    processPlanLine(line);
    logs.add(line);
  }

  public String getPlanLog() {
    if (isNotEmpty(logs)) {
      return on("\n").join(logs);
    }
    return "";
  }

  public void processPlanLine(String line) {
    if (isNotEmpty(line)) {
      if (line.contains(NO_CHANGES_REF_POINT)) {
        numOfAdd = 0;
        numOfChange = 0;
        numOfDestroy = 0;
        planProcessed = true;
        return;
      }

      if (line.contains(PLAN_REF_POINT)) {
        // example: "[0m[1mPlan:[0m 1 to add, 0 to change, 0 to destroy."
        // line will contain special characters which are formatting the text in the output. We need to remove those
        // special characters.
        parsePlanChanges(line.replaceAll(TEXT_FORMAT_REGEX, ""));
      }
    }
  }

  private boolean parsePlanChanges(String line) {
    if (planProcessed) {
      return planProcessed;
    }

    try {
      // example: "Plan: 2912 to add, 0 to change, 0 to destroy."
      int startIndex = line.indexOf(PLAN_REF_POINT) + PLAN_REF_POINT.length();
      if (startIndex > -1) {
        String processedLine = line.substring(startIndex).trim();
        String[] changeTypes = processedLine.split(PLAN_CHANGE_TYPES_SPLITTER_REGEX);
        if (EmptyPredicate.isNotEmpty(changeTypes) && changeTypes.length == 3) {
          numOfAdd = Integer.parseInt(changeTypes[0].trim().split(CHANGE_TYPE_SPLITTER_REGEX)[0]);
          numOfChange = Integer.parseInt(changeTypes[1].trim().split(CHANGE_TYPE_SPLITTER_REGEX)[0]);
          numOfDestroy = Integer.parseInt(changeTypes[2].trim().split(CHANGE_TYPE_SPLITTER_REGEX)[0]);
        }
      }

      planProcessed = true;
    } catch (Exception e) {
      numOfAdd = 0;
      numOfChange = 0;
      numOfDestroy = 0;
      log.error("Failed processing Terraform plan!", e);
    }

    return planProcessed;
  }

  public boolean processPlan() {
    return processPlan(logs);
  }

  public boolean processPlan(List<String> lines) {
    if (isEmpty(lines)) {
      return true;
    }

    for (String line : lines) {
      processPlanLine(line);
    }

    return processPlan(getPlanLog());
  }

  public boolean processPlan(String plan) {
    if (planProcessed) {
      return planProcessed;
    }

    try {
      ObjectMapper mapper = new ObjectMapper();
      TerraformPlan terraformPlan = mapper.readValue(plan, TerraformPlan.class);
      if (terraformPlan != null && terraformPlan.getTerraformResourceChanges() != null) {
        for (TerraformResourceChange terraformResourceChange : terraformPlan.getTerraformResourceChanges()) {
          if (terraformResourceChange != null && terraformResourceChange.getResourceChange() != null
              && terraformResourceChange.getResourceChange().getActions() != null) {
            for (String action : terraformResourceChange.getResourceChange().getActions()) {
              if (ResourceAction.CREATE.getName().equals(action)) {
                numOfAdd++;
                continue;
              }

              if (ResourceAction.UPDATE.getName().equals(action)) {
                numOfChange++;
                continue;
              }

              if (ResourceAction.DELETE.getName().equals(action)) {
                numOfDestroy++;
                continue;
              }
            }

          } else {
            log.warn(String.format(
                "Terraform json .plan did not contain resource change or its actions. Actual json plan: %n %s",
                getPlanLog()));
          }
        }
      } else {
        log.warn(String.format(
            "Terraform json plan is null or did not contain resource changes. Actual json plan: %n %s", getPlanLog()));
      }
      planProcessed = true;
    } catch (JsonProcessingException e) {
      numOfAdd = 0;
      numOfChange = 0;
      numOfDestroy = 0;
      log.error("Failed processing Terraform plan json!", e);
    }

    return planProcessed;
  }

  public boolean planChangesExist() {
    return numOfAdd > 0 || numOfChange > 0 || numOfDestroy > 0;
  }

  public int getNumOfResourcesToAdd() {
    return numOfAdd;
  }

  public int getNumOfResourcesToChange() {
    return numOfChange;
  }

  public int getNumOfResourcesToDestroy() {
    return numOfDestroy;
  }
}
