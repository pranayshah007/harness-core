/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.commons.entities.datadeletion;

import static io.harness.ccm.commons.entities.datadeletion.DataDeletionStatus.INCOMPLETE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.CE)
public class DataDeletionStepRecord {
  DataDeletionBucket dataDeletionBucket;
  @Builder.Default DataDeletionStatus status = INCOMPLETE;
  @Builder.Default Long retryCount = 0L;
  @Builder.Default Long recordsCount = 0L;
  Long lastExecutedAt;
}
