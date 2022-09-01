/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.ccm.commons.entities.ExternalDataSource;
import io.harness.ccm.service.intf.ExternalDataService;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryError;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.InsertAllRequest;
import com.google.cloud.bigquery.InsertAllResponse;
import com.google.cloud.bigquery.TableId;
import com.google.inject.Inject;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.YearMonth;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CE)
@Slf4j
public class ExternalDataServiceImpl implements ExternalDataService {
  @Inject private BigQueryService bigQueryService;

  @Override
  public boolean insertData(TableId tableId, ExternalDataSource externalDataSource) throws BigQueryException {
    InsertAllRequest insertAllRequest = null;
    BigQuery bigquery = bigQueryService.get();
    try {
      insertAllRequest = constructInsertAllRequest(tableId, externalDataSource);
    } catch (ParseException e) {
      log.error("Date Exception, please ensure month and year are valid");
    }

    InsertAllResponse response = bigquery.insertAll(insertAllRequest);
    if (response.hasErrors()) {
      for (Map.Entry<Long, List<BigQueryError>> entry : response.getInsertErrors().entrySet()) {
        log.error("External Data Insertion Response Error: {}", entry.getValue());
      }
      return false;
    }
    log.info("External Data Insertion Succesful for External Source: {}", externalDataSource);
    return true;
  }

  private InsertAllRequest constructInsertAllRequest(TableId tableId, ExternalDataSource externalDataSource)
      throws ParseException {
    InsertAllRequest.Builder insertAllQueryBuilder = InsertAllRequest.newBuilder(tableId);
    final String datasource = externalDataSource.getDatasource();

    YearMonth yearMonthObject = YearMonth.of(externalDataSource.getYear(), externalDataSource.getMonth());
    int daysInMonth = yearMonthObject.lengthOfMonth();
    double costPerDay = externalDataSource.getCost() / daysInMonth;

    for (int i = 1; i <= daysInMonth; i++) {
      Map<String, Object> rowContent = new HashMap<>();

      DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
      Date date =
          dateFormat.parse(String.format("%s/%s/%s", i, externalDataSource.getMonth(), externalDataSource.getYear()));
      Timestamp timestamp = new Timestamp(date.getTime());

      rowContent.put("startTime", timestamp.getTime() / 1000L);
      rowContent.put("cost", costPerDay);
      rowContent.put("product", datasource);
      rowContent.put("cloudProvider", "EXTERNAL");
      insertAllQueryBuilder.addRow(rowContent);
    }

    return insertAllQueryBuilder.build();
  }
}
