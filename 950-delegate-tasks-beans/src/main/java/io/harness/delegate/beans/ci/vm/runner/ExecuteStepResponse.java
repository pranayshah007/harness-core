/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.ci.vm.runner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExecuteStepResponse {
  @JsonProperty("exit_code") int exitCode;
  @JsonProperty("exited") boolean exited;
  @JsonProperty("error") String error;
  @JsonProperty("oom_killed") boolean oomKilled;
  @JsonProperty("outputs") Map<String, String> outputs;
  @JsonProperty("artifact") byte[] artifact;
}
