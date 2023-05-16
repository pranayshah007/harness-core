/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
public class RetryStagesMetadata {
  // RetryExecutionMetadata is for execution summary to store execution ids of root and parent and
  // RetryExecutionParameters has some additional fields like pipeline yaml which we don't need that's why we created
  // this new class to only store stage identifiers of retried and skipped stages.
  List<String> retryStagesIdentifier;
  List<String> skipStagesIdentifier;
}
