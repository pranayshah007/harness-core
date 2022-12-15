/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.activity.entities;

import io.harness.cvng.beans.activity.ActivityDTO;
import io.harness.cvng.beans.activity.ActivityType;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.VerificationJobInstanceBuilder;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@FieldNameConstants(innerTypeName = "HarnessCDCurrentGenActivityKeys")
@Data
@SuperBuilder
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class HarnessCDCurrentGenActivity extends Activity {
  String appId;
  String serviceId;
  String environmentId;
  String workflowId;
  String status;
  Instant workflowStartTime;
  Instant workflowEndTime;
  String workflowExecutionId;
  String name;
  String artifactType;
  String artifactName;

  @Override
  public ActivityType getType() {
    return ActivityType.HARNESS_CD_CURRENT_GEN;
  }

  @Override
  public void fromDTO(ActivityDTO activityDTO) {
    throw new UnsupportedOperationException("HarnessCD Current Gen Events can be transformed only from ChangeEventDTO");
  }

  @Override
  public void fillInVerificationJobInstanceDetails(VerificationJobInstanceBuilder verificationJobInstanceBuilder) {
    throw new UnsupportedOperationException("HarnessCD Current Gen Events shouldn't have verification jobs");
  }

  @Override
  public void validateActivityParams() {}

  @Override
  public boolean deduplicateEvents() {
    return false;
  }

  public static class HarnessCDCurrentGenActivityUpdatableEntity
      extends ActivityUpdatableEntity<HarnessCDCurrentGenActivity, HarnessCDCurrentGenActivity> {
    @Override
    public Class getEntityClass() {
      return HarnessCDCurrentGenActivity.class;
    }

    @Override
    public String getEntityKeyLongString(HarnessCDCurrentGenActivity activity) {
      return super.getKeyBuilder(activity).add(activity.getWorkflowExecutionId()).toString();
    }

    public Query<HarnessCDCurrentGenActivity> populateKeyQuery(
        Query<HarnessCDCurrentGenActivity> query, HarnessCDCurrentGenActivity activity) {
      return super.populateKeyQuery(query, activity)
          .filter(HarnessCDCurrentGenActivityKeys.workflowExecutionId, activity.getWorkflowExecutionId());
    }

    @Override
    public void setUpdateOperations(
        UpdateOperations<HarnessCDCurrentGenActivity> updateOperations, HarnessCDCurrentGenActivity activity) {
      setCommonUpdateOperations(updateOperations, activity);
      updateOperations.set(HarnessCDCurrentGenActivityKeys.workflowId, activity.getWorkflowId())
          .set(HarnessCDCurrentGenActivityKeys.workflowStartTime, activity.getWorkflowStartTime())
          .set(HarnessCDCurrentGenActivityKeys.workflowEndTime, activity.getWorkflowEndTime())
          .set(HarnessCDCurrentGenActivityKeys.appId, activity.getAppId())
          .set(HarnessCDCurrentGenActivityKeys.serviceId, activity.getServiceId())
          .set(HarnessCDCurrentGenActivityKeys.environmentId, activity.getEnvironmentId())
          .set(HarnessCDCurrentGenActivityKeys.name, activity.getName());
    }
  }
}
