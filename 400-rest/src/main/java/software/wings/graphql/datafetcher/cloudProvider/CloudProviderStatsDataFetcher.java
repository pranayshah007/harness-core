/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.cloudProvider;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.WingsException;

import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.dl.WingsPersistence;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.datafetcher.SettingsAttributeStatsDataFetcher;
import software.wings.graphql.schema.type.aggregation.QLData;
import software.wings.graphql.schema.type.aggregation.QLNoOpAggregateFunction;
import software.wings.graphql.schema.type.aggregation.QLNoOpSortCriteria;
import software.wings.graphql.schema.type.aggregation.cloudprovider.QLCloudProviderAggregation;
import software.wings.graphql.schema.type.aggregation.cloudprovider.QLCloudProviderFilter;
import software.wings.graphql.schema.type.aggregation.cloudprovider.QLCloudProviderTypeAggregation;
import software.wings.graphql.utils.nameservice.NameService;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import graphql.schema.DataFetchingEnvironment;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;

@OwnedBy(CDP)
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class CloudProviderStatsDataFetcher extends SettingsAttributeStatsDataFetcher<QLNoOpAggregateFunction,
    QLCloudProviderFilter, QLCloudProviderAggregation, QLNoOpSortCriteria> {
  @Inject CloudProviderQueryHelper cloudProviderQueryHelper;
  @Override
  protected QLData fetch(String accountId, QLNoOpAggregateFunction aggregateFunction,
      List<QLCloudProviderFilter> filters, List<QLCloudProviderAggregation> groupBy,
      List<QLNoOpSortCriteria> sortCriteria, DataFetchingEnvironment dataFetchingEnvironment) {
    final Class entityClass = SettingAttribute.class;
    List<String> groupByList = new ArrayList<>();
    if (isNotEmpty(groupBy)) {
      groupByList = groupBy.stream()
                        .filter(g -> g != null && g.getTypeAggregation() != null)
                        .map(g -> g.getTypeAggregation().name())
                        .collect(Collectors.toList());
    }
    return getQLData(accountId, filters, entityClass, groupByList);
  }

  @Override
  @NotNull
  public Query populateFilters(DataFetcherUtils utils, WingsPersistence wingsPersistence, String accountId,
      List<QLCloudProviderFilter> filters, Class entityClass) {
    Query query = super.populateFilters(utils, wingsPersistence, accountId, filters, entityClass);
    query.filter(SettingAttributeKeys.category, SettingCategory.CLOUD_PROVIDER);
    return query;
  }

  @Override
  public String getAggregationFieldName(String aggregation) {
    QLCloudProviderTypeAggregation cloudProviderAggregation = QLCloudProviderTypeAggregation.valueOf(aggregation);
    switch (cloudProviderAggregation) {
      case Type:
        return "value.type";
      default:
        throw new WingsException("Unknown aggregation type" + aggregation);
    }
  }

  @Override
  public void populateFilters(String accountId, List<QLCloudProviderFilter> filters, Query query) {
    cloudProviderQueryHelper.setQuery(filters, query);
  }

  @Override
  public String getEntityType() {
    return NameService.cloudProvider;
  }
}
