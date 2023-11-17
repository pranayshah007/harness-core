/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitxwebhook;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.spec.server.ng.v1.OrgGitxWebhooksApi;
import io.harness.spec.server.ng.v1.model.CreateGitXWebhookRequest;
import io.harness.spec.server.ng.v1.model.UpdateGitXWebhookRequest;

import com.google.inject.Inject;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class OrgGitXWebhooksApiImpl implements OrgGitxWebhooksApi {
  @Override
  public Response createOrgGitxWebhook(String org, @Valid CreateGitXWebhookRequest body, String harnessAccount) {
    return null;
  }

  @Override
  public Response getOrgGitxWebhook(String org, String gitxWebhook, String harnessAccount) {
    return null;
  }

  @Override
  public Response updateOrgGitxWebhook(
      String org, String gitxWebhook, @Valid UpdateGitXWebhookRequest body, String harnessAccount) {
    return null;
  }

  @Override
  public Response deleteOrgGitxWebhook(String org, String gitxWebhook, String harnessAccount) {
    return null;
  }

  @Override
  public Response listOrgGitxWebhooks(
      String org, String harnessAccount, Integer page, @Max(1000L) Integer limit, String webhookIdentifier) {
    return null;
  }

  @Override
  public Response listOrgGitxWebhookEvents(String org, String harnessAccount, Integer page, @Max(1000L) Integer limit,
      String webhookIdentifier, Long eventStartTime, Long eventEndTime, String repoName, String filePath,
      String eventIdentifier, List<String> eventStatus) {
    return null;
  }
}
