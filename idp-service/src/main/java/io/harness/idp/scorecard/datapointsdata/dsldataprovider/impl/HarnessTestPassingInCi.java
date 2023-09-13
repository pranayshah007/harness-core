/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapointsdata.dsldataprovider.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.beans.entities.TIServiceConfig;
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
import com.google.inject.name.Named;
import java.io.IOException;
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

  @Inject @Named("tiServiceConfig") TIServiceConfig tiServiceConfig;

  @Override
  public Map<String, Object> getDslData(String accountIdentifier, DataSourceDataPointInfo dataSourceDataPointInfo) {
    Map<String, Object> returnData = new HashMap<>();

    log.info("HarnessTestPassingInCi DSL invoked for account - {} datapoints - {}", accountIdentifier,
        dataSourceDataPointInfo.getDataSourceLocation().getDataPoints());

    String ciPipelineUrl = DslUtils.getCiUrlFromCatalogInfoYaml(dataSourceDataPointInfo.getCatalogInfoYaml());

    Map<String, Object> errorMessageForMissingNewAnnotations =
        DslUtils.checkAndGetMissingNewAnnotationErrorMessage(ciPipelineUrl, true, null, false, dataSourceDataPointInfo);
    if (errorMessageForMissingNewAnnotations != null) {
      returnData.putAll(errorMessageForMissingNewAnnotations);
      return returnData;
    }

    Map<String, String> ciIdentifiers = DslUtils.getCiPipelineUrlIdentifiers(ciPipelineUrl);
    log.info("CI Pipeline Identifiers - {}", ciIdentifiers);
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
    log.info("CI Response in HarnessTestPassingInCi - {}, CI Pipeline url - {}", responseCI, ciPipelineUrl);

    int buildNo = DslDataProviderUtil.getRunSequenceForPipelineExecution(responseCI);
    log.info("Build no - {} in HarnessTestPassingInCi", buildNo);

    String token = null;
    log.info("Account id - {}", ciIdentifiers.get(DslConstants.CI_ACCOUNT_IDENTIFIER_KEY));
    try {
      token = tiServiceUtils.getTIServiceToken(ciIdentifiers.get(DslConstants.CI_ACCOUNT_IDENTIFIER_KEY));
    } catch (Exception e) {
      log.error(String.format("Error in getting the token for ti-service in account - %s",
                    ciIdentifiers.get(DslConstants.CI_ACCOUNT_IDENTIFIER_KEY)),
          e);
    }

    Call<JsonObject> summaryReportCall =
        tiServiceClient.getSummaryReport(ciIdentifiers.get(DslConstants.CI_ACCOUNT_IDENTIFIER_KEY),
            ciIdentifiers.get(DslConstants.CI_ORG_IDENTIFIER_KEY),
            ciIdentifiers.get(DslConstants.CI_PROJECT_IDENTIFIER_KEY),
            ciIdentifiers.get(DslConstants.CI_PIPELINE_IDENTIFIER_KEY), buildNo, JUNIT_REPORT, null, null, token);

    Response<JsonObject> response = null;
    JsonObject summaryReport = null;
    try {
      response = summaryReportCall.execute();
      if (!response.isSuccessful()) {
        log.info(response.errorBody().string());
      }
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
    log.info("Summary Report execute response - {}", response);
    log.info("Summary Report in HarnessTestPassingInCi - {}", summaryReport);

    List<DataPointInputValues> dataPointInputValuesList =
        dataSourceDataPointInfo.getDataSourceLocation().getDataPoints();

    for (DataPointInputValues dataPointInputValues : dataPointInputValuesList) {
      String dataPointIdentifier = dataPointInputValues.getDataPointIdentifier();
      returnData.putAll(pipelineTestSummaryReportResponseFactory.getResponseParser(dataPointIdentifier)
                            .getParsedValue(summaryReport, dataPointIdentifier, ciPipelineUrl));
    }
    log.info("Return data in HarnessTestPassingInCi - {}", returnData);

    return returnData;
  }
}
