/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.pubsub.consumer;

import static io.harness.batch.processing.pricing.gcp.bigquery.BQConst.BIG_QUERY_TIME_FORMAT;
import static io.harness.ccm.commons.utils.BigQueryHelper.UNIFIED_TABLE;

import static org.apache.commons.lang3.ObjectUtils.max;
import static org.apache.commons.lang3.ObjectUtils.min;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.BatchProcessingException;
import io.harness.batch.processing.pricing.gcp.bigquery.BigQueryHelperService;
import io.harness.batch.processing.pubsub.message.BigQueryUpdateMessage;
import io.harness.beans.FeatureName;
import io.harness.ccm.commons.utils.BigQueryHelper;
import io.harness.ccm.views.businessmapping.entities.BusinessMapping;
import io.harness.ccm.views.businessmapping.entities.BusinessMappingHistory;
import io.harness.ccm.views.businessmapping.service.intf.BusinessMappingHistoryService;
import io.harness.ccm.views.entities.ViewLabelsFlattened;
import io.harness.ccm.views.graphql.ViewsQueryBuilder;
import io.harness.ccm.views.service.LabelFlattenedService;
import io.harness.ff.FeatureFlagService;

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.gson.Gson;
import com.google.inject.Singleton;
import com.google.pubsub.v1.PubsubMessage;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CE)
@Slf4j
@Singleton
public class BigQueryUpdateMessageReceiver implements MessageReceiver {
  private static final String COST_CATEGORY_FORMAT = "STRUCT('%s' as costCategoryName, %s as costBucketName)";

  private final Gson gson = new Gson();
  private final BigQueryHelper bigQueryHelper;
  private final BigQueryHelperService bigQueryHelperService;
  private final BusinessMappingHistoryService businessMappingHistoryService;
  private final ViewsQueryBuilder viewsQueryBuilder;
  private final Set<String> accountsInCluster;
  private final FeatureFlagService featureFlagService;
  private final LabelFlattenedService labelFlattenedService;

  public BigQueryUpdateMessageReceiver(BigQueryHelper bigQueryHelper, BigQueryHelperService bigQueryHelperService,
      BusinessMappingHistoryService businessMappingHistoryService, ViewsQueryBuilder viewsQueryBuilder,
      Set<String> accountsInCluster, FeatureFlagService featureFlagService,
      LabelFlattenedService labelFlattenedService) {
    this.bigQueryHelper = bigQueryHelper;
    this.bigQueryHelperService = bigQueryHelperService;
    this.businessMappingHistoryService = businessMappingHistoryService;
    this.viewsQueryBuilder = viewsQueryBuilder;
    this.accountsInCluster = accountsInCluster;
    this.featureFlagService = featureFlagService;
    this.labelFlattenedService = labelFlattenedService;
  }

  @Override
  public void receiveMessage(PubsubMessage message, AckReplyConsumer consumer) {
    String data = message.getData().toStringUtf8();
    log.info("Received Big Query update message: {}", data);

    boolean ack;
    try {
      ack = processPubSubMessage(parseMessage(data));
    } catch (IOException e) {
      throw new BatchProcessingException(
          String.format("Failed to handle Big Query update message %s.", message.getData().toStringUtf8()), e);
    }

    if (ack) {
      consumer.ack();
      log.info("Acknowledged Big Query update message: {}", message.getData().toStringUtf8());
    } else {
      consumer.nack();
      log.warn("Not acknowledged Big Query update message: {}", message.getData().toStringUtf8());
    }
  }

  public boolean processPubSubMessage(BigQueryUpdateMessage message) throws IOException {
    if (message != null && message.getEventType() != null && message.getMessage() != null) {
      if (message.getEventType() == BigQueryUpdateMessage.EventType.COST_CATEGORY_UPDATE) {
        return processCostCategoryUpdateMessage(message.getMessage());
      }
      log.error("Unknown event type: {}, message: {}", message.getEventType(), message.getMessage());
      return true;
    }
    log.warn("Invalid BigQueryUpdateMessage, skipping processing the pub/sub message");
    return true;
  }

  private boolean processCostCategoryUpdateMessage(BigQueryUpdateMessage.Message message) {
    if (!validateBigQueryUpdateMessage(message)) {
      log.error("Please check for empty or null values in message");
      return true;
    }
    if (!accountsInCluster.contains(message.getAccountId())) {
      log.info("Account doesn't belong to current cluster, skipping message");
      return true;
    }
    String tableName = bigQueryHelper.getCloudProviderTableName(message.getAccountId(), UNIFIED_TABLE);
    Instant startTime;
    Instant endTime;
    try {
      startTime = convertDateToInstant(message.getStartDate());
      endTime = convertDateToInstant(message.getEndDate());
    } catch (ParseException e) {
      log.error(
          "Please check date formats for startDate: {}, endDate: {}", message.getStartDate(), message.getEndDate());
      return true;
    }

    YearMonth currentMonth = getYearMonth(startTime);

    do {
      Instant monthStartTime = getInstant(currentMonth, DayOfMonth.FIRST);
      Instant monthEndTime = getInstant(currentMonth, DayOfMonth.LAST);
      Instant queryStartTime = max(startTime, monthStartTime);
      Instant queryEndTime = min(endTime, monthEndTime);
      log.info("Processing cost categories update from time: {} to: {}", queryStartTime, queryEndTime);

      List<BusinessMappingHistory> businessMappingHistories =
          businessMappingHistoryService.getInRange(message.getAccountId(), queryStartTime, queryEndTime);
      if (businessMappingHistories.isEmpty()) {
        currentMonth = currentMonth.plusMonths(1);
        continue;
      }
      insertCostCategoriesToBigQuery(message, tableName, queryStartTime, queryEndTime, businessMappingHistories);

      currentMonth = currentMonth.plusMonths(1);

    } while (!currentMonth.isAfter(getYearMonth(endTime)));

    return true;
  }

