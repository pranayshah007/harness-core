/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changehandlers;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.Arrays.asList;
import static java.util.Objects.isNull;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.changestreamsframework.ChangeEvent;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.helpers.RuntimeInputFormHelper;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;

import com.mongodb.DBObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
@Slf4j
@OwnedBy(CDP)
public class RuntimeInputsInfoCDChangeDataHandler extends AbstractChangeDataHandler {
  private static final String ID = "id";
  private static final String FQN_HASH = "fqn_hash";

  @Override
  public boolean handleChange(ChangeEvent<?> changeEvent, String tableName, String[] fields) {
    log.trace("In TimeScale Change Handler: {}, {}, {}", changeEvent, tableName, fields);
    List<Map<String, String>> columnValueMapping;
    List<String> primaryKeys = null;

    if (changeEvent == null) {
      return true;
    }

    try {
      primaryKeys = getPrimaryKeys();
      columnValueMapping = getColumnValueMapping(changeEvent);
    } catch (Exception e) {
      log.info(String.format("Not able to parse this event %s", changeEvent));
      return false;
    }

    Map<String, String> keyMap = new HashMap<>();
    keyMap.put(ID, changeEvent.getUuid());

    switch (changeEvent.getChangeType()) {
      case INSERT:
      case UPDATE:
        if (isNotEmpty(columnValueMapping)) {
          for (Map<String, String> column : columnValueMapping) {
            dbOperation(updateSQL(tableName, column, keyMap, primaryKeys));
          }
        }
        break;
      case DELETE:
        dbOperation(deleteSQL(tableName, keyMap));
        break;
      default:
        log.info("Change Event Type not Handled: {}", changeEvent.getChangeType());
    }
    return true;
  }

  @Override
  public Map<String, String> getColumnValueMapping(ChangeEvent<?> changeEvent, String[] fields) {
    return new HashMap<>();
  }

  public List<Map<String, String>> getColumnValueMapping(ChangeEvent<?> changeEvent) {
    if (changeEvent == null) {
      return null;
    }
    List<Map<String, String>> nodeMap = new ArrayList<>();
    DBObject dbObject = changeEvent.getFullDocument();

    if (dbObject == null) {
      return null;
    }
    if (dbObject.get(PlanExecutionSummaryKeys.inputSetYaml) == null
        || isEmpty(dbObject.get(PlanExecutionSummaryKeys.inputSetYaml).toString())) {
      return nodeMap;
    }
    if (dbObject.get(PlanExecutionSummaryKeys.pipelineTemplate) == null
        || isEmpty(dbObject.get(PlanExecutionSummaryKeys.pipelineTemplate).toString())) {
      return nodeMap;
    }
    YamlConfig inputSetYamlConfig = new YamlConfig(dbObject.get(PlanExecutionSummaryKeys.inputSetYaml).toString());
    YamlConfig pipelineTemplateYamlConfig =
        new YamlConfig(dbObject.get(PlanExecutionSummaryKeys.pipelineTemplate).toString());
    Map<FQN, Object> runtimeFqnToValueMap =
        RuntimeInputFormHelper.getRuntimeInputFormYamlConfig(pipelineTemplateYamlConfig, inputSetYamlConfig);

    if (isEmpty(runtimeFqnToValueMap)) {
      return nodeMap;
    }
    String accountId = null;
    String orgIdentifier = null;
    String projectIdentifier = null;
    String planExecutionId = null;

    if (dbObject.get(PlanExecutionSummaryKeys.accountId) != null) {
      accountId = dbObject.get(PlanExecutionSummaryKeys.accountId).toString();
    }

    if (dbObject.get(PlanExecutionSummaryKeys.orgIdentifier) != null) {
      orgIdentifier = dbObject.get(PlanExecutionSummaryKeys.orgIdentifier).toString();
    }

    if (dbObject.get(PlanExecutionSummaryKeys.projectIdentifier) != null) {
      projectIdentifier = dbObject.get(PlanExecutionSummaryKeys.projectIdentifier).toString();
    }

    if (dbObject.get(PlanExecutionSummaryKeys.planExecutionId) != null) {
      planExecutionId = dbObject.get(PlanExecutionSummaryKeys.planExecutionId).toString();
    }

    if (isNull(accountId) || isNull(orgIdentifier) || isNull(projectIdentifier) || isNull(planExecutionId)) {
      return nodeMap;
    }

    for (Map.Entry<FQN, Object> entry : runtimeFqnToValueMap.entrySet()) {
      if (isNull(entry.getKey())) {
        continue;
      }
      String fqn = entry.getKey().getExpressionFqn();
      String displayName = entry.getKey().getFieldName();
      if (!isEmpty(fqn)) {
        String[] fqnArray = fqn.split("\\.");
        if (!isEmpty(fqnArray)) {
          displayName = fqnArray[fqnArray.length - 1];
        }
      }
      Map<String, String> columnValueMapping = new HashMap<>();
      columnValueMapping.put("id", changeEvent.getUuid());
      columnValueMapping.put("account_id", accountId);
      columnValueMapping.put("org_identifier", orgIdentifier);
      columnValueMapping.put("project_identifier", projectIdentifier);
      columnValueMapping.put("plan_execution_id", planExecutionId);
      columnValueMapping.put(FQN_HASH, DigestUtils.md5Hex(fqn));
      columnValueMapping.put("display_name", displayName);
      columnValueMapping.put("fqn", fqn);
      columnValueMapping.put(
          "input_value", entry.getValue() == null ? "" : entry.getValue().toString().replaceAll("\"", "'"));
      nodeMap.add(columnValueMapping);
    }

    return nodeMap;
  }

  @Override
  public List<String> getPrimaryKeys() {
    return asList(ID, FQN_HASH);
  }
}
