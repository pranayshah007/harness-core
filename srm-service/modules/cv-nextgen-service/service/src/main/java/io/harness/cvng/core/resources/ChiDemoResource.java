/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import io.harness.annotations.ExposeInternalException;
import io.harness.cvng.core.beans.change.DemoChangeEventDTO;
import io.harness.cvng.core.beans.params.ProjectScopedProjectParams;
import io.harness.cvng.core.services.api.demo.ChiDemoService;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import retrofit2.http.Body;

@Api("demo")
@Path("/demo")
@Produces("application/json")
@ExposeInternalException
public class ChiDemoResource {
  @Inject private ChiDemoService chiDemoService;
  @POST
  @Timed
  @ExceptionMetered
  @NextGenManagerAuth
  @Path("register-event")
  @ApiOperation(value = "register a ChangeEvent", nickname = "registerChangeEventDemo")
  public RestResponse<Void> register(@BeanParam ProjectScopedProjectParams projectParams,
      @NotNull @Valid @Body DemoChangeEventDTO demoChangeEventDTO) {
    chiDemoService.registerDemoChangeEvent(projectParams.getProjectParams(), demoChangeEventDTO);
    return new RestResponse<>(null);
  }
}
