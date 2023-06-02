/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.anomalydetection;

import io.harness.ccm.billing.graphql.CloudBillingAggregate;
import io.harness.ccm.billing.graphql.CloudBillingFilter;
import io.harness.ccm.billing.graphql.CloudBillingGroupBy;
import io.harness.ccm.billing.graphql.CloudBillingSortCriteria;
import io.harness.ccm.billing.graphql.CloudSortType;
import io.harness.ccm.billing.preaggregated.PreAggregatedTableSchema;

import com.healthmarketscience.sqlbuilder.AliasedObject;
import com.healthmarketscience.sqlbuilder.BinaryCondition;
import com.healthmarketscience.sqlbuilder.CustomSql;
import com.healthmarketscience.sqlbuilder.FunctionCall;
import com.healthmarketscience.sqlbuilder.InCondition;
import com.healthmarketscience.sqlbuilder.OrderObject;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import com.healthmarketscience.sqlbuilder.SqlObject;
import com.healthmarketscience.sqlbuilder.UnaryCondition;
import com.healthmarketscience.sqlbuilder.dbspec.Column;
import com.healthmarketscience.sqlbuilder.dbspec.basic.DbColumn;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CloudQueryMetaData {
  String accountId;

  List<DbColumn> selectColumns;
  List<CloudBillingFilter> filterList;
  List<CloudBillingGroupBy> groupByList;
  List<CloudBillingAggregate> aggregationList;
  List<CloudBillingSortCriteria> sortCriteriaList;
  List<DbColumn> notNullColumns;

  public String getAggerateColumnName() {
    CloudBillingAggregate aggregate = aggregationList.get(0);
    return String.join("_", aggregate.getOperationType().name().toLowerCase(), aggregate.getColumnName());
  }

  public String getMetaDataQuery() {
    SelectQuery metaQuery = new SelectQuery();

    metaQuery.setIsDistinct(true);

    metaQuery.addCustomColumns(getHashColumn(groupByList, true));

    for (DbColumn column : notNullColumns) {
      metaQuery.addCondition(UnaryCondition.isNotNull(column));
    }

    for (CloudBillingAggregate aggregateColumn : aggregationList) {
      metaQuery.addCustomColumns(aggregateColumn.toFunctionCall());
      metaQuery.addHaving(BinaryCondition.greaterThan(
          new CustomSql(aggregateColumn.getAlias()), AnomalyDetectionConstants.MINIMUM_AMOUNT, true));
    }

    for (CloudBillingFilter filter : filterList) {
      metaQuery.addCondition(filter.toCondition());
    }

    for (CloudBillingGroupBy groupBy : groupByList) {
      if (groupBy.isEntityGroupBY()) {
        metaQuery.addCustomColumns(groupBy.getEntityGroupBy().getDbObject());
        metaQuery.addCondition(UnaryCondition.isNotNull(groupBy.getEntityGroupBy().getDbObject()));
        metaQuery.addGroupings((Column) groupBy.toGroupbyObject());
      }
    }

    for (CloudBillingSortCriteria criteria : sortCriteriaList) {
      if (criteria.getSortType() != CloudSortType.Time) {
        metaQuery.addCustomOrderings(criteria.toOrderObject());
      }
    }

    return metaQuery.validate().toString();
  }

  public String getQuery() {
    return formQuery(filterList, groupByList, aggregationList, sortCriteriaList, new ArrayList<>(), notNullColumns);
  }

  public String getQuery(List<String> hashCodes) {
    return formQuery(filterList, groupByList, aggregationList, sortCriteriaList, hashCodes, notNullColumns);
  }

  private String formQuery(List<CloudBillingFilter> filterList, List<CloudBillingGroupBy> groupByList,
      List<CloudBillingAggregate> aggregationList, List<CloudBillingSortCriteria> sortCriteriaList,
      List<String> hashCodes, List<DbColumn> notNullColumns) {
    SelectQuery query = new SelectQuery();

    query.addCustomColumns(getHashColumn(groupByList, true));

    for (DbColumn column : notNullColumns) {
      query.addCondition(UnaryCondition.isNotNull(column));
    }

    for (CloudBillingAggregate aggregateColumn : aggregationList) {
      query.addCustomColumns(aggregateColumn.toFunctionCall());
    }

    for (CloudBillingFilter filter : filterList) {
      query.addCondition(filter.toCondition());
    }

    for (CloudBillingGroupBy groupBy : groupByList) {
      if (groupBy.isEntityGroupBY()) {
        query.addCustomColumns(groupBy.getEntityGroupBy().getDbObject());
        query.addCondition(UnaryCondition.isNotNull(groupBy.getEntityGroupBy().getDbObject()));
        query.addGroupings((Column) groupBy.toGroupbyObject());
      }
      if (groupBy.isTimeGroupBY()) {
        query.addCustomColumns(groupBy.getTimeTruncGroupby().getEntity());
        query.addCondition(UnaryCondition.isNotNull(groupBy.getTimeTruncGroupby().getEntity()));
        query.addGroupings(groupBy.getTimeTruncGroupby().getEntity());
      }
    }

    for (CloudBillingSortCriteria criteria : sortCriteriaList) {
      if (criteria.getSortType() != CloudSortType.Time) {
        query.addCustomOrderings(criteria.toOrderObject());
      } else {
        query.addCustomOrderings(
            new OrderObject(OrderObject.Dir.ASCENDING, PreAggregatedTableSchema.startTime.getColumnNameSQL()));
      }
    }

    query.addCondition(new InCondition(getHashColumn(groupByList, false), hashCodes));

    return query.validate().toString();
  }

  public SqlObject getHashColumn(List<CloudBillingGroupBy> groupByList) {
    FunctionCall concatFunction = new FunctionCall(new CustomSql("CONCAT"));
    FunctionCall md5HashFunction = new FunctionCall(new CustomSql("MD5"));

    for (CloudBillingGroupBy groupBy : groupByList) {
      concatFunction.addColumnParams((Column) groupBy.toGroupbyObject());
    }
    md5HashFunction.addCustomParams(new CustomSql(concatFunction.toString()));
    return AliasedObject.toAliasedObject(md5HashFunction, "hashcode");
  }

  public SqlObject getHashColumn(List<CloudBillingGroupBy> groupByList, boolean isAliased) {
    FunctionCall concatFunction = new FunctionCall(new CustomSql("CONCAT"));
    FunctionCall md5HashFunction = new FunctionCall(new CustomSql("MD5"));
    FunctionCall base64Function = new FunctionCall(new CustomSql("TO_BASE64"));

    for (CloudBillingGroupBy groupBy : groupByList) {
      if (groupBy.isEntityGroupBY()) {
        concatFunction.addColumnParams((Column) groupBy.toGroupbyObject());
      }
    }
    md5HashFunction.addCustomParams(new CustomSql(concatFunction.toString()));
    base64Function.addCustomParams(new CustomSql(md5HashFunction.toString()));
    if (isAliased) {
      return AliasedObject.toAliasedObject(base64Function, "hashcode");
    } else {
      return new CustomSql(base64Function.toString());
    }
  }
  public List<Object> convertList(List<CloudBillingGroupBy> list) {
    List<Object> returnlist = new ArrayList<>();
    for (CloudBillingGroupBy item : list) {
      returnlist.add((Object) item);
    }
    return returnlist;
  }
}
