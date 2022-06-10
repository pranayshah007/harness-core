/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.beans.anomaly;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.filter.FilterConstants.ANOMALY_FILTER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.entities.CCMAggregation;
import io.harness.ccm.commons.entities.CCMGroupBy;
import io.harness.ccm.commons.entities.CCMSort;
import io.harness.ccm.commons.entities.CCMTimeFilter;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterPropertiesDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName(ANOMALY_FILTER)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("AnomalyFilterProperties")
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@OwnedBy(CE)
@Schema(name = "AnomalyFilterProperties", description = "Properties of the Anomaly Filter defined in Harness")
public class AnomalyFilterPropertiesDTO extends FilterPropertiesDTO {
  @Schema(description = "This is the list of Cluster Names on which filter will be applied.")
  List<String> k8sClusterNames;
  @Schema(description = "This is the list of Namespaces on which filter will be applied.") List<String> k8sNamespaces;
  @Schema(description = "This is the list of Workload Names on which filter will be applied.")
  List<String> k8sWorkloadNames;

  @Schema(description = "This is the list of GCP Projects on which filter will be applied.") List<String> gcpProjects;
  @Schema(description = "This is the list of GCP Products on which filter will be applied.") List<String> gcpProducts;
  @Schema(description = "This is the list of GCP SKU Descriptions on which filter will be applied.")
  List<String> gcpSKUDescriptions;

  @Schema(description = "This is the list of AWS Accounts on which filter will be applied.") List<String> awsAccounts;
  @Schema(description = "This is the list of AWS Services on which filter will be applied.") List<String> awsServices;
  @Schema(description = "This is the list of AWS Usage Types on which filter will be applied.")
  List<String> awsUsageTypes;

  @Schema(description = "This is the list of Azure Subscription Guids on which filter will be applied.")
  List<String> azureSubscriptionGuids;
  @Schema(description = "This is the list of Azure Resource Groups on which filter will be applied.")
  List<String> azureResourceGroups;
  @Schema(description = "This is the list of Azure Meter Categories on which filter will be applied.")
  List<String> azureMeterCategories;

  @Schema(description = "Fetch anomalies with Actual Amount greater-than or equal-to minActualAmount")
  Double minActualAmount;
  @Schema(description = "Fetch anomalies with Anomalous Spend greater-than or equal-to minAnomalousSpend")
  Double minAnomalousSpend;

  @Schema(description = "List of filters to be applied on Anomaly Time") List<CCMTimeFilter> timeFilters;
  @Schema(description = "The order by condition for anomaly query") List<CCMSort> orderBy;
  @Schema(description = "The group by clause for anomaly query") List<CCMGroupBy> groupBy;
  @Schema(description = "The aggregations for anomaly query") List<CCMAggregation> aggregations;
  @Schema(description = "The search text entered to filter out rows") List<String> searchText;

  @Schema(description = "Query Offset") Integer offset;
  @Schema(description = "Query Limit") Integer limit;

  @Override
  public FilterType getFilterType() {
    return FilterType.ANOMALY;
  }
}
