/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.msp.service.impl;

import static io.harness.ccm.views.utils.ViewFieldUtils.UNIFIED_TABLE;

import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.ccm.commons.utils.BigQueryHelper;
import io.harness.ccm.msp.entities.MarginDetails;
import io.harness.ccm.msp.service.intf.MarginDetailsBqService;
import io.harness.ccm.views.graphql.ViewsQueryBuilder;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MarginDetailsBqServiceImpl implements MarginDetailsBqService {
  @Inject private BigQueryService bigQueryService;
  @Inject private BigQueryHelper bigQueryHelper;
  @Inject private ViewsQueryBuilder viewsQueryBuilder;

  private static final String MARGIN_DETAILS_BQ_INSERT_QUERY =
      "INSERT INTO `%s.CE_INTERNAL.mspMarkup`(accountId,mspAccountId,condition) VALUES(\"%s\", \"%s\", \"%s\")";
  private static final String MARGIN_DETAILS_BQ_UPDATE_QUERY =
      "UPDATE `%s.CE_INTERNAL.mspMarkup` SET condition = \"%s\" WHERE accountId = \"%s\" AND mspAccountId = \"%s\"";

  @Override
  public void insertMarginDetailsInBQ(MarginDetails marginDetails) {
    BigQuery bigQuery = bigQueryService.get();
    String condition = viewsQueryBuilder.getSQLCaseStatementForMarginDetails(marginDetails, UNIFIED_TABLE);
    String insertQuery = String.format(MARGIN_DETAILS_BQ_INSERT_QUERY, bigQueryHelper.getGcpProjectId(),
        marginDetails.getAccountId(), marginDetails.getMspAccountId(), condition);

    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(insertQuery).build();
    try {
      bigQuery.query(queryConfig);
    } catch (InterruptedException e) {
      log.error("Failed to insert margin details in big query.", e);
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void updateMarginDetailsInBQ(MarginDetails marginDetails) {
    BigQuery bigQuery = bigQueryService.get();
    String condition = viewsQueryBuilder.getSQLCaseStatementForMarginDetails(marginDetails, UNIFIED_TABLE);
    String updateQuery = String.format(MARGIN_DETAILS_BQ_UPDATE_QUERY, bigQueryHelper.getGcpProjectId(), condition,
        marginDetails.getAccountId(), marginDetails.getMspAccountId());

    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(updateQuery).build();
    try {
      bigQuery.query(queryConfig);
    } catch (InterruptedException e) {
      log.error("Failed to update margin details in big query", e);
      Thread.currentThread().interrupt();
    }
  }
}
