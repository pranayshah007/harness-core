/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.activity.entities;

import io.harness.cvng.beans.activity.ActivityDTO;
import io.harness.cvng.beans.activity.ActivityType;
import io.harness.cvng.beans.change.InternalChangeEventMetaData;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@JsonTypeName("INTERNAL_CHANGE")
@FieldNameConstants(innerTypeName = "InternalChangeActivityKeys")
@Data
@SuperBuilder
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class InternalChangeActivity extends Activity {
  ActivityType type;
  String updatedBy;
  InternalChangeEventMetaData.EventDetails eventDetails;

  @Override
  public ActivityType getType() {
    return type;
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
}
