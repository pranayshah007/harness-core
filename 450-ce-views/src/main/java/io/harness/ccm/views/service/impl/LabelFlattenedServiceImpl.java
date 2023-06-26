/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.ccm.commons.utils.BigQueryHelper;
import io.harness.ccm.views.graphql.ViewsQueryBuilder;
import io.harness.ccm.views.helper.ViewBillingServiceHelper;
import io.harness.ccm.views.service.LabelFlattenedService;
import io.harness.ff.FeatureFlagService;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldList;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.TableResult;
import com.google.inject.Inject;
import com.healthmarketscience.sqlbuilder.BinaryCondition;
import com.healthmarketscience.sqlbuilder.CustomSql;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CE)
@Slf4j
public class LabelFlattenedServiceImpl implements LabelFlattenedService {
  private static final String FLATTENED_LABELS_KEY_AND_COLUMN_MAPPING = "labelKeysToColumnMapping";
  private static final String LABEL_KEY = "labelKey";
  private static final String LABEL_COLUMN_NAME = "columnName";
  private static final String COLUMN_NAME = "column_name";
  private static final String TABLE_NAME = "table_name";
  private static final String COLUMN_VIEW = "COLUMNS";

  @Inject private BigQueryHelper bigQueryHelper;
  @Inject private BigQueryService bigQueryService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private ViewBillingServiceHelper viewBillingServiceHelper;
  @Inject private ViewsQueryBuilder viewsQueryBuilder;

  private final LoadingCache<CacheKey, Set<String>> flattenedLabelsTableColumnsCache =
      Caffeine.newBuilder()
          .maximumSize(20)
          .expireAfterWrite(30, TimeUnit.MINUTES)
          .build(cacheKey
              -> getFlattenedLabelsTableColumnsFromInformationSchema(cacheKey.getAccountId(), cacheKey.getTableName()));

  @Override
  public Map<String, String> getLabelsKeyAndColumnMapping(final String accountId) {
    if (!featureFlagService.isEnabled(FeatureName.CCM_LABELS_FLATTENING, accountId)) {
      return Collections.emptyMap();
    }
    final String tableName =
        bigQueryHelper.getCloudProviderTableName(accountId, FLATTENED_LABELS_KEY_AND_COLUMN_MAPPING);
    final SelectQuery selectQuery = getLabelsKeyAndColumnMappingQuery(tableName);
    final TableResult tableResult = getTableResultFromQuery(selectQuery);
    return convertToLabelsKeyAndColumnMapping(tableResult, tableName);
  }

  @Override
  public Set<String> getFlattenedLabelsTableColumns(final String accountId, final String tableName) {
    if (!featureFlagService.isEnabled(FeatureName.CCM_LABELS_FLATTENING, accountId)) {
      return Collections.emptySet();
    }
    final CacheKey cacheKey = new CacheKey(accountId, tableName);
    return flattenedLabelsTableColumnsCache.get(cacheKey);
  }

  private Set<String> getFlattenedLabelsTableColumnsFromInformationSchema(
      final String accountId, final String tableName) {
    final String informationSchemaView = bigQueryHelper.getInformationSchemaViewForDataset(accountId, COLUMN_VIEW);
    final SelectQuery selectQuery = getInformationSchemaQueryForColumns(informationSchemaView, tableName);
    final TableResult tableResult = getTableResultFromQuery(selectQuery);
    return convertToFlattenedLabelsTableColumns(tableResult);
  }

  public SelectQuery getInformationSchemaQueryForColumns(final String informationSchemaView, final String table) {
    final SelectQuery selectQuery = new SelectQuery();
    selectQuery.addCustomFromTable(informationSchemaView);
    selectQuery.addCustomColumns(new CustomSql(COLUMN_NAME));
    selectQuery.addCondition(BinaryCondition.equalTo(new CustomSql(TABLE_NAME), table));
    log.info("Information schema query for flattened label's table: {}", selectQuery);
    return selectQuery;
  }

  private SelectQuery getLabelsKeyAndColumnMappingQuery(final String tableName) {
    final SelectQuery selectQuery = new SelectQuery();
    selectQuery.addCustomFromTable(tableName);
    selectQuery.addAllColumns();
    log.info("Label's key and column mapping query: {}", selectQuery);
    return selectQuery;
  }

  private TableResult getTableResultFromQuery(final SelectQuery selectQuery) {
    final BigQuery bigQuery = bigQueryService.get();
    final QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(selectQuery.toString()).build();
    TableResult result;
    try {
      result = bigQuery.query(queryConfig);
    } catch (final InterruptedException e) {
      log.error("Failed to get label's key and column mapping result from query {}", queryConfig, e);
      Thread.currentThread().interrupt();
      return null;
    }
    return result;
  }

  private Map<String, String> convertToLabelsKeyAndColumnMapping(
      final TableResult tableResult, final String tableName) {
    final Map<String, String> labelsKeyAndColumnMapping = new HashMap<>();
    if (!Objects.isNull(tableResult)) {
      final Schema schema = tableResult.getSchema();
      final FieldList fields = schema.getFields();
      for (final FieldValueList row : tableResult.iterateAll()) {
        addLabelsKeyAndColumnMapping(fields, row, labelsKeyAndColumnMapping, tableName);
      }
    }
    return labelsKeyAndColumnMapping;
  }

  private void addLabelsKeyAndColumnMapping(final FieldList fields, final FieldValueList row,
      final Map<String, String> labelsKeyAndColumnMapping, final String tableName) {
    String labelKey = null;
    String columnName = null;
    for (final Field field : fields) {
      if (LABEL_KEY.equals(field.getName())) {
        labelKey = getStringValue(row, field);
      } else if (LABEL_COLUMN_NAME.equals(field.getName())) {
        columnName = getStringValue(row, field);
      }
    }
    if (Objects.nonNull(labelKey) && Objects.nonNull(columnName)) {
      labelsKeyAndColumnMapping.put(labelKey, columnName);
    } else {
      log.warn("Null value is present in {} table for label's column mapping", tableName);
    }
  }

  private Set<String> convertToFlattenedLabelsTableColumns(final TableResult tableResult) {
    final Set<String> flattenedLabelsTableColumns = new HashSet<>();
    if (!Objects.isNull(tableResult)) {
      final Schema schema = tableResult.getSchema();
      final FieldList fields = schema.getFields();
      for (final FieldValueList row : tableResult.iterateAll()) {
        addFlattenedLabelsTableColumn(fields, row, flattenedLabelsTableColumns);
      }
    }
    return flattenedLabelsTableColumns;
  }

  private void addFlattenedLabelsTableColumn(
      final FieldList fields, final FieldValueList row, final Set<String> flattenedLabelsTableColumns) {
    for (final Field field : fields) {
      if (COLUMN_NAME.equals(field.getName())) {
        flattenedLabelsTableColumns.add(getStringValue(row, field));
      }
    }
  }

  private static String getStringValue(final FieldValueList row, final Field field) {
    final FieldValue value = row.get(field.getName());
    if (!value.isNull()) {
      return (String) value.getValue();
    }
    return null;
  }

  @Value
  private static class CacheKey {
    String accountId;
    String tableName;
  }
}
