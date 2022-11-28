/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.activity.entities;

import static io.harness.cvng.beans.activity.ActivityType.PAGER_DUTY;

import io.harness.cvng.beans.activity.ActivityDTO;
import io.harness.cvng.beans.activity.ActivityType;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.VerificationJobInstanceBuilder;
import io.harness.mongo.index.FdSparseIndex;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@FieldNameConstants(innerTypeName = "PagerDutyActivityKeys")
@Data
@SuperBuilder
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PagerDutyActivity extends Activity {
  @FdSparseIndex private String eventId;
  private String pagerDutyUrl;
  String status;
  Instant triggeredAt;
  String urgency;
  String htmlUrl;
  String priority;
  String assignment;
  String assignmentUrl;
  String escalationPolicy;
  String escalationPolicyUrl;

  @Override
  public ActivityType getType() {
    return PAGER_DUTY;
  }

  @Override
  public void fromDTO(ActivityDTO activityDTO) {
    throw new UnsupportedOperationException("Pagerduty Events can be transformed only from ChangeEventDTO");
  }

  @Override
  public void fillInVerificationJobInstanceDetails(VerificationJobInstanceBuilder verificationJobInstanceBuilder) {
    throw new UnsupportedOperationException("Pagerduty Events shouldn't have verification jobs");
  }

  @Override
  public void validateActivityParams() {}

  @Override
  public boolean deduplicateEvents() {
    return false;
  }

  public static class PagerDutyActivityUpdatableEntity
      extends ActivityUpdatableEntity<PagerDutyActivity, PagerDutyActivity> {
    @Override
    public Class getEntityClass() {
      return PagerDutyActivity.class;
    }

    @Override
    public String getEntityKeyLongString(PagerDutyActivity activity) {
      return getKeyBuilder(activity).add(activity.getEventId()).toString();
    }

    public Query<PagerDutyActivity> populateKeyQuery(Query<PagerDutyActivity> query, PagerDutyActivity activity) {
      return super.populateKeyQuery(query, activity).filter(PagerDutyActivityKeys.eventId, activity.getEventId());
    }

    @Override
    public void setUpdateOperations(UpdateOperations<PagerDutyActivity> updateOperations, PagerDutyActivity activity) {
      setCommonUpdateOperations(updateOperations, activity);
      updateOperations.set(PagerDutyActivityKeys.eventId, activity.getEventId())
          .set(PagerDutyActivityKeys.status, activity.getStatus())
          .set(PagerDutyActivityKeys.htmlUrl, activity.getHtmlUrl())
          .set(PagerDutyActivityKeys.pagerDutyUrl, activity.getPagerDutyUrl());

      if (activity.getUrgency() != null) {
        updateOperations.set(PagerDutyActivityKeys.urgency, activity.getUrgency());
      }
      if (activity.getPriority() != null) {
        updateOperations.set(PagerDutyActivityKeys.priority, activity.getPriority());
      }
      if (activity.getAssignment() != null) {
        updateOperations.set(PagerDutyActivityKeys.assignment, activity.getAssignment());
        updateOperations.set(PagerDutyActivityKeys.assignmentUrl, activity.getAssignmentUrl());
      }
      if (activity.getEscalationPolicy() != null) {
        updateOperations.set(PagerDutyActivityKeys.escalationPolicy, activity.getEscalationPolicy());
        updateOperations.set(PagerDutyActivityKeys.escalationPolicyUrl, activity.getEscalationPolicyUrl());
      }
    }
  }
}
