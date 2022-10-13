/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.instancestats;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.DX)
public enum InstanceStatsQuery {
  FETCH_LATEST_RECORD("SELECT * FROM NG_INSTANCE_STATS WHERE ACCOUNTID=? AND ORGID=? AND PROJECTID=? AND SERVICEID=? "
      + "ORDER BY REPORTEDAT DESC LIMIT 1");

  private String query;

  InstanceStatsQuery(String query) {
    this.query = query;
  }

  public String query() {
    return this.query;
  }
}
