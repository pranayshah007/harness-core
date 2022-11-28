/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SortOrder;
import io.harness.rest.RestResponse;

import software.wings.beans.ResourceLookup;
import software.wings.beans.ResourceLookup.ResourceLookupKeys;
import software.wings.service.intfc.ResourceLookupService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.util.List;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

/**
 * The Class AuditResource.
 */
@Api("resourcelookup")
@Path("/resourcelookup")
public class ResourceLookupResource {
  private ResourceLookupService resourceLookupService;

  @Inject
  public ResourceLookupResource(ResourceLookupService resourceLookupService) {
    this.resourceLookupService = resourceLookupService;
  }

  public void setResourceLookupService(ResourceLookupService resourceLookupService) {
    this.resourceLookupService = resourceLookupService;
  }

  @GET
  @Timed
  @ExceptionMetered
  @Produces("application/json")
  public RestResponse<PageResponse<ResourceLookup>> list(
      @QueryParam("accountId") String accountId, @BeanParam PageRequest<ResourceLookup> pageRequest) {
    // Sorting has been added so that it takes index correctly
    pageRequest.addOrder(ResourceLookupKeys.accountId, SortOrder.OrderType.ASC);
    return new RestResponse<>(resourceLookupService.list(pageRequest));
  }

  @GET
  @Path("acc_resource_types")
  @Timed
  @ExceptionMetered
  public RestResponse<List<String>> listAccResourceTypes(@QueryParam("accountId") String accountId) {
    return new RestResponse<>(resourceLookupService.listAccountLevelResourceTypes());
  }

  @GET
  @Path("app_resource_types")
  @Timed
  @ExceptionMetered
  public RestResponse<List<String>> listAppResourceTypes(@QueryParam("accountId") String accountId) {
    return new RestResponse<>(resourceLookupService.listApplicationLevelResourceTypes());
  }

  @GET
  @Path("tags")
  @Timed
  @ExceptionMetered
  @Produces("application/json")
  public PageResponse<ResourceLookup> listResourcesWithTag(@QueryParam("accountId") String accountId,
      @QueryParam("filter") String filter, @QueryParam("limit") String limit, @QueryParam("offset") String offset) {
    return resourceLookupService.listResourceLookupRecordsWithTags(accountId, filter, limit, offset);
  }
}
