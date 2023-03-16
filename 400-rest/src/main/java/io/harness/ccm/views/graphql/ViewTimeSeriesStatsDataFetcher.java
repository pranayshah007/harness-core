/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.graphql;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.billing.preaggregated.PreAggregatedBillingDataHelper.convertTimeSeriesPointsMapToList;
import static io.harness.ccm.billing.preaggregated.PreAggregatedBillingDataHelper.getNumericValue;

import static software.wings.graphql.datafetcher.billing.CloudTimeSeriesStatsDataFetcher.OTHERS;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.billing.TimeSeriesDataPoints;
import io.harness.ccm.views.service.ViewsBillingService;

import software.wings.graphql.datafetcher.AbstractStatsDataFetcherWithAggregationListAndLimit;
import software.wings.graphql.datafetcher.billing.BillingDataHelper;
import software.wings.graphql.schema.type.aggregation.QLBillingDataPoint;
import software.wings.graphql.schema.type.aggregation.QLBillingDataPoint.QLBillingDataPointBuilder;
import software.wings.graphql.schema.type.aggregation.QLData;
import software.wings.graphql.schema.type.aggregation.QLReference;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.AuthRule;

import com.google.cloud.Timestamp;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldList;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.TableResult;
import com.google.inject.Inject;
import graphql.schema.DataFetchingEnvironment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@OwnedBy(CE)
@TargetModule(HarnessModule._375_CE_GRAPHQL)
public class ViewTimeSeriesStatsDataFetcher
    extends AbstractStatsDataFetcherWithAggregationListAndLimit<QLCEViewAggregation, QLCEViewFilterWrapper,
        QLCEViewGroupBy, QLCEViewSortCriteria> {
  @Inject ViewsBillingService viewsBillingService;
  @Inject BillingDataHelper billingDataHelper;

  public static final String nullStringValueConstant = "Others";

  @Override
  @AuthRule(permissionType = PermissionAttribute.PermissionType.LOGGED_IN)
  protected QLData fetch(String accountId, List<QLCEViewAggregation> aggregateFunction,
      List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy, List<QLCEViewSortCriteria> sort,
      Integer limit, Integer offset) {
    return convertToQLViewTimeSeriesData(
        viewsBillingService.getTimeSeriesStats(accountId, filters, groupBy, aggregateFunction, sort));
  }

  public QLViewTimeSeriesData convertToQLViewTimeSeriesData(TableResult result) {
    Schema schema = result.getSchema();
    FieldList fields = schema.getFields();

    Map<Timestamp, List<QLBillingDataPoint>> timeSeriesDataPointsMap = new LinkedHashMap();
    for (FieldValueList row : result.iterateAll()) {
      QLBillingDataPointBuilder billingDataPointBuilder = QLBillingDataPoint.builder();
      Timestamp startTimeTruncatedTimestamp = null;
      Double value = Double.valueOf(0);
      for (Field field : fields) {
        switch (field.getType().getStandardType()) {
          case TIMESTAMP:
            startTimeTruncatedTimestamp = Timestamp.ofTimeMicroseconds(row.get(field.getName()).getTimestampValue());
            break;
          case STRING:
            String stringValue = fetchStringValue(row, field);
            billingDataPointBuilder.key(
                QLReference.builder().id(stringValue).name(stringValue).type(field.getName()).build());
            break;
          case FLOAT64:
            value += getNumericValue(row, field);
            break;
          default:
            break;
        }
      }

      billingDataPointBuilder.value(getRoundedDoubleValue(value));
      List<QLBillingDataPoint> dataPoints = new ArrayList<>();
      if (timeSeriesDataPointsMap.containsKey(startTimeTruncatedTimestamp)) {
        dataPoints = timeSeriesDataPointsMap.get(startTimeTruncatedTimestamp);
      }
      dataPoints.add(billingDataPointBuilder.build());
      timeSeriesDataPointsMap.put(startTimeTruncatedTimestamp, dataPoints);
    }

    return QLViewTimeSeriesData.builder().stats(convertTimeSeriesPointsMapToList(timeSeriesDataPointsMap)).build();
  }

  public static String fetchStringValue(FieldValueList row, Field field) {
    Object value = row.get(field.getName()).getValue();
    if (value != null) {
      return value.toString();
    }
    return nullStringValueConstant;
  }

  private double getRoundedDoubleValue(double value) {
    return Math.round(value * 100D) / 100D;
  }

  @Override
  protected QLData postFetch(String accountId, List<QLCEViewGroupBy> groupByList,
      List<QLCEViewAggregation> aggregations, List<QLCEViewSortCriteria> sort, QLData qlData, Integer limit,
      boolean includeOthers) {
    QLViewTimeSeriesData data = (QLViewTimeSeriesData) qlData;
    Map<String, Double> aggregatedDataPerUniqueId = new HashMap<>();
    data.getStats().forEach(dataPoint -> {
      for (QLBillingDataPoint entry : dataPoint.getValues()) {
        QLReference qlReference = entry.getKey();
        if (qlReference != null && qlReference.getId() != null) {
          String key = qlReference.getId();
          if (aggregatedDataPerUniqueId.containsKey(key)) {
            aggregatedDataPerUniqueId.put(key, entry.getValue().doubleValue() + aggregatedDataPerUniqueId.get(key));
          } else {
            aggregatedDataPerUniqueId.put(key, entry.getValue().doubleValue());
          }
        }
      }
    });
    if (aggregatedDataPerUniqueId.isEmpty()) {
      return qlData;
    }
    List<String> selectedIdsAfterLimit = billingDataHelper.getElementIdsAfterLimit(aggregatedDataPerUniqueId, limit);

    return QLViewTimeSeriesData.builder().stats(getDataAfterLimit(data, selectedIdsAfterLimit, includeOthers)).build();
  }

  private List<TimeSeriesDataPoints> getDataAfterLimit(
      QLViewTimeSeriesData data, List<String> selectedIdsAfterLimit, boolean includeOthers) {
    List<TimeSeriesDataPoints> limitProcessedData = new ArrayList<>();
    data.getStats().forEach(dataPoint -> {
      List<QLBillingDataPoint> limitProcessedValues = new ArrayList<>();
      QLBillingDataPoint others =
          QLBillingDataPoint.builder().key(QLReference.builder().id(OTHERS).name(OTHERS).build()).value(0).build();
      for (QLBillingDataPoint entry : dataPoint.getValues()) {
        String key = entry.getKey().getId();
        if (selectedIdsAfterLimit.contains(key)) {
          limitProcessedValues.add(entry);
        } else {
          others.setValue(others.getValue().doubleValue() + entry.getValue().doubleValue());
        }
      }

      if (others.getValue().doubleValue() > 0 && includeOthers) {
        others.setValue(billingDataHelper.getRoundedDoubleValue(others.getValue().doubleValue()));
        limitProcessedValues.add(others);
      }

      limitProcessedData.add(
          TimeSeriesDataPoints.builder().time(dataPoint.getTime()).values(limitProcessedValues).build());
    });
    return limitProcessedData;
  }

  @Override
  protected QLData fetchSelectedFields(String accountId, List<QLCEViewAggregation> aggregateFunction,
      List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy, List<QLCEViewSortCriteria> sort,
      Integer limit, Integer offset, boolean skipRoundOff, DataFetchingEnvironment dataFetchingEnvironment) {
    return null;
  }

  @Override
  public String getEntityType() {
    return null;
  }

  @Override
  public boolean isCESampleAccountIdAllowed() {
    return false;
  }
}
