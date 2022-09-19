/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.beans.SearchFilter.Operator.EQ;

import static software.wings.security.PermissionAttribute.PermissionType.AUDIT_VIEWER;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.rest.RestResponse;

import software.wings.audit.AuditHeader;
import software.wings.audit.AuditHeader.AuditHeaderKeys;
import software.wings.audit.AuditHeaderYamlResponse;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AuditService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.dropwizard.jersey.caching.CacheControl;
import io.swagger.annotations.Api;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

/**
 * The Class AuditResource.
 */
@Api("audits")
@Path("/audits")
public class AuditResource {
  private AuditService httpAuditService;
  private AccountService accountService;

  /**
   * Gets http audit service.
   *
   * @return the http audit service
   */
  @Inject
  public AuditResource(AuditService httpAuditService, AccountService accountService) {
    this.httpAuditService = httpAuditService;
    this.accountService = accountService;
  }

  /**
   * Sets http audit service.
   *
   * @param httpAuditService the http audit service
   */
  public void setHttpAuditService(AuditService httpAuditService) {
    this.httpAuditService = httpAuditService;
  }

  /**
   * List.
   *
   * @param pageRequest the page request
   * @return the rest response
   */
  @GET
  @Timed
  @ExceptionMetered
  @Produces("application/json")
  @AuthRule(permissionType = AUDIT_VIEWER)
  public RestResponse<PageResponse<AuditHeader>> list(
      @QueryParam("accountId") String accountId, @BeanParam PageRequest<AuditHeader> pageRequest) {
    pageRequest.addFilter(AuditHeaderKeys.accountId, EQ, accountId);

    return new RestResponse<>(httpAuditService.list(pageRequest));
  }

  @GET
  @Path("filter")
  @Timed
  @ExceptionMetered
  @Produces("application/json")
  @AuthRule(permissionType = AUDIT_VIEWER)
  public RestResponse<PageResponse<AuditHeader>> listUsingFilter(@QueryParam("accountId") String accountId,
      @QueryParam("filter") String filter, @QueryParam("limit") String limit, @QueryParam("offset") String offset) {
    return new RestResponse<>(httpAuditService.listUsingFilter(accountId, filter, limit, offset));
  }

  @GET
  @Path("{auditHeaderId}/yamldetails")
  @Timed
  @ExceptionMetered
  @CacheControl(maxAge = 15, maxAgeUnit = TimeUnit.MINUTES)
  @Produces("application/json")
  @AuthRule(permissionType = AUDIT_VIEWER)
  public RestResponse<AuditHeaderYamlResponse> getAuditHeaderDetails(@PathParam("auditHeaderId") String auditHeaderId,
      @QueryParam("entityId") String entityId, @QueryParam("accountId") String accountId) {
    return new RestResponse<>(httpAuditService.fetchAuditEntityYamls(auditHeaderId, entityId, accountId));
  }
}
