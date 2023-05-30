/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changehandlers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.changestreamsframework.ChangeEvent;
import io.harness.cvng.verificationjob.entities.VerificationJob.RuntimeParameter.RuntimeParameterKeys;
import io.harness.cvng.verificationjob.entities.VerificationJob.VerificationJobKeys;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.VerificationJobInstanceKeys;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CV)
@Slf4j
public class VerifyStepExecutionHandler extends AbstractChangeDataHandler {
  @Override
  public Map<String, String> getColumnValueMapping(ChangeEvent<?> changeEvent, String[] fields) {
    if (Objects.isNull(changeEvent)) {
      return null;
    }
    Map<String, String> columnValueMapping = new HashMap<>();
    DBObject dbObject = changeEvent.getFullDocument();
    String verificationJonInstanceId = dbObject.get("_id").toString();
    log.info("Handling change event: {} for VerificationJonInstance _id: {}", changeEvent.getUuid(),
        verificationJonInstanceId);
    columnValueMapping.put("id", verificationJonInstanceId);
    columnValueMapping.put("accountId", dbObject.get(VerificationJobInstanceKeys.accountId).toString());
    if (dbObject.get(VerificationJobInstanceKeys.planExecutionId) != null) {
      columnValueMapping.put("planExecutionId", dbObject.get(VerificationJobInstanceKeys.planExecutionId).toString());
    }
    if (dbObject.get(VerificationJobInstanceKeys.stageStepId) != null) {
      columnValueMapping.put("stageStepId", dbObject.get(VerificationJobInstanceKeys.stageStepId).toString());
    }
    if (dbObject.get(VerificationJobInstanceKeys.nodeExecutionId) != null) {
      columnValueMapping.put("nodeExecutionId", dbObject.get(VerificationJobInstanceKeys.nodeExecutionId).toString());
    }
    columnValueMapping.put(
        "startedAtTimestamp", String.valueOf(((Date) dbObject.get(VerificationJobInstanceKeys.startTime)).getTime()));
    columnValueMapping.put("deploymentStartedAtTimestamp",
        String.valueOf(((Date) dbObject.get(VerificationJobInstanceKeys.deploymentStartTime)).getTime()));
    columnValueMapping.put(
        "lastUpdatedAtTimestamp", dbObject.get(VerificationJobInstanceKeys.lastUpdatedAt).toString());
    if (dbObject.get(VerificationJobInstanceKeys.verificationStatus) != null) {
      columnValueMapping.put(
          "verificationStatus", dbObject.get(VerificationJobInstanceKeys.verificationStatus).toString());
    }
    columnValueMapping.put("executionStatus", dbObject.get(VerificationJobInstanceKeys.executionStatus).toString());
    if (dbObject.get(VerificationJobInstanceKeys.monitoredServiceType) != null) {
      columnValueMapping.put(
          "monitoredServiceType", dbObject.get(VerificationJobInstanceKeys.monitoredServiceType).toString());
    }
    if (dbObject.get(VerificationJobInstanceKeys.appliedDeploymentAnalysisTypeMap) != null) {
      columnValueMapping.put("appliedVerificationTypes",
          parseAppliedVerificationTypes(dbObject.get(VerificationJobInstanceKeys.appliedDeploymentAnalysisTypeMap)));
    }

    populateVerificationJobDetails(columnValueMapping, dbObject);
    log.info("Handled change event: {} for VerificationJonInstance _id: {}", changeEvent.getUuid(),
        verificationJonInstanceId);
    return columnValueMapping;
  }

  private static void populateVerificationJobDetails(Map<String, String> columnValueMapping, DBObject dbObject) {
    BasicDBObject verificationJob = (BasicDBObject) dbObject.get(VerificationJobInstanceKeys.resolvedJob);
    columnValueMapping.put("selectedVerificationType", verificationJob.getString("type"));
    columnValueMapping.put("orgIdentifier", verificationJob.getString(VerificationJobKeys.orgIdentifier));
    columnValueMapping.put("projectIdentifier", verificationJob.getString(VerificationJobKeys.projectIdentifier));
    columnValueMapping.put(
        "monitoredServiceIdentifier", verificationJob.getString(VerificationJobKeys.monitoredServiceIdentifier));
    columnValueMapping.put("serviceRef",
        ((BasicDBObject) verificationJob.get(VerificationJobKeys.serviceIdentifier))
            .getString(RuntimeParameterKeys.value));
    columnValueMapping.put("envRef",
        ((BasicDBObject) verificationJob.get(VerificationJobKeys.envIdentifier)).getString(RuntimeParameterKeys.value));
    columnValueMapping.put(
        "sensitivity", ((BasicDBObject) verificationJob.get("sensitivity")).getString(RuntimeParameterKeys.value));
    columnValueMapping.put("durationInMinutes",
        parseDuration(
            ((BasicDBObject) verificationJob.get(VerificationJobKeys.duration)).getString(RuntimeParameterKeys.value)));
  }

  @Override
  public List<String> getPrimaryKeys() {
    return List.of("id");
  }

  private static String parseDuration(String durationWithUnits) {
    return durationWithUnits.substring(0, durationWithUnits.length() - 1);
  }

  private static String parseAppliedVerificationTypes(Object appliedVerificationTypesObject) {
    return ((Map<String, String>) appliedVerificationTypesObject).values().toString();
  }
}
