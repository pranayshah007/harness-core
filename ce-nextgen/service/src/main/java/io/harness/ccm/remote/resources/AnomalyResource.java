/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.rbac.CCMRbacHelperImpl.PERMISSION_MISSING_MESSAGE;
import static io.harness.ccm.rbac.CCMRbacHelperImpl.RESOURCE_FOLDER;
import static io.harness.ccm.rbac.CCMRbacPermissions.PERSPECTIVE_VIEW;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.entities.anomaly.AnomalyData;
import io.harness.ccm.commons.entities.anomaly.AnomalyFeedbackDTO;
import io.harness.ccm.commons.entities.anomaly.AnomalySummary;
import io.harness.ccm.commons.entities.anomaly.AnomalyWidgetData;
import io.harness.ccm.commons.entities.anomaly.PerspectiveAnomalyData;
import io.harness.ccm.graphql.dto.recommendation.FilterStatsDTO;
import io.harness.ccm.helper.AnomalyQueryHelper;
import io.harness.ccm.rbac.CCMRbacHelper;
import io.harness.ccm.remote.beans.anomaly.AnomalyFilterPropertiesDTO;
import io.harness.ccm.service.intf.AnomalyService;
import io.harness.ccm.utils.LogAccountIdentifier;
import io.harness.ccm.views.dto.PerspectiveQueryDTO;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.service.CEViewService;
import io.harness.exception.WingsException;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Api("anomaly")
@Path("anomaly")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@NextGenManagerAuth
@Slf4j
@Service
@OwnedBy(CE)
@Tag(name = "Cloud Cost Anomalies", description = "Get details about any anomalous spike in your cloud costs")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content = { @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FailureDTO.class)) })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content = { @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorDTO.class)) })
public class AnomalyResource {
  @Inject private AnomalyService anomalyService;
  @Inject private CEViewService ceViewService;
  @Inject private CCMRbacHelper rbacHelper;

