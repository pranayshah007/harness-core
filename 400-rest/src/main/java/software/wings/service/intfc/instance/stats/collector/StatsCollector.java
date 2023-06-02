/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.instance.stats.collector;

/**
 * This API will be called by CRON job every 10 minutes to take a capture of instance state for each account.
 */
public interface StatsCollector {
  /**
   * This should:
   * <ol>
   * <li> Get the current state of instances since last time stats were saved. </li>
   * <li> Aggregate them </li>
   * <li> Save them </li>
   * </ol>
   *
   * @param accountId accountId for which to get instance data
   * @return true if at least one stat snapshot was created in database
   */
  boolean createStats(String accountId);
  boolean createStatsAtIfMissing(String accountId, long timestamp);
  boolean createServerlessStats(String accountId);
}
