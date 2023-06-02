/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.billing.graphql;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.billing.graphql.CloudBillingFilter.BILLING_AWS_STARTTIME;
import static io.harness.ccm.billing.graphql.CloudBillingFilter.BILLING_GCP_ENDTIME;
import static io.harness.ccm.billing.graphql.CloudBillingFilter.BILLING_GCP_STARTTIME;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.billing.RawBillingTableSchema;
import io.harness.ccm.billing.preaggregated.PreAggregatedTableSchema;
import io.harness.exception.InvalidRequestException;

import software.wings.graphql.schema.type.aggregation.Filter;
import software.wings.graphql.schema.type.aggregation.QLTimeOperator;

import com.google.cloud.Timestamp;
import com.google.common.base.Preconditions;
import com.healthmarketscience.sqlbuilder.BinaryCondition;
import com.healthmarketscience.sqlbuilder.Condition;
import com.healthmarketscience.sqlbuilder.dbspec.basic.DbColumn;
import java.util.Calendar;
import java.util.Date;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.time.DateUtils;

@Data
@Builder
@OwnedBy(CE)
@TargetModule(HarnessModule._375_CE_GRAPHQL)
public class CloudBillingTimeFilter implements Filter {
  private QLTimeOperator operator;
  private String variable;
  private Number value;
  private static final String invalidTimeFilter = "Invalid time filter.";

  @Override
  public Number[] getValues() {
    return new Number[] {value};
  }

  public Condition toCondition() {
    Preconditions.checkNotNull(value, "The billing time filter is missing value.");
    Preconditions.checkNotNull(operator, "The billing time filter is missing operator");
    Timestamp timestamp = Timestamp.of(DateUtils.round(new Date(value.longValue()), Calendar.SECOND));

    DbColumn dbColumn = null;
    if (variable.equals(BILLING_GCP_STARTTIME)) {
      dbColumn = PreAggregatedTableSchema.startTime;
    } else if (variable.equals(BILLING_GCP_ENDTIME)) {
      dbColumn = PreAggregatedTableSchema.endTime;
    } else if (variable.equals(BILLING_AWS_STARTTIME)) {
      dbColumn = PreAggregatedTableSchema.startTime;
    } else {
      throw new InvalidRequestException(invalidTimeFilter);
    }

    return getCondition(dbColumn, timestamp);
  }

  public Condition toRawTableCondition() {
    Preconditions.checkNotNull(value, "The billing time filter is missing value.");
    Preconditions.checkNotNull(operator, "The billing time filter is missing operator");
    Timestamp timestamp = Timestamp.of(DateUtils.round(new Date(value.longValue()), Calendar.SECOND));

    DbColumn dbColumn = null;
    if (variable.equals(BILLING_GCP_STARTTIME)) {
      dbColumn = RawBillingTableSchema.startTime;
    } else if (variable.equals(BILLING_GCP_ENDTIME)) {
      dbColumn = RawBillingTableSchema.endTime;
    } else if (variable.equals(BILLING_AWS_STARTTIME)) {
      dbColumn = RawBillingTableSchema.startTime;
    } else {
      throw new InvalidRequestException(invalidTimeFilter);
    }

    return getCondition(dbColumn, timestamp);
  }

  public Condition toAwsRawTableCondition() {
    Preconditions.checkNotNull(value, "The AWS Raw Table billing time filter is missing value.");
    Preconditions.checkNotNull(operator, "The AWS Raw Table billing time filter is missing operator");
    Timestamp timestamp = Timestamp.of(DateUtils.round(new Date(value.longValue()), Calendar.SECOND));

    DbColumn dbColumn = null;
    if (variable.equals(BILLING_GCP_STARTTIME) || variable.equals(BILLING_GCP_ENDTIME)
        || variable.equals(BILLING_AWS_STARTTIME)) {
      dbColumn = RawBillingTableSchema.awsStartTime;
    } else {
      throw new InvalidRequestException(invalidTimeFilter);
    }

    return getCondition(dbColumn, timestamp);
  }

  private Condition getCondition(DbColumn dbColumn, Timestamp timestamp) {
    switch (operator) {
      case AFTER:
        return BinaryCondition.greaterThanOrEq(dbColumn, timestamp);
      case BEFORE:
        return BinaryCondition.lessThanOrEq(dbColumn, timestamp);
      default:
        return null;
    }
  }
}
