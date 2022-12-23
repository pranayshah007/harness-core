/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.core.currency;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.ccm.commons.beans.currency.CurrencyPreferenceRecord;
import io.harness.ccm.commons.dao.CEMetadataRecordDao;
import io.harness.ccm.commons.utils.BigQueryHelper;
import io.harness.ccm.config.CurrencyPreferencesConfig;
import io.harness.ccm.currency.Currency;
import io.harness.ccm.currency.CurrencyConversionFactorTableSchema.Fields;
import io.harness.ccm.graphql.dto.common.CloudServiceProvider;
import io.harness.ccm.graphql.dto.currency.ConversionSource;
import io.harness.ccm.graphql.dto.currency.ConversionType;
import io.harness.ccm.graphql.dto.currency.CurrencyConversionFactorDTO;
import io.harness.ccm.graphql.dto.currency.CurrencyConversionFactorData;
import io.harness.ccm.graphql.dto.currency.CurrencyConversionFactorData.CurrencyConversionFactorDataBuilder;
import io.harness.ccm.graphql.dto.currency.CurrencyDTO;
import io.harness.ccm.graphql.dto.currency.CurrencyData;
import io.harness.exception.InvalidRequestException;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryError;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldList;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.InsertAllRequest;
import com.google.cloud.bigquery.InsertAllResponse;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.bigquery.TableResult;
import com.google.inject.Inject;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import io.fabric8.utils.Lists;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@OwnedBy(CE)
@Slf4j
public class CurrencyPreferenceServiceImpl implements CurrencyPreferenceService {
  private static final String CURRENCY_CONVERSION_FACTOR_DEFAULT_TABLE_NAME = "currencyConversionFactorDefault";
  private static final String CURRENCY_CONVERSION_FACTOR_USER_INPUT_TABLE_NAME = "currencyConversionFactorUserInput";
  private static final String DEFAULT_LOCALE = "en-us";

  @Inject private BigQueryHelper bigQueryHelper;
  @Inject private BigQueryService bigQueryService;
  @Inject private CEMetadataRecordDao ceMetadataRecordDao;
  @Inject private CurrencyPreferencesConfig currencyPreferencesConfig;

  @Override
  public CurrencyDTO getCurrencies() {
    final List<CurrencyData> currencies =
        Arrays.stream(Currency.values())
            .filter(currency -> !currency.equals(Currency.NONE))
            .map(currency
                -> CurrencyData.builder().currency(currency.getCurrency()).symbol(currency.getSymbol()).build())
            .sorted(Comparator.comparing(CurrencyData::getCurrency))
            .collect(Collectors.toList());
    return CurrencyDTO.builder().currencies(currencies).build();
  }

  @Override
  public CurrencyConversionFactorDTO getCurrencyConversionFactorData(
      @NonNull final String accountId, @NonNull final Currency destinationCurrency) {
    CurrencyConversionFactorDTO currencyConversionFactorDTO;
    if (Currency.NONE.getCurrency().equals(destinationCurrency.getCurrency())) {
      currencyConversionFactorDTO = getSourceCurrencyConversionFactorDTO(accountId);
    } else {
      currencyConversionFactorDTO = getDestinationCurrencyConversionFactorDTO(accountId, destinationCurrency);
    }
    return currencyConversionFactorDTO;
  }

  private CurrencyConversionFactorDTO getSourceCurrencyConversionFactorDTO(final String accountId) {
    final String currencyConversionFactorTableName =
        bigQueryHelper.getCEInternalDatasetTable(CURRENCY_CONVERSION_FACTOR_DEFAULT_TABLE_NAME);
    final SelectQuery selectQuery = CurrencyPreferenceQueryBuilder.getCloudProviderSourceCurrenciesQuery(
        accountId, currencyConversionFactorTableName);
    return convertToCurrencyConversionFactorDTO(getTableResultFromQuery(selectQuery));
  }

  private CurrencyConversionFactorDTO getDestinationCurrencyConversionFactorDTO(
      final String accountId, final Currency destinationCurrency) {
    final List<CurrencyConversionFactorData> userEnteredConversionFactors =
        getUserEnteredCurrencyConversionFactors(accountId);
    checkValidDestinationCurrency(destinationCurrency, userEnteredConversionFactors);

    final CurrencyConversionFactorDTO currencyConversionFactorDTO = getDefaultCurrencyConversionFactorDTO(accountId);
    final List<CurrencyConversionFactorData> sourceCurrencyConversionFactors =
        getSourceCurrencyConversionFactors(currencyConversionFactorDTO.getCurrencyConversionFactorDataList());
    final List<CurrencyConversionFactorData> accountDefaultConversionFactors =
        getAccountDefaultConversionFactors(currencyConversionFactorDTO.getCurrencyConversionFactorDataList());
    final List<CurrencyConversionFactorData> apiDefaultConversionFactors =
        getApiDefaultConversionFactors(currencyConversionFactorDTO.getCurrencyConversionFactorDataList());

    final List<CurrencyConversionFactorData> currencyConversionFactors = new ArrayList<>();
    for (final CurrencyConversionFactorData currencyConversionFactorData : sourceCurrencyConversionFactors) {
      final CurrencyConversionFactorData trueCurrencyConversionFactorData =
          getTrueCurrencyConversionFactorData(accountId, destinationCurrency, userEnteredConversionFactors,
              accountDefaultConversionFactors, apiDefaultConversionFactors, currencyConversionFactorData);
      currencyConversionFactors.add(trueCurrencyConversionFactorData);
    }
    return CurrencyConversionFactorDTO.builder().currencyConversionFactorDataList(currencyConversionFactors).build();
  }

  @NotNull
  private CurrencyConversionFactorData getTrueCurrencyConversionFactorData(final String accountId,
      final Currency destinationCurrency, final List<CurrencyConversionFactorData> userEnteredConversionFactors,
      final List<CurrencyConversionFactorData> accountDefaultConversionFactors,
      final List<CurrencyConversionFactorData> apiDefaultConversionFactors,
      final CurrencyConversionFactorData currencyConversionFactorData) {
    CurrencyConversionFactorData trueCurrencyConversionFactorData = null;
    final Optional<CurrencyConversionFactorData> userEnteredCurrencyConversionFactorData =
        getUserEnteredTrueCurrencyConversionFactorData(currencyConversionFactorData.getCloudServiceProvider(),
            currencyConversionFactorData.getSourceCurrency(), destinationCurrency, userEnteredConversionFactors);
    if (userEnteredCurrencyConversionFactorData.isPresent()) {
      trueCurrencyConversionFactorData = userEnteredCurrencyConversionFactorData.get();
    }
    if (Objects.isNull(trueCurrencyConversionFactorData)) {
      final Optional<CurrencyConversionFactorData> accountDefaultCurrencyConversionFactorData =
          getAccountDefaultTrueCurrencyConversionFactorData(currencyConversionFactorData.getCloudServiceProvider(),
              currencyConversionFactorData.getSourceCurrency(), destinationCurrency, accountDefaultConversionFactors);
      if (accountDefaultCurrencyConversionFactorData.isPresent()) {
        trueCurrencyConversionFactorData = accountDefaultCurrencyConversionFactorData.get();
      }
    }
    if (Objects.isNull(trueCurrencyConversionFactorData)) {
      final CurrencyConversionFactorData apiDefaultCurrencyConversionFactorData =
          getAPITrueCurrencyConversionFactorData(
              currencyConversionFactorData, destinationCurrency, apiDefaultConversionFactors);
      if (Objects.nonNull(apiDefaultCurrencyConversionFactorData)) {
        trueCurrencyConversionFactorData = apiDefaultCurrencyConversionFactorData;
      }
    }

    if (Objects.isNull(trueCurrencyConversionFactorData)) {
      log.error("Unable to get the destination currency conversion factor. "
              + "AccountId: {}, CloudServiceProvider: {}, SourceCurrency: {}, DestinationCurrency: {}",
          accountId, currencyConversionFactorData.getCloudServiceProvider(),
          currencyConversionFactorData.getSourceCurrency(), destinationCurrency);
      throw new IllegalStateException("Unable to get the destination currency conversion factor");
    }
    return trueCurrencyConversionFactorData;
  }

