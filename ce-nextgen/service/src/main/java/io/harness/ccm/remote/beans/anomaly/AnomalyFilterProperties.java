/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.remote.beans.anomaly;

import static io.harness.filter.FilterConstants.ANOMALY_FILTER;

import io.harness.ccm.commons.entities.CCMAggregation;
import io.harness.ccm.commons.entities.CCMGroupBy;
import io.harness.ccm.commons.entities.CCMSort;
import io.harness.ccm.commons.entities.CCMTimeFilter;
import io.harness.filter.entity.FilterProperties;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModel;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel("AnomalyFilterProperties")
@JsonTypeName(ANOMALY_FILTER)
public class AnomalyFilterProperties extends FilterProperties {
  List<String> k8sClusterNames;
  List<String> k8sNamespaces;
  List<String> k8sWorkloadNames;

  List<String> gcpProjects;
  List<String> gcpProducts;
  List<String> gcpSKUDescriptions;

  List<String> awsAccounts;
  List<String> awsServices;
  List<String> awsUsageTypes;

  List<String> azureSubscriptionGuids;
  List<String> azureResourceGroups;
  List<String> azureMeterCategories;

  Double minActualAmount;
  Double minAnomalousSpend;

  List<CCMTimeFilter> timeFilters;
  List<CCMSort> orderBy;
  List<CCMGroupBy> groupBy;
  List<CCMAggregation> aggregations;
  List<String> searchText;

  Integer offset;
  Integer limit;
}
