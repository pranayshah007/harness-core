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
import static io.harness.ccm.currency.CurrencyConversionFactorTableSchema.Fields.CONVERSION_SOURCE;
import static io.harness.ccm.currency.CurrencyConversionFactorTableSchema.Fields.MONTH;
import static io.harness.ccm.currency.CurrencyConversionFactorTableSchema.Fields.SOURCE_CURRENCY;
import static io.harness.ccm.currency.CurrencyConversionFactorTableSchema.Fields.UPDATED_AT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.currency.CurrencyConversionFactorTableSchema;
import io.harness.ccm.graphql.dto.common.CloudServiceProvider;
import io.harness.ccm.graphql.dto.currency.ConversionSource;
import io.harness.ccm.graphql.dto.currency.CurrencyConversionFactorDTO;
import io.harness.ccm.graphql.dto.currency.CurrencyConversionFactorData;

import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.TableId;
import com.healthmarketscience.sqlbuilder.BinaryCondition;
import com.healthmarketscience.sqlbuilder.ComboCondition;
import com.healthmarketscience.sqlbuilder.Condition;
import com.healthmarketscience.sqlbuilder.Converter;
import com.healthmarketscience.sqlbuilder.CustomSql;
import com.healthmarketscience.sqlbuilder.FunctionCall;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneOffset;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@OwnedBy(CE)
@UtilityClass
@Slf4j
public class CurrencyPreferenceQueryBuilder {
  public static final String MONTH_PATTERN = "yyyy-MM-dd";
  private static final String DISTINCT_PAIRS = "DISTINCT %s, %s";
  private static final String OPEN_BRACKET = "(";
  private static final String CLOSE_BRACKET = ")";
  private static final String COMMA_AND_SPACE = ", ";
  private static final String CURRENT_TIMESTAMP = "CURRENT_TIMESTAMP()";
  private static final String STRING_VALUE = "'%s'";

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
  public QueryJobConfiguration getCurrencyConversionFactorQueryJobConfiguration(final TableId tableId,
      final CurrencyConversionFactorDTO currencyConversionFactorDTO,
      final boolean shouldCreatePreviousMonthInsertRequests, final int historicalUpdateMonthsCount,
      final String accountId) {
    final String insertQuery = String.format("INSERT INTO `%s.%s.%s` %s VALUES %s", tableId.getProject(),
        tableId.getDataset(), tableId.getTable(), getUserInputTableColumns(),
        getUserInputRows(currencyConversionFactorDTO, shouldCreatePreviousMonthInsertRequests,
            historicalUpdateMonthsCount, accountId));
    log.info("Currency Preference insertQuery {} for an account: {}", insertQuery, accountId);
    return QueryJobConfiguration.newBuilder(insertQuery).build();
  }

  private static String getUserInputTableColumns() {
    final StringBuilder columns = new StringBuilder(OPEN_BRACKET);
    for (final CurrencyConversionFactorTableSchema.Fields field : CurrencyConversionFactorTableSchema.Fields.values()) {
      if (!CONVERSION_SOURCE.equals(field)) {
        columns.append(field.getFieldName()).append(COMMA_AND_SPACE);
      }
    }
    columns.delete(columns.length() - 2, columns.length());
    columns.append(CLOSE_BRACKET);
    return columns.toString();
  }

  private static String getUserInputRows(final CurrencyConversionFactorDTO currencyConversionFactorDTO,
      final boolean shouldCreatePreviousMonthInsertRequests, final int historicalUpdateMonthsCount,
      final String accountId) {
    final StringBuilder rows = new StringBuilder();
    for (final CurrencyConversionFactorData currencyConversionFactorData :
        currencyConversionFactorDTO.getCurrencyConversionFactorDataList()) {
      rows.append(getRowFromCurrencyConversionFactorData(
                      currencyConversionFactorData, getMonthStartDate(0), false, accountId))
          .append(COMMA_AND_SPACE);
      if (shouldCreatePreviousMonthInsertRequests
          && !CloudServiceProvider.K8S.equals(currencyConversionFactorData.getCloudServiceProvider())) {
        for (int monthCount = 1; monthCount <= historicalUpdateMonthsCount; monthCount++) {
          rows.append(getRowFromCurrencyConversionFactorData(
                          currencyConversionFactorData, getMonthStartDate(monthCount), true, accountId))
              .append(COMMA_AND_SPACE);
        }
      }
    }
    rows.delete(rows.length() - 2, rows.length());
    return rows.toString();
  }

  private long getMonthStartDate(final int minusMonthCount) {
    return LocalDate.now()
        .withDayOfMonth(1)
        .minusMonths(minusMonthCount)
        .atStartOfDay()
        .toInstant(ZoneOffset.UTC)
        .toEpochMilli();
  }

  private String getRowFromCurrencyConversionFactorData(final CurrencyConversionFactorData currencyConversionFactorData,
      final long monthStartDate, final boolean isHistoricalUpdateRequired, final String accountId) {
    final SimpleDateFormat monthDateFormatter = new SimpleDateFormat(MONTH_PATTERN);
    return OPEN_BRACKET + String.format(STRING_VALUE, accountId) + COMMA_AND_SPACE
        + String.format(STRING_VALUE, currencyConversionFactorData.getCloudServiceProvider().name()) + COMMA_AND_SPACE
        + String.format(STRING_VALUE, currencyConversionFactorData.getSourceCurrency().getCurrency()) + COMMA_AND_SPACE
        + String.format(STRING_VALUE, currencyConversionFactorData.getDestinationCurrency().getCurrency())
        + COMMA_AND_SPACE + currencyConversionFactorData.getConversionFactor() + COMMA_AND_SPACE
        + String.format(STRING_VALUE, monthDateFormatter.format(monthStartDate)) + COMMA_AND_SPACE
        + String.format(STRING_VALUE, currencyConversionFactorData.getConversionType().name()) + COMMA_AND_SPACE
        + CURRENT_TIMESTAMP + COMMA_AND_SPACE + CURRENT_TIMESTAMP + COMMA_AND_SPACE + isHistoricalUpdateRequired
        + CLOSE_BRACKET;
  }
}
