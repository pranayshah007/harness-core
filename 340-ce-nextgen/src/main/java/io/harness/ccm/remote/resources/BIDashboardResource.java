/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.CENextGenConfiguration;
import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.ccm.graphql.core.overview.CCMMetaDataService;
import io.harness.ccm.helper.BIDashboardQueryHelper;
import io.harness.ccm.remote.beans.BIDashboardSummary;
import io.harness.ccm.utils.LogAccountIdentifier;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.TableResult;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Api("bi-dashboards")
@Path("bi-dashboards")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@NextGenManagerAuth
@Slf4j
@Service
@OwnedBy(CE)
@Tag(name = "Cloud Cost BI Dashboards", description = "Get details of BI-dashboards specific to CCM")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content = { @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FailureDTO.class)) })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content = { @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorDTO.class)) })
public class BIDashboardResource {
  @Inject private BigQueryService bigQueryService;
  @Inject private CCMMetaDataService ccmMetaDataService;
  @Inject private CENextGenConfiguration configuration;

  @GET
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "List all CCM BI Dashboards", nickname = "listBIDashboards")
  @Operation(operationId = "listBIDashboards", description = "List all the Cloud Cost BI Dashboards.",
      summary = "List all the BI Dashboards for CCM",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the list of all BI Dashboards",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<List<BIDashboardSummary>>
  list(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
      NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId) {
    String query = BIDashboardQueryHelper.createQueryToListDashboards(configuration.getGcpConfig().getGcpProjectId(),
        configuration.getDeploymentClusterName(), ccmMetaDataService.getCCMMetaData(accountId));
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query).build();
    List<BIDashboardSummary> dashboardSummaries = new ArrayList<>();
    try {
      log.info("Querying BQ to fetch CCM BI Dashboards: {}", query);
      TableResult result = bigQueryService.get().query(queryConfig);
      dashboardSummaries = BIDashboardQueryHelper.extractDashboardSummaries(result, accountId);
    } catch (Exception e) {
      log.error("Failed to fetch the list of CCM BI Dashboards from BQ.", e);
    }
    return ResponseDTO.newResponse(dashboardSummaries);
  }
}