  private void insertCostCategoriesToBigQuery(BigQueryUpdateMessage.Message message, String tableName,
      Instant queryStartTime, Instant queryEndTime, List<BusinessMappingHistory> businessMappingHistories) {
    // Try to update all cost categories in a single query
    ViewLabelsFlattened viewLabelsFlattened =
        ViewLabelsFlattened.builder().shouldUseFlattenedLabelsColumn(false).build();
    if (featureFlagService.isEnabled(FeatureName.CCM_LABELS_FLATTENING, message.getAccountId())) {
      Map<String, String> labelsKeyAndColumnMapping =
          labelFlattenedService.getLabelsKeyAndColumnMapping(message.getAccountId());
      viewLabelsFlattened =
          viewsQueryBuilder.getViewLabelsFlattened(labelsKeyAndColumnMapping, message.getAccountId(), UNIFIED_TABLE);
    }

    List<String> sqlCaseStatements = new ArrayList<>();
    for (BusinessMappingHistory businessMappingHistory : businessMappingHistories) {
      String sqlCaseStatement = String.format(COST_CATEGORY_FORMAT, businessMappingHistory.getName(),
          viewsQueryBuilder.getSQLCaseStatementBusinessMapping(
              null, BusinessMapping.fromHistory(businessMappingHistory), UNIFIED_TABLE, viewLabelsFlattened));
      sqlCaseStatements.add(sqlCaseStatement);
    }
    String costCategoriesStatement = "[" + String.join(", ", sqlCaseStatements) + "]";

    try {
      bigQueryHelperService.insertCostCategories(tableName, costCategoriesStatement,
          formattedTime(Date.from(queryStartTime)), formattedTime(Date.from(queryEndTime)), message.getCloudProvider(),
          message.getCloudProviderAccountIds());
      return;
    } catch (Exception e) {
      log.error("BigQuery insert cost categories in a single update failed, trying individually", e);
    }

    // If single update query doesn't work try adding cost categories one by one
    bigQueryHelperService.removeAllCostCategories(tableName, formattedTime(Date.from(queryStartTime)),
        formattedTime(Date.from(queryEndTime)), message.getCloudProvider(), message.getCloudProviderAccountIds());
    for (BusinessMappingHistory businessMappingHistory : businessMappingHistories) {
      try {
        costCategoriesStatement = "["
            + String.format(COST_CATEGORY_FORMAT, businessMappingHistory.getName(),
                viewsQueryBuilder.getSQLCaseStatementBusinessMapping(
                    null, BusinessMapping.fromHistory(businessMappingHistory), UNIFIED_TABLE, viewLabelsFlattened))
            + "]";
        bigQueryHelperService.addCostCategory(tableName, costCategoriesStatement,
            formattedTime(Date.from(queryStartTime)), formattedTime(Date.from(queryEndTime)),
            message.getCloudProvider(), message.getCloudProviderAccountIds());
      } catch (Exception e) {
        log.warn("Couldn't add Cost Category {}, skipping it.", businessMappingHistory.getName(), e);
      }
    }
  }

  private BigQueryUpdateMessage parseMessage(String data) {
    if (StringUtils.isNotBlank(data)) {
      return gson.fromJson(data, BigQueryUpdateMessage.class);
    }
    return null;
  }

  private boolean validateBigQueryUpdateMessage(BigQueryUpdateMessage.Message message) {
    return !StringUtils.isEmpty(message.getAccountId()) && !StringUtils.isEmpty(message.getStartDate())
        && !StringUtils.isEmpty(message.getEndDate()) && message.getCloudProvider() != null
        && message.getCloudProviderAccountIds() != null && !message.getCloudProviderAccountIds().isEmpty();
  }

  private Instant convertDateToInstant(String date) throws ParseException {
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
    formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    return formatter.parse(date).toInstant();
  }

  private static String formattedTime(Date time) {
    final SimpleDateFormat formatter = new SimpleDateFormat(BIG_QUERY_TIME_FORMAT);
    formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    return formatter.format(time);
  }

  private static YearMonth getYearMonth(Instant instant) {
    return YearMonth.from(instant.atZone(ZoneId.of("UTC")).toLocalDate());
  }

  private static Instant getInstant(YearMonth yearMonth, DayOfMonth dayOfMonth) {
    if (dayOfMonth == DayOfMonth.LAST) {
      return yearMonth.atEndOfMonth().atTime(23, 59, 59).atZone(ZoneId.of("UTC")).toInstant();
    }
    return yearMonth.atDay(1).atTime(0, 0, 0).atZone(ZoneId.of("UTC")).toInstant();
  }

  private enum DayOfMonth { FIRST, LAST }
}
