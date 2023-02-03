/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans;

import io.harness.cvng.downtime.beans.EntityType;
import io.harness.cvng.downtime.beans.EntityUnavailabilityStatus;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UnavailabilityInstancesResponse {
  String orgIdentifier;
  String projectIdentifier;

  long startTime;
  long endTime;
  EntityUnavailabilityStatus status;

  String entityIdentifier;

  EntityType entityType;
}
