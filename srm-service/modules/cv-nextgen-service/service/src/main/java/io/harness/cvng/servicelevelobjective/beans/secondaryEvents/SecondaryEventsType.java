/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans.secondaryEvents;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum SecondaryEventsType {
  @JsonProperty("Downtime") DOWNTIME,
  @JsonProperty("DataCollectionFailure") DATA_COLLECTION_FAILURE,
  @JsonProperty("Annotation") ANNOTATION
}
