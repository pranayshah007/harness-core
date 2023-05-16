/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.execution;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXTERNAL_PROPERTY, visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = ManualExecutionSource.class, name = "MANUAL")
  , @JsonSubTypes.Type(value = WebhookExecutionSource.class, name = "WEBHOOK"),
      @JsonSubTypes.Type(value = CustomExecutionSource.class, name = "CUSTOM")
})

public interface ExecutionSource {
  enum Type { WEBHOOK, MANUAL, CUSTOM }
  ExecutionSource.Type getType();
}
