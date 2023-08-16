/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datapointsdata.resource;

import io.harness.spec.server.idp.v1.DataPointsDataApi;
import io.harness.spec.server.idp.v1.model.DataSourceDataPointInfoRequest;

import javax.validation.Valid;
import javax.ws.rs.core.Response;

public class DataPointDataApiImpl implements DataPointsDataApi {
  @Override
  public Response getDataSourceDataPointValues(
      String dataSource, @Valid DataSourceDataPointInfoRequest body, String harnessAccount) {
    return null;
  }
}
