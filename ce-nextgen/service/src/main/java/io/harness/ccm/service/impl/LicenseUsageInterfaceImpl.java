/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.service.impl;

import static io.harness.ccm.commons.utils.BigQueryHelper.UNIFIED_TABLE;

import static java.lang.String.format;

import io.harness.ModuleType;
import io.harness.ccm.CENextGenConfiguration;
import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.ccm.clickHouse.ClickHouseService;
import io.harness.ccm.commons.beans.config.ClickHouseConfig;
import io.harness.ccm.commons.beans.usage.CELicenseUsageDTO;
import io.harness.ccm.commons.utils.BigQueryHelper;
import io.harness.ccm.commons.utils.CCMLicenseUsageHelper;
import io.harness.licensing.usage.beans.UsageDataDTO;
import io.harness.licensing.usage.interfaces.LicenseUsageInterface;
import io.harness.licensing.usage.params.PageableUsageRequestParams;
import io.harness.licensing.usage.params.UsageRequestParams;
import io.harness.timescaledb.DBUtils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.TableResult;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.data.domain.Page;

@Slf4j
public class LicenseUsageInterfaceImpl implements LicenseUsageInterface<CELicenseUsageDTO, UsageRequestParams> {
  private static final String TABLE_NAME = "costAggregated";

  @Inject private BigQueryService bigQueryService;
  @Inject private CENextGenConfiguration configuration;
  @Inject @Named("isClickHouseEnabled") private boolean isClickHouseEnabled;
  @Inject @Nullable @Named("clickHouseConfig") private ClickHouseConfig clickHouseConfig;
  @Inject private ClickHouseService clickHouseService;
  @Inject private BigQueryHelper bigQueryHelper;

  private final Cache<CacheKey, CELicenseUsageDTO> licenseUsageCache =
      Caffeine.newBuilder().expireAfterWrite(8, TimeUnit.HOURS).build();

  @Value
  @AllArgsConstructor
  private static class CacheKey {
    private String accountId;
    private Long timestamp;
  }

  @Override
  public CELicenseUsageDTO getLicenseUsage(
      String accountIdentifier, ModuleType module, long timestamp, UsageRequestParams usageRequest) {
    CacheKey cacheKey = new CacheKey(accountIdentifier, timestamp);
    CELicenseUsageDTO cachedCELicenseUsageDTO = licenseUsageCache.getIfPresent(cacheKey);
    if (null != cachedCELicenseUsageDTO) {
      return cachedCELicenseUsageDTO;
    }

    Double activeSpend = isClickHouseEnabled ? getActiveSpendClickHouse(timestamp, accountIdentifier)
                                             : getActiveSpend(timestamp, accountIdentifier);

    CELicenseUsageDTO ceLicenseUsageDTO =
        CELicenseUsageDTO.builder()
            .activeSpend(UsageDataDTO.builder().count(Math.round(activeSpend)).displayName("").build())
            .timestamp(timestamp)
            .accountIdentifier(accountIdentifier)
            .build();
    licenseUsageCache.put(cacheKey, ceLicenseUsageDTO);
    return ceLicenseUsageDTO;
  }

  @Override
  public Page<CELicenseUsageDTO> listLicenseUsage(
      String accountIdentifier, ModuleType module, long currentTS, PageableUsageRequestParams usageRequest) {
    throw new NotImplementedException("List license usage is not implemented yet for CE module");
  }

  @Override
  public File getLicenseUsageCSVReport(String accountIdentifier, ModuleType moduleType, long currentTsInMs) {
    throw new NotImplementedException("Get license usage CSV report is not implemented yet for CE module");
  }

  private Double getActiveSpend(long timestamp, String accountIdentifier) {
    long endOfDay = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS).toEpochMilli();
    String cloudProviderTableName = bigQueryHelper.getCloudProviderTableName(accountIdentifier, UNIFIED_TABLE);
    String query = format(CCMLicenseUsageHelper.QUERY_TEMPLATE_BIGQUERY, cloudProviderTableName,
        Instant.ofEpochMilli(timestamp), Instant.ofEpochMilli(endOfDay));

    BigQuery bigQuery = bigQueryService.get();
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query).build();
    TableResult result;
    try {
      result = bigQuery.query(queryConfig);
    } catch (InterruptedException e) {
      log.error("Failed to getActiveSpend for Account:{}, {}", accountIdentifier, e);
      Thread.currentThread().interrupt();
      return 0.0D;
    }
    return CCMLicenseUsageHelper.computeDeduplicatedActiveSpend(
        CCMLicenseUsageHelper.getActiveSpendResultSetDTOs(result));
  }

  private Double getActiveSpendClickHouse(long timestamp, String accountIdentifier) {
    String cloudProviderTableName = format("%s.%s", "ccm", TABLE_NAME);
    long endOfDay = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS).toEpochMilli();
    String query = format(
        CCMLicenseUsageHelper.QUERY_TEMPLATE_CLICKHOUSE, cloudProviderTableName, timestamp / 1000, endOfDay / 1000);
    log.info("Query for active spend: {}", query);
    ResultSet resultSet = null;
    try (Connection connection = clickHouseService.getConnection(clickHouseConfig);
         Statement statement = connection.createStatement()) {
      resultSet = statement.executeQuery(query);
      return CCMLicenseUsageHelper.computeDeduplicatedActiveSpend(
          CCMLicenseUsageHelper.getActiveSpendResultSetDTOs(resultSet));
    } catch (SQLException e) {
      log.error("Failed to getActiveSpend for Account:{}, {}", accountIdentifier, e);
    } finally {
      DBUtils.close(resultSet);
    }
    return 0.0D;
  }
}
