package io.harness.idp.scorecard.datapointsdata.dsldataprovider.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.tiserviceclient.TIServiceClient;
import io.harness.ci.tiserviceclient.TIServiceUtils;
import io.harness.exception.GeneralException;
import io.harness.idp.scorecard.datapoints.parser.DataPointParser;
import io.harness.idp.scorecard.datapoints.parser.DataPointParserFactory;
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
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
  private DataPointParserFactory dataPointParserFactory;
  private PipelineTestSummaryReportResponseFactory pipelineTestSummaryReportResponseFactory;
  private static final String JUNIT_REPORT = "junit";

  @Override
  public Map<String, Object> getDslData(String accountIdentifier, DataSourceDataPointInfo dataSourceDataPointInfo) {
    Map<String, Object> returnData = new HashMap<>();

    Map<String, String> ciIdentifiers =
        DslUtils.getCiPipelineUrlIdentifiers(dataSourceDataPointInfo.getCiPipelineUrl());
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

    String buildNo = DslDataProviderUtil.getRunSequenceForPipelineExecution(responseCI);

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
      Set<String> inputValues = new HashSet<>(dataPointInputValues.getValues());
      if (!inputValues.isEmpty()) {
        DataPointParser dataPointParser = dataPointParserFactory.getParser(dataPointIdentifier);
        String key = dataPointParser.getReplaceKey();
        log.info("replace key : {}, value: [{}]", key, inputValues);
      }
      returnData.putAll(pipelineTestSummaryReportResponseFactory.getResponseParser(dataPointIdentifier)
                            .getParsedValue(summaryReport, dataPointIdentifier));
    }

    return returnData;
  }
}
