/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.billing.graphql;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.billing.graphql.CloudBillingFilter.BILLING_AWS_INSTANCE_TYPE;
import static io.harness.ccm.billing.graphql.CloudBillingFilter.BILLING_AWS_LINKED_ACCOUNT;
import static io.harness.ccm.billing.graphql.CloudBillingFilter.BILLING_AWS_SERVICE;
import static io.harness.ccm.billing.graphql.CloudBillingFilter.BILLING_AWS_TAG;
import static io.harness.ccm.billing.graphql.CloudBillingFilter.BILLING_AWS_TAG_KEY;
import static io.harness.ccm.billing.graphql.CloudBillingFilter.BILLING_AWS_TAG_VALUE;
import static io.harness.ccm.billing.graphql.CloudBillingFilter.BILLING_AWS_USAGE_TYPE;
import static io.harness.ccm.billing.graphql.CloudBillingFilter.BILLING_GCP_BILLING_ACCOUNT_ID;
import static io.harness.ccm.billing.graphql.CloudBillingFilter.BILLING_GCP_LABEL;
import static io.harness.ccm.billing.graphql.CloudBillingFilter.BILLING_GCP_LABEL_KEY;
import static io.harness.ccm.billing.graphql.CloudBillingFilter.BILLING_GCP_LABEL_VALUE;
import static io.harness.ccm.billing.graphql.CloudBillingFilter.BILLING_GCP_PRODUCT;
import static io.harness.ccm.billing.graphql.CloudBillingFilter.BILLING_GCP_PROJECT;
import static io.harness.ccm.billing.graphql.CloudBillingFilter.BILLING_GCP_SKU;
import static io.harness.ccm.billing.graphql.CloudBillingFilter.BILLING_REGION;
import static io.harness.ccm.billing.graphql.CloudBillingFilter.CLOUD_PROVIDER;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.billing.RawBillingTableSchema;
import io.harness.ccm.billing.preaggregated.PreAggregateConstants;
import io.harness.ccm.billing.preaggregated.PreAggregatedTableSchema;

import software.wings.graphql.schema.type.aggregation.Filter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;

import com.google.common.base.Preconditions;
import com.healthmarketscience.sqlbuilder.BinaryCondition;
import com.healthmarketscience.sqlbuilder.ComboCondition;
import com.healthmarketscience.sqlbuilder.Condition;
import com.healthmarketscience.sqlbuilder.CustomSql;
import com.healthmarketscience.sqlbuilder.InCondition;
import com.healthmarketscience.sqlbuilder.UnaryCondition;
import com.healthmarketscience.sqlbuilder.dbspec.basic.DbColumn;
import java.util.Arrays;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(CE)
@TargetModule(HarnessModule._375_CE_GRAPHQL)
public class CloudBillingIdFilter implements Filter<QLIdOperator, String> {
  private QLIdOperator operator;
  private String variable;
  private String[] values;

  private static final String awsCustomSqlTags = "CONCAT(tags.key, ':', tags.value)";
  private static final String gcpCustomSqlLabels = "CONCAT(labels.key, ':', labels.value)";
  private static final String[] listOfNullTranslatedFilters = {PreAggregateConstants.entityConstantNoRegion,
      PreAggregateConstants.entityConstantAwsNoLinkedAccount, PreAggregateConstants.entityConstantAwsNoUsageType,
      PreAggregateConstants.entityConstantAwsNoInstanceType, PreAggregateConstants.entityConstantAwsNoService,
      PreAggregateConstants.entityConstantGcpNoProjectId, PreAggregateConstants.entityConstantGcpNoProduct,
      PreAggregateConstants.entityConstantGcpNoSkuId, PreAggregateConstants.entityConstantGcpNoSku,
      PreAggregateConstants.entityNoCloudProviderConst};

  @Override
  public String[] getValues() {
    return values.clone();
  }

