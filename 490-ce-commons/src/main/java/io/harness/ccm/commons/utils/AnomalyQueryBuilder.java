/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.commons.utils;

import static io.harness.ccm.commons.entities.CCMField.ALL;
import static io.harness.ccm.commons.entities.CCMField.ANOMALOUS_SPEND;
import static io.harness.ccm.commons.entities.CCMField.COST_IMPACT;
import static io.harness.ccm.commons.entities.CCMOperator.LIKE;
import static io.harness.ccm.commons.utils.TimeUtils.toOffsetDateTime;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.timescaledb.Tables.ANOMALIES;

import io.harness.ccm.commons.entities.CCMField;
import io.harness.ccm.commons.entities.CCMFilter;
import io.harness.ccm.commons.entities.CCMNumberFilter;
import io.harness.ccm.commons.entities.CCMOperator;
import io.harness.ccm.commons.entities.CCMSort;
import io.harness.ccm.commons.entities.CCMStringFilter;
import io.harness.ccm.commons.entities.CCMTimeFilter;
import io.harness.exception.InvalidRequestException;
import io.harness.timescaledb.tables.records.AnomaliesRecord;

import com.sun.istack.internal.NotNull;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.OrderField;
import org.jooq.TableField;
import org.jooq.impl.DSL;

@Slf4j
public class AnomalyQueryBuilder {
  private static final List<TableField<AnomaliesRecord, String>> ANOMALY_TABLE_ENTITIES =
      Arrays.asList(ANOMALIES.WORKLOADNAME, ANOMALIES.NAMESPACE, ANOMALIES.CLUSTERNAME, ANOMALIES.AWSACCOUNT,
          ANOMALIES.AWSSERVICE, ANOMALIES.AWSINSTANCETYPE, ANOMALIES.AWSUSAGETYPE, ANOMALIES.GCPPRODUCT,
          ANOMALIES.GCPPROJECT, ANOMALIES.GCPSKUDESCRIPTION, ANOMALIES.GCPSKUID);

  // Fields which don't directly correspond to a column in anomalies table
  private static final List<CCMField> NON_TABLE_FIELDS = Arrays.asList(ANOMALOUS_SPEND, COST_IMPACT, ALL);

  @NotNull
  public List<OrderField<?>> getOrderByFields(@NotNull List<CCMSort> sortList) {
    List<OrderField<?>> orderByFields = new ArrayList<>();
    sortList.forEach(sortField -> {
      if (!NON_TABLE_FIELDS.contains(sortField.getField())) {
        orderByFields.add(getOrderByField(sortField));
      }
    });
    return orderByFields;
  }

  @NotNull
  private OrderField<?> getOrderByField(CCMSort sort) {
    switch (sort.getOrder()) {
      case ASCENDING:
        return getTableField(sort.getField()).asc().nullsLast();
      case DESCENDING:
        return getTableField(sort.getField()).desc().nullsLast();
      default:
        throw new InvalidRequestException(String.format("%s sort order not supported", sort.getOrder().toString()));
    }
  }

  @NotNull
  public Condition applyAllFilters(@NotNull CCMFilter filter) {
    Condition condition = DSL.noCondition();

    if (filter.getNumericFilters() != null) {
      condition = applyNumericFilters(filter.getNumericFilters(), condition);
    }

    if (filter.getStringFilters() != null) {
      // Todo: Remove perspectiveId filter if present
      condition = applyStringFilters(filter.getStringFilters(), condition);
    }

    if (filter.getTimeFilters() != null) {
      condition = applyTimeFilters(filter.getTimeFilters(), condition);
    }

    return condition;
  }

  @NotNull
  public Condition applyAllFilters(@NotNull CCMFilter filter, @NotNull List<CCMFilter> ruleFilters) {
    Condition condition = DSL.noCondition();
    condition = applyPerspectiveRuleFilters(ruleFilters);

    if (filter.getNumericFilters() != null) {
      condition = applyNumericFilters(filter.getNumericFilters(), condition);
    }

    if (filter.getStringFilters() != null) {
      // Todo: Remove perspectiveId filter if present
      condition = applyStringFilters(filter.getStringFilters(), condition);
    }

    if (filter.getTimeFilters() != null) {
      condition = applyTimeFilters(filter.getTimeFilters(), condition);
    }

    return condition;
  }

  @NotNull
  private Condition applyTimeFilters(@NotNull List<CCMTimeFilter> filters, Condition condition) {
    for (CCMTimeFilter filter : filters) {
      condition = condition.and(constructCondition(ANOMALIES.ANOMALYTIME, filter.getTimestamp(), filter.getOperator()));
    }
    return condition;
  }

  @NotNull
  private Condition applyNumericFilters(@NotNull List<CCMNumberFilter> filters, Condition condition) {
    for (CCMNumberFilter filter : filters) {
      switch (filter.getField()) {
        case ACTUAL_COST:
          condition = condition.and(
              constructCondition(ANOMALIES.ACTUALCOST, filter.getValue().doubleValue(), filter.getOperator()));
          break;
        default:
          throw new InvalidRequestException(
              String.format("%s numeric filter not supported", filter.getField().toString()));
      }
    }
    return condition;
  }

  @NotNull
  private Condition applyStringFilters(@NotNull List<CCMStringFilter> filters, Condition condition) {
    for (CCMStringFilter filter : filters) {
      try {
        if (filter.getField() == ALL && filter.getOperator() == LIKE) {
          condition = condition.and(constructSearchCondition(filter.getValues()));
        } else {
          condition = condition.and(
              constructCondition(getTableField(filter.getField()), filter.getValues(), filter.getOperator()));
        }
      } catch (Exception ignored) {
      }
    }
    return condition;
  }

  @NotNull
  private Condition applyPerspectiveRuleFilters(@NotNull List<CCMFilter> filters) {
    Condition overallCondition = DSL.noCondition();
    for (CCMFilter filter : filters) {
      Condition ruleCondition = DSL.noCondition();
      if (filter.getStringFilters() != null) {
        ruleCondition = applyStringFilters(filter.getStringFilters(), ruleCondition);
      }
      overallCondition = overallCondition.or(ruleCondition);
    }
    return overallCondition;
  }

  @NotNull
  private static TableField<AnomaliesRecord, String> getStringField(CCMField field) {
    switch (field) {
      case WORKLOAD:
        return ANOMALIES.WORKLOADNAME;
      case WORKLOAD_TYPE:
        return ANOMALIES.WORKLOADTYPE;
      case NAMESPACE:
        return ANOMALIES.NAMESPACE;
      case CLUSTER_ID:
        return ANOMALIES.CLUSTERID;
      case CLUSTER_NAME:
        return ANOMALIES.CLUSTERNAME;
      case AWS_ACCOUNT:
        return ANOMALIES.AWSACCOUNT;
      case AWS_SERVICE:
        return ANOMALIES.AWSSERVICE;
      case AWS_USAGE_TYPE:
        return ANOMALIES.AWSUSAGETYPE;
      case AWS_INSTANCE_TYPE:
        return ANOMALIES.AWSINSTANCETYPE;
      case GCP_PROJECT:
        return ANOMALIES.GCPPROJECT;
      case GCP_PRODUCT:
        return ANOMALIES.GCPPRODUCT;
      case GCP_SKU_ID:
        return ANOMALIES.GCPSKUID;
      case GCP_SKU_DESCRIPTION:
        return ANOMALIES.GCPSKUDESCRIPTION;
      default:
        throw new InvalidRequestException(String.format("%s not supported", field.toString()));
    }
  }

  @NotNull
  private static TableField<AnomaliesRecord, ?> getTableField(CCMField field) {
    switch (field) {
      case ANOMALY_TIME:
        return ANOMALIES.ANOMALYTIME;
      case ACTUAL_COST:
        return ANOMALIES.ACTUALCOST;
      case WORKLOAD:
        return ANOMALIES.WORKLOADNAME;
      case WORKLOAD_TYPE:
        return ANOMALIES.WORKLOADTYPE;
      case NAMESPACE:
        return ANOMALIES.NAMESPACE;
      case CLUSTER_ID:
        return ANOMALIES.CLUSTERID;
      case CLUSTER_NAME:
        return ANOMALIES.CLUSTERNAME;
      case AWS_ACCOUNT:
        return ANOMALIES.AWSACCOUNT;
      case AWS_SERVICE:
        return ANOMALIES.AWSSERVICE;
      case AWS_USAGE_TYPE:
        return ANOMALIES.AWSUSAGETYPE;
      case AWS_INSTANCE_TYPE:
        return ANOMALIES.AWSINSTANCETYPE;
      case GCP_PROJECT:
        return ANOMALIES.GCPPROJECT;
      case GCP_PRODUCT:
        return ANOMALIES.GCPPRODUCT;
      case GCP_SKU_ID:
        return ANOMALIES.GCPSKUID;
      case GCP_SKU_DESCRIPTION:
        return ANOMALIES.GCPSKUDESCRIPTION;
      default:
        throw new InvalidRequestException(String.format("%s not supported", field.toString()));
    }
  }

  @NotNull
  private static Condition constructCondition(
      TableField<AnomaliesRecord, ?> field, List<String> values, CCMOperator operator) {
    switch (operator) {
      case IN:
        return !isEmpty(values) ? field.in(values) : DSL.noCondition();
      case NOT_IN:
        return !isEmpty(values) ? field.notIn(values) : DSL.noCondition();
      case LIKE:
        return !isEmpty(values) ? field.likeIgnoreCase(values.get(0)) : DSL.noCondition();
      case NULL:
        return field.isNull();
      case NOT_NULL:
        return field.isNotNull();
      default:
        throw new InvalidRequestException(String.format("%s not supported for string fields", operator.toString()));
    }
  }

  @NotNull
  private static Condition constructSearchCondition(List<String> values) {
    String searchKey = !isEmpty(values) ? values.get(0) + "%" : null;
    Condition condition = DSL.noCondition();
    if (searchKey != null) {
      for (TableField<AnomaliesRecord, String> entity : ANOMALY_TABLE_ENTITIES) {
        condition = condition.or(entity.likeIgnoreCase(searchKey));
      }
    }
    return condition;
  }

  @NotNull
  private static Condition constructCondition(
      TableField<AnomaliesRecord, Double> field, Double value, CCMOperator operator) {
    switch (operator) {
      case GREATER_THAN:
        return value != null ? field.greaterThan(value) : DSL.noCondition();
      case GREATER_THAN_EQUALS_TO:
        return value != null ? field.greaterOrEqual(value) : DSL.noCondition();
      case LESS_THAN:
        return value != null ? field.lessThan(value) : DSL.noCondition();
      case LESS_THAN_EQUALS_TO:
        return value != null ? field.lessOrEqual(value) : DSL.noCondition();
      default:
        throw new InvalidRequestException(String.format("%s not supported for numeric fields", operator.toString()));
    }
  }

  @NotNull
  private static Condition constructCondition(
      TableField<AnomaliesRecord, OffsetDateTime> field, long timestamp, CCMOperator operator) {
    switch (operator) {
      case AFTER:
        return field.greaterOrEqual(toOffsetDateTime(timestamp));
      case BEFORE:
        return field.lessOrEqual(toOffsetDateTime(timestamp));
      default:
        throw new InvalidRequestException(String.format("%s not supported for time fields", operator.toString()));
    }
  }
}
