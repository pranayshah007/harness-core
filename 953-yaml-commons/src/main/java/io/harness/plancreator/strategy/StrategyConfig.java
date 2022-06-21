/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.strategy;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.validation.OneOfField;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@OwnedBy(PIPELINE)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@OneOfField(fields = {"matrix", "for", "parallelism"})
@RecasterAlias("io.harness.plancreator.strategy.StrategyConfig")
public class StrategyConfig {
  @JsonProperty("matrix") MatrixConfigInterface matrixConfig;
  @JsonProperty("for") HarnessForConfig forConfig;
  @JsonProperty("parallelism") int parallelism;
}
