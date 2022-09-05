/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.helpers;

import static java.lang.String.format;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.remote.client.RestClientUtils;
import io.harness.webhook.WebhookConfigProvider;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.SPG)
public class UrlHelper {
  private static final String NG_UI_PATH_PREFIX = "ng/";
  @Inject private AccountClient accountClient;
  @Inject private WebhookConfigProvider webhookConfigProvider;

  public String getBaseUrl(String accountIdentifier) {
    return RestClientUtils.getResponse(accountClient.getBaseUrl(accountIdentifier));
  }

  public String buildApiExecutionUrl(String uuid, String accountIdentifier) {
    return format("%swebhook/triggerExecutionDetails/%s?accountIdentifier=%s",
        webhookConfigProvider.getCustomApiBaseUrl(), uuid, accountIdentifier);
  }

  public String buildUiUrl(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    return format("%s%s#/account/%s/cd/orgs/%s/projects/%s/deployments?pipelineIdentifier=%s&page=0",
        getBaseUrl(accountIdentifier), NG_UI_PATH_PREFIX, accountIdentifier, orgIdentifier, projectIdentifier,
        pipelineIdentifier);
  }

  public String buildUiSetupUrl(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    return format("%s%s#/account/%s/cd/orgs/%s/projects/%s/pipelines/%s/pipeline-studio/",
        getBaseUrl(accountIdentifier), NG_UI_PATH_PREFIX, accountIdentifier, orgIdentifier, projectIdentifier,
        pipelineIdentifier);
  }
}
