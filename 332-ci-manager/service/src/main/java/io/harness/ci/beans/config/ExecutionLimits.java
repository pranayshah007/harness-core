/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.config;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Value;
import org.codehaus.jackson.annotate.JsonIgnore;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionLimits {
  ExecutionLimitSpec free;
  ExecutionLimitSpec team;
  ExecutionLimitSpec enterprise;
  List<String> overrideConfig;
  @JsonIgnore Map<String, ExecutionLimitSpec> overrideConfigMap;
  @Value
  @Builder
  public static class ExecutionLimitSpec {
    long defaultTotalExecutionCount;
    long defaultMacExecutionCount;
  }
}
