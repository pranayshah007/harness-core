/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import static io.harness.cvng.core.beans.params.ProjectParams.fromProjectPathParams;
import static io.harness.cvng.core.services.CVNextGenConstants.Webhook_NG_PROJECT_PATH;

import io.harness.cvng.core.beans.CustomChangeWebhookPayload;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.ProjectPathParams;
import io.harness.cvng.core.services.api.WebhookService;
import io.harness.cvng.utils.SRMServiceAuthIfHasApiKey;
import io.harness.security.annotations.PublicApi;

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
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import retrofit2.http.Body;

@Api(Webhook_NG_PROJECT_PATH)
@Path(Webhook_NG_PROJECT_PATH)
@Produces("application/json")
public class WebhookNgResource {
  @Inject private WebhookService webhookService;

  @POST
  @Path("custom-change")
  @Timed
  @PublicApi
  @ExceptionMetered
  @SRMServiceAuthIfHasApiKey
  @ApiOperation(
      value = "accepts a custom change webhook request", nickname = "handleCustomChangeSourceWebhookNgRequest")
  public void
  handleCustomChangeWebhookNgRequest(@Valid @BeanParam ProjectPathParams projectPathParams,
      @NotNull @QueryParam("monitoredServiceIdentifier") String monitoredServiceIdentifier,
      @NotNull @QueryParam("changeSourceIdentifier") String changeSourceIdentifier,
      @Body @Valid CustomChangeWebhookPayload customChangeWebhookPayload, @Context HttpHeaders httpHeaders) {
    ProjectParams projectParams = fromProjectPathParams(projectPathParams);
    webhookService.checkAuthorization(projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
        projectParams.getProjectIdentifier(), monitoredServiceIdentifier, httpHeaders);
    webhookService.handleCustomChangeWebhook(
        projectParams, monitoredServiceIdentifier, changeSourceIdentifier, customChangeWebhookPayload);
  }
}
