/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.trigger;

import software.wings.beans.trigger.WebHookTriggerCondition;
import software.wings.beans.trigger.WebhookSource;

public class WebhookFactory {
  private static final WebhookHandler githubHandler = new GithubWebhookHandlerImpl();
  private static final WebhookHandler gitlabHandler = new GitlabWebhookHandlerImpl();
  private static final WebhookHandler azureHandler = new AzureWebhookHandlerImpl();
  private static final WebhookHandler bitbucketHandler = new BitbucketWebhookHandlerImpl();
  private static final WebhookHandler defaultHandler = new DefaultWebhookHandlerImpl();

  public static WebhookHandler getHandler(WebHookTriggerCondition triggerCondition) {
    if (triggerCondition.getWebhookSource() == WebhookSource.GITHUB) {
      return githubHandler;
    }

    if (triggerCondition.getWebhookSource() == WebhookSource.BITBUCKET) {
      return bitbucketHandler;
    }

    if (triggerCondition.getWebhookSource() == WebhookSource.GITLAB) {
      return gitlabHandler;
    }

    if (triggerCondition.getWebhookSource() == WebhookSource.AZURE_DEVOPS) {
      return azureHandler;
    }
    return defaultHandler;
  }
}