  private List<CurrencyConversionFactorData> getUserEnteredCurrencyConversionFactors(final String accountId) {
    final String currencyConversionFactorUserInputTableName =
        bigQueryHelper.getCloudProviderTableName(accountId, CURRENCY_CONVERSION_FACTOR_USER_INPUT_TABLE_NAME);
    final SelectQuery selectQuery = CurrencyPreferenceQueryBuilder.getCloudProviderUserEnteredConversionFactorsQuery(
        currencyConversionFactorUserInputTableName);
    final CurrencyConversionFactorDTO currencyConversionFactorDTO =
        convertToCurrencyConversionFactorDTO(getTableResultFromQuery(selectQuery));
    return currencyConversionFactorDTO.getCurrencyConversionFactorDataList();
  }

  private CurrencyConversionFactorDTO getDefaultCurrencyConversionFactorDTO(final String accountId) {
    final String currencyConversionFactorDefaultTableName =
        bigQueryHelper.getCEInternalDatasetTable(CURRENCY_CONVERSION_FACTOR_DEFAULT_TABLE_NAME);
    final SelectQuery selectQuery = CurrencyPreferenceQueryBuilder.getCloudProviderDefaultConversionFactorsQuery(
        accountId, currencyConversionFactorDefaultTableName);
    return convertToCurrencyConversionFactorDTO(getTableResultFromQuery(selectQuery));
  }

  private void checkValidDestinationCurrency(
      final Currency destinationCurrency, final List<CurrencyConversionFactorData> userEnteredConversionFactors) {
    final Optional<Currency> savedDestinationCurrency = getSavedDestinationCurrency(userEnteredConversionFactors);
    if (savedDestinationCurrency.isPresent() && !savedDestinationCurrency.get().equals(destinationCurrency)) {
      throw new InvalidRequestException(
          String.format("Can't set different destination currency %s. Saved destination currency is %s",
              destinationCurrency.getCurrency(), savedDestinationCurrency.get().getCurrency()));
    }
  }

  private Optional<Currency> getSavedDestinationCurrency(
      final List<CurrencyConversionFactorData> userEnteredConversionFactors) {
    if (Lists.isNullOrEmpty(userEnteredConversionFactors)) {
      return Optional.empty();
    }
    return userEnteredConversionFactors.stream().map(CurrencyConversionFactorData::getDestinationCurrency).findFirst();
  }

  private Optional<CurrencyConversionFactorData> getUserEnteredTrueCurrencyConversionFactorData(
      final CloudServiceProvider cloudServiceProvider, final Currency sourceCurrency,
      final Currency destinationCurrency, final List<CurrencyConversionFactorData> currencyConversionFactors) {
    return currencyConversionFactors.stream()
        .filter(currencyConversionFactorData
            // Skipping DEFAULT conversion types because it will change for that month.
            // Later, will pick it from Default currency conversion table.
            -> ConversionType.CUSTOM.equals(currencyConversionFactorData.getConversionType())
                && cloudServiceProvider.equals(currencyConversionFactorData.getCloudServiceProvider())
                && sourceCurrency.equals(currencyConversionFactorData.getSourceCurrency())
                && destinationCurrency.equals(currencyConversionFactorData.getDestinationCurrency()))
        .findFirst();
  }

  private Optional<CurrencyConversionFactorData> getAccountDefaultTrueCurrencyConversionFactorData(
      final CloudServiceProvider cloudServiceProvider, final Currency sourceCurrency,
      final Currency destinationCurrency, final List<CurrencyConversionFactorData> currencyConversionFactors) {
    return currencyConversionFactors.stream()
        .filter(currencyConversionFactorData
            -> cloudServiceProvider.equals(currencyConversionFactorData.getCloudServiceProvider())
                && sourceCurrency.equals(currencyConversionFactorData.getSourceCurrency())
                && destinationCurrency.equals(currencyConversionFactorData.getDestinationCurrency()))
        .findFirst();
  }

