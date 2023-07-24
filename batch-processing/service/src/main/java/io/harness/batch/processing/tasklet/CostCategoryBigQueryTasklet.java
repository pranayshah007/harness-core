/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet;

import static io.harness.batch.processing.pricing.gcp.bigquery.BQConst.BIG_QUERY_TIME_FORMAT;
import static io.harness.ccm.commons.utils.BigQueryHelper.UNIFIED_TABLE;

import static org.apache.commons.lang3.ObjectUtils.max;
import static org.apache.commons.lang3.ObjectUtils.min;

import io.harness.ModuleType;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.pricing.gcp.bigquery.BigQueryHelperService;
import io.harness.beans.FeatureName;
import io.harness.ccm.commons.beans.JobConstants;
import io.harness.ccm.commons.utils.BigQueryHelper;
import io.harness.ccm.views.businessmapping.entities.BusinessMapping;
import io.harness.ccm.views.businessmapping.service.intf.BusinessMappingService;
import io.harness.ccm.views.entities.ViewLabelsFlattened;
import io.harness.ccm.views.graphql.ViewsQueryBuilder;
import io.harness.ccm.views.service.LabelFlattenedService;
import io.harness.ff.FeatureFlagService;
import io.harness.licensing.Edition;
import io.harness.licensing.beans.summary.LicensesWithSummaryDTO;
import io.harness.licensing.remote.NgLicenseHttpClient;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.remote.client.NGRestUtils;

import com.google.inject.Singleton;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import retrofit2.Call;

@Slf4j
@Singleton
public class CostCategoryBigQueryTasklet implements Tasklet {
  @Autowired private NgLicenseHttpClient ngLicenseHttpClient;
  @Autowired private BigQueryHelper bigQueryHelper;
  @Autowired private BigQueryHelperService bigQueryHelperService;
  @Autowired private BusinessMappingService businessMappingService;
  @Autowired private ViewsQueryBuilder viewsQueryBuilder;
  @Autowired private FeatureFlagService featureFlagService;
  @Autowired private LabelFlattenedService labelFlattenedService;

  private static final String COST_CATEGORY_FORMAT = "STRUCT('%s' as costCategoryName, %s as costBucketName)";

  @Override
  public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
    final JobConstants jobConstants = CCMJobConstants.fromContext(chunkContext);
    String accountId = jobConstants.getAccountId();
    Instant startTime = Instant.ofEpochMilli(jobConstants.getJobStartTime());
    Instant endTime = Instant.ofEpochMilli(jobConstants.getJobEndTime());

    if (!checkEnterpriseLicense(accountId)) {
      return null;
    }

    String tableName = bigQueryHelper.getCloudProviderTableName(accountId, UNIFIED_TABLE);

    YearMonth currentMonth = getYearMonth(endTime);
    Instant monthStartTime = getInstant(currentMonth, DayOfMonth.FIRST);
    Instant monthEndTime = getInstant(currentMonth, DayOfMonth.LAST);
    Instant queryStartTime = max(startTime, monthStartTime);
    Instant queryEndTime = min(endTime, monthEndTime);

    log.info("Processing cost categories update from time: {} to: {}", queryStartTime, queryEndTime);

    List<BusinessMapping> businessMappings = businessMappingService.getAll(accountId);
    if (businessMappings == null || businessMappings.isEmpty()) {
      log.info("No cost categories found for account: {}", accountId);
      return null;
    }
    insertCostCategoriesToBigQuery(accountId, tableName, queryStartTime, queryEndTime, businessMappings);
    return null;
  }

  private void insertCostCategoriesToBigQuery(String accountId, String tableName, Instant queryStartTime,
      Instant queryEndTime, List<BusinessMapping> businessMappings) {
    // Try to update all cost categories in a single query
    ViewLabelsFlattened viewLabelsFlattened =
        ViewLabelsFlattened.builder().shouldUseFlattenedLabelsColumn(false).build();
    if (featureFlagService.isEnabled(FeatureName.CCM_LABELS_FLATTENING, accountId)) {
      Map<String, String> labelsKeyAndColumnMapping = labelFlattenedService.getLabelsKeyAndColumnMapping(accountId);
      viewLabelsFlattened =
          viewsQueryBuilder.getViewLabelsFlattened(labelsKeyAndColumnMapping, accountId, UNIFIED_TABLE);
    }

    List<String> sqlCaseStatements = new ArrayList<>();
    for (BusinessMapping businessMapping : businessMappings) {
      String sqlCaseStatement = String.format(COST_CATEGORY_FORMAT, businessMapping.getName(),
          viewsQueryBuilder.getSQLCaseStatementBusinessMapping(
              null, businessMapping, UNIFIED_TABLE, viewLabelsFlattened));
      sqlCaseStatements.add(sqlCaseStatement);
    }
    String costCategoriesStatement = "[" + String.join(", ", sqlCaseStatements) + "]";

    try {
      bigQueryHelperService.insertCostCategories(tableName, costCategoriesStatement,
          formattedTime(Date.from(queryStartTime)), formattedTime(Date.from(queryEndTime)));
      return;
    } catch (Exception e) {
      log.error("BigQuery insert cost categories in a single update failed, trying individually", e);
    }

    // If single update query doesn't work try adding cost categories one by one
    bigQueryHelperService.removeAllCostCategories(
        tableName, formattedTime(Date.from(queryStartTime)), formattedTime(Date.from(queryEndTime)));
    for (BusinessMapping businessMapping : businessMappings) {
      try {
        costCategoriesStatement = "["
            + String.format(COST_CATEGORY_FORMAT, businessMapping,
                viewsQueryBuilder.getSQLCaseStatementBusinessMapping(
                    null, businessMapping, UNIFIED_TABLE, viewLabelsFlattened))
            + "]";
        bigQueryHelperService.addCostCategory(tableName, costCategoriesStatement,
            formattedTime(Date.from(queryStartTime)), formattedTime(Date.from(queryEndTime)));
      } catch (Exception e) {
        log.warn("Couldn't add Cost Category {}, skipping it.", businessMapping.getName(), e);
      }
    }
  }

  private boolean checkEnterpriseLicense(String accountId) {
    Call<ResponseDTO<LicensesWithSummaryDTO>> licenseCall =
        ngLicenseHttpClient.getLicenseSummary(accountId, ModuleType.CE.toString());
    LicensesWithSummaryDTO license = NGRestUtils.getResponse(licenseCall);
    if (license == null) {
      log.info("NG license not found for accountId: {}", accountId);
      return false;
    }
    log.info("license type: {}, license edition: {}", license.getLicenseType(), license.getEdition());
    return license.getEdition().equals(Edition.ENTERPRISE);
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
