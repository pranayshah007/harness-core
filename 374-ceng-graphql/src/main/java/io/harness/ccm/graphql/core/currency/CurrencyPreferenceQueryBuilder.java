/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.core.currency;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.currency.CurrencyConversionFactorTableSchema.Fields.ACCOUNT_ID;
import static io.harness.ccm.currency.CurrencyConversionFactorTableSchema.Fields.CLOUD_SERVICE_PROVIDER;
import static io.harness.ccm.currency.CurrencyConversionFactorTableSchema.Fields.CONVERSION_FACTOR;
import static io.harness.ccm.currency.CurrencyConversionFactorTableSchema.Fields.CONVERSION_SOURCE;
import static io.harness.ccm.currency.CurrencyConversionFactorTableSchema.Fields.CONVERSION_TYPE;
import static io.harness.ccm.currency.CurrencyConversionFactorTableSchema.Fields.CREATED_AT;
import static io.harness.ccm.currency.CurrencyConversionFactorTableSchema.Fields.DESTINATION_CURRENCY;
import static io.harness.ccm.currency.CurrencyConversionFactorTableSchema.Fields.IS_HISTORICAL_UPDATE_REQUIRED;
import static io.harness.ccm.currency.CurrencyConversionFactorTableSchema.Fields.MONTH;
import static io.harness.ccm.currency.CurrencyConversionFactorTableSchema.Fields.SOURCE_CURRENCY;
import static io.harness.ccm.currency.CurrencyConversionFactorTableSchema.Fields.UPDATED_AT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.graphql.dto.common.CloudServiceProvider;
import io.harness.ccm.graphql.dto.currency.ConversionSource;
import io.harness.ccm.graphql.dto.currency.CurrencyConversionFactorDTO;
import io.harness.ccm.graphql.dto.currency.CurrencyConversionFactorData;

import com.google.cloud.bigquery.InsertAllRequest;
import com.google.cloud.bigquery.TableId;
import com.healthmarketscience.sqlbuilder.BinaryCondition;
import com.healthmarketscience.sqlbuilder.ComboCondition;
import com.healthmarketscience.sqlbuilder.Condition;
import com.healthmarketscience.sqlbuilder.Converter;
import com.healthmarketscience.sqlbuilder.CustomSql;
import com.healthmarketscience.sqlbuilder.FunctionCall;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@OwnedBy(CE)
@UtilityClass
@Slf4j
public class CurrencyPreferenceQueryBuilder {
  private static final String DISTINCT_PAIRS = "DISTINCT %s, %s";
  public static final String MONTH_PATTERN = "yyyy-MM-dd";
  public static final long THOUSAND = 1000L;

  public SelectQuery getCloudProviderSourceCurrenciesQuery(
      final String accountId, final String currencyConversionFactorTableName) {
    final SelectQuery selectQuery = new SelectQuery();
    selectQuery.addCustomFromTable(currencyConversionFactorTableName);
    selectQuery.addCondition(BinaryCondition.equalTo(new CustomSql(ACCOUNT_ID.getFieldName()), accountId));
    selectQuery.addCondition(BinaryCondition.equalTo(
        new CustomSql(CONVERSION_SOURCE.getFieldName()), ConversionSource.BILLING_EXPORT_SRC_CCY));
    selectQuery.addAliasedColumn(new CustomSql(String.format(DISTINCT_PAIRS, CLOUD_SERVICE_PROVIDER.getFieldName(),
                                     SOURCE_CURRENCY.getFieldName())),
        SOURCE_CURRENCY.getFieldName());
    log.info("Cloud provider source currencies query: {}", selectQuery);
    return selectQuery;
  }

  public SelectQuery getCloudProviderUserEnteredConversionFactorsQuery(final String currencyConversionFactorTableName) {
    final SelectQuery selectQuery = new SelectQuery();
    selectQuery.addCustomFromTable(currencyConversionFactorTableName);
    final SelectQuery selectSubQuery = new SelectQuery();
    selectSubQuery.addCustomFromTable(currencyConversionFactorTableName);
    selectSubQuery.addCustomColumns(Converter.toCustomColumnSqlObject(
        FunctionCall.max().addCustomParams(new CustomSql(UPDATED_AT.getFieldName()))));
    selectSubQuery.addCondition(getMaxMonthCondition(currencyConversionFactorTableName));
    selectQuery.addCondition(BinaryCondition.equalTo(
        new CustomSql(UPDATED_AT.getFieldName()), new CustomSql(String.format("(%s)", selectSubQuery))));
    selectQuery.addAllColumns();
    log.info("Cloud provider user entered conversion factors query: {}", selectQuery);
    return selectQuery;
  }

