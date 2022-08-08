/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import io.harness.cvng.beans.SplunkSavedSearch;
import io.harness.cvng.core.beans.TimeSeriesSampleDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.SplunkService;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SortedSet;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import org.hibernate.validator.constraints.NotEmpty;

@Api("splunk/")
@Path("splunk")
@Produces("application/json")
@NextGenManagerAuth
public class SplunkResource {
  @Inject private SplunkService splunkService;
  @GET
  @Path("saved-searches")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "gets saved searches in splunk", nickname = "getSplunkSavedSearches")
  public RestResponse<List<SplunkSavedSearch>> getSavedSearches(@NotNull @BeanParam ProjectParams projectParams,
      @QueryParam("connectorIdentifier") String connectorIdentifier,
      @QueryParam("requestGuid") @NotNull String requestGuid) {
    return new RestResponse<>(splunkService.getSavedSearches(projectParams.getAccountIdentifier(),
        projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), connectorIdentifier, requestGuid));
  }

  @GET
  @Path("sample-data")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "validates given setting for splunk data source", nickname = "getSplunkSampleData")
  public RestResponse<List<LinkedHashMap>> getSampleData(@NotNull @BeanParam ProjectParams projectParams,
      @NotNull @QueryParam("connectorIdentifier") String connectorIdentifier,
      @NotNull @NotEmpty @QueryParam("query") String query, @QueryParam("requestGuid") @NotNull String requestGuid) {
    return new RestResponse<>(
        splunkService.getSampleData(projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
            projectParams.getProjectIdentifier(), connectorIdentifier, query, requestGuid));
  }

  @GET
  @Path("metric-sample-data")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "validates given setting for splunk data source", nickname = "getSplunkMetricSampleData")
  public RestResponse<SortedSet<TimeSeriesSampleDTO>> getMetricSampleData(
      @NotNull @BeanParam ProjectParams projectParams,
      @NotNull @QueryParam("connectorIdentifier") String connectorIdentifier,
      @NotNull @NotEmpty @QueryParam("query") String query, @QueryParam("requestGuid") @NotNull String requestGuid) {
    return new RestResponse<>(
        splunkService.getMetricSampleData(projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
            projectParams.getProjectIdentifier(), connectorIdentifier, query, requestGuid));
  }

  @GET
  @Path("latest-histogram")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get latest histogram for the query", nickname = "getSplunkLatestHistogram")
  public RestResponse<List<LinkedHashMap>> getLatestHistogram(@NotNull @BeanParam ProjectParams projectParams,
      @NotNull @QueryParam("connectorIdentifier") String connectorIdentifier,
      @NotNull @NotEmpty @QueryParam("query") String query, @QueryParam("requestGuid") @NotNull String requestGuid) {
    return new RestResponse<>(
        splunkService.getLatestHistogram(projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
            projectParams.getProjectIdentifier(), connectorIdentifier, query, requestGuid));
  }
}
