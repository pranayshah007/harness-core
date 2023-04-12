/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.releaseradar.resources;

import io.harness.releaseradar.dto.UserSubscriptionDTO;
import io.harness.releaseradar.entities.UserSubscription;
import io.harness.releaseradar.util.SlackWebhookEncryptionUtil;
import io.harness.repositories.UserSubscriptionRepository;
import io.harness.security.annotations.PublicApi;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

@Api("subscriptions")
@Path("/subscriptions")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@PublicApi
@Slf4j
public class SubscriptionResource {
  @Inject private UserSubscriptionRepository repository;

  @POST
  @Path("/subscribe")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  public Response subscribe(UserSubscriptionDTO request) throws Exception {
    repository.save(UserSubscription.builder()
                        .slackUserId(request.getSlackUserId())
                        .email(request.getEmail())
                        .filter(request.getFilter())
                        .slackWebhookUrlEncrypted(SlackWebhookEncryptionUtil.encrypt(request.getSlackWebhookURL()))
                        .build());
    return Response.ok("Subscription successful!").build();
  }
}
