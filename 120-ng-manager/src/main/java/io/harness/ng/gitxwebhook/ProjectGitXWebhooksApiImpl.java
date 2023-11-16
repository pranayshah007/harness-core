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
import io.harness.spec.server.ng.v1.ProjectGitxWebhooksApi;
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
public class ProjectGitXWebhooksApiImpl implements ProjectGitxWebhooksApi {
  @Override
  public Response createProjectGitxWebhook(
      String org, String project, @Valid CreateGitXWebhookRequest body, String harnessAccount) {
    return null;
  }

  @Override
  public Response deleteProjectGitxWebhook(String org, String project, String gitxWebhook, String harnessAccount) {
    return null;
  }

  @Override
  public Response getProjectGitxWebhook(String org, String project, String harnessAccount, Integer page,
      @Max(1000L) Integer limit, String webhookIdentifier) {
    return null;
  }

  @Override
  public Response getProjectGitxWebhook_1(String org, String project, String gitxWebhook, String harnessAccount) {
    return null;
  }

  @Override
  public Response listProjectGitxWebhookEvents(String org, String project, String harnessAccount, Integer page,
      @Max(1000L) Integer limit, String webhookIdentifier, Long eventStartTime, Long eventEndTime, String repoName,
      String filePath, String eventIdentifier, List<String> eventStatus) {
    return null;
  }

  @Override
  public Response updateProjectGitxWebhook(
      String org, String project, String gitxWebhook, @Valid UpdateGitXWebhookRequest body, String harnessAccount) {
    return null;
  }
}
