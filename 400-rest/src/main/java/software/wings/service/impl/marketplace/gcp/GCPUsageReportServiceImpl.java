/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.marketplace.gcp;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.persistence.HIterator;

import software.wings.beans.marketplace.gcp.GCPUsageReport;
import software.wings.beans.marketplace.gcp.GCPUsageReport.GCPUsageReportKeys;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.marketplace.gcp.GCPUsageReportService;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Sort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

/**
 * Mongo backed implementation for gcpUsage service.
 */
@OwnedBy(PL)
@Slf4j
@Singleton
@ParametersAreNonnullByDefault
public class GCPUsageReportServiceImpl implements GCPUsageReportService {
  @Inject private WingsPersistence persistence;

  @Override
  public boolean create(GCPUsageReport gcpUsageReport) {
    String id = persistence.save(gcpUsageReport);

    if (null == id) {
      log.error("Could not create gcpUsageReport: {}", gcpUsageReport);
      return false;
    }

    log.info("Saved gcpUsageReport. accountId: {}, startTime: {}, endTime: {}, instanceCount {}, id {}",
        gcpUsageReport.getAccountId(), gcpUsageReport.getStartTimestamp(), gcpUsageReport.getEndTimestamp(),
        gcpUsageReport.getInstanceUsage(), id);

    return true;
  }

  @Override
  @Nullable
  public Instant fetchLastGCPUsageReportTime(String accountId) {
    FindOptions options = new FindOptions();
    options.limit(1);

    List<GCPUsageReport> gcpUsageReports = persistence.createQuery(GCPUsageReport.class)
                                               .filter(GCPUsageReportKeys.accountId, accountId)
                                               .order(Sort.descending(GCPUsageReportKeys.startTimestamp))
                                               .asList(options);

    if (CollectionUtils.isEmpty(gcpUsageReports)) {
      return null;
    }

    return gcpUsageReports.get(0).getEndTimestamp();
  }

  @Nullable
  @Override
  public List<GCPUsageReport> fetchGCPUsageReport(String accountId, Instant from, Instant to) {
    Preconditions.checkArgument(to.isAfter(from), "'to' timestamp should be after 'from'");
    List<GCPUsageReport> gcpUsageReports = new ArrayList<>();

    try (HIterator<GCPUsageReport> iterator = fetchGCPUsageReportIterator(accountId, from, to)) {
      while (iterator.hasNext()) {
        gcpUsageReports.add(iterator.next());
      }
    }
    return gcpUsageReports;
  }

  private HIterator<GCPUsageReport> fetchGCPUsageReportIterator(String accountId, Instant from, Instant to) {
    return new HIterator<>(persistence.createQuery(GCPUsageReport.class)
                               .filter(GCPUsageReportKeys.accountId, accountId)
                               .field(GCPUsageReportKeys.startTimestamp)
                               .greaterThanOrEq(from)
                               .field(GCPUsageReportKeys.startTimestamp)
                               .lessThan(to)
                               .order(Sort.ascending(GCPUsageReportKeys.startTimestamp))
                               .fetch());
  }
}
