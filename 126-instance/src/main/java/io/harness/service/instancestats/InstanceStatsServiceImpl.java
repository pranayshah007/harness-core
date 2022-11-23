/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.instancestats;

import io.harness.ng.core.entities.Project;
import io.harness.repositories.instancestats.InstanceStatsRepository;

import com.google.inject.Inject;
import java.sql.Timestamp;
import java.time.Instant;
import lombok.AllArgsConstructor;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class InstanceStatsServiceImpl implements InstanceStatsService {
  private InstanceStatsRepository instanceStatsRepository;

  @Override
  public Instant getLastSnapshotTime(Project project) throws Exception {
    Timestamp lastSnapshotTime = instanceStatsRepository.getLastSnapshotTime(
        project.getAccountIdentifier(), project.getOrgIdentifier(), project.getIdentifier());
    if (lastSnapshotTime == null) {
      // no record found
      return null;
    }
    return lastSnapshotTime.toInstant();
  }
}
