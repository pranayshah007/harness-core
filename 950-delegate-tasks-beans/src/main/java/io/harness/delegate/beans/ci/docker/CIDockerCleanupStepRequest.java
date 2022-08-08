/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.ci.docker;

import io.harness.delegate.beans.ci.CICleanupTaskParams;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CIDockerCleanupStepRequest implements CICleanupTaskParams {
  @JsonProperty("correlation_id") String correlationID;
  @JsonProperty("id") String id;
  @Builder.Default private static final CICleanupTaskParams.Type type = Type.DOCKER;

  @Override
  public Type getType() {
    return type;
  }
}