  private CurrencyConversionFactorData getAPITrueCurrencyConversionFactorData(
      final CurrencyConversionFactorData sourceCurrencyConversionFactorData, final Currency destinationCurrency,
      final List<CurrencyConversionFactorData> apiDefaultConversionFactors) {
    CurrencyConversionFactorData currencyConversionFactorData = null;
    final Optional<CurrencyConversionFactorData> currencyConversionFactorData1 =
        apiDefaultConversionFactors.stream()
            .filter(currencyConversionFactor
                -> sourceCurrencyConversionFactorData.getSourceCurrency().equals(
                       currencyConversionFactor.getSourceCurrency())
                    && Currency.USD.equals(currencyConversionFactor.getDestinationCurrency()))
            .findFirst();
    final Optional<CurrencyConversionFactorData> currencyConversionFactorData2 =
        apiDefaultConversionFactors.stream()
            .filter(currencyConversionFactor
                -> Currency.USD.equals(currencyConversionFactor.getSourceCurrency())
                    && destinationCurrency.equals(currencyConversionFactor.getDestinationCurrency()))
            .findFirst();
    if (currencyConversionFactorData1.isPresent() && currencyConversionFactorData2.isPresent()) {
      currencyConversionFactorData =
          CurrencyConversionFactorData.builder()
              .accountId(sourceCurrencyConversionFactorData.getAccountId())
              .cloudServiceProvider(sourceCurrencyConversionFactorData.getCloudServiceProvider())
              .sourceCurrency(sourceCurrencyConversionFactorData.getSourceCurrency())
              .destinationCurrency(destinationCurrency)
              .conversionFactor(currencyConversionFactorData1.get().getConversionFactor()
                  * currencyConversionFactorData2.get().getConversionFactor())
              .month(currencyConversionFactorData1.get().getMonth())
              .conversionType(currencyConversionFactorData1.get().getConversionType())
              .createdAt(currencyConversionFactorData1.get().getCreatedAt())
              .updatedAt(currencyConversionFactorData1.get().getUpdatedAt())
              .build();
    }
    return currencyConversionFactorData;
  }

  private List<CurrencyConversionFactorData> getSourceCurrencyConversionFactors(
      final List<CurrencyConversionFactorData> currencyConversionFactorDataList) {
    final List<CurrencyConversionFactorData> updatedCurrencyConversionFactors = new ArrayList<>();
    final List<CurrencyConversionFactorData> sortedCurrencyConversionFactors =
        currencyConversionFactorDataList.stream()
            .filter(currencyConversionFactorData
                -> Objects.nonNull(currencyConversionFactorData.getAccountId())
                    && ConversionSource.BILLING_EXPORT_SRC_CCY.equals(
                        currencyConversionFactorData.getConversionSource()))
            .sorted(Comparator.comparing(CurrencyConversionFactorData::getUpdatedAt).reversed())
            .collect(Collectors.toList());
    final Set<String> distinctCloudServiceProviderAndSourceCurrency = new HashSet<>();
    for (final CurrencyConversionFactorData currencyConversionFactorData : sortedCurrencyConversionFactors) {
      final String distinctPairKey = currencyConversionFactorData.getCloudServiceProvider().name()
          + currencyConversionFactorData.getSourceCurrency().getCurrency();
      if (!distinctCloudServiceProviderAndSourceCurrency.contains(distinctPairKey)) {
        updatedCurrencyConversionFactors.add(currencyConversionFactorData);
        distinctCloudServiceProviderAndSourceCurrency.add(distinctPairKey);
      }
    }
    return updatedCurrencyConversionFactors;
  }