  @POST
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "List Anomalies", nickname = "listAnomalies")
  @Operation(operationId = "listAnomalies",
      description = "Fetch the list of anomalies reported according to the filters applied", summary = "List Anomalies",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "List of Anomalies", content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<List<AnomalyData>>
  listAnomalies(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                    NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(description = "Anomaly Filter Properties") AnomalyFilterPropertiesDTO anomalyFilterPropertiesDTO) {
    List<CEView> ceViewsList = ceViewService.getAllViews(accountId);
    Set<String> allowedFolderIds = rbacHelper.checkFolderIdsGivenPermission(accountId, null, null,
        ceViewsList.stream().map(ceView -> ceView.getFolderId()).collect(Collectors.toSet()), PERSPECTIVE_VIEW);

    HashMap<String, CEView> allowedAnomaliesIdAndPerspectives =
        anomalyService.listAllowedAnomaliesIdAndPerspectives(accountId, allowedFolderIds, ceViewsList);
    List<AnomalyData> anomalyData = anomalyService.listAnomalies(accountId,
        AnomalyQueryHelper.buildAnomalyQueryFromFilterProperties(anomalyFilterPropertiesDTO),
        allowedAnomaliesIdAndPerspectives.keySet());

    return ResponseDTO.newResponse(anomalyService.addPerspectiveInfo(anomalyData, allowedAnomaliesIdAndPerspectives));
  }

  @POST
  @Path("filter-values")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "Filter Values available for given Anomaly-Filter fields", nickname = "anomalyFilterValues")
  @Operation(operationId = "anomalyFilterValues",
      description = "Returns the list of distinct values for all the specified Anomaly fields.",
      summary = "Returns the list of distinct values for all the specified Anomaly fields.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns the list of distinct values for all the specified Anomaly fields.",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<List<FilterStatsDTO>>
  getAnomalyFilterStats(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                            NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(required = true, description = "List of Anomaly columns whose unique values will be fetched")
      List<String> anomalyColumnsList) {
    return ResponseDTO.newResponse(anomalyService.getAnomalyFilterStats(accountId, anomalyColumnsList));
  }

  @POST
  @Path("perspective/{perspectiveId}")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "List Anomalies for Perspective", nickname = "listPerspectiveAnomalies")
  @Operation(operationId = "listPerspectiveAnomalies", description = "Fetch anomalies for perspective",
      summary = "List Anomalies for Perspective",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "List of Anomalies for Perspective",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<List<PerspectiveAnomalyData>>
  listPerspectiveAnomalies(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                               NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @Parameter(required = true, description = "Unique identifier for perspective") @PathParam(
          "perspectiveId") String perspectiveId,
      @RequestBody(required = true, description = "Perspective Query") PerspectiveQueryDTO perspectiveQueryDTO) {
    CEView perspective = ceViewService.get(perspectiveId);
    List<PerspectiveAnomalyData> perspectiveAnomalyData = null;
    if (perspective != null) {
      rbacHelper.checkAnomalyViewPermission(accountId, null, null, perspective.getFolderId());
      perspectiveAnomalyData = anomalyService.listPerspectiveAnomalies(accountId, perspective, perspectiveQueryDTO);
    }
    return ResponseDTO.newResponse(perspectiveAnomalyData);
  }

  @PUT
  @Path("feedback")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "Report Anomaly Feedback", nickname = "reportAnomalyFeedback")
  @Operation(operationId = "reportAnomalyFeedback", description = "Mark an anomaly as true/false anomaly",
      summary = "Report Anomaly feedback",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Report Anomaly Feedback", content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<Boolean>
  reportAnomalyFeedback(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                            NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @Parameter(required = true, description = "Unique identifier for perspective") @QueryParam("anomalyId")
      String anomalyId, @RequestBody(required = true, description = "Feedback") AnomalyFeedbackDTO feedback) {
    List<CEView> ceViewsList = ceViewService.getAllViews(accountId);
    Set<String> allowedFolderIds = rbacHelper.checkFolderIdsGivenPermission(accountId, null, null,
        ceViewsList.stream().map(ceView -> ceView.getFolderId()).collect(Collectors.toSet()), PERSPECTIVE_VIEW);
    Set<String> allowedAnomaliesIds = anomalyService.listAllowedAnomaliesIds(accountId, allowedFolderIds, ceViewsList);

    if (!allowedAnomaliesIds.contains(anomalyId)) {
      throw new NGAccessDeniedException(
          String.format(PERMISSION_MISSING_MESSAGE, PERSPECTIVE_VIEW, RESOURCE_FOLDER), WingsException.USER, null);
    }

    return ResponseDTO.newResponse(anomalyService.updateAnomalyFeedback(accountId, anomalyId, feedback));
  }

  @POST
  @Path("summary")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "Get Anomalies Summary", nickname = "getAnomaliesSummary")
  @Operation(operationId = "getAnomaliesSummary", description = "Fetch the result of anomaly query",
      summary = "List Anomalies",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Anomaly Query result", content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<List<AnomalySummary>>
  getAnomaliesSummary(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(description = "Anomaly Filter Properties") AnomalyFilterPropertiesDTO anomalyFilterPropertiesDTO) {
    List<CEView> ceViewsList = ceViewService.getAllViews(accountId);
    Set<String> allowedFolderIds = rbacHelper.checkFolderIdsGivenPermission(accountId, null, null,
        ceViewsList.stream().map(ceView -> ceView.getFolderId()).collect(Collectors.toSet()), PERSPECTIVE_VIEW);
    Set<String> allowedAnomaliesIds = anomalyService.listAllowedAnomaliesIds(accountId, allowedFolderIds, ceViewsList);

    return ResponseDTO.newResponse(anomalyService.getAnomalySummary(accountId,
        AnomalyQueryHelper.buildAnomalyQueryFromFilterProperties(anomalyFilterPropertiesDTO), allowedAnomaliesIds));
  }

  @POST
  @Hidden
  @Path("widgets")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "Get Anomaly Widgets", nickname = "getAnomalyWidgetsData")
  @Operation(operationId = "getAnomalyWidgetsData",
      description = "Fetch the data corresponding to anomaly list page widgets", summary = "Get Anomaly Widgets",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Anomaly Query result", content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<List<AnomalyWidgetData>>
  getAnomalyWidgetsData(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                            NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(description = "Anomaly Filter Properties") AnomalyFilterPropertiesDTO anomalyFilterPropertiesDTO) {
    List<CEView> ceViewsList = ceViewService.getAllViews(accountId);
    Set<String> allowedFolderIds = rbacHelper.checkFolderIdsGivenPermission(accountId, null, null,
        ceViewsList.stream().map(ceView -> ceView.getFolderId()).collect(Collectors.toSet()), PERSPECTIVE_VIEW);
    Set<String> allowedAnomaliesIds = anomalyService.listAllowedAnomaliesIds(accountId, allowedFolderIds, ceViewsList);

    return ResponseDTO.newResponse(anomalyService.getAnomalyWidgetData(accountId,
        AnomalyQueryHelper.buildAnomalyQueryFromFilterProperties(anomalyFilterPropertiesDTO), allowedAnomaliesIds));
  }
}
