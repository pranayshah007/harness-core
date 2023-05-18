/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.config;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Value;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionLimits {
  ExecutionLimitSpec freeNewUser;
  ExecutionLimitSpec freeBasicUser;
  ExecutionLimitSpec free;
  ExecutionLimitSpec team;
  ExecutionLimitSpec enterprise;
  List<String> overrideConfig;
  @Value
  @Builder
  public static class ExecutionLimitSpec {
    long defaultTotalExecutionCount;
    long defaultMacExecutionCount;
    long dailyMaxBuildsCount;
  }
}
