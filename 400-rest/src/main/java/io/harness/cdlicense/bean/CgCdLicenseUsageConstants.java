/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdlicense.bean;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class CgCdLicenseUsageConstants {
  public static final int TIME_PERIOD = 30;
  public static final double INSTANCE_COUNT_PERCENTILE_DISC = 0.95;
  public static final int CG_LICENSE_INSTANCE_LIMIT = 20;
  public static final int MAX_RETRY = 3;
  public static final String QUERY_FECTH_SERVICES_IN_LAST_N_DAYS_DEPLOYMENT =
      "SELECT DISTINCT UNNEST(SERVICES) FROM DEPLOYMENT WHERE ACCOUNTID = ? AND STARTTIME > NOW() - ? * INTERVAL '1' DAY";
  public static final String QUERY_FECTH_PERCENTILE_INSTANCE_COUNT_FOR_SERVICES =
      "SELECT SERVICEID, PERCENTILE_DISC(?) WITHIN GROUP (ORDER BY INSTANCECOUNT) AS INSTANCECOUNT FROM INSTANCE_STATS WHERE ACCOUNTID = ? AND SERVICEID = ANY (?) AND REPORTEDAT > NOW() - ? * INTERVAL '1' DAY GROUP BY SERVICEID";
}
