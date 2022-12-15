/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import static io.harness.cvng.core.services.CVNextGenConstants.DELEGATE_DATA_COLLECTION;

import io.harness.cvng.beans.TimeSeriesDataCollectionRecord;
import io.harness.cvng.core.services.api.TimeSeriesRecordService;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.DelegateAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import retrofit2.http.Body;

@Api(DELEGATE_DATA_COLLECTION)
@Path(DELEGATE_DATA_COLLECTION)
@Produces("application/json")
public class DataCollectionResource {
  @Inject private TimeSeriesRecordService timeSeriesRecordService;

  @POST
  @Timed
  @ExceptionMetered
  @DelegateAuth
  @ApiOperation(value = "save time series records", nickname = "saveTimeSeriesData")
  public RestResponse<Boolean> saveTimeSeriesData(@QueryParam("accountId") @NotNull String accountId,
      @NotNull @Valid @Body List<TimeSeriesDataCollectionRecord> dataCollectionRecords) {
    return new RestResponse<>(timeSeriesRecordService.save(dataCollectionRecords));
  }
}
