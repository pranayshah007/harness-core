/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

/**
 *
 */

package software.wings.resources;

import static software.wings.security.PermissionAttribute.PermissionType.ACCOUNT_MANAGEMENT;
import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.ResourceConstraint;
import io.harness.distribution.constraint.Constraint.Strategy;
import io.harness.rest.RestResponse;

import software.wings.beans.ResourceConstraintUsage;
import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.ListAPI;
import software.wings.security.annotations.Scope;
import software.wings.service.intfc.ResourceConstraintService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.util.List;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

@Api("resource-constraints")
@Path("/resource-constraints")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Scope(ResourceType.SETTING)
public class ResourceConstraintResource {
  @Inject private ResourceConstraintService resourceConstraintService;

  @GET
  @Timed
  @ExceptionMetered
  @ListAPI(ResourceType.SETTING)
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<PageResponse<ResourceConstraint>> list(
      @QueryParam("accountId") String accountId, @BeanParam PageRequest<ResourceConstraint> pageRequest) {
    return new RestResponse<>(resourceConstraintService.list(pageRequest));
  }

  @POST
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  public RestResponse<ResourceConstraint> save(
      @QueryParam("accountId") String accountId, ResourceConstraint resourceConstraint) {
    resourceConstraint.setAccountId(accountId);
    if (resourceConstraint.getStrategy() == null) {
      resourceConstraint.setStrategy(Strategy.ASAP);
    }
    return new RestResponse<>(resourceConstraintService.save(resourceConstraint));
  }

  @PUT
  @Path("{resourceConstraintId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  public RestResponse<ResourceConstraint> update(@QueryParam("accountId") String accountId,
      @PathParam("resourceConstraintId") String resourceConstraintId, ResourceConstraint resourceConstraint) {
    resourceConstraint.setUuid(resourceConstraintId);
    resourceConstraint.setAccountId(accountId);
    resourceConstraintService.update(resourceConstraint);
    return new RestResponse<>(resourceConstraint);
  }

  @DELETE
  @Path("{resourceConstraintId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = ACCOUNT_MANAGEMENT)
  public RestResponse delete(
      @QueryParam("accountId") String accountId, @PathParam("resourceConstraintId") String resourceConstraintId) {
    resourceConstraintService.delete(accountId, resourceConstraintId);
    return new RestResponse();
  }

  @POST
  @Path("usage")
  @Timed
  @ExceptionMetered
  @ListAPI(ResourceType.SETTING)
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<List<ResourceConstraintUsage>> usage(
      @QueryParam("accountId") String accountId, List<String> resourceConstraintIds) {
    return new RestResponse<>(resourceConstraintService.usage(accountId, resourceConstraintIds));
  }
}
