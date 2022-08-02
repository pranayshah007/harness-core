/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.helper;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.remote.beans.BIDashboardSummary;
import io.harness.ccm.remote.beans.BIDashboardSummary.BIDashboardSummaryBuilder;

import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldList;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.TableResult;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CE)
@UtilityClass
@Slf4j
public class BIDashboardQueryHelper {
  public final String dashboardName = "dashboardName";
  public final String dashboardId = "dashboardId";
  public final String cloudProvider = "cloudProvider";
  public final String description = "description";
  public final String serviceType = "serviceType";
  public final String redirectionURL = "redirectionURL";
  public final String BQ_CCM_LIST_BI_DASHBOARDS_QUERY = "SELECT dashboardName, dashboardId, cloudProvider, "
      + "description, serviceType, redirectionURL FROM `%s.CE_INTERNAL.biDashboards`;";

  public List<BIDashboardSummary> extractDashboardSummaries(TableResult result, String accountId) {
    List<BIDashboardSummary> dashboardSummaries = new ArrayList<>();
    Preconditions.checkNotNull(result);
    if (result.getTotalRows() == 0) {
      log.warn("No result from this query");
      return dashboardSummaries;
    }

    Schema schema = result.getSchema();
    FieldList fields = schema.getFields();
    for (FieldValueList row : result.iterateAll()) {
      BIDashboardSummaryBuilder dashboardSummaryBuilder = BIDashboardSummary.builder();
      for (Field field : fields) {
        switch (field.getName()) {
          case dashboardName:
            dashboardSummaryBuilder.dashboardName((String) row.get(field.getName()).getValue());
            break;
          case dashboardId:
            dashboardSummaryBuilder.dashboardId((String) row.get(field.getName()).getValue());
            break;
          case cloudProvider:
            dashboardSummaryBuilder.cloudProvider((String) row.get(field.getName()).getValue());
            break;
          case description:
            dashboardSummaryBuilder.description((String) row.get(field.getName()).getValue());
            break;
          case serviceType:
            dashboardSummaryBuilder.serviceType((String) row.get(field.getName()).getValue());
            break;
          case redirectionURL:
            dashboardSummaryBuilder.redirectionURL(
                String.format("#/account/%s/%s", accountId, row.get(field.getName()).getValue()));
            break;
          default:
            break;
        }
      }
      dashboardSummaries.add(dashboardSummaryBuilder.build());
    }
    return dashboardSummaries;
  }
}