  private List<CurrencyConversionFactorData> getAccountDefaultConversionFactors(
      final List<CurrencyConversionFactorData> currencyConversionFactorDataList) {
    return currencyConversionFactorDataList.stream()
        .filter(currencyConversionFactorData
            -> Objects.nonNull(currencyConversionFactorData.getAccountId())
                && ConversionSource.BILLING_EXPORT.equals(currencyConversionFactorData.getConversionSource()))
        .sorted(Comparator.comparing(CurrencyConversionFactorData::getUpdatedAt).reversed())
        .collect(Collectors.toList());
  }

  private List<CurrencyConversionFactorData> getApiDefaultConversionFactors(
      final List<CurrencyConversionFactorData> currencyConversionFactorDataList) {
    return currencyConversionFactorDataList.stream()
        .filter(currencyConversionFactorData
            -> ConversionSource.API.equals(currencyConversionFactorData.getConversionSource()))
        .collect(Collectors.toList());
  }

  @Override
  public void updateCEMetadataCurrencyPreferenceRecord(
      @NonNull final String accountId, @NonNull final Currency destinationCurrency) {
    final CurrencyPreferenceRecord currencyPreference = CurrencyPreferenceRecord.builder()
                                                            .destinationCurrency(destinationCurrency.getCurrency())
                                                            .symbol(destinationCurrency.getSymbol())
                                                            .locale(DEFAULT_LOCALE)
                                                            .setupTime(Instant.now().toEpochMilli())
                                                            .build();
    ceMetadataRecordDao.updateCurrencyPreferenceOnce(accountId, currencyPreference);
  }

  @Override
  public Double getDestinationCurrencyConversionFactor(final @NonNull String accountId,
      final @NonNull CloudServiceProvider cloudServiceProvider, final @NonNull Currency sourceCurrency) {
    Double conversionFactor = 1.0D;
    final Currency currency = ceMetadataRecordDao.getDestinationCurrency(accountId);
    if (!Currency.NONE.equals(currency)) {
      try {
        final List<CurrencyConversionFactorData> userEnteredConversionFactors =
            getUserEnteredCurrencyConversionFactors(accountId);
        final CurrencyConversionFactorDTO currencyConversionFactorDTO =
            getDefaultCurrencyConversionFactorDTO(accountId);
        final List<CurrencyConversionFactorData> accountDefaultConversionFactors =
            getAccountDefaultConversionFactors(currencyConversionFactorDTO.getCurrencyConversionFactorDataList());
        final List<CurrencyConversionFactorData> apiDefaultConversionFactors =
            getApiDefaultConversionFactors(currencyConversionFactorDTO.getCurrencyConversionFactorDataList());
        final CurrencyConversionFactorData defaultSourceCurrencyConversionFactorData =
            getDefaultSourceCurrencyConversionFactorData(accountId, cloudServiceProvider, sourceCurrency);
        final CurrencyConversionFactorData currencyConversionFactorData = getTrueCurrencyConversionFactorData(accountId,
            currency, userEnteredConversionFactors, accountDefaultConversionFactors, apiDefaultConversionFactors,
            defaultSourceCurrencyConversionFactorData);
        conversionFactor = currencyConversionFactorData.getConversionFactor();
      } catch (final IllegalStateException | BigQueryException exception) {
        log.error("Unable to get destination currency conversion factor for "
                + "account {} and cloudServiceProvider {} with sourceCurrency {}",
            accountId, cloudServiceProvider, sourceCurrency, exception);
      }
      log.info("Destination currency conversion factor for accountId {}, cloudServiceProvider {} "
              + "and sourceCurrency {} is {}",
          accountId, cloudServiceProvider, sourceCurrency, conversionFactor);
    } else {
      log.info("Destination currency is not set. So returning 1.0 as conversion factor for accountId {}, "
              + "cloudServiceProvider {} and sourceCurrency {}",
          accountId, cloudServiceProvider, sourceCurrency);
    }
    return conversionFactor;
  }

  private CurrencyConversionFactorData getDefaultSourceCurrencyConversionFactorData(
      final String accountId, final CloudServiceProvider cloudServiceProvider, final Currency sourceCurrency) {
    return CurrencyConversionFactorData.builder()
        .accountId(accountId)
        .cloudServiceProvider(cloudServiceProvider)
        .sourceCurrency(sourceCurrency)
        .build();
  }

