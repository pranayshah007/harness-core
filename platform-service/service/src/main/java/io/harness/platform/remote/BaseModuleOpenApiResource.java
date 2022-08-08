/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.platform.remote;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.jaxrs2.integration.JaxrsOpenApiContextBuilder;
import io.swagger.v3.jaxrs2.integration.ServletConfigContextUtils;
import io.swagger.v3.jaxrs2.integration.resources.BaseOpenApiResource;
import io.swagger.v3.oas.integration.api.OpenApiContext;
import io.swagger.v3.oas.models.OpenAPI;
import javax.servlet.ServletConfig;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import lombok.Getter;
import lombok.Setter;

@OwnedBy(HarnessTeam.PL)
@Getter
@Setter
public abstract class BaseModuleOpenApiResource extends BaseOpenApiResource {
  protected String module;
  public Response getOpenApi(ServletConfig config) throws Exception {
    String ctxId = ServletConfigContextUtils.getContextIdFromServletConfig(config) + module;
    OpenApiContext ctx = new JaxrsOpenApiContextBuilder()
                             .openApiConfiguration(this.openApiConfiguration)
                             .ctxId(ctxId)
                             .buildContext(true);
    OpenAPI oas = ctx.read();
    boolean pretty = false;
    if (ctx.getOpenApiConfiguration() != null && Boolean.TRUE.equals(ctx.getOpenApiConfiguration().isPrettyPrint())) {
      pretty = true;
    }
    if (oas == null) {
      return Response.status(404).build();
    } else {
      return Response.status(Response.Status.OK)
          .entity(pretty ? Json.pretty(oas) : Json.mapper().writeValueAsString(oas))
          .type(MediaType.APPLICATION_JSON_TYPE)
          .build();
    }
  }
}
