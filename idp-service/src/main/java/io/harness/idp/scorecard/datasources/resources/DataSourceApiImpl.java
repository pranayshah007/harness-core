/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.resources;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.scorecard.datasources.service.DataSourceService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.DataSourceApi;
import io.harness.spec.server.idp.v1.model.DataPoint;
import io.harness.spec.server.idp.v1.model.DataPointsResponse;
import io.harness.spec.server.idp.v1.model.DataSource;
import io.harness.spec.server.idp.v1.model.DataSourceDataPointsMap;
import io.harness.spec.server.idp.v1.model.DataSourceDataPointsMapResponse;
import io.harness.spec.server.idp.v1.model.DataSourcesResponse;

import java.util.List;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @com.google.inject.Inject }))
@NextGenManagerAuth
@Slf4j
public class DataSourceApiImpl implements DataSourceApi {
  DataSourceService dataSourceService;
  @Override
  public Response getAllDatasourcesForAccount(String harnessAccount) {
    try {
      List<DataSource> dataSources = dataSourceService.getAllDataSourcesDetailsForAnAccount(harnessAccount);
      DataSourcesResponse dataSourcesResponse = new DataSourcesResponse();
      dataSourcesResponse.setDataSources(dataSources);
      return Response.status(Response.Status.OK).entity(dataSourcesResponse).build();
    } catch (Exception e) {
      log.error("Error in getting data sources details for account - {}", harnessAccount);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response getDataPointsForDataSource(String dataSource, String harnessAccount) {
    try {
      List<DataPoint> dataPoints = dataSourceService.getAllDataPointsDetailsForDataSource(harnessAccount, dataSource);
      DataPointsResponse dataPointsResponse = new DataPointsResponse();
      dataPointsResponse.dataPoints(dataPoints);
      return Response.status(Response.Status.OK).entity(dataPointsResponse).build();
    } catch (Exception e) {
      log.error(
          "Error in getting data points details for account - {} and datasource - {}", harnessAccount, dataSource);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response getDataSourcesDataPointsMap(String harnessAccount) {
    try {
      List<DataSourceDataPointsMap> dataSourceDataPointsMaps =
          dataSourceService.getDataPointsForDataSources(harnessAccount);
      DataSourceDataPointsMapResponse dataSourceDataPointsMapResponse = new DataSourceDataPointsMapResponse();
      dataSourceDataPointsMapResponse.dataSourceDataPointsMap(dataSourceDataPointsMaps);
      return Response.status(Response.Status.OK).entity(dataSourceDataPointsMapResponse).build();
    } catch (Exception e) {
      log.error("Error in getting data source data points map for account - {}", harnessAccount);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }
}