/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.beans.SearchFilter.Operator.EQ;

import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_API_KEYS;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.InternalApi;

import software.wings.audit.AuditSkip;
import software.wings.beans.ApiKeyEntry;
import software.wings.beans.ApiKeyEntry.ApiKeyEntryKeys;
import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.Scope;
import software.wings.service.intfc.ApiKeyService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.util.HashMap;
import java.util.Map;
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
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

@Api("/api-keys")
@Path("/api-keys")
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Scope(ResourceType.API_KEY)
@AuthRule(permissionType = MANAGE_API_KEYS)
@Slf4j
public class ApiKeyResource {
  private ApiKeyService apiKeyService;

  @Inject
  public ApiKeyResource(ApiKeyService apiKeyService) {
    this.apiKeyService = apiKeyService;
  }

  @POST
  @Timed
  @ExceptionMetered
  public RestResponse<ApiKeyEntry> generate(
      @NotEmpty @QueryParam("accountId") String accountId, ApiKeyEntry apiKeyEntry) {
    return new RestResponse<>(apiKeyService.generate(accountId, apiKeyEntry));
  }

  @PUT
  @Path("{apiKeyId}")
  @Timed
  @ExceptionMetered
  public RestResponse<ApiKeyEntry> update(@NotEmpty @QueryParam("accountId") String accountId,
      @NotEmpty @PathParam("apiKeyId") String uuid, ApiKeyEntry apiKeyEntry) {
    return new RestResponse<>(apiKeyService.update(uuid, accountId, apiKeyEntry));
  }

  @GET
  @Timed
  @ExceptionMetered
  public RestResponse<PageResponse<ApiKeyEntry>> list(
      @NotEmpty @QueryParam("accountId") String accountId, @BeanParam PageRequest<ApiKeyEntry> pageRequest) {
    pageRequest.addFilter(ApiKeyEntryKeys.accountId, EQ, accountId);
    return new RestResponse<>(apiKeyService.list(pageRequest, accountId, true, false));
  }

  @GET
  @Path("{apiKeyId}")
  @Timed
  @ExceptionMetered
  public RestResponse<ApiKeyEntry> get(
      @NotEmpty @QueryParam("accountId") String accountId, @NotEmpty @PathParam("apiKeyId") String uuid) {
    return new RestResponse<>(apiKeyService.get(uuid, accountId));
  }

  @DELETE
  @Path("{apiKeyId}")
  @Timed
  @ExceptionMetered
  public RestResponse<Void> delete(
      @NotEmpty @QueryParam("accountId") String accountId, @NotEmpty @PathParam("apiKeyId") String uuid) {
    apiKeyService.delete(accountId, uuid);
    return new RestResponse<>();
  }

  @DELETE
  public RestResponse<Map<String, Object>> deleteAll(@NotEmpty @QueryParam("accountId") String accountId) {
    apiKeyService.deleteByAccountId(accountId);
    Map<String, Object> status = new HashMap<>();
    status.put("deleted", true);
    return new RestResponse<>(status);
  }

  @POST
  @Path("validate")
  @Timed
  @ExceptionMetered
  @InternalApi
  @AuditSkip
  public RestResponse<Boolean> validate(@QueryParam("accountId") String accountId, String apiKey) {
    return new RestResponse<>(apiKeyService.isApiKeyValid(apiKey, accountId));
  }
}
