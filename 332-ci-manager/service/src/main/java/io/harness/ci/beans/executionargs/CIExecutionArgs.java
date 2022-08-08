/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.executionargs;

import io.harness.annotation.RecasterAlias;
import io.harness.beans.execution.ExecutionSource;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@AllArgsConstructor
@Slf4j
@TypeAlias("ciExecutionArgs")
@RecasterAlias("io.harness.beans.executionargs.CIExecutionArgs")
public class CIExecutionArgs implements ExecutionArgs {
  private ExecutionSource executionSource;
  private String runSequence;
}
