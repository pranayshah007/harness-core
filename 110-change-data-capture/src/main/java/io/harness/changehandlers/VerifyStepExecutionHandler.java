/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changehandlers;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.changestreamsframework.ChangeEvent;
import io.harness.cvng.verificationjob.entities.VerificationJob.RuntimeParameter.RuntimeParameterKeys;
import io.harness.cvng.verificationjob.entities.VerificationJob.VerificationJobKeys;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.VerificationJobInstanceKeys;
import io.harness.serializer.JsonUtils;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.text.StrBuilder;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(HarnessTeam.CV)
public class VerifyStepExecutionHandler extends AbstractChangeDataHandler {
  private static final String EMPTY_ARRAY = "{}";
  @Override
  public Map<String, String> getColumnValueMapping(ChangeEvent<?> changeEvent, String[] fields) {
    if (Objects.isNull(changeEvent)) {
      return null;
    }
    Map<String, String> columnValueMapping = new HashMap<>();
    DBObject dbObject = changeEvent.getFullDocument();
    String verificationJonInstanceId = dbObject.get("_id").toString();
    columnValueMapping.put("id", verificationJonInstanceId);
    if (dbObject.get(VerificationJobInstanceKeys.accountId) != null) {
      columnValueMapping.put("accountId", dbObject.get(VerificationJobInstanceKeys.accountId).toString());
    }
    if (dbObject.get(VerificationJobInstanceKeys.planExecutionId) != null) {
      columnValueMapping.put("planExecutionId", dbObject.get(VerificationJobInstanceKeys.planExecutionId).toString());
    }
    if (dbObject.get(VerificationJobInstanceKeys.stageStepId) != null) {
      columnValueMapping.put("stageStepId", dbObject.get(VerificationJobInstanceKeys.stageStepId).toString());
    }
    if (dbObject.get(VerificationJobInstanceKeys.nodeExecutionId) != null) {
      columnValueMapping.put("nodeExecutionId", dbObject.get(VerificationJobInstanceKeys.nodeExecutionId).toString());
    }
    if (dbObject.get(VerificationJobInstanceKeys.startTime) != null) {
      columnValueMapping.put(
          "startedAtTimestamp", String.valueOf(((Date) dbObject.get(VerificationJobInstanceKeys.startTime)).getTime()));
    }
    if (dbObject.get(VerificationJobInstanceKeys.deploymentStartTime) != null) {
      columnValueMapping.put("deploymentStartedAtTimestamp",
          String.valueOf(((Date) dbObject.get(VerificationJobInstanceKeys.deploymentStartTime)).getTime()));
    }
    if (dbObject.get(VerificationJobInstanceKeys.lastUpdatedAt) != null) {
      columnValueMapping.put(
          "lastUpdatedAtTimestamp", dbObject.get(VerificationJobInstanceKeys.lastUpdatedAt).toString());
    }
    if (dbObject.get(VerificationJobInstanceKeys.verificationStatus) != null) {
      columnValueMapping.put(
          "verificationStatus", dbObject.get(VerificationJobInstanceKeys.verificationStatus).toString());
    }
    if (dbObject.get(VerificationJobInstanceKeys.executionStatus) != null) {
      columnValueMapping.put("executionnStatus", dbObject.get(VerificationJobInstanceKeys.executionStatus).toString());
    }
    if (dbObject.get(VerificationJobInstanceKeys.monitoredServiceType) != null) {
      columnValueMapping.put(
          "monitoredServiceType", dbObject.get(VerificationJobInstanceKeys.monitoredServiceType).toString());
    }
    if (dbObject.get(VerificationJobInstanceKeys.appliedDeploymentAnalysisTypeMap) != null) {
      columnValueMapping.put("appliedVerificationTypes",
          parseAppliedVerificationTypes(dbObject.get(VerificationJobInstanceKeys.appliedDeploymentAnalysisTypeMap)));
    }

    populateVerificationJobDetails(columnValueMapping, dbObject);
    return columnValueMapping;
  }

  private static void populateVerificationJobDetails(Map<String, String> columnValueMapping, DBObject dbObject) {
    if (dbObject.get(VerificationJobInstanceKeys.resolvedJob) != null) {
      BasicDBObject verificationJob = (BasicDBObject) dbObject.get(VerificationJobInstanceKeys.resolvedJob);
      if (verificationJob.get("type") != null) {
        columnValueMapping.put("selectedVerificationType", verificationJob.getString("type"));
      }
      if (verificationJob.get(VerificationJobKeys.orgIdentifier) != null) {
        columnValueMapping.put("orgIdentifier", verificationJob.getString(VerificationJobKeys.orgIdentifier));
      }
      if (verificationJob.get(VerificationJobKeys.projectIdentifier) != null) {
        columnValueMapping.put("projectIdentifier", verificationJob.getString(VerificationJobKeys.projectIdentifier));
      }
      if (verificationJob.get(VerificationJobKeys.monitoredServiceIdentifier) != null) {
        columnValueMapping.put(
            "monitoredServiceIdentifier", verificationJob.getString(VerificationJobKeys.monitoredServiceIdentifier));
      }
      if (verificationJob.get(VerificationJobKeys.serviceIdentifier) != null) {
        columnValueMapping.put("serviceRef",
            ((BasicDBObject) verificationJob.get(VerificationJobKeys.serviceIdentifier))
                .getString(RuntimeParameterKeys.value));
      }
      if (verificationJob.get(VerificationJobKeys.envIdentifier) != null) {
        columnValueMapping.put("envRef",
            ((BasicDBObject) verificationJob.get(VerificationJobKeys.envIdentifier))
                .getString(RuntimeParameterKeys.value));
      }
      if (verificationJob.get("sensitivity") != null) {
        columnValueMapping.put(
            "sensitivity", ((BasicDBObject) verificationJob.get("sensitivity")).getString(RuntimeParameterKeys.value));
      }
      if (verificationJob.get(VerificationJobKeys.duration) != null) {
        columnValueMapping.put("durationInMinutes",
            parseDuration(((BasicDBObject) verificationJob.get(VerificationJobKeys.duration))
                              .getString(RuntimeParameterKeys.value)));
      }
    }
  }

  @Override
  public List<String> getPrimaryKeys() {
    return List.of("id");
  }

  private static String parseDuration(String durationWithUnits) {
    return durationWithUnits.substring(0, durationWithUnits.length() - 1);
  }

  private static String parseAppliedVerificationTypes(Object appliedVerificationTypesObject) {
    Collection<String> appliedVerificationTypes = ((Map<String, String>) appliedVerificationTypesObject).values();
    if (CollectionUtils.isNotEmpty(appliedVerificationTypes)) {
      String listString = JsonUtils.asJson(appliedVerificationTypes);
      StrBuilder strBuilder = new StrBuilder(listString);
      strBuilder.replaceAll("[", "{").replaceAll("]", "}");
      return strBuilder.toString();
    }
    return EMPTY_ARRAY;
  }
}