  public SelectQuery getCloudProviderDefaultConversionFactorsQuery(
      final String accountId, final String currencyConversionFactorTableName) {
    final Condition accountIdCondition = BinaryCondition.equalTo(new CustomSql(ACCOUNT_ID.getFieldName()), accountId);
    final Condition conversionSourceCondition =
        BinaryCondition.equalTo(new CustomSql(CONVERSION_SOURCE.getFieldName()), ConversionSource.API);
    final SelectQuery selectQuery = new SelectQuery();
    selectQuery.addCustomFromTable(currencyConversionFactorTableName);
    selectQuery.addCondition(ComboCondition.or(accountIdCondition, conversionSourceCondition));
    selectQuery.addAllColumns();
    log.info("Cloud provider default conversion factors query: {}", selectQuery);
    return selectQuery;
  }

  @NotNull
  private Condition getMaxMonthCondition(final String currencyConversionFactorTableName) {
    final SelectQuery selectSubQuery = new SelectQuery();
    selectSubQuery.addCustomFromTable(currencyConversionFactorTableName);
    selectSubQuery.addCustomColumns(
        Converter.toCustomColumnSqlObject(FunctionCall.max().addCustomParams(new CustomSql(MONTH.getFieldName()))));
    return BinaryCondition.equalTo(
        new CustomSql(MONTH.getFieldName()), new CustomSql(String.format("(%s)", selectSubQuery)));
  }

  @NotNull
  public InsertAllRequest getCurrencyConversionFactorInsertAllRequest(final TableId tableId,
      final CurrencyConversionFactorDTO currencyConversionFactorDTO,
      final boolean shouldCreatePreviousMonthInsertRequests, final int historicalUpdateMonthsCount,
      final String accountId) {
    final InsertAllRequest.Builder insertAllRequestBuilder = InsertAllRequest.newBuilder(tableId);
    for (final CurrencyConversionFactorData currencyConversionFactorData :
        currencyConversionFactorDTO.getCurrencyConversionFactorDataList()) {
      insertAllRequestBuilder.addRow(populateMapObjectFromCurrencyConversionFactorData(
          currencyConversionFactorData, getMonthStartDate(0), false, accountId));
      if (shouldCreatePreviousMonthInsertRequests
          && !CloudServiceProvider.K8S.equals(currencyConversionFactorData.getCloudServiceProvider())) {
        for (int monthCount = 1; monthCount <= historicalUpdateMonthsCount; monthCount++) {
          insertAllRequestBuilder.addRow(populateMapObjectFromCurrencyConversionFactorData(
              currencyConversionFactorData, getMonthStartDate(monthCount), true, accountId));
        }
      }
    }
    final InsertAllRequest insertAllRequest = insertAllRequestBuilder.build();
    log.info("Currency conversion factor insert all request {} for tableId {}", insertAllRequest, tableId);
    return insertAllRequest;
  }

  private long getMonthStartDate(final int minusMonthCount) {
    return LocalDate.now()
        .withDayOfMonth(1)
        .minusMonths(minusMonthCount)
        .atStartOfDay()
        .toInstant(ZoneOffset.UTC)
        .toEpochMilli();
  }

  private Map<String, Object> populateMapObjectFromCurrencyConversionFactorData(
      final CurrencyConversionFactorData currencyConversionFactorData, final long monthStartDate,
      final boolean isHistoricalUpdateRequired, final String accountId) {
    final Map<String, Object> currencyConversionFactorDataRecord = new HashMap<>();
    final Instant currentTime = Instant.now();
    final SimpleDateFormat monthDateFormatter = new SimpleDateFormat(MONTH_PATTERN);
    currencyConversionFactorDataRecord.put(ACCOUNT_ID.getFieldName(), accountId);
    currencyConversionFactorDataRecord.put(
        CLOUD_SERVICE_PROVIDER.getFieldName(), currencyConversionFactorData.getCloudServiceProvider().name());
    currencyConversionFactorDataRecord.put(
        SOURCE_CURRENCY.getFieldName(), currencyConversionFactorData.getSourceCurrency().getCurrency());
    currencyConversionFactorDataRecord.put(
        DESTINATION_CURRENCY.getFieldName(), currencyConversionFactorData.getDestinationCurrency().getCurrency());
    currencyConversionFactorDataRecord.put(
        CONVERSION_FACTOR.getFieldName(), currencyConversionFactorData.getConversionFactor());
    currencyConversionFactorDataRecord.put(MONTH.getFieldName(), monthDateFormatter.format(monthStartDate));
    currencyConversionFactorDataRecord.put(
        CONVERSION_TYPE.getFieldName(), currencyConversionFactorData.getConversionType().name());
    currencyConversionFactorDataRecord.put(CREATED_AT.getFieldName(), Timestamp.from(currentTime).getTime() / THOUSAND);
    currencyConversionFactorDataRecord.put(UPDATED_AT.getFieldName(), Timestamp.from(currentTime).getTime() / THOUSAND);
    currencyConversionFactorDataRecord.put(IS_HISTORICAL_UPDATE_REQUIRED.getFieldName(), isHistoricalUpdateRequired);
    return currencyConversionFactorDataRecord;
  }
}
