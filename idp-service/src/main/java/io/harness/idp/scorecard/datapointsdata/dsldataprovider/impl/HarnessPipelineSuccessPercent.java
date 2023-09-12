/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datapointsdata.dsldataprovider.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datapointsdata.datapointvalueparser.factory.PipelineSuccessPercentResponseFactory;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.DslConstants;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.base.DslDataProvider;
import io.harness.idp.scorecard.datapointsdata.utils.DslUtils;
import io.harness.pipeline.dashboards.PMSDashboardResourceClient;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.spec.server.idp.v1.model.DataPointInputValues;
import io.harness.spec.server.idp.v1.model.DataSourceDataPointInfo;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class HarnessPipelineSuccessPercent implements DslDataProvider {
  PipelineServiceClient pipelineServiceClient;
  PMSDashboardResourceClient pmsDashboardResourceClient;
  PipelineSuccessPercentResponseFactory pipelineSuccessPercentResponseFactory;

  @Override
  public Map<String, Object> getDslData(String accountIdentifier, DataSourceDataPointInfo dataSourceDataPointInfo) {
    log.info("HarnessPipelineSuccessPercent DSL invoked - for {} datapoints - {}", accountIdentifier,
        dataSourceDataPointInfo.getDataSourceLocation().getDataPoints());

    String ciPipelineUrl = DslUtils.getCiUrlFromCatalogInfoYaml(dataSourceDataPointInfo.getCatalogInfoYaml());
    Map<String, Object> returnData = new HashMap<>();

    Map<String, Object> errorMessageForMissingNewAnnotations =
        DslUtils.checkAndGetMissingNewAnnotationErrorMessage(ciPipelineUrl, true, null, false, dataSourceDataPointInfo);
    if (errorMessageForMissingNewAnnotations != null) {
      returnData.putAll(errorMessageForMissingNewAnnotations);
      return returnData;
    }

    Map<String, String> ciIdentifiers = DslUtils.getCiPipelineUrlIdentifiers(ciPipelineUrl);

    long currentTime = System.currentTimeMillis();

    Object dashboard = null;

    try {
      dashboard = NGRestUtils.getResponse(
          pmsDashboardResourceClient.fetchPipelinedHealth(ciIdentifiers.get(DslConstants.CI_ACCOUNT_IDENTIFIER_KEY),
              ciIdentifiers.get(DslConstants.CI_ORG_IDENTIFIER_KEY),
              ciIdentifiers.get(DslConstants.CI_PROJECT_IDENTIFIER_KEY),
              ciIdentifiers.get(DslConstants.CI_PIPELINE_IDENTIFIER_KEY), null,
              currentTime - DslConstants.SEVEN_DAYS_IN_MILLIS, currentTime));
    } catch (Exception e) {
      log.error(
          String.format(
              "Error in getting the ci pipeline dash board summary info of success percent in seven days check in account - %s, org - %s, project - %s, and pipeline - %s",
              ciIdentifiers.get(DslConstants.CI_ACCOUNT_IDENTIFIER_KEY),
              ciIdentifiers.get(DslConstants.CI_ORG_IDENTIFIER_KEY),
              ciIdentifiers.get(DslConstants.CI_PROJECT_IDENTIFIER_KEY),
              ciIdentifiers.get(DslConstants.CI_PIPELINE_IDENTIFIER_KEY)),
          e);
    }
    log.info(
        "Dashboard response in HarnessPipelineSuccessPercent - {}, CI Pipeline url - {}", dashboard, ciPipelineUrl);

    List<DataPointInputValues> dataPointInputValuesList =
        dataSourceDataPointInfo.getDataSourceLocation().getDataPoints();

    for (DataPointInputValues dataPointInputValues : dataPointInputValuesList) {
      String dataPointIdentifier = dataPointInputValues.getDataPointIdentifier();
      returnData.putAll(pipelineSuccessPercentResponseFactory.getResponseParser(dataPointIdentifier)
                            .getParsedValue(dashboard, dataPointIdentifier, ciPipelineUrl));
    }
    log.info("Return data in HarnessPipelineSuccessPercent DSL - {}, CI  Pipeline url - {}", returnData, ciPipelineUrl);
    return returnData;
  }
}
