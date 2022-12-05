/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import io.harness.cvng.core.beans.PagerDutyWebhookEvent;
import io.harness.cvng.core.services.api.WebhookService;
import io.harness.security.annotations.PublicApi;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

@Api("webhook")
@Path("webhook")
@Produces("application/json")
public class WebhookResource {
  @Inject private WebhookService webhookService;

  @POST
  @Path("pagerduty/{token}")
  @Timed
  @PublicApi
  @ExceptionMetered
  @ApiOperation(value = "accepts a webhook request", nickname = "handleWebhookRequest")
  public void handleWebhookRequest(@PathParam("token") String token, PagerDutyWebhookEvent payload) {
    webhookService.handlePagerDutyWebhook(token, payload);
  }
}
