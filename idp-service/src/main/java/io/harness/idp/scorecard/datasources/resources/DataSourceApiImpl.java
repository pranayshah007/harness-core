/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datasources.resources;

import io.harness.spec.server.idp.v1.DataSourceApi;

import io.swagger.v3.oas.annotations.Parameter;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

public class DataSourceApiImpl implements DataSourceApi {
  @Override
  public Response getAllDatasourcesForAccount(@HeaderParam("Harness-Account") @Parameter(
      description =
          "Identifier field of the account the resource is scoped to. This is required for Authorization methods other than the x-api-key header. If you are using the x-api-key header, this can be skipped.")
      String harnessAccount) {
    return null;
  }

  @Override
  public Response getDatapointsForDataource(
      @PathParam("data-source") @Parameter(description = "Identifier for datasource") String dataSource,
      @HeaderParam("Harness-Account") @Parameter(
          description =
              "Identifier field of the account the resource is scoped to. This is required for Authorization methods other than the x-api-key header. If you are using the x-api-key header, this can be skipped.")
      String harnessAccount) {
    return null;
  }
}