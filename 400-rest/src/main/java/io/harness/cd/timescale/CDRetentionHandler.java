/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cd.timescale;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.timescaledb.retention.BaseRetentionHandler;
import io.harness.timescaledb.retention.RetentionManager;

import com.google.inject.Inject;
import com.google.inject.name.Named;

@OwnedBy(HarnessTeam.CDP)
public class CDRetentionHandler extends BaseRetentionHandler {
  private final String cdTsDbRetentionPeriodMonths;
  private static final String INSTANCE_STATS = "instance_stats";
  private static final String INSTANCE_STATS_DAY = "instance_stats_day";
  private static final String INSTANCE_STATS_HOUR = "instance_stats_hour";

  @Inject
  public CDRetentionHandler(
      @Named("cdTsDbRetentionPeriodMonths") String cdTsDbRetentionPeriodMonths, RetentionManager retentionManager) {
    super(retentionManager);
    this.cdTsDbRetentionPeriodMonths = cdTsDbRetentionPeriodMonths;
  }

  @Override
  public void configureRetentionPolicy() {
    this.retentionManager.addPolicy(INSTANCE_STATS, cdTsDbRetentionPeriodMonths);
    this.retentionManager.addPolicy(INSTANCE_STATS_DAY, cdTsDbRetentionPeriodMonths);
    this.retentionManager.addPolicy(INSTANCE_STATS_HOUR, cdTsDbRetentionPeriodMonths);
  }
}
