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

    Object responseCI = NGRestUtils.getResponse(
        pipelineServiceClient.getListOfExecutions(ciIdentifiers.get(DslConstants.CI_ACCOUNT_IDENTIFIER_KEY),
            ciIdentifiers.get(DslConstants.CI_ORG_IDENTIFIER_KEY),
            ciIdentifiers.get(DslConstants.CI_PROJECT_IDENTIFIER_KEY), null,
            ciIdentifiers.get(DslConstants.CI_PIPELINE_IDENTIFIER_KEY), 0, 5, null, null, null, null, null, false));

    String buildNo = DslDataProviderUtil.getRunSequenceForPipelineExecution(responseCI);

    String token =
        tiServiceUtils.getTIServiceToken(ciIdentifiers.get(ciIdentifiers.get(DslConstants.CI_ACCOUNT_IDENTIFIER_KEY)));
    String accessToken = "ApiKey " + token;

    Call<JsonObject> summaryReportCall =
        tiServiceClient.getSummaryReport(accessToken, ciIdentifiers.get(DslConstants.CI_ACCOUNT_IDENTIFIER_KEY),
            ciIdentifiers.get(DslConstants.CI_ORG_IDENTIFIER_KEY),
            ciIdentifiers.get(DslConstants.CI_PROJECT_IDENTIFIER_KEY),
            ciIdentifiers.get(DslConstants.CI_PIPELINE_IDENTIFIER_KEY), buildNo, JUNIT_REPORT, null, null);

    Response<JsonObject> response = null;
    try {
      response = summaryReportCall.execute();
    } catch (IOException e) {
      throw new GeneralException("API request to TI service call failed", e);
    }

    JsonObject summaryReport = response.body();

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
