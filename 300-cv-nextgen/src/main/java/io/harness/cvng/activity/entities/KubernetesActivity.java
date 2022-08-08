/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.activity.entities;

import io.harness.cvng.beans.activity.ActivityDTO;
import io.harness.cvng.beans.activity.ActivityType;
import io.harness.cvng.beans.activity.KubernetesActivityDTO;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.VerificationJobInstanceBuilder;
import io.harness.mongo.index.FdIndex;

import java.time.Instant;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.NotImplementedException;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@Data
@FieldNameConstants(innerTypeName = "KubernetesActivityKeys")
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@Deprecated
public class KubernetesActivity extends Activity {
  String namespace;
  String workloadName;
  String kind;
  Set<KubernetesActivityDTO> activities;
  @FdIndex Instant bucketStartTime;

  @Override
  public ActivityType getType() {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public void fromDTO(ActivityDTO activityDTO) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public void fillInVerificationJobInstanceDetails(VerificationJobInstanceBuilder verificationJobInstanceBuilder) {
    verificationJobInstanceBuilder.startTime(getActivityStartTime());
  }

  @Override
  public void validateActivityParams() {}

  @Override
  public String getActivityName() {
    return activities.size() + " kubernetes events";
  }

  @Override
  public boolean deduplicateEvents() {
    return true;
  }

  public static class KubernetesActivityUpdatableEntity
      extends ActivityUpdatableEntity<KubernetesActivity, KubernetesActivity> {
    @Override
    public Class getEntityClass() {
      return KubernetesActivity.class;
    }

    @Override
    public String getEntityKeyLongString(KubernetesActivity activity) {
      throw new UnsupportedOperationException("KubernetesActivity events have no unique key");
    }

    @Override
    public Query<KubernetesActivity> populateKeyQuery(Query<KubernetesActivity> query, KubernetesActivity changeEvent) {
      throw new UnsupportedOperationException("KubernetesActivity events have no unique key");
    }

    @Override
    public void setUpdateOperations(
        UpdateOperations<KubernetesActivity> updateOperations, KubernetesActivity activity) {
      setCommonUpdateOperations(updateOperations, activity);
    }
  }
}
