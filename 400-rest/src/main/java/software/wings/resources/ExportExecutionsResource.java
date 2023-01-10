/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.CreatedByType;
import io.harness.beans.PageRequest;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.export.ExportExecutionsResourceService;
import io.harness.execution.export.request.ExportExecutionsRequestHelper;
import io.harness.execution.export.request.ExportExecutionsRequestLimitChecks;
import io.harness.execution.export.request.ExportExecutionsRequestSummary;
import io.harness.execution.export.request.ExportExecutionsUserParams;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.PublicApi;

import software.wings.beans.WorkflowExecution;
import software.wings.dl.WingsPersistence;
import software.wings.security.PermissionAttribute;
import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.Scope;
import software.wings.service.intfc.WorkflowExecutionService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import io.swagger.annotations.Api;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@OwnedBy(CDC)
@Api(ExportExecutionsRequestHelper.EXPORT_EXECUTIONS_RESOURCE)
@Path("/" + ExportExecutionsRequestHelper.EXPORT_EXECUTIONS_RESOURCE)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Scope(ResourceType.APPLICATION)
public class ExportExecutionsResource {
  @Inject private ExportExecutionsResourceService exportExecutionsResourceService;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private WorkflowExecutionService workflowExecutionService;

  @GET
  @Path("limit-checks")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionAttribute.PermissionType.LOGGED_IN)
  public RestResponse<ExportExecutionsRequestLimitChecks> getLimitChecks(@QueryParam("accountId") String accountId,
      @QueryParam("tagFilter") String tagFilter, @BeanParam PageRequest<WorkflowExecution> pageRequest) {
    Query<WorkflowExecution> query = convertToQuery(pageRequest, tagFilter);
    return new RestResponse<>(exportExecutionsResourceService.getLimitChecks(accountId, query));
  }

  @POST
  @Path("export")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = PermissionAttribute.PermissionType.LOGGED_IN)
  public RestResponse<ExportExecutionsRequestSummary> export(@QueryParam("accountId") String accountId,
      @QueryParam("tagFilter") String tagFilter, @BeanParam PageRequest<WorkflowExecution> pageRequest,
      ExportExecutionsUserParams userParams) {
    Query<WorkflowExecution> query = convertToQuery(pageRequest, tagFilter);
    if (userParams != null) {
      userParams.setCreatedByType(CreatedByType.USER);
    }
    return new RestResponse<>(exportExecutionsResourceService.export(accountId, query, userParams));
  }

  @GET
  @Path(ExportExecutionsRequestHelper.STATUS_PATH + "/{requestId}")
  @Timed
  @ExceptionMetered
  @PublicApi
  public Response getStatus(@QueryParam("accountId") String accountId, @PathParam("requestId") String requestId) {
    return Response.ok(exportExecutionsResourceService.getStatusJson(accountId, requestId), MediaType.APPLICATION_JSON)
        .build();
  }

  @GET
  @Path(ExportExecutionsRequestHelper.DOWNLOAD_PATH + "/{requestId}")
  @Timed
  @ExceptionMetered
  @PublicApi
  public Response downloadFile(@QueryParam("accountId") String accountId, @PathParam("requestId") String requestId) {
    return Response
        .ok(exportExecutionsResourceService.downloadFile(accountId, requestId), MediaType.APPLICATION_OCTET_STREAM)
        .header("content-disposition", format("attachment; filename = %s_%s.zip", accountId, requestId))
        .build();
  }

  private Query<WorkflowExecution> convertToQuery(PageRequest<WorkflowExecution> pageRequest, String tagFilter) {
    if (pageRequest == null) {
      throw new InvalidRequestException("No filters provided for selecting executions to export");
    }

    if (EmptyPredicate.isNotEmpty(tagFilter)) {
      workflowExecutionService.addTagFilterToPageRequest(pageRequest, tagFilter);
    }
    return wingsPersistence.convertToQuery(WorkflowExecution.class, pageRequest);
  }
}
