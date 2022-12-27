/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans.activity;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@Data
@FieldNameConstants(innerTypeName = "DeploymentActivityDTOKeys")
@SuperBuilder
@NoArgsConstructor
@JsonTypeName("DEPLOYMENT")
@EqualsAndHashCode(callSuper = true)
public class DeploymentActivityDTO extends ActivityDTO {
  Long dataCollectionDelayMs;
  Set<String> oldVersionHosts;
  Set<String> newVersionHosts;
  Integer newHostsTrafficSplitPercentage;
  String deploymentTag;
  Long verificationStartTime;

  @Override
  public ActivityType getType() {
    return ActivityType.DEPLOYMENT;
  }
}
