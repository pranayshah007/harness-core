/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.text.resolver;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Value;
import lombok.With;

@Value
@Builder
@OwnedBy(HarnessTeam.PIPELINE)
public class StringReplacerResponse {
  @With String finalExpressionValue;
  // If this flag is true, it means all expressions inside it are rendered and thus jexl evaluate should not be called,
  // because if jexl is called, then it will throw exception
  boolean onlyRenderedExpressions;

  boolean originalExpressionAltered;
}