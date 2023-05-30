/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.dashboard;

import static io.harness.timescaledb.Tables.PIPELINES;
import static io.harness.timescaledb.Tables.PIPELINE_EXECUTION_SUMMARY;

import static org.jooq.impl.DSL.row;

import io.harness.data.structure.EmptyPredicate;
import io.harness.ng.core.OrgProjectIdentifier;
import io.harness.pms.dashboards.ExecutionsCount;
import io.harness.pms.dashboards.PipelinesCount;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.List;
import javax.validation.constraints.NotNull;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record2;
import org.jooq.Row2;
import org.jooq.Table;
import org.jooq.impl.DSL;

public class PMSLandingDashboardServiceImpl implements PMSLandingDashboardService {
  @Inject private DSLContext dsl;
  private static String ORG_ID = "orgId";
  private static String PROJECT_ID = "projectId";

  @Override
  public PipelinesCount getPipelinesCount(String accountIdentifier, List<OrgProjectIdentifier> orgProjectIdentifiers,
      long startInterval, long endInterval) {
    if (EmptyPredicate.isEmpty(orgProjectIdentifiers)) {
      return PipelinesCount.builder().build();
    }
    Table<Record2<String, String>> orgProjectTable = getOrgProjectTable(orgProjectIdentifiers);

    Integer totalCount = getTotalPipelinesCount(accountIdentifier, orgProjectTable);
    int trendCount = getNewPipelinesCount(accountIdentifier, startInterval, endInterval, orgProjectTable)
        - getDeletedPipelinesCount(accountIdentifier, startInterval, endInterval, orgProjectTable);

    return PipelinesCount.builder().totalCount(totalCount).newCount(trendCount).build();
  }

  @Override
  public ExecutionsCount getExecutionsCount(String accountIdentifier, List<OrgProjectIdentifier> orgProjectIdentifiers,
      long startInterval, long endInterval) {
    if (EmptyPredicate.isEmpty(orgProjectIdentifiers)) {
      return ExecutionsCount.builder().build();
    }
    Table<Record2<String, String>> orgProjectTable = getOrgProjectTable(orgProjectIdentifiers);

    Integer totalCount = getTotalExecutionsCount(accountIdentifier, orgProjectTable);
    int trendCount = getNewExecutionsCount(accountIdentifier, startInterval, endInterval, orgProjectTable);
    return ExecutionsCount.builder().totalCount(totalCount).newCount(trendCount).build();
  }

  private Integer getTotalExecutionsCount(String accountIdentifier, Table<Record2<String, String>> orgProjectTable) {
    return dsl.select(DSL.count())
        .from(PIPELINE_EXECUTION_SUMMARY)
        .where(PIPELINE_EXECUTION_SUMMARY.ACCOUNTID.eq(accountIdentifier))
        .andExists(dsl.selectOne()
                       .from(orgProjectTable)
                       .where(PIPELINE_EXECUTION_SUMMARY.ORGIDENTIFIER.eq((Field<String>) orgProjectTable.field(ORG_ID))
                                  .and(PIPELINE_EXECUTION_SUMMARY.PROJECTIDENTIFIER.eq(
                                      (Field<String>) orgProjectTable.field(PROJECT_ID)))))
        .fetchInto(Integer.class)
        .get(0);
  }

  private Integer getTotalPipelinesCount(String accountIdentifier, Table<Record2<String, String>> orgProjectTable) {
    return dsl.select(DSL.count())
        .from(PIPELINES)
        .where(PIPELINES.ACCOUNT_ID.eq(accountIdentifier))
        .and(PIPELINES.DELETED.eq(false))
        .andExists(
            dsl.selectOne()
                .from(orgProjectTable)
                .where(PIPELINES.ORG_IDENTIFIER.eq((Field<String>) orgProjectTable.field(ORG_ID))
                           .and(PIPELINES.PROJECT_IDENTIFIER.eq((Field<String>) orgProjectTable.field(PROJECT_ID)))))
        .fetchInto(Integer.class)
        .get(0);
  }

