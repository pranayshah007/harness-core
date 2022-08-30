/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gitfileactivity.beans;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;

import io.harness.ng.DbAliases;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Builder
@StoreIn(DbAliases.NG_MANAGER)
@Document("gitFileProcessingSummaryNG")
@TypeAlias("io.harness.gitsync.gitfileactivity.beans.gitFileProcessingSummary")
@OwnedBy(DX)
public class GitFileProcessingSummary {
  private final Long failureCount;
  private final Long successCount;
  private final Long totalCount;
  private final Long skippedCount;
  private final Long queuedCount;
}
