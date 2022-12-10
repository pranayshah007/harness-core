/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.instancestats;

import io.harness.models.InstanceStats;
import io.harness.repositories.instancestats.InstanceStatsRepository;

import com.google.inject.Inject;
import java.time.Instant;
import lombok.AllArgsConstructor;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class InstanceStatsServiceImpl implements InstanceStatsService {
  private InstanceStatsRepository instanceStatsRepository;

  @Override
  public Instant getLastSnapshotTime(String accountId, String orgId, String projectId, String serviceId) {
    InstanceStats record = instanceStatsRepository.getLatestRecord(accountId, orgId, projectId, serviceId);
    if (record == null) {
      // no record found
      return null;
    }
    return record.getReportedAt().toInstant();
  }
}