  private Integer getNewPipelinesCount(
      String accountIdentifier, long startInterval, long endInterval, Table<Record2<String, String>> orgProjectTable) {
    return dsl.select(DSL.count())
        .from(PIPELINES)
        .where(PIPELINES.ACCOUNT_ID.eq(accountIdentifier))
        .and(PIPELINES.CREATED_AT.greaterOrEqual(startInterval))
        .and(PIPELINES.CREATED_AT.lessThan(endInterval))
        .and(PIPELINES.DELETED.eq(false))
        .andExists(
            dsl.selectOne()
                .from(orgProjectTable)
                .where(PIPELINES.ORG_IDENTIFIER.eq((Field<String>) orgProjectTable.field(ORG_ID))
                           .and(PIPELINES.PROJECT_IDENTIFIER.eq((Field<String>) orgProjectTable.field(PROJECT_ID)))))
        .fetchInto(Integer.class)
        .get(0);
  }

  private Integer getNewExecutionsCount(
      String accountIdentifier, long startInterval, long endInterval, Table<Record2<String, String>> orgProjectTable) {
    return dsl.select(DSL.count())
        .from(PIPELINE_EXECUTION_SUMMARY)
        .where(PIPELINE_EXECUTION_SUMMARY.ACCOUNTID.eq(accountIdentifier))
        .and(PIPELINE_EXECUTION_SUMMARY.STARTTS.greaterOrEqual(startInterval))
        .and(PIPELINE_EXECUTION_SUMMARY.STARTTS.lessThan(endInterval))
        .andExists(dsl.selectOne()
                       .from(orgProjectTable)
                       .where(PIPELINE_EXECUTION_SUMMARY.ORGIDENTIFIER.eq((Field<String>) orgProjectTable.field(ORG_ID))
                                  .and(PIPELINE_EXECUTION_SUMMARY.PROJECTIDENTIFIER.eq(
                                      (Field<String>) orgProjectTable.field(PROJECT_ID)))))
        .fetchInto(Integer.class)
        .get(0);
  }

  private Integer getDeletedPipelinesCount(
      String accountIdentifier, long startInterval, long endInterval, Table<Record2<String, String>> orgProjectTable) {
    return dsl.select(DSL.count())
        .from(PIPELINES)
        .where(PIPELINES.ACCOUNT_ID.eq(accountIdentifier))
        .and(PIPELINES.LAST_UPDATED_AT.greaterOrEqual(startInterval))
        .and(PIPELINES.LAST_UPDATED_AT.lessThan(endInterval))
        .and(PIPELINES.DELETED.eq(true))
        .and(PIPELINES.CREATED_AT.lessThan(startInterval))
        .andExists(
            dsl.selectOne()
                .from(orgProjectTable)
                .where(PIPELINES.ORG_IDENTIFIER.eq((Field<String>) orgProjectTable.field(ORG_ID))
                           .and(PIPELINES.PROJECT_IDENTIFIER.eq((Field<String>) orgProjectTable.field(PROJECT_ID)))))
        .fetchInto(Integer.class)
        .get(0);
  }

  @VisibleForTesting
  @org.jetbrains.annotations.NotNull
  Table<Record2<String, String>> getOrgProjectTable(@NotNull List<OrgProjectIdentifier> orgProjectIdentifiers) {
    Row2<String, String>[] orgProjectRows = new Row2[orgProjectIdentifiers.size()];
    int index = 0;
    for (OrgProjectIdentifier orgProjectIdentifier : orgProjectIdentifiers) {
      orgProjectRows[index++] =
          row(orgProjectIdentifier.getOrgIdentifier(), orgProjectIdentifier.getProjectIdentifier());
    }

    return DSL.values(orgProjectRows).as("t", "orgId", "projectId");
  }
}
