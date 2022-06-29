/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.exception.InvalidRequestException;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.DelegateAuth2;

import software.wings.service.intfc.AccountService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.util.List;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.hibernate.validator.constraints.NotEmpty;

@Api("version")
@Path("/version")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DelegateVersionInfoResource {
  private final AccountService accountService;

  @GET
  @Path("/delegate")
  @Timed
  @ExceptionMetered
  @DelegateAuth2
  public RestResponse<List<String>> getDelegateVersion(@QueryParam("accountId") @NotEmpty String accountId) {
    return new RestResponse<>(accountService.getDelegateConfiguration(accountId).getDelegateVersions());
  }

  @GET
  @Path("/watcher")
  @Timed
  @ExceptionMetered
  @DelegateAuth2
  public String getWatcherVersion(@QueryParam("accountId") @NotEmpty String accountId) {
    List<String> watcherVersion = accountService.getWatcherVersion(accountId);
    if (isNotEmpty(watcherVersion)) {
      return watcherVersion.get(0);
    }
    throw new InvalidRequestException("Unable to get watcher version from ring");
  }
}
