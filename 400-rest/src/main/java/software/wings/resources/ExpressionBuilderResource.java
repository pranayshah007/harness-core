/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static software.wings.security.PermissionAttribute.ResourceType.APPLICATION;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.exception.InvalidRequestException;
import io.harness.rest.RestResponse;

import software.wings.beans.EntityType;
import software.wings.beans.entity.SubEntityType;
import software.wings.security.annotations.Scope;
import software.wings.service.intfc.expression.ExpressionBuilderService;
import software.wings.sm.StateType;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.util.Set;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Api("expression-builder")
@Path("/expression-builder")
@Produces("application/json")
@Consumes("application/json")
@Scope(APPLICATION)
public class ExpressionBuilderResource {
  @Inject private ExpressionBuilderService expressionBuilderService;

  @GET
  @Timed
  @ExceptionMetered
  public RestResponse<Set<String>> listExpressions(@QueryParam("appId") String appId,
      @QueryParam("entityId") String entityId, @QueryParam("entityType") EntityType entityType,
      @QueryParam("serviceId") String serviceId, @QueryParam("stateType") String strStateType,
      @QueryParam("subEntityType") String strSubEntityType,
      @QueryParam("forTags") @DefaultValue("false") boolean forTags) {
    StateType stateType = null;
    if (isNotBlank(strStateType)) {
      try {
        if (!strStateType.contentEquals("\"\"")) {
          stateType = StateType.valueOf(strStateType);
        }
      } catch (IllegalArgumentException e) {
        throw new InvalidRequestException("Invalid state type " + strStateType);
      }
    }
    SubEntityType subEntityType = null;
    if (isNotBlank(strSubEntityType)) {
      try {
        if (!strSubEntityType.contentEquals("\"\"")) {
          subEntityType = SubEntityType.valueOf(strSubEntityType);
        }
      } catch (IllegalArgumentException e) {
        throw new InvalidRequestException("Invalid state type " + strStateType);
      }
    }
    return new RestResponse(expressionBuilderService.listExpressions(
        appId, entityId, entityType, serviceId, stateType, subEntityType, forTags));
  }

  @GET
  @Path("/values")
  @Timed
  @ExceptionMetered
  public RestResponse<Set<String>> listExpressionsFromValues(
      @QueryParam("appId") String appId, @QueryParam("serviceId") String serviceId) {
    return new RestResponse<>(expressionBuilderService.listExpressionsFromValuesForService(appId, serviceId));
  }
}
