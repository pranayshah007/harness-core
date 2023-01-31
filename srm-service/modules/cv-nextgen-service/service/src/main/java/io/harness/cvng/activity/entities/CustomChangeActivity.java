/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.activity.entities;

import io.harness.cvng.beans.activity.ActivityDTO;
import io.harness.cvng.beans.activity.ActivityType;
import io.harness.cvng.beans.change.CustomChangeEvent;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.mongo.index.FdIndex;

import com.fasterxml.jackson.annotation.JsonTypeName;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@JsonTypeName("CUSTOM_CHANGE")
@FieldNameConstants(innerTypeName = "CustomChangeActivityKeys")
@Data
@SuperBuilder
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CustomChangeActivity extends Activity {
  @FdIndex ActivityType activityType;
  CustomChangeEvent customChangeEvent;
  long endTime;
  String user;

  @Override
  public ActivityType getType() {
    return activityType;
  }

  @Override
  public void fromDTO(ActivityDTO activityDTO) {}

  @Override
  public void fillInVerificationJobInstanceDetails(
      VerificationJobInstance.VerificationJobInstanceBuilder verificationJobInstanceBuilder) {}

  @Override
  public void validateActivityParams() {}

  @Override
  public boolean deduplicateEvents() {
    return false;
  }

  public static class CustomChangeActivityUpdatableEntity
      extends ActivityUpdatableEntity<CustomChangeActivity, CustomChangeActivity> {
    @Override
    public Class getEntityClass() {
      return CustomChangeActivity.class;
    }

    @Override
    public String getEntityKeyLongString(CustomChangeActivity activity) {
      if (activity.getActivitySourceId() != null) {
        return super.getKeyBuilder(activity)
            .add(activity.getActivitySourceId())
            .add(activity.getChangeSourceIdentifier())
            .toString();
      }
      return super.getKeyBuilder(activity).add(activity.toString()).toString();
    }

    public Query<CustomChangeActivity> populateKeyQuery(
        Query<CustomChangeActivity> query, CustomChangeActivity activity) {
      if (activity.getActivitySourceId() != null && !activity.getActivitySourceId().isEmpty()) {
        return super.populateKeyQuery(query, activity)
            .filter(ActivityKeys.activitySourceId, activity.getActivitySourceId())
            .filter(ActivityKeys.changeSourceIdentifier, activity.getChangeSourceIdentifier());
      }
      return super.populateKeyQuery(query, activity)
          .filter(ActivityKeys.changeSourceIdentifier, activity.getChangeSourceIdentifier())
          .filter(ActivityKeys.type, null);
    }

    @Override
    public void setUpdateOperations(
        UpdateOperations<CustomChangeActivity> updateOperations, CustomChangeActivity activity) {
      setCommonUpdateOperations(updateOperations, activity);
      updateOperations.set(CustomChangeActivityKeys.customChangeEvent, activity.customChangeEvent);
      updateOperations.set(CustomChangeActivityKeys.activityType, activity.activityType);
      updateOperations.set(CustomChangeActivityKeys.user, activity.user);
      updateOperations.set(CustomChangeActivityKeys.endTime, activity.endTime);
    }
  }
}
