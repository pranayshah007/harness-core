/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.downtime.beans;

import static io.harness.cvng.servicelevelobjective.beans.secondaryevents.SecondaryEventsType.DATA_COLLECTION_FAILURE;
import static io.harness.cvng.servicelevelobjective.beans.secondaryevents.SecondaryEventsType.DOWNTIME;

import io.harness.cvng.servicelevelobjective.beans.secondaryevents.SecondaryEventsType;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum EntityType {
  @JsonProperty("MaintenanceWindow") MAINTENANCE_WINDOW,
  @JsonProperty("Slo") SLO,
  @JsonProperty("MonitoredService") MONITORED_SERVICE;

  public SecondaryEventsType getSecondaryEventTypeFromEntityType() {
    switch (this) {
      case MAINTENANCE_WINDOW:
        return DOWNTIME;
      case SLO:
        return DATA_COLLECTION_FAILURE;
      default:
        throw new IllegalStateException("No Secondary event exists for entity " + this.name());
    }
  }
}
