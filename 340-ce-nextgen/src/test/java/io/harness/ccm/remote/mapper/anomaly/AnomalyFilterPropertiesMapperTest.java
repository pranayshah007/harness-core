/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.remote.mapper.anomaly;

import static io.harness.rule.OwnerRule.ABHIJEET;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.entities.CCMAggregation;
import io.harness.ccm.commons.entities.CCMField;
import io.harness.ccm.commons.entities.CCMGroupBy;
import io.harness.ccm.commons.entities.CCMOperator;
import io.harness.ccm.commons.entities.CCMSort;
import io.harness.ccm.commons.entities.CCMSortOrder;
import io.harness.ccm.commons.entities.CCMTimeFilter;
import io.harness.ccm.remote.beans.anomaly.AnomalyFilterProperties;
import io.harness.ccm.remote.beans.anomaly.AnomalyFilterPropertiesDTO;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterPropertiesDTO;
import io.harness.filter.entity.FilterProperties;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class AnomalyFilterPropertiesMapperTest extends CategoryTest {
  List<String> k8sClusterNames = Arrays.asList("k8sCluster1");
  List<String> k8sNamespaces = Arrays.asList("k8sNamespace1");
  List<String> k8sWorkloadNames = Arrays.asList("k8sWorkloadName1");
  List<String> gcpProjects = Arrays.asList("gcpProject1");
  List<String> gcpProducts = Arrays.asList("gcpProduct1");
  List<String> gcpSKUDescriptions = Arrays.asList("gcpSKUDescription1");
  List<String> awsAccounts = Arrays.asList("awsAccount1");
  List<String> awsServices = Arrays.asList("awsService1");
  List<String> awsUsageTypes = Arrays.asList("awsUsageType1");
  List<String> azureSubscriptionGuids = Arrays.asList("azureSubscriptionGuid1");
  List<String> azureResourceGroups = Arrays.asList("azureResourceGroup1");
  List<String> azureMeterCategories = Arrays.asList("azureMeterCategory1");
  List<CCMSort> orderBy =
      Arrays.asList(CCMSort.builder().field(CCMField.ANOMALOUS_SPEND).order(CCMSortOrder.ASCENDING).build());
  List<CCMGroupBy> groupBy = new ArrayList<>();
  List<CCMAggregation> aggregations = new ArrayList<>();
  List<String> searchText = Arrays.asList("abc");
  Double minActualAmount = 123.45;
  Double minAnomalousSpend = 1234.5;
  List<CCMTimeFilter> timeFilters = Arrays.asList(
      CCMTimeFilter.builder().operator(CCMOperator.BEFORE).timestamp(Calendar.getInstance().getTimeInMillis()).build());
  Integer offset = 0;
  Integer limit = 1000;
  @InjectMocks AnomalyFilterPropertiesMapper anomalyFilterPropertiesMapper;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ABHIJEET)
  @Category(UnitTests.class)
  public void testWriteDTO() {
    FilterProperties filterProperties = AnomalyFilterProperties.builder()
                                            .k8sClusterNames(k8sClusterNames)
                                            .k8sNamespaces(k8sNamespaces)
                                            .k8sWorkloadNames(k8sWorkloadNames)
                                            .gcpProjects(gcpProjects)
                                            .gcpProducts(gcpProducts)
                                            .gcpSKUDescriptions(gcpSKUDescriptions)
                                            .awsAccounts(awsAccounts)
                                            .awsServices(awsServices)
                                            .awsUsageTypes(awsUsageTypes)
                                            .azureSubscriptionGuids(azureSubscriptionGuids)
                                            .azureResourceGroups(azureResourceGroups)
                                            .azureMeterCategories(azureMeterCategories)
                                            .minActualAmount(minActualAmount)
                                            .minAnomalousSpend(minAnomalousSpend)
                                            .timeFilters(timeFilters)
                                            .orderBy(orderBy)
                                            .groupBy(groupBy)
                                            .aggregations(aggregations)
                                            .searchText(searchText)
                                            .offset(offset)
                                            .limit(limit)
                                            .build();
    AnomalyFilterPropertiesDTO filterPropertiesDTO =
        (AnomalyFilterPropertiesDTO) anomalyFilterPropertiesMapper.writeDTO(filterProperties);
    assertThat(filterPropertiesDTO.getFilterType()).isEqualTo(FilterType.ANOMALY);
    assertThat(filterPropertiesDTO.getK8sClusterNames()).isEqualTo(k8sClusterNames);
    assertThat(filterPropertiesDTO.getK8sNamespaces()).isEqualTo(k8sNamespaces);
    assertThat(filterPropertiesDTO.getK8sWorkloadNames()).isEqualTo(k8sWorkloadNames);
    assertThat(filterPropertiesDTO.getGcpProjects()).isEqualTo(gcpProjects);
    assertThat(filterPropertiesDTO.getGcpProducts()).isEqualTo(gcpProducts);
    assertThat(filterPropertiesDTO.getGcpSKUDescriptions()).isEqualTo(gcpSKUDescriptions);
    assertThat(filterPropertiesDTO.getAwsAccounts()).isEqualTo(awsAccounts);
    assertThat(filterPropertiesDTO.getAwsServices()).isEqualTo(awsServices);
    assertThat(filterPropertiesDTO.getAwsUsageTypes()).isEqualTo(awsUsageTypes);
    assertThat(filterPropertiesDTO.getAzureSubscriptionGuids()).isEqualTo(azureSubscriptionGuids);
    assertThat(filterPropertiesDTO.getAzureResourceGroups()).isEqualTo(azureResourceGroups);
    assertThat(filterPropertiesDTO.getAzureMeterCategories()).isEqualTo(azureMeterCategories);
    assertThat(filterPropertiesDTO.getMinActualAmount()).isEqualTo(minActualAmount);
    assertThat(filterPropertiesDTO.getMinAnomalousSpend()).isEqualTo(minAnomalousSpend);
    assertThat(filterPropertiesDTO.getTimeFilters()).isEqualTo(timeFilters);
    assertThat(filterPropertiesDTO.getOrderBy()).isEqualTo(orderBy);
    assertThat(filterPropertiesDTO.getGroupBy()).isEqualTo(groupBy);
    assertThat(filterPropertiesDTO.getAggregations()).isEqualTo(aggregations);
    assertThat(filterPropertiesDTO.getSearchText()).isEqualTo(searchText);
    assertThat(filterPropertiesDTO.getOffset()).isEqualTo(offset);
    assertThat(filterPropertiesDTO.getLimit()).isEqualTo(limit);
  }

  @Test
  @Owner(developers = ABHIJEET)
  @Category(UnitTests.class)
  public void testToEntity() {
    FilterPropertiesDTO filterPropertiesDTO = AnomalyFilterPropertiesDTO.builder()
                                                  .k8sClusterNames(k8sClusterNames)
                                                  .k8sNamespaces(k8sNamespaces)
                                                  .k8sWorkloadNames(k8sWorkloadNames)
                                                  .gcpProjects(gcpProjects)
                                                  .gcpProducts(gcpProducts)
                                                  .gcpSKUDescriptions(gcpSKUDescriptions)
                                                  .awsAccounts(awsAccounts)
                                                  .awsServices(awsServices)
                                                  .awsUsageTypes(awsUsageTypes)
                                                  .azureSubscriptionGuids(azureSubscriptionGuids)
                                                  .azureResourceGroups(azureResourceGroups)
                                                  .azureMeterCategories(azureMeterCategories)
                                                  .minActualAmount(minActualAmount)
                                                  .minAnomalousSpend(minAnomalousSpend)
                                                  .timeFilters(timeFilters)
                                                  .orderBy(orderBy)
                                                  .groupBy(groupBy)
                                                  .aggregations(aggregations)
                                                  .searchText(searchText)
                                                  .offset(offset)
                                                  .limit(limit)
                                                  .build();
    AnomalyFilterProperties filterProperties =
        (AnomalyFilterProperties) anomalyFilterPropertiesMapper.toEntity(filterPropertiesDTO);
    assertThat(filterProperties.getK8sClusterNames()).isEqualTo(k8sClusterNames);
    assertThat(filterProperties.getK8sNamespaces()).isEqualTo(k8sNamespaces);
    assertThat(filterProperties.getK8sWorkloadNames()).isEqualTo(k8sWorkloadNames);
    assertThat(filterProperties.getGcpProjects()).isEqualTo(gcpProjects);
    assertThat(filterProperties.getGcpProducts()).isEqualTo(gcpProducts);
    assertThat(filterProperties.getGcpSKUDescriptions()).isEqualTo(gcpSKUDescriptions);
    assertThat(filterProperties.getAwsAccounts()).isEqualTo(awsAccounts);
    assertThat(filterProperties.getAwsServices()).isEqualTo(awsServices);
    assertThat(filterProperties.getAwsUsageTypes()).isEqualTo(awsUsageTypes);
    assertThat(filterProperties.getAzureSubscriptionGuids()).isEqualTo(azureSubscriptionGuids);
    assertThat(filterProperties.getAzureResourceGroups()).isEqualTo(azureResourceGroups);
    assertThat(filterProperties.getAzureMeterCategories()).isEqualTo(azureMeterCategories);
    assertThat(filterProperties.getMinActualAmount()).isEqualTo(minActualAmount);
    assertThat(filterProperties.getMinAnomalousSpend()).isEqualTo(minAnomalousSpend);
    assertThat(filterProperties.getTimeFilters()).isEqualTo(timeFilters);
    assertThat(filterProperties.getOrderBy()).isEqualTo(orderBy);
    assertThat(filterProperties.getGroupBy()).isEqualTo(groupBy);
    assertThat(filterProperties.getAggregations()).isEqualTo(aggregations);
    assertThat(filterProperties.getSearchText()).isEqualTo(searchText);
    assertThat(filterProperties.getOffset()).isEqualTo(offset);
    assertThat(filterProperties.getLimit()).isEqualTo(limit);
  }
}
