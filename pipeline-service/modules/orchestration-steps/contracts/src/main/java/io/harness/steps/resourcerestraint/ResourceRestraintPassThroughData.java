/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.steps.io.PassThroughData;

import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
@RecasterAlias("io.harness.steps.resourcerestraint.ResourceRestraintPassThroughData")
public class ResourceRestraintPassThroughData implements PassThroughData {
  String consumerId;
  String resourceRestraintId;
  String resourceUnit;
  String name;
  int capacity;
  String releaseEntityId;
  String releaseEntityType;
}
