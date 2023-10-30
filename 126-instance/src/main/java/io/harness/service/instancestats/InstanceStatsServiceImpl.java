/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.instancestats;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.models.InstanceStats;
import io.harness.models.InstanceStatsIterator;
import io.harness.repositories.instancestats.InstanceStatsRepository;
import io.harness.repositories.instancestatsiterator.InstanceStatsIteratorRepository;

import com.google.inject.Inject;
import java.time.Instant;
import lombok.AllArgsConstructor;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class InstanceStatsServiceImpl implements InstanceStatsService {
  private InstanceStatsRepository instanceStatsRepository;
  private InstanceStatsIteratorRepository instanceStatsIteratorRepository;
  private CDFeatureFlagHelper cdFeatureFlagHelper;

  @Override
  public Instant getLastSnapshotTime(String accountId, String orgId, String projectId, String serviceId)
      throws Exception {
    InstanceStats record = instanceStatsRepository.getLatestRecord(accountId, orgId, projectId, serviceId);
    InstanceStatsIterator iteratorRecord =
        instanceStatsIteratorRepository.getLatestRecord(accountId, orgId, projectId, serviceId);
    if (iteratorRecord != null) {
      if (record == null) {
        return iteratorRecord.getReportedAt().toInstant();
      }
      return record.getReportedAt().toInstant().isAfter(iteratorRecord.getReportedAt().toInstant())
          ? record.getReportedAt().toInstant()
          : iteratorRecord.getReportedAt().toInstant();
    }
    if (record == null) {
      // no record found
      return null;
    }
    return record.getReportedAt().toInstant();
  }
}
