/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.tasks.ProgressData;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

/**
 * This progress callback updates the latest delegate task id for approval callback which handles polling
 */
@Value
@Builder
@JsonTypeName("approvalProgressData")
@TypeAlias("approvalProgressData")
@OwnedBy(HarnessTeam.CDC)
public class ApprovalProgressData implements ProgressData {
  String latestDelegateTaskId;
}
