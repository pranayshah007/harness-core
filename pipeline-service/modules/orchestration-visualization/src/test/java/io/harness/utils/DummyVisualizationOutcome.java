/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.data.Outcome;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@AllArgsConstructor
@JsonTypeName("Dummy1")
@OwnedBy(PIPELINE)
@RecasterAlias("io.harness.utils.DummyVisualizationOutcome")
public class DummyVisualizationOutcome implements Outcome {
  String test;
}