  @Override
  public boolean createCurrencyConversionFactors(@NonNull final String accountId,
      @NonNull final Currency destinationCurrency,
      @NonNull final CurrencyConversionFactorDTO currencyConversionFactorDTO) {
    final Currency setDestinationCurrency = ceMetadataRecordDao.getDestinationCurrency(accountId);
    validateCreateCurrencyConversionFactorsRequest(
        setDestinationCurrency, destinationCurrency, currencyConversionFactorDTO);
    final String[] tableIds =
        bigQueryHelper.getCloudProviderTableName(accountId, CURRENCY_CONVERSION_FACTOR_USER_INPUT_TABLE_NAME)
            .split("\\.");
    final TableId tableId = TableId.of(tableIds[0], tableIds[1], tableIds[2]);
    final InsertAllRequest insertAllRequest =
        CurrencyPreferenceQueryBuilder.getCurrencyConversionFactorInsertAllRequest(tableId, currencyConversionFactorDTO,
            Currency.NONE.equals(setDestinationCurrency), currencyPreferencesConfig.getHistoricalUpdateMonthsCount(),
            accountId);
    final InsertAllResponse response = getCurrencyConversionFactorInsertAllResponse(insertAllRequest);
    return !response.hasErrors();
  }

  private void validateCreateCurrencyConversionFactorsRequest(final Currency setDestinationCurrency,
      final Currency destinationCurrency, final CurrencyConversionFactorDTO currencyConversionFactorDTO) {
    if (Currency.NONE.equals(destinationCurrency)) {
      throw new InvalidRequestException("Destination currency can't be NONE");
    }
    validateSetDestinationCurrency(setDestinationCurrency, destinationCurrency);
    if (Lists.isNullOrEmpty(currencyConversionFactorDTO.getCurrencyConversionFactorDataList())) {
      throw new InvalidRequestException("Currency conversion factor entered list is empty");
    } else {
      for (final CurrencyConversionFactorData currencyConversionFactorData :
          currencyConversionFactorDTO.getCurrencyConversionFactorDataList()) {
        validateSetDestinationCurrency(setDestinationCurrency, currencyConversionFactorData.getDestinationCurrency());
      }
    }
  }

  private void validateSetDestinationCurrency(
      final Currency setDestinationCurrency, final Currency destinationCurrency) {
    if (!Currency.NONE.equals(setDestinationCurrency) && !setDestinationCurrency.equals(destinationCurrency)) {
      throw new InvalidRequestException(
          String.format("Can't set different destination currency. Already set destination currency is %s",
              setDestinationCurrency.getCurrency()));
    }
  }

  private CurrencyConversionFactorDTO convertToCurrencyConversionFactorDTO(final TableResult tableResult) {
    final List<CurrencyConversionFactorData> currencyConversionFactorDataList = new ArrayList<>();
    if (!Objects.isNull(tableResult)) {
      final Schema schema = tableResult.getSchema();
      final FieldList fields = schema.getFields();
      for (final FieldValueList row : tableResult.iterateAll()) {
        currencyConversionFactorDataList.add(getCurrencyConversionFactorData(fields, row));
      }
    }
    return CurrencyConversionFactorDTO.builder()
        .currencyConversionFactorDataList(currencyConversionFactorDataList)
        .build();
  }

