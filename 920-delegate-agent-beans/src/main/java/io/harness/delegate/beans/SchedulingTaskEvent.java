/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType", include = As.PROPERTY)
@JsonTypeName("SchedulingTaskEvent")
@Jacksonized
@Value
@Builder
public class SchedulingTaskEvent {
  public enum Method { POST, GET, DELETE, OTHER }
  private String accountId;
  private String taskId;

  /**
   * Uri: Identifies the resource of the recipient delegate
   */
  private String uri;
  // method of string to avoid serialization/deserialization compatibility complexity of using enum
  private String method;
}
