/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.service;

import com.google.cloud.bigquery.BigQuery;
import java.util.Map;

public interface CEReportTemplateBuilderService {
  // For ad-hoc reports
  Map<String, String> getTemplatePlaceholders(
      String accountId, String viewId, BigQuery bigQuery, String cloudProviderTableName, String baseUrl);

  // For batch-job scheduled reports
  Map<String, String> getTemplatePlaceholders(String accountId, String viewId, String reportId, BigQuery bigQuery,
      String cloudProviderTableName, String baseUrl);
}
