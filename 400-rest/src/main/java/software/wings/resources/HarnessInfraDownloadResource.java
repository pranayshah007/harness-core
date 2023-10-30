/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.logging.common.AccessTokenBean;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.DelegateAuth;

import software.wings.service.impl.infra.InfraDownloadService;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.hibernate.validator.constraints.NotEmpty;

@Api("/agent/infra-download")
@Path("/agent/infra-download")
public class HarnessInfraDownloadResource {
  @Inject InfraDownloadService infraDownloadService;

  @GET
  @Path("delegate-auth/watcher/{version}")
  @Produces(MediaType.APPLICATION_JSON)
  @DelegateAuth
  public RestResponse<String> getWatcherDownloadUrlFromDelegate(@PathParam("version") String version) {
    return new RestResponse<>(infraDownloadService.getDownloadUrlForWatcher(version, null));
  }

  @GET
  @Path("delegate-auth/delegate/{version}")
  @Produces(MediaType.APPLICATION_JSON)
  @DelegateAuth
  public RestResponse<String> getDelegateDownloadUrlFromDelegate(
      @PathParam("version") String version, @QueryParam("accountId") String accountId) {
    return new RestResponse<>(infraDownloadService.getDownloadUrlForDelegate(version, accountId));
  }

  @GET
  @Path("default/watcher/{version}")
  @Produces(MediaType.APPLICATION_JSON)
  public RestResponse<String> getWatcherDownloadUrlFromDefaultAuth(@PathParam("version") String version,
      @QueryParam("accountId") @NotEmpty String accountId, @QueryParam("env") @DefaultValue("") String env) {
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(infraDownloadService.getDownloadUrlForWatcher(version, env, accountId));
    }
  }

  @GET
  @Path("default/delegate/{version}")
  @Produces(MediaType.APPLICATION_JSON)
  public RestResponse<String> getDelegateDownloadUrlFromDefaultAuth(@PathParam("version") String version,
      @QueryParam("accountId") @NotEmpty String accountId, @QueryParam("env") @DefaultValue("") String env) {
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(infraDownloadService.getDownloadUrlForDelegate(version, env, accountId));
    }
  }

  @GET
  @Path("delegate-auth/delegate/logging-token")
  @Produces(MediaType.APPLICATION_JSON)
  @DelegateAuth
  public RestResponse<AccessTokenBean> getDelegateLoggingTokenFromDelegate(
      @QueryParam("accountId") @NotEmpty String accountId) {
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(infraDownloadService.getStackdriverLoggingToken());
    }
  }
}
