/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans.change;

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
public class HarnessCDCurrentGenEventMetadata extends ChangeEventMetadata {
  String accountId;
  String appId;
  String serviceId;
  String environmentId;
  String workflowId;
  String status;
  long workflowStartTime;
  long workflowEndTime;
  String workflowExecutionId;
  String name;
  String artifactType;
  String artifactName;

  @Override
  public ChangeSourceType getType() {
    return ChangeSourceType.HARNESS_CD_CURRENT_GEN;
  }
}
