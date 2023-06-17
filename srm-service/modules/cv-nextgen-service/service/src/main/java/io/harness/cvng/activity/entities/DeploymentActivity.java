/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.activity.entities;

import static io.harness.cvng.core.services.CVNextGenConstants.DATA_COLLECTION_DELAY;

import io.harness.cvng.beans.activity.ActivityDTO;
import io.harness.cvng.beans.activity.ActivityType;
import io.harness.cvng.beans.activity.DeploymentActivityDTO;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.VerificationJobInstanceBuilder;
import io.harness.mongo.index.FdSparseIndex;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.base.Preconditions;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@JsonTypeName("DEPLOYMENT")
@FieldNameConstants(innerTypeName = "DeploymentActivityKeys")
@Data
@SuperBuilder
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DeploymentActivity extends Activity {
  Long dataCollectionDelayMs;
  Set<String> oldVersionHosts;
  Set<String> newVersionHosts;
  Integer newHostsTrafficSplitPercentage;
  String deploymentTag;
  @Getter(AccessLevel.NONE) @NotNull Long verificationStartTime;
  @FdSparseIndex String planExecutionId;
  String pipelineId;
  String stageStepId;
  String stageId;
  String artifactType;
  String artifactTag;
  String deploymentStatus;
  boolean isDemoActivity;

  @Override
  public ActivityType getType() {
    return ActivityType.DEPLOYMENT;
  }

  @Override
  public void fromDTO(ActivityDTO activityDTO) {
    Preconditions.checkState(activityDTO instanceof DeploymentActivityDTO);
    DeploymentActivityDTO deploymentActivityDTO = (DeploymentActivityDTO) activityDTO;
    setDataCollectionDelayMs(deploymentActivityDTO.getDataCollectionDelayMs());
    setOldVersionHosts(deploymentActivityDTO.getOldVersionHosts());
    setNewVersionHosts(deploymentActivityDTO.getNewVersionHosts());
    setNewHostsTrafficSplitPercentage(deploymentActivityDTO.getNewHostsTrafficSplitPercentage());
    setDeploymentTag(deploymentActivityDTO.getDeploymentTag());
    setVerificationStartTime(deploymentActivityDTO.getVerificationStartTime());
    setType(ActivityType.DEPLOYMENT);
    addCommonFields(activityDTO);
  }

  @Override
  public void fillInVerificationJobInstanceDetails(VerificationJobInstanceBuilder verificationJobInstanceBuilder) {
    verificationJobInstanceBuilder.oldVersionHosts(this.getOldVersionHosts());
    verificationJobInstanceBuilder.newVersionHosts(this.getNewVersionHosts());
    verificationJobInstanceBuilder.newHostsTrafficSplitPercentage(this.getNewHostsTrafficSplitPercentage());
    verificationJobInstanceBuilder.dataCollectionDelay(this.getDataCollectionDelay());
    verificationJobInstanceBuilder.startTime(this.getVerificationStartTime());
    verificationJobInstanceBuilder.planExecutionId(this.getPlanExecutionId());
    verificationJobInstanceBuilder.stageStepId(this.getStageStepId());
  }

  @Override
  public void validateActivityParams() {}

  @Override
  public boolean deduplicateEvents() {
    return false;
  }

  @JsonIgnore
  public Duration getDataCollectionDelay() {
    if (dataCollectionDelayMs == null) {
      return DATA_COLLECTION_DELAY;
    } else {
      return Duration.ofMillis(dataCollectionDelayMs);
    }
  }

  @JsonIgnore
  public Instant getVerificationStartTime() {
    if (verificationStartTime != null) {
      return Instant.ofEpochMilli(this.verificationStartTime);
    }
    return null;
  }

  public static class DeploymentActivityUpdatableEntity
      extends ActivityUpdatableEntity<DeploymentActivity, DeploymentActivity> {
    @Override
    public Class getEntityClass() {
      return DeploymentActivity.class;
    }

    @Override
    public String getEntityKeyLongString(DeploymentActivity activity) {
      return super.getKeyBuilder(activity).add(activity.getPlanExecutionId()).add(activity.getStageStepId()).toString();
    }

    public Query<DeploymentActivity> populateKeyQuery(Query<DeploymentActivity> query, DeploymentActivity activity) {
      return super.populateKeyQuery(query, activity)
          .filter(DeploymentActivityKeys.planExecutionId, activity.getPlanExecutionId())
          .filter(DeploymentActivityKeys.stageStepId, activity.getStageStepId());
    }

    @Override
    public void setUpdateOperations(
        UpdateOperations<DeploymentActivity> updateOperations, DeploymentActivity activity) {
      setCommonUpdateOperations(updateOperations, activity);
      updateOperations.set(DeploymentActivityKeys.planExecutionId, activity.getPlanExecutionId())
          .set(DeploymentActivityKeys.pipelineId, activity.getPipelineId())
          .set(DeploymentActivityKeys.stageStepId, activity.getStageStepId())
          .set(DeploymentActivityKeys.stageId, activity.getStageId());
      if (activity.getArtifactType() != null) {
        updateOperations.set(DeploymentActivityKeys.artifactType, activity.getArtifactType());
      }
      if (activity.getArtifactTag() != null) {
        updateOperations.set(DeploymentActivityKeys.artifactTag, activity.getArtifactTag());
      }
      if (activity.getDeploymentStatus() != null) {
        updateOperations.set(DeploymentActivityKeys.deploymentStatus, activity.getDeploymentStatus());
      }
    }
  }
}
