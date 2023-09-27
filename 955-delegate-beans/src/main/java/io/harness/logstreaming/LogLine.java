/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logstreaming;

import io.harness.logging.LogLevel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LogLine {
  @JsonProperty("level") @NotNull LogLevel level;

  @JsonProperty("out") @NotNull String message;

  @JsonProperty("time") @NotNull Instant timestamp;

  @JsonProperty("pos") int position;

  @JsonProperty("args") Map<String, String> arguments;

  @JsonIgnore boolean skipColoring;
}
