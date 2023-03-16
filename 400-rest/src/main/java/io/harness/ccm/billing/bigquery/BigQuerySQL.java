/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.billing.bigquery;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;

import com.google.common.base.Preconditions;
import com.healthmarketscience.sqlbuilder.Condition;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import com.healthmarketscience.sqlbuilder.custom.postgresql.PgLimitClause;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(CE)
public class BigQuerySQL {
  private SelectQuery query;
  private List<Object> selectColumns;
  private List<Object> groupbyObjects;
  private List<Object> sortObjects;
  private List<Condition> conditions;
  private PgLimitClause pgLimit;

  public static class BigQuerySQLBuilder {
    private SelectQuery query = new SelectQuery();
    private List<Object> selectColumns;
    private List<Object> groupbyObjects;
    private List<Object> sortObjects;
    private List<Condition> conditions;
    private PgLimitClause pgLimit;

    public BigQuerySQLBuilder selectColumns(List<Object> selectColumns) {
      Preconditions.checkNotNull(selectColumns);
      this.selectColumns = selectColumns;
      for (Object selectColumn : selectColumns) {
        query.addCustomColumns(selectColumn);
      }
      return this;
    }

    public BigQuerySQLBuilder groupbyObjects(List<Object> groupbyObjects) {
      Preconditions.checkNotNull(groupbyObjects);
      this.groupbyObjects = groupbyObjects;
      for (Object groupbyObject : groupbyObjects) {
        query.addCustomGroupings(groupbyObject);
      }
      return this;
    }

    public BigQuerySQLBuilder sortObjects(List<Object> sortObjects) {
      Preconditions.checkNotNull(sortObjects);
      this.sortObjects = sortObjects;
      for (Object sortObject : sortObjects) {
        query.addCustomOrderings(sortObject);
      }
      return this;
    }

    public BigQuerySQLBuilder conditions(List<Condition> conditions) {
      Preconditions.checkNotNull(conditions);
      this.conditions = conditions;
      for (Condition condition : conditions) {
        query.addCondition(condition);
      }
      return this;
    }

    public BigQuerySQLBuilder pgLimit(PgLimitClause pgLimit) {
      Preconditions.checkNotNull(pgLimit);
      this.pgLimit = pgLimit;
      query.addCustomization(pgLimit);
      return this;
    }
  }
}
