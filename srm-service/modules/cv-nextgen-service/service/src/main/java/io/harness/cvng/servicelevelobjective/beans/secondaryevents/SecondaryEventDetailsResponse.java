/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans.secondaryevents;

import static io.harness.cvng.CVConstants.SECONDARY_EVENTS_TYPE;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SecondaryEventDetailsResponse {
  @JsonProperty(SECONDARY_EVENTS_TYPE) @NotNull SecondaryEventsType type;
  @NotNull Long startTime;
  Long endTime;
  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME, property = SECONDARY_EVENTS_TYPE, include = JsonTypeInfo.As.EXTERNAL_PROPERTY)
  @NotNull
  @Valid
  SecondaryEventDetails details;

  public SecondaryEventsType getType() {
    return this.details.getType();
  }
}
