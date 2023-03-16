/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.anomaly.graphql;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.anomaly.entities.AnomalyEntity.AnomaliesDataTableSchema;
import io.harness.ccm.billing.preaggregated.PreAggregateConstants;

import software.wings.graphql.schema.type.aggregation.QLIdOperator;

import com.google.common.base.Preconditions;
import com.healthmarketscience.sqlbuilder.BinaryCondition;
import com.healthmarketscience.sqlbuilder.ComboCondition;
import com.healthmarketscience.sqlbuilder.Condition;
import com.healthmarketscience.sqlbuilder.InCondition;
import com.healthmarketscience.sqlbuilder.UnaryCondition;
import com.healthmarketscience.sqlbuilder.dbspec.basic.DbColumn;
import java.util.Arrays;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Builder
@Slf4j
@OwnedBy(CE)
@TargetModule(HarnessModule._375_CE_GRAPHQL)
public class AnomaliesIdFilter implements AnomaliesFilter {
  private QLIdOperator operator;
  private AnomaliesDataTableSchema.fields variable;
  private String[] values;
  private static final String[] listOfNullTranslatedFilters = {PreAggregateConstants.entityConstantNoRegion,
      PreAggregateConstants.entityConstantAwsNoLinkedAccount, PreAggregateConstants.entityConstantAwsNoUsageType,
      PreAggregateConstants.entityConstantAwsNoInstanceType, PreAggregateConstants.entityConstantAwsNoService,
      PreAggregateConstants.entityConstantGcpNoProjectId, PreAggregateConstants.entityConstantGcpNoProduct,
      PreAggregateConstants.entityConstantGcpNoSkuId, PreAggregateConstants.entityConstantGcpNoSku,
      PreAggregateConstants.entityNoCloudProviderConst};

  @Override
  public Condition toCondition() {
    Preconditions.checkNotNull(values, "The Anomalies Id filter is missing values");
    Preconditions.checkNotNull(operator, "The Anomalies Id filter is missing operator");

    DbColumn dbColumn = null;
    switch (variable) {
      case CLUSTER_ID:
        dbColumn = AnomaliesDataTableSchema.clusterId;
        break;
      case CLUSTER_NAME:
        dbColumn = AnomaliesDataTableSchema.clusterName;
        break;
      case NAMESPACE:
        dbColumn = AnomaliesDataTableSchema.namespace;
        break;
      case WORKLOAD_TYPE:
        dbColumn = AnomaliesDataTableSchema.workloadType;
        break;
      case GCP_PRODUCT:
        dbColumn = AnomaliesDataTableSchema.gcpProduct;
        break;
      case GCP_PROJECT:
        dbColumn = AnomaliesDataTableSchema.gcpProject;
        break;
      case GCP_SKU_ID:
        dbColumn = AnomaliesDataTableSchema.gcpSkuId;
        break;
      case GCP_SKU_DESCRIPTION:
        dbColumn = AnomaliesDataTableSchema.gcpSkuDescription;
        break;
      case AWS_ACCOUNT:
        dbColumn = AnomaliesDataTableSchema.awsAccount;
        break;
      case AWS_SERVICE:
        dbColumn = AnomaliesDataTableSchema.awsService;
        break;
      default:
        return null;
    }

    return createCondition(dbColumn);
  }
  private boolean containsNullFilter(List<String> values) {
    for (String filter : listOfNullTranslatedFilters) {
      if (values.contains(filter)) {
        return true;
      }
    }
    return false;
  }
  private Condition createCondition(DbColumn dbColumn) {
    Condition condition;
    boolean containsNullStringConst = containsNullFilter(Arrays.asList(values));
    switch (operator) {
      case EQUALS:
        if (containsNullStringConst) {
          return UnaryCondition.isNull(dbColumn);
        }
        condition = BinaryCondition.equalTo(dbColumn, values[0]);
        break;
      case IN:
        if (containsNullStringConst) {
          return ComboCondition.or(new InCondition(dbColumn, getValues()), UnaryCondition.isNull(dbColumn));
        }
        return new InCondition(dbColumn, getValues());
      case NOT_IN:
        if (containsNullStringConst) {
          return ComboCondition.or(
              new InCondition(dbColumn, getValues()).setNegate(true), UnaryCondition.isNull(dbColumn));
        }
        condition = new InCondition(dbColumn, getValues()).setNegate(true);
        break;
      case NOT_NULL:
        condition = UnaryCondition.isNotNull(dbColumn);
        break;
      case LIKE:
        condition = BinaryCondition.like(dbColumn, "%" + values[0] + "%");
        break;
      default:
        return null;
    }
    return condition;
  }
}
