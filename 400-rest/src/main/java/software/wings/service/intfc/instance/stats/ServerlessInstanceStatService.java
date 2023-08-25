/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.instance.stats;

import software.wings.beans.infrastructure.instance.InvocationCount.InvocationCountKey;
import software.wings.beans.infrastructure.instance.stats.ServerlessInstanceStats;
import software.wings.resources.stats.model.ServerlessInstanceTimeline;

import java.time.Instant;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * This service is used to render the timelines and provide aggregates on user dashboard.
 */
@ParametersAreNonnullByDefault
public interface ServerlessInstanceStatService {
  boolean save(ServerlessInstanceStats stats);
  @Nullable Instant getLastSnapshotTime(String accountId);
  @Nullable Instant getFirstSnapshotTime(String accountId);
  ServerlessInstanceTimeline aggregate(String accountId, long from, long to, InvocationCountKey invocationCountKey);
}
