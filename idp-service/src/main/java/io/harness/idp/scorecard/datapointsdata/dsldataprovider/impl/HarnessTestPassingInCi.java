/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapointsdata.dsldataprovider.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.tiserviceclient.TIServiceClient;
import io.harness.ci.tiserviceclient.TIServiceUtils;
import io.harness.idp.scorecard.datapointsdata.datapointvalueparser.factory.PipelineTestSummaryReportResponseFactory;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.DslConstants;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.base.DslDataProvider;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.utils.DslDataProviderUtil;
import io.harness.idp.scorecard.datapointsdata.utils.DslUtils;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.spec.server.idp.v1.model.DataPointInputValues;
import io.harness.spec.server.idp.v1.model.DataSourceDataPointInfo;

import com.google.gson.JsonObject;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class HarnessTestPassingInCi implements DslDataProvider {
  private TIServiceUtils tiServiceUtils;
  private TIServiceClient tiServiceClient;
  private PipelineServiceClient pipelineServiceClient;
  private PipelineTestSummaryReportResponseFactory pipelineTestSummaryReportResponseFactory;
  private static final String JUNIT_REPORT = "junit";

  @Override
  public Map<String, Object> getDslData(String accountIdentifier, DataSourceDataPointInfo dataSourceDataPointInfo) {
    Map<String, Object> returnData = new HashMap<>();

    log.info("HarnessTestPassingInCi DSL invoked for account - {} datapoints - {}", accountIdentifier,
        dataSourceDataPointInfo.getDataSourceLocation().getDataPoints());

    Map<String, String> ciIdentifiers = DslUtils.getCiPipelineUrlIdentifiers(
        DslUtils.getCiUrlFromCatalogInfoYaml(dataSourceDataPointInfo.getCatalogInfoYaml()));
    Object responseCI = null;

    try {
      responseCI = NGRestUtils.getResponse(
          pipelineServiceClient.getListOfExecutions(ciIdentifiers.get(DslConstants.CI_ACCOUNT_IDENTIFIER_KEY),
              ciIdentifiers.get(DslConstants.CI_ORG_IDENTIFIER_KEY),
              ciIdentifiers.get(DslConstants.CI_PROJECT_IDENTIFIER_KEY), null,
              ciIdentifiers.get(DslConstants.CI_PIPELINE_IDENTIFIER_KEY), 0, 5, null, null, null, null, false));
    } catch (Exception e) {
      log.error(
          String.format(
              "Error in getting the ci pipeline info of test passing on ci check in account - %s, org - %s, project - %s, and pipeline - %s",
              ciIdentifiers.get(DslConstants.CI_ACCOUNT_IDENTIFIER_KEY),
              ciIdentifiers.get(DslConstants.CI_ORG_IDENTIFIER_KEY),
              ciIdentifiers.get(DslConstants.CI_PROJECT_IDENTIFIER_KEY),
              ciIdentifiers.get(DslConstants.CI_PIPELINE_IDENTIFIER_KEY)),
          e);
    }

    int buildNo = DslDataProviderUtil.getRunSequenceForPipelineExecution(responseCI);

    String token = null;
    try {
      token = tiServiceUtils.getTIServiceToken(
          ciIdentifiers.get(ciIdentifiers.get(DslConstants.CI_ACCOUNT_IDENTIFIER_KEY)));
    } catch (Exception e) {
      log.error(String.format("Error in getting the token for ti-service in account - %s",
                    ciIdentifiers.get(DslConstants.CI_ACCOUNT_IDENTIFIER_KEY)),
          e);
    }

    String accessToken = "ApiKey " + token;
    Call<JsonObject> summaryReportCall =
        tiServiceClient.getSummaryReport(accessToken, ciIdentifiers.get(DslConstants.CI_ACCOUNT_IDENTIFIER_KEY),
            ciIdentifiers.get(DslConstants.CI_ORG_IDENTIFIER_KEY),
            ciIdentifiers.get(DslConstants.CI_PROJECT_IDENTIFIER_KEY),
            ciIdentifiers.get(DslConstants.CI_PIPELINE_IDENTIFIER_KEY), buildNo, JUNIT_REPORT, null, null);

    Response<JsonObject> response = null;
    JsonObject summaryReport = null;
    try {
      response = summaryReportCall.execute();
      summaryReport = response.body();
    } catch (Exception e) {
      log.error(
          String.format(
              "Error in getting the summary report info  from ti service of test passing in ci check account - %s, org - %s, project - %s, and pipeline - %s",
              ciIdentifiers.get(DslConstants.CI_ACCOUNT_IDENTIFIER_KEY),
              ciIdentifiers.get(DslConstants.CI_ORG_IDENTIFIER_KEY),
              ciIdentifiers.get(DslConstants.CI_PROJECT_IDENTIFIER_KEY),
              ciIdentifiers.get(DslConstants.CI_PIPELINE_IDENTIFIER_KEY)),
          e);
    }

    List<DataPointInputValues> dataPointInputValuesList =
        dataSourceDataPointInfo.getDataSourceLocation().getDataPoints();

    for (DataPointInputValues dataPointInputValues : dataPointInputValuesList) {
      String dataPointIdentifier = dataPointInputValues.getDataPointIdentifier();
      returnData.putAll(pipelineTestSummaryReportResponseFactory.getResponseParser(dataPointIdentifier)
                            .getParsedValue(summaryReport, dataPointIdentifier,
                                DslUtils.getCiUrlFromCatalogInfoYaml(dataSourceDataPointInfo.getCatalogInfoYaml())));
    }

    return returnData;
  }
}
