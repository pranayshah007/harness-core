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
  public static final String FETCH_DEPLOYED_SERVICES_IN_LAST_N_DAYS = ""
      + "SELECT\n"
      + "   appId,\n"
      + "   appName,\n"
      + "   serviceId,\n"
      + "   cgServices.name AS serviceName\n"
      + "FROM\n"
      + "   (\n"
      + "      SELECT\n"
      + "\t\t serviceId,\n"
      + "         appId,\n"
      + "         cgApps.name AS appName\n"
      + "      FROM\n"
      + "         (\n"
      + "            SELECT\n"
      + "               DISTINCT UNNEST(services) AS serviceId,\n"
      + "               appId\n"
      + "            FROM\n"
      + "               DEPLOYMENT\n"
      + "            WHERE\n"
      + "               accountid = ?\n"
      + "               AND starttime > CURRENT_TIMESTAMP -  ? * INTERVAL '1' DAY\n"
      + "         ) distinctServices\n"
      + "         LEFT JOIN cg_applications cgApps ON distinctServices.appid = cgApps.id\n"
      + "   ) AS distinctServicesWithApps\n"
      + "   LEFT JOIN cg_services cgServices ON distinctServicesWithApps.serviceId = cgServices.id";
  public static final String FETCH_PERCENTILE_INSTANCE_AND_LICENSE_USAGE_FOR_SERVICES = ""
      + "SELECT\n"
      + "   percentileInstanceCount.serviceId AS serviceId,\n"
      + "   percentileInstanceCount.percentileInstanceCount AS instanceCount,\n"
      + "   CASE\n"
      + "      WHEN percentileInstanceCount.percentileInstanceCount = 0 THEN 1\n"
      + "      ELSE CEILING(\n"
      + "         percentileInstanceCount.percentileInstanceCount / 20.0\n"
      + "      )\n"
      + "   END AS serviceLicenses\n"
      + "FROM\n"
      + "   (\n"
      + "      SELECT\n"
      + "         PERCENTILE_DISC(?) WITHIN GROUP (\n"
      + "            ORDER BY\n"
      + "               instanceCountsPerReportedAt.instancecount\n"
      + "         ) AS percentileInstanceCount,\n"
      + "         appid,\n"
      + "         serviceid\n"
      + "      FROM\n"
      + "         (\n"
      + "            SELECT\n"
      + "               appid,\n"
      + "               serviceid,\n"
      + "               SUM(instancecount) AS instancecount,\n"
      + "               DATE_TRUNC('minute', reportedat) AS reportedat\n"
      + "            FROM\n"
      + "               instance_stats\n"
      + "            WHERE\n"
      + "               accountid = ?\n"
      + "               AND reportedat > CURRENT_TIMESTAMP - ? * INTERVAL '1' DAY\n"
      + "               AND serviceid = ANY (?)\n"
      + "            GROUP BY\n"
      + "               appid,\n"
      + "               serviceid,\n"
      + "               DATE_TRUNC('minute', reportedat)\n"
      + "         ) AS instanceCountsPerReportedAt\n"
      + "      GROUP BY\n"
      + "         appid,\n"
      + "         serviceid\n"
      + "   ) AS percentileInstanceCount";
  public static final String QUERY_FETCH_SERVICE_INSTANCE_USAGE = ""
      + "select percentile_disc(?) within group (order by instanceCountsPerReportedAt.instancecount) as percentileInstanceCount\n"
      + "from (\n"
      + "    select \n"
      + "        sum(instancecount) as instancecount,\n"
      + "        date_trunc('minute', reportedat) as reportedat,\n"
      + "        accountid\n"
      + "    from instance_stats \n"
      + "    where accountid = ? \n"
      + "        and reportedat > now() - INTERVAL '30 day' \n"
      + "    group by accountid, date_trunc('minute', reportedat)\n"
      + ") as instanceCountsPerReportedAt";
}
