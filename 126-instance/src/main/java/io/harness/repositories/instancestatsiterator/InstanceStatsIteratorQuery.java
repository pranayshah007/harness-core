/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.instancestatsiterator;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.CDP)
public enum InstanceStatsIteratorQuery {
  FETCH_LATEST_RECORD_PROJECT_LEVEL(
      "SELECT * FROM NG_INSTANCE_STATS_ITERATOR WHERE ACCOUNTID=? AND ORGID=? AND PROJECTID=? AND SERVICEID=? "
      + "ORDER BY REPORTEDAT DESC LIMIT 1"),

  FETCH_LATEST_RECORD_ORG_LEVEL(
      "SELECT * FROM NG_INSTANCE_STATS_ITERATOR WHERE ACCOUNTID=? AND ORGID=? AND SERVICEID=? "
      + "ORDER BY REPORTEDAT DESC LIMIT 1"),

  FETCH_LATEST_RECORD_ACCOUNT_LEVEL("SELECT * FROM NG_INSTANCE_STATS_ITERATOR WHERE ACCOUNTID=? AND SERVICEID=? "
      + "ORDER BY REPORTEDAT DESC LIMIT 1"),

  UPDATE_RECORD(
      "INSERT INTO NG_INSTANCE_STATS_ITERATOR (REPORTEDAT, ACCOUNTID, ORGID, PROJECTID, SERVICEID) VALUES (?,?,?,?,?) "
      + "ON CONFLICT (ACCOUNTID, ORGID, PROJECTID, SERVICEID) Do UPDATE SET REPORTEDAT=?");

  private String query;

  InstanceStatsIteratorQuery(String query) {
    this.query = query;
  }

  public String query() {
    return this.query;
  }
}
