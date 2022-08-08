/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.helper;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.entities.CCMField;
import io.harness.ccm.commons.entities.CCMFilter;
import io.harness.ccm.commons.entities.CCMNumberFilter;
import io.harness.ccm.commons.entities.CCMOperator;
import io.harness.ccm.commons.entities.CCMStringFilter;
import io.harness.ccm.commons.entities.CCMTimeFilter;
import io.harness.ccm.commons.entities.anomaly.AnomalyQueryDTO;
import io.harness.ccm.commons.helper.CCMFilterHelper;
import io.harness.ccm.commons.utils.AnomalyUtils;
import io.harness.ccm.remote.beans.anomaly.AnomalyFilterPropertiesDTO;
import io.harness.data.structure.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.experimental.UtilityClass;

@OwnedBy(CE)
@UtilityClass
public class AnomalyQueryHelper {
  public AnomalyQueryDTO buildAnomalyQueryFromFilterProperties(AnomalyFilterPropertiesDTO anomalyFilterPropertiesDTO) {
    if (anomalyFilterPropertiesDTO == null) {
      return null;
    }
    List<CCMStringFilter> stringFilters = new ArrayList<>();
    List<CCMNumberFilter> numberFilters = new ArrayList<>();
    List<CCMTimeFilter> timeFilters = isNotEmpty(anomalyFilterPropertiesDTO.getTimeFilters())
        ? anomalyFilterPropertiesDTO.getTimeFilters()
        : Collections.emptyList();
    Integer offset = (anomalyFilterPropertiesDTO.getOffset() != null) ? anomalyFilterPropertiesDTO.getOffset()
                                                                      : AnomalyUtils.DEFAULT_OFFSET;
    Integer limit = (anomalyFilterPropertiesDTO.getLimit() != null) ? anomalyFilterPropertiesDTO.getLimit()
                                                                    : AnomalyUtils.DEFAULT_LIMIT;

    addStringFilter(
        CCMField.CLUSTER_NAME, CCMOperator.IN, anomalyFilterPropertiesDTO.getK8sClusterNames(), stringFilters);
    addStringFilter(CCMField.NAMESPACE, CCMOperator.IN, anomalyFilterPropertiesDTO.getK8sNamespaces(), stringFilters);
    addStringFilter(CCMField.WORKLOAD, CCMOperator.IN, anomalyFilterPropertiesDTO.getK8sWorkloadNames(), stringFilters);

    addStringFilter(CCMField.GCP_PROJECT, CCMOperator.IN, anomalyFilterPropertiesDTO.getGcpProjects(), stringFilters);
    addStringFilter(CCMField.GCP_PRODUCT, CCMOperator.IN, anomalyFilterPropertiesDTO.getGcpProducts(), stringFilters);
    addStringFilter(CCMField.GCP_SKU_DESCRIPTION, CCMOperator.IN, anomalyFilterPropertiesDTO.getGcpSKUDescriptions(),
        stringFilters);

    addStringFilter(CCMField.AWS_ACCOUNT, CCMOperator.IN, anomalyFilterPropertiesDTO.getAwsAccounts(), stringFilters);
    addStringFilter(CCMField.AWS_SERVICE, CCMOperator.IN, anomalyFilterPropertiesDTO.getAwsServices(), stringFilters);
    addStringFilter(
        CCMField.AWS_USAGE_TYPE, CCMOperator.IN, anomalyFilterPropertiesDTO.getAwsUsageTypes(), stringFilters);

    addStringFilter(CCMField.AZURE_SUBSCRIPTION_GUID, CCMOperator.IN,
        anomalyFilterPropertiesDTO.getAzureSubscriptionGuids(), stringFilters);
    addStringFilter(CCMField.AZURE_RESOURCE_GROUP_NAME, CCMOperator.IN,
        anomalyFilterPropertiesDTO.getAzureResourceGroups(), stringFilters);
    addStringFilter(CCMField.AZURE_METER_CATEGORY, CCMOperator.IN, anomalyFilterPropertiesDTO.getAzureMeterCategories(),
        stringFilters);
    addStringFilter(CCMField.ALL, CCMOperator.LIKE, anomalyFilterPropertiesDTO.getSearchText(), stringFilters);

    addNumberFilter(CCMField.ACTUAL_COST, CCMOperator.GREATER_THAN_EQUALS_TO,
        anomalyFilterPropertiesDTO.getMinActualAmount(), numberFilters);
    addNumberFilter(CCMField.ANOMALOUS_SPEND, CCMOperator.GREATER_THAN_EQUALS_TO,
        anomalyFilterPropertiesDTO.getMinAnomalousSpend(), numberFilters);

    CCMFilter ccmFilter =
        CCMFilter.builder().stringFilters(stringFilters).numericFilters(numberFilters).timeFilters(timeFilters).build();

    return AnomalyQueryDTO.builder()
        .filter(ccmFilter)
        .groupBy(CollectionUtils.emptyIfNull(anomalyFilterPropertiesDTO.getGroupBy()))
        .orderBy(CollectionUtils.emptyIfNull(anomalyFilterPropertiesDTO.getOrderBy()))
        .aggregations(CollectionUtils.emptyIfNull(anomalyFilterPropertiesDTO.getAggregations()))
        .offset(offset)
        .limit(limit)
        .build();
  }

  public void addStringFilter(
      CCMField field, CCMOperator operator, List<String> values, List<CCMStringFilter> stringFilters) {
    if (isNotEmpty(values)) {
      stringFilters.add(CCMFilterHelper.buildStringFilter(field, operator, values));
    }
  }

  public void addNumberFilter(CCMField field, CCMOperator operator, Number value, List<CCMNumberFilter> numberFilters) {
    if (value != null) {
      numberFilters.add(CCMFilterHelper.buildNumberFilter(field, operator, value));
    }
  }
}
