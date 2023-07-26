/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changehandlers;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.manifest.ManifestType.HelmChart;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.Arrays.asList;
import static java.util.Objects.isNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.execution.StageExecutionInfo.StageExecutionInfoKeys;
import io.harness.cdng.manifest.yaml.summary.HelmChartManifestSummary.HelmChartManifestSummaryKeys;
import io.harness.changehandlers.helper.ChangeHandlerHelper;
import io.harness.changestreamsframework.ChangeEvent;

import com.google.inject.Inject;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
public class CDStageHelmManifestInfoHandler extends AbstractChangeDataHandler {
  private static final String ID = "id";
  private static final String STAGE_EXECUTION_ID = "stage_execution_id";
  private static final String SERVICE_INFO = "serviceInfo";
  private static final String MANIFESTS = "manifests";
  private static final String MANIFEST_SUMMARIES = "manifestSummaries";
  @Inject ChangeHandlerHelper changeHandlerHelper;

  @Override
  public boolean handleChange(ChangeEvent<?> changeEvent, String tableName, String[] fields) {
    log.trace("In TimeScale Change Handler: {}, {}, {}", changeEvent, tableName, fields);
    List<Map<String, String>> columnValueMapping;
    List<String> primaryKeys;

    if (changeEvent == null) {
      return true;
    }

    try {
      primaryKeys = getPrimaryKeys();
      columnValueMapping = getColumnValueMapping(changeEvent);
    } catch (Exception e) {
      log.info(String.format("Not able to parse this event %s: %s", changeEvent, e));
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
    if (isInvalidEvent(changeEvent)) {
      return Collections.emptyList();
    }
    DBObject dbObject = changeEvent.getFullDocument();
    BasicDBObject executionSummaryDetails =
        (BasicDBObject) dbObject.get(StageExecutionInfoKeys.executionSummaryDetails);
    BasicDBObject serviceInfo = (BasicDBObject) executionSummaryDetails.get(SERVICE_INFO);
    BasicDBObject manifestInfo = (BasicDBObject) serviceInfo.get(MANIFESTS);
    BasicDBList manifestSummaries = (BasicDBList) manifestInfo.get(MANIFEST_SUMMARIES);
    List<Map<String, String>> nodeMap = new ArrayList<>();
    for (Object manifestSummary : manifestSummaries) {
      Map<String, String> columnValueMapping = new HashMap<>();
      BasicDBObject manifest = (BasicDBObject) manifestSummary;
      if (manifest != null && manifest.get(HelmChartManifestSummaryKeys.identifier) != null) {
        if (isNull(manifest.get(HelmChartManifestSummaryKeys.type))) {
          continue;
        }
        String type = manifest.get(HelmChartManifestSummaryKeys.type).toString();
        if (HelmChart.equals(type)) {
          changeHandlerHelper.addKeyValuePairToMapFromDBObject(
              manifest, columnValueMapping, HelmChartManifestSummaryKeys.identifier, ID);
          columnValueMapping.put("type", type);
          changeHandlerHelper.addKeyValuePairToMapFromDBObject(
              dbObject, columnValueMapping, StageExecutionInfoKeys.stageExecutionId, STAGE_EXECUTION_ID);
          changeHandlerHelper.addKeyValuePairToMapFromDBObject(
              manifest, columnValueMapping, HelmChartManifestSummaryKeys.chartVersion, "chart_version");
          changeHandlerHelper.addKeyValuePairToMapFromDBObject(
              manifest, columnValueMapping, HelmChartManifestSummaryKeys.helmVersion, "helm_version");
          nodeMap.add(columnValueMapping);
        }
      }
    }
    return nodeMap;
  }

  private boolean isInvalidEvent(ChangeEvent<?> changeEvent) {
    if (changeEvent == null) {
      return true;
    }
    DBObject dbObject = changeEvent.getFullDocument();
    if (dbObject == null) {
      return true;
    }
    BasicDBObject executionSummaryDetails =
        (BasicDBObject) dbObject.get(StageExecutionInfoKeys.executionSummaryDetails);
    if (executionSummaryDetails == null) {
      return true;
    }
    BasicDBObject serviceInfo = (BasicDBObject) executionSummaryDetails.get(SERVICE_INFO);
    if (serviceInfo == null) {
      return true;
    }
    BasicDBObject manifestInfo = (BasicDBObject) serviceInfo.get(MANIFESTS);
    if (manifestInfo == null) {
      return true;
    }
    BasicDBList manifestSummaries = (BasicDBList) manifestInfo.get(MANIFEST_SUMMARIES);
    return manifestSummaries == null;
  }

  @Override
  public List<String> getPrimaryKeys() {
    return asList(ID, STAGE_EXECUTION_ID);
  }
}
