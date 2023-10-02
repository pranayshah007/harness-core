/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.beans.change;

import io.harness.cvng.beans.activity.ActivityType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class InternalChangeEventMetaData extends ChangeEventMetadata {
  ActivityType activityType;
  String updatedBy;
  InternalChangeEvent internalChangeEvent;
  Long eventStartTime;
  Long eventEndTime;
  String pipelineId;
  String stageStepId;
  String stageId;
  String planExecutionId;

  @Override
  public ChangeSourceType getType() {
    return ChangeSourceType.ofActivityType(activityType);
  }
}
