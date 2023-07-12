/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.activity.entities;

import io.harness.cvng.beans.activity.ActivityDTO;
import io.harness.cvng.beans.activity.ActivityType;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.VerificationJobInstanceBuilder;
import io.harness.mongo.index.FdSparseIndex;

import com.fasterxml.jackson.annotation.JsonTypeName;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@JsonTypeName("SRM_STEP_ANALYSIS")
@FieldNameConstants(innerTypeName = "SRMStepAnalysisActivityKeys")
@Data
@SuperBuilder
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SRMStepAnalysisActivity extends Activity {
  @FdSparseIndex String planExecutionId;
  String pipelineId;
  String stageStepId;
  String stageId;
  String artifactType;
  String artifactTag;
  String executionNotificationDetailsId;

  @Override
  public ActivityType getType() {
    return ActivityType.SRM_STEP_ANALYSIS;
  }

  @Override
  public void fromDTO(ActivityDTO activityDTO) {}

  @Override
  public void fillInVerificationJobInstanceDetails(VerificationJobInstanceBuilder verificationJobInstanceBuilder) {}

  @Override
  public void validateActivityParams() {}

  @Override
  public boolean deduplicateEvents() {
    return false;
  }

  public static class SRMAnalysiActivityUpdatableEntity
      extends ActivityUpdatableEntity<SRMStepAnalysisActivity, SRMStepAnalysisActivity> {
    @Override
    public Class getEntityClass() {
      return SRMStepAnalysisActivity.class;
    }

    @Override
    public String getEntityKeyLongString(SRMStepAnalysisActivity activity) {
      return super.getKeyBuilder(activity).add(activity.getPlanExecutionId()).add(activity.getStageStepId()).toString();
    }

    public Query<SRMStepAnalysisActivity> populateKeyQuery(
        Query<SRMStepAnalysisActivity> query, SRMStepAnalysisActivity activity) {
      return super.populateKeyQuery(query, activity)
          .filter(SRMStepAnalysisActivityKeys.planExecutionId, activity.getPlanExecutionId())
          .filter(SRMStepAnalysisActivityKeys.stageStepId, activity.getStageStepId());
    }

    @Override
    public void setUpdateOperations(
        UpdateOperations<SRMStepAnalysisActivity> updateOperations, SRMStepAnalysisActivity activity) {
      setCommonUpdateOperations(updateOperations, activity);
      updateOperations.set(SRMStepAnalysisActivityKeys.planExecutionId, activity.getPlanExecutionId())
          .set(SRMStepAnalysisActivityKeys.pipelineId, activity.getPipelineId())
          .set(SRMStepAnalysisActivityKeys.stageStepId, activity.getStageStepId())
          .set(SRMStepAnalysisActivityKeys.stageId, activity.getStageId());
      if (activity.getArtifactType() != null) {
        updateOperations.set(SRMStepAnalysisActivityKeys.artifactType, activity.getArtifactType());
      }
      if (activity.getArtifactTag() != null) {
        updateOperations.set(SRMStepAnalysisActivityKeys.artifactTag, activity.getArtifactTag());
      }
    }
  }
}
