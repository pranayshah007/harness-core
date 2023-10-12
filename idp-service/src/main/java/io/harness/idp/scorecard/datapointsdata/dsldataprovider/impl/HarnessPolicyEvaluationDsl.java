/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datapointsdata.dsldataprovider.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dashboard.DashboardResourceClient;
import io.harness.idp.scorecard.datapointsdata.datapointvalueparser.factory.PipelineExecutionResponseFactory;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.DslConstants;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.base.DslDataProvider;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.utils.DslDataProviderUtil;
import io.harness.idp.scorecard.datapointsdata.utils.DslUtils;
import io.harness.ng.core.dashboard.DeploymentsInfo;
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
public class HarnessPolicyEvaluationDsl implements DslDataProvider {
  PipelineServiceClient pipelineServiceClient;
  PipelineExecutionResponseFactory pipelineExecutionResponseFactory;
  DashboardResourceClient dashboardResourceClient;

  @Override
  public Map<String, Object> getDslData(String accountIdentifier, Object config) {
    Map<String, Object> returnData = new HashMap<>();
    if (!(config instanceof DataSourceDataPointInfo)) {
      return returnData;
    }

    DataSourceDataPointInfo dataSourceDataPointInfo = (DataSourceDataPointInfo) config;
    log.info("HarnessPolicyEvaluationDsl DSL invoked for account - {} datapoints - {}", accountIdentifier,
        dataSourceDataPointInfo.getDataSourceLocation().getDataPoints());

    String ciPipelineUrl = DslUtils.getCiUrlFromCatalogInfoYaml(dataSourceDataPointInfo.getCatalogInfoYaml());
    String serviceUrl = DslUtils.getServiceUrlFromCatalogInfoYaml(dataSourceDataPointInfo.getCatalogInfoYaml());

    Map<String, Object> errorMessageForMissingNewAnnotations = DslUtils.checkAndGetMissingNewAnnotationErrorMessage(
        ciPipelineUrl, true, serviceUrl, true, dataSourceDataPointInfo);
    if (errorMessageForMissingNewAnnotations != null) {
      returnData.putAll(errorMessageForMissingNewAnnotations);
      return returnData;
    }

    // ci pipeline detail
    Map<String, String> ciIdentifiers = DslUtils.getCiPipelineUrlIdentifiers(ciPipelineUrl);

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
              "Error in getting the ci pipeline info of policy evaluation check in account - %s, org - %s, project - %s, and pipeline - %s",
              ciIdentifiers.get(DslConstants.CI_ACCOUNT_IDENTIFIER_KEY),
              ciIdentifiers.get(DslConstants.CI_ORG_IDENTIFIER_KEY),
              ciIdentifiers.get(DslConstants.CI_PROJECT_IDENTIFIER_KEY),
              ciIdentifiers.get(DslConstants.CI_PIPELINE_IDENTIFIER_KEY)),
          e);
    }
    log.info("CI response in HarnessPolicyEvaluationDsl - {}, CI Pipeline Url - {}", responseCI, ciPipelineUrl);

    // cd pipeline detail
    Map<String, String> serviceIdentifiers = DslUtils.getCdServiceUrlIdentifiers(serviceUrl);
    log.info("Service Url in HarnessPolicyEvaluationDsl : {}", serviceUrl);
    log.info("Service identifiers in HarnessPolicyEvaluationDsl - {}", serviceIdentifiers);

    long currentTime = System.currentTimeMillis();
    DeploymentsInfo serviceDeploymentInfo = null;
    try {
      serviceDeploymentInfo = NGRestUtils
                                  .getResponse(dashboardResourceClient.getDeploymentsByServiceId(
                                      serviceIdentifiers.get(DslConstants.CD_ACCOUNT_IDENTIFIER_KEY),
                                      serviceIdentifiers.get(DslConstants.CD_ORG_IDENTIFIER_KEY),
                                      serviceIdentifiers.get(DslConstants.CD_PROJECT_IDENTIFIER_KEY),
                                      serviceIdentifiers.get(DslConstants.CD_SERVICE_IDENTIFIER_KEY),
                                      currentTime - DslConstants.THIRTY_DAYS_IN_MILLIS, currentTime))
                                  .get();
    } catch (Exception e) {
      log.error(
          String.format(
              "Error in getting the service dashboard info of policy evaluation check in account - %s, org - %s, project - %s, and service - %s",
              serviceIdentifiers.get(DslConstants.CD_ACCOUNT_IDENTIFIER_KEY),
              serviceIdentifiers.get(DslConstants.CD_ORG_IDENTIFIER_KEY),
              serviceIdentifiers.get(DslConstants.CD_PROJECT_IDENTIFIER_KEY),
              serviceIdentifiers.get(DslConstants.CD_SERVICE_IDENTIFIER_KEY)),
          e);
    }
    log.info("Service deployment info in HarnessPolicyEvaluationDsl - {}", serviceDeploymentInfo);

    String cdPipelineId = null;
    if (serviceDeploymentInfo != null && !serviceDeploymentInfo.getDeployments().isEmpty()) {
      cdPipelineId = serviceDeploymentInfo.getDeployments().get(0).getPipelineIdentifier();
    }
    log.info("CD pipeline id in HarnessPolicyEvaluationDsl - {}", cdPipelineId);

    Object responseCD = null;

    if (cdPipelineId != null) {
      try {
        responseCD = NGRestUtils.getResponse(
            pipelineServiceClient.getListOfExecutions(serviceIdentifiers.get(DslConstants.CD_ACCOUNT_IDENTIFIER_KEY),
                serviceIdentifiers.get(DslConstants.CD_ORG_IDENTIFIER_KEY),
                serviceIdentifiers.get(DslConstants.CD_PROJECT_IDENTIFIER_KEY), null, cdPipelineId, 0, 5, null, null,
                null, null, false));
      } catch (Exception e) {
        log.error(
            String.format(
                "Error in getting the cd pipeline info of policy evaluation check in account - %s, org - %s, project - %s, and pipeline - %s",
                serviceIdentifiers.get(DslConstants.CD_ACCOUNT_IDENTIFIER_KEY),
                serviceIdentifiers.get(DslConstants.CD_ORG_IDENTIFIER_KEY),
                serviceIdentifiers.get(DslConstants.CD_PROJECT_IDENTIFIER_KEY), cdPipelineId),
            e);
      }
    }
    log.info("CD pipeline response in HarnessPolicyEvaluationDsl - {}, CD Pipeline url -{}", responseCD,
        DslDataProviderUtil.getCdPipelineFromIdentifiers(serviceIdentifiers, cdPipelineId));

    List<DataPointInputValues> dataPointInputValuesList =
        dataSourceDataPointInfo.getDataSourceLocation().getDataPoints();

    for (DataPointInputValues dataPointInputValues : dataPointInputValuesList) {
      String dataPointIdentifier = dataPointInputValues.getDataPointIdentifier();
      returnData.putAll(pipelineExecutionResponseFactory.getResponseParser(dataPointIdentifier)
                            .getParsedValue(responseCI, responseCD, dataPointIdentifier, ciPipelineUrl,
                                DslDataProviderUtil.getCdPipelineFromIdentifiers(serviceIdentifiers, cdPipelineId)));
    }
    log.info("Return data in HarnessPolicyEvaluationDsl - {}", returnData);
    return returnData;
  }
}
