package io.harness.cvng.core.resources;

import io.harness.annotations.ExposeInternalException;
import io.harness.cvng.beans.MetricPackDTO;
import io.harness.cvng.beans.newrelic.NewRelicApplication;
import io.harness.cvng.core.beans.MetricPackValidationResponse;
import io.harness.cvng.core.beans.TimeSeriesSampleDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.NewRelicService;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.LinkedHashMap;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import retrofit2.http.Body;

@Api("newrelic")
@Path("/newrelic")
@Produces("application/json")
@NextGenManagerAuth
@ExposeInternalException
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
public class NewRelicResource {
  @Inject private NewRelicService newRelicService;

  @GET
  @Path("/endpoints")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get all newrelic endpoints", nickname = "getNewRelicEndPoints")
  public ResponseDTO<List<String>> getNewRelicEndPoints() {
    return ResponseDTO.newResponse(newRelicService.getNewRelicEndpoints());
  }

  @GET
  @Path("/applications")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get all newrelic applications", nickname = "getNewRelicApplications")
  public ResponseDTO<List<NewRelicApplication>> getNewRelicApplications(
      @NotNull @QueryParam("accountId") String accountId,
      @NotNull @QueryParam("connectorIdentifier") final String connectorIdentifier,
      @QueryParam("orgIdentifier") @NotNull String orgIdentifier,
      @QueryParam("projectIdentifier") @NotNull String projectIdentifier, @QueryParam("pageSize") @NotNull int pageSize,
      @QueryParam("offset") @NotNull int offset, @QueryParam("filter") @DefaultValue("") String filter,
      @NotNull @QueryParam("tracingId") String tracingId) {
    return ResponseDTO.newResponse(newRelicService.getNewRelicApplications(
        accountId, connectorIdentifier, orgIdentifier, projectIdentifier, filter, tracingId));
  }

  @POST
  @Path("/metric-data")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get metric data for given metric packs", nickname = "getNewRelicMetricData")
  public ResponseDTO<MetricPackValidationResponse> getNewRelicMetricData(
      @QueryParam("accountId") @NotNull String accountId, @QueryParam("orgIdentifier") @NotNull String orgIdentifier,
      @QueryParam("projectIdentifier") @NotNull String projectIdentifier,
      @QueryParam("connectorIdentifier") @NotNull String connectorIdentifier,
      @QueryParam("appName") @NotNull String appName, @QueryParam("appId") @NotNull String appId,
      @QueryParam("requestGuid") @NotNull String requestGuid, @NotNull @Valid @Body List<MetricPackDTO> metricPacks) {
    return ResponseDTO.newResponse(newRelicService.validateData(
        accountId, connectorIdentifier, orgIdentifier, projectIdentifier, appName, appId, metricPacks, requestGuid));
  }

  @GET
  @Path("/fetch-sample-data")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get sample data for given nrql", nickname = "getSampleDataForNRQL")
  public ResponseDTO<LinkedHashMap> getSampleDataForNRQL(@QueryParam("accountId") @NotNull String accountId,
      @QueryParam("orgIdentifier") @NotNull String orgIdentifier,
      @QueryParam("projectIdentifier") @NotNull String projectIdentifier,
      @QueryParam("connectorIdentifier") @NotNull String connectorIdentifier,
      @QueryParam("requestGuid") @NotNull String requestGuid, @QueryParam("nrql") @NotNull String nrql) {
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(accountId)
                                      .orgIdentifier(orgIdentifier)
                                      .projectIdentifier(projectIdentifier)
                                      .build();
    return ResponseDTO.newResponse(
        newRelicService.fetchSampleData(projectParams, connectorIdentifier, nrql, requestGuid));
  }

  @GET
  @Path("/parse-sample-data")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "parse sample data for given json response", nickname = "getParsedTimeseries")
  public ResponseDTO<List<TimeSeriesSampleDTO>> parseSampleData(@QueryParam("accountId") @NotNull String accountId,
      @QueryParam("orgIdentifier") @NotNull String orgIdentifier,
      @QueryParam("projectIdentifier") @NotNull String projectIdentifier,
      @QueryParam("jsonResponse") @NotNull String jsonResponse, @QueryParam("groupName") @NotNull String groupName,
      @QueryParam("metricValueJsonPath") @NotNull String metricValueJsonPath,
      @QueryParam("timestampJsonPath") @NotNull String timestampJsonPath,
      @QueryParam("timestampFormat") String timestampFormat) {
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(accountId)
                                      .orgIdentifier(orgIdentifier)
                                      .projectIdentifier(projectIdentifier)
                                      .build();
    return ResponseDTO.newResponse(newRelicService.parseSampleData(
        projectParams, jsonResponse, groupName, metricValueJsonPath, timestampJsonPath, timestampFormat));
  }
}