  private CurrencyConversionFactorData getCurrencyConversionFactorData(
      final FieldList fields, final FieldValueList row) {
    final CurrencyConversionFactorDataBuilder currencyConversionFactorDataBuilder =
        CurrencyConversionFactorData.builder();
    for (final Field field : fields) {
      final Fields currencyConversionFactorTableField = Fields.fromString(field.getName());
      if (Objects.nonNull(currencyConversionFactorTableField)) {
        switch (currencyConversionFactorTableField) {
          case ACCOUNT_ID:
            currencyConversionFactorDataBuilder.accountId(getStringValue(row, field));
            break;
          case CLOUD_SERVICE_PROVIDER:
            final String cloudServiceProvider = getStringValue(row, field);
            if (Objects.nonNull(cloudServiceProvider)) {
              currencyConversionFactorDataBuilder.cloudServiceProvider(
                  CloudServiceProvider.valueOf(cloudServiceProvider));
            }
            break;
          case SOURCE_CURRENCY:
            currencyConversionFactorDataBuilder.sourceCurrency(Currency.valueOf(getStringValue(row, field)));
            break;
          case DESTINATION_CURRENCY:
            currencyConversionFactorDataBuilder.destinationCurrency(Currency.valueOf(getStringValue(row, field)));
            break;
          case CONVERSION_FACTOR:
            currencyConversionFactorDataBuilder.conversionFactor(getNumericValue(row, field));
            break;
          case MONTH:
            currencyConversionFactorDataBuilder.month(getDateValue(row, field));
            break;
          case CONVERSION_TYPE:
            currencyConversionFactorDataBuilder.conversionType(ConversionType.valueOf(getStringValue(row, field)));
            break;
          case CONVERSION_SOURCE:
            currencyConversionFactorDataBuilder.conversionSource(ConversionSource.valueOf(getStringValue(row, field)));
            currencyConversionFactorDataBuilder.conversionType(ConversionType.DEFAULT);
            break;
          case CREATED_AT:
            currencyConversionFactorDataBuilder.createdAt(
                Instant.ofEpochMilli(getNumericValue(row, field).longValue() * CurrencyPreferenceQueryBuilder.THOUSAND)
                    .getEpochSecond());
            break;
          case UPDATED_AT:
            currencyConversionFactorDataBuilder.updatedAt(
                Instant.ofEpochMilli(getNumericValue(row, field).longValue() * CurrencyPreferenceQueryBuilder.THOUSAND)
                    .getEpochSecond());
            break;
          default:
            break;
        }
      }
    }
    return currencyConversionFactorDataBuilder.build();
  }

  private static String getStringValue(final FieldValueList row, final Field field) {
    final FieldValue value = row.get(field.getName());
    if (!value.isNull()) {
      return (String) value.getValue();
    }
    return null;
  }

  private static Double getNumericValue(final FieldValueList row, final Field field) {
    final FieldValue value = row.get(field.getName());
    if (!value.isNull()) {
      return value.getNumericValue().doubleValue();
    }
    return 0.0D;
  }

  private Date getDateValue(final FieldValueList row, final Field field) {
    final FieldValue value = row.get(field.getName());
    if (!value.isNull()) {
      final String monthDate = (String) value.getValue();
      try {
        final SimpleDateFormat monthDateFormatter = new SimpleDateFormat(CurrencyPreferenceQueryBuilder.MONTH_PATTERN);
        return monthDateFormatter.parse(monthDate);
      } catch (final ParseException e) {
        log.error("Unable to parse month date: {}", monthDate);
      }
    }
    return null;
  }

  private TableResult getTableResultFromQuery(final SelectQuery selectQuery) {
    final BigQuery bigQuery = bigQueryService.get();
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(selectQuery.toString()).build();
    TableResult result;
    try {
      result = bigQuery.query(queryConfig);
    } catch (final InterruptedException e) {
      log.error("Failed to get CurrencyConversionFactor table result from query {}", queryConfig, e);
      Thread.currentThread().interrupt();
      return null;
    }
    return result;
  }

  @NotNull
  private InsertAllResponse getCurrencyConversionFactorInsertAllResponse(final InsertAllRequest insertAllRequest) {
    final BigQuery bigQuery = bigQueryService.get();
    final InsertAllResponse response = bigQuery.insertAll(insertAllRequest);
    if (response.hasErrors()) {
      for (Map.Entry<Long, List<BigQueryError>> entry : response.getInsertErrors().entrySet()) {
        log.error("Currency conversion factor insertion failed for an entry: {}", entry);
      }
    } else {
      log.info("Currency conversion factors insertion successful. InsertAllRequest: {}", insertAllRequest);
    }
    return response;
  }
}
