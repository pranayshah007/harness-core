/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.anomaly.url;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

@OwnedBy(CE)
@TargetModule(HarnessModule._375_CE_GRAPHQL)
public enum UrlParams {
  AGGREGATION_TYPE("aggregationType"),
  CHART_TYPE("chartType"),
  UTILIZATION_AGGREGATION("utilizationAggregationType"),
  FILTER_ON("isFilterOn"),
  GROUP_BY("groupBy"),
  FROM_DATE("from"),
  TO_DATE("to"),

  SHOW_UNALLOCATED("showUnallocated"),
  SHOW_OTHERS("showOthers"),
  CURRENT_VIEW("currentView"),

  CLUSTER_ID("clusterList"),
  NAMESPACE("clusterNamespaceList"),

  GCP_DISCOUNTS("includeGCPDiscounts"),
  GCP_PRODUCT("gcpProductList"),
  GCP_PROJECT("gcpProjectList"),
  GCP_SKU("gcpSKUList"),

  AWS_ACCOUNT("awsAccountList"),
  AWS_SERVICE("awsServiceList"),

  CURRENT_TAB("currentTab"),
  FILTER("filter"),
  FILTERS("filters"),
  TIME_RANGE("timeRange"),
  AGGREGATION("aggregation"),
  SELECTED_VIEW("selectedView");

  private String param;
  UrlParams(String param) {
    this.param = param;
  }

  public String getParam() {
    return param;
  }
}
