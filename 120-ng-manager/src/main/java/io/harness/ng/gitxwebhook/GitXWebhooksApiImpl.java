/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitxwebhook;

import io.harness.spec.server.ng.v1.GitXWebhooksApi;
import io.harness.spec.server.ng.v1.model.CreateGitXWebhookRequest;
import io.harness.spec.server.ng.v1.model.DeleteGitXWebhookRequest;
import io.harness.spec.server.ng.v1.model.GetGitXWebhookRequest;
import io.harness.spec.server.ng.v1.model.ListGitXWebhookRequest;
import io.harness.spec.server.ng.v1.model.UpdateGitXWebhookRequest;

import com.google.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class GitXWebhooksApiImpl implements GitXWebhooksApi {
  @Override
  public Response createGitxWebhook(@Valid CreateGitXWebhookRequest body, String harnessAccount) {
    return null;
  }

  @Override
  public Response getGitxWebhook(
      String gitXWebhookIdentifier, @Valid GetGitXWebhookRequest body, String harnessAccount) {
    return null;
  }

  @Override
  public Response updateGitxWebhook(
      String gitXWebhookIdentifier, @Valid UpdateGitXWebhookRequest body, String harnessAccount) {
    return Response.ok().build();
  }

  @Override
  public Response deleteGitxWebhook(
      String gitXWebhookIdentifier, @Valid DeleteGitXWebhookRequest body, String harnessAccount) {
    return Response.ok().build();
  }

  @Override
  public Response listGitxWebhooks(@Valid ListGitXWebhookRequest body, String harnessAccount) {
    return Response.ok().build();
  }
}