  public Condition toCondition() {
    Preconditions.checkNotNull(values, "The billing Id filter is missing values");
    Preconditions.checkNotNull(operator, "The billing Id filter is missing operator");

    DbColumn dbColumn = null;
    switch (variable) {
      case CLOUD_PROVIDER:
        dbColumn = PreAggregatedTableSchema.cloudProvider;
        break;
      case BILLING_GCP_PROJECT:
        dbColumn = PreAggregatedTableSchema.gcpProjectId;
        break;
      case BILLING_GCP_PRODUCT:
        dbColumn = PreAggregatedTableSchema.gcpProduct;
        break;
      case BILLING_GCP_SKU:
        dbColumn = PreAggregatedTableSchema.gcpSkuDescription;
        break;
      case BILLING_GCP_BILLING_ACCOUNT_ID:
        dbColumn = PreAggregatedTableSchema.gcpBillingAccountId;
        break;
      case BILLING_REGION:
        dbColumn = PreAggregatedTableSchema.region;
        break;
      case BILLING_AWS_SERVICE:
        dbColumn = PreAggregatedTableSchema.awsServiceCode;
        break;
      case BILLING_AWS_USAGE_TYPE:
        dbColumn = PreAggregatedTableSchema.awsUsageType;
        break;
      case BILLING_AWS_INSTANCE_TYPE:
        dbColumn = PreAggregatedTableSchema.awsInstanceType;
        break;
      case BILLING_AWS_LINKED_ACCOUNT:
        dbColumn = PreAggregatedTableSchema.awsUsageAccountId;
        break;
      default:
        return null;
    }

    return returnCondition(operator, dbColumn);
  }

  public Condition toRawTableCondition() {
    Preconditions.checkNotNull(values, "The billing Id filter is missing values");
    Preconditions.checkNotNull(operator, "The billing Id filter is missing operator");

    DbColumn dbColumn = null;
    switch (variable) {
      case BILLING_GCP_PROJECT:
        dbColumn = RawBillingTableSchema.gcpProjectId;
        break;
      case BILLING_GCP_PRODUCT:
        dbColumn = RawBillingTableSchema.gcpProduct;
        break;
      case BILLING_GCP_SKU:
        dbColumn = RawBillingTableSchema.gcpSkuDescription;
        break;
      case BILLING_GCP_BILLING_ACCOUNT_ID:
        dbColumn = RawBillingTableSchema.gcpBillingAccountId;
        break;
      case BILLING_REGION:
        dbColumn = RawBillingTableSchema.region;
        break;
      case BILLING_GCP_LABEL_KEY:
        dbColumn = RawBillingTableSchema.labelsKey;
        break;
      case BILLING_GCP_LABEL_VALUE:
        dbColumn = RawBillingTableSchema.labelsValue;
        break;
      case BILLING_GCP_LABEL:
        dbColumn = RawBillingTableSchema.labels;
        break;
      default:
        return null;
    }
    return returnCondition(operator, dbColumn);
  }

  public Condition toAwsRawTableCondition() {
    Preconditions.checkNotNull(values, "The AWS Raw Table billing Id filter is missing values");
    Preconditions.checkNotNull(operator, "The AWS Raw Table billing Id filter is missing operator");

    DbColumn dbColumn = null;
    switch (variable) {
      case BILLING_REGION:
        dbColumn = RawBillingTableSchema.awsRegion;
        break;
      case BILLING_AWS_SERVICE:
        dbColumn = RawBillingTableSchema.awsServiceCode;
        break;
      case BILLING_AWS_USAGE_TYPE:
        dbColumn = RawBillingTableSchema.awsUsageType;
        break;
      case BILLING_AWS_INSTANCE_TYPE:
        dbColumn = RawBillingTableSchema.awsInstanceType;
        break;
      case BILLING_AWS_LINKED_ACCOUNT:
        dbColumn = RawBillingTableSchema.awsUsageAccountId;
        break;
      case BILLING_AWS_TAG_KEY:
        dbColumn = RawBillingTableSchema.tagsKey;
        break;
      case BILLING_AWS_TAG_VALUE:
        dbColumn = RawBillingTableSchema.tagsValue;
        break;
      case BILLING_AWS_TAG:
        dbColumn = RawBillingTableSchema.tags;
        break;
      default:
        return null;
    }
    return returnCondition(operator, dbColumn);
  }

  public Condition returnCondition(QLIdOperator operator, DbColumn dbColumnName) {
    Object dbColumn = dbColumnName;
    if (dbColumnName.equals(RawBillingTableSchema.labelsKey) || dbColumnName.equals(RawBillingTableSchema.labelsValue)
        || dbColumnName.equals(RawBillingTableSchema.tagsKey) || dbColumnName.equals(RawBillingTableSchema.tagsValue)) {
      dbColumn = new CustomSql(dbColumnName.getColumnNameSQL());
    }
    if (dbColumnName.equals(RawBillingTableSchema.labels)) {
      dbColumn = new CustomSql(gcpCustomSqlLabels);
    }
    if (dbColumnName.equals(RawBillingTableSchema.tags)) {
      dbColumn = new CustomSql(awsCustomSqlTags);
    }
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

  private boolean containsNullFilter(List<String> values) {
    for (String filter : listOfNullTranslatedFilters) {
      if (values.contains(filter)) {
        return true;
      }
    }
    return false;
  }
}
