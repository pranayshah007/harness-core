/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.data.Outcome;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDC)
@Value
@Builder
@TypeAlias("resourceRestraintOutcome")
@JsonTypeName("resourceRestraintOutcome")
@RecasterAlias("io.harness.steps.resourcerestraint.beans.ResourceRestraintOutcome")
public class ResourceRestraintOutcome implements Outcome {
  @JsonIgnore String name;
  @JsonIgnore int capacity;
  String resourceUnit;
  @JsonIgnore int usage;
  @JsonIgnore int alreadyAcquiredPermits;
}
