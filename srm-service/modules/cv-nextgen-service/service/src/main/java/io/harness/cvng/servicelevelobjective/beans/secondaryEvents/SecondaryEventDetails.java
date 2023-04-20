/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans.secondaryEvents;

import static io.harness.cvng.CVConstants.SECONDARY_EVENTS_TYPE;

import io.harness.cvng.downtime.beans.DataCollectionFailureInstanceDetails;
import io.harness.cvng.downtime.beans.DowntimeInstanceDetails;
import io.harness.cvng.servicelevelobjective.beans.AnnotationInstanceDetails;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = DowntimeInstanceDetails.class, name = "Downtime")
  , @JsonSubTypes.Type(value = AnnotationInstanceDetails.class, name = "Annotation"),
      @JsonSubTypes.Type(value = DataCollectionFailureInstanceDetails.class, name = "DataCollectionFailure"),
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = SECONDARY_EVENTS_TYPE, include = JsonTypeInfo.As.EXTERNAL_PROPERTY)
public abstract class SecondaryEventDetails {
  @JsonIgnore public abstract SecondaryEventsType getType();
}
