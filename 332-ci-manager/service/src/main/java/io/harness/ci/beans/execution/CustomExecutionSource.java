/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.execution;

import static io.harness.beans.execution.ExecutionSource.Type.CUSTOM;

import io.harness.annotation.RecasterAlias;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@JsonTypeName("Custom")
@TypeAlias("CUSTOM")
@RecasterAlias("io.harness.beans.execution.CustomExecutionSource")
public class CustomExecutionSource implements ExecutionSource {
  private String branch;
  private String tag;

  @Override
  public Type getType() {
    return CUSTOM;
  }
}
