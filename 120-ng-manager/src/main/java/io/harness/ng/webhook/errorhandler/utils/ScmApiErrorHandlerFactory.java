/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.webhook.errorhandler.utils;

import static io.harness.annotations.dev.HarnessTeam.SPG;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.common.dtos.RepoProviders;
import io.harness.ng.webhook.constants.ScmApis;
import io.harness.ng.webhook.errorhandler.handlers.DefaultScmApiErrorHandler;
import io.harness.ng.webhook.errorhandler.handlers.ScmApiErrorHandler;
import io.harness.ng.webhook.errorhandler.handlers.azure.AzureUpsertWebhookScmApiErrorHandler;
import io.harness.ng.webhook.errorhandler.handlers.bitbucket.BitbucketUpsertWebhookScmApiErrorHandler;
import io.harness.ng.webhook.errorhandler.handlers.github.GithubUpsertWebhookScmApiErrorHandler;
import io.harness.ng.webhook.errorhandler.handlers.gitlab.GitlabUpsertWebhookScmApiErrorHandler;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@UtilityClass
@Slf4j
@OwnedBy(SPG)
public class ScmApiErrorHandlerFactory {
  private final Map<Pair<ScmApis, RepoProviders>, Class<? extends ScmApiErrorHandler>> scmApiErrorHandlerMap =
      ImmutableMap.<Pair<ScmApis, RepoProviders>, Class<? extends ScmApiErrorHandler>>builder()
          .put(Pair.of(ScmApis.UPSERT_WEBHOOK, RepoProviders.GITHUB), GithubUpsertWebhookScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.UPSERT_WEBHOOK, RepoProviders.BITBUCKET), BitbucketUpsertWebhookScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.UPSERT_WEBHOOK, RepoProviders.GITLAB), GitlabUpsertWebhookScmApiErrorHandler.class)
          .put(Pair.of(ScmApis.UPSERT_WEBHOOK, RepoProviders.AZURE), AzureUpsertWebhookScmApiErrorHandler.class)
          .build();

  public ScmApiErrorHandler getHandler(ScmApis scmApi, RepoProviders repoProvider) {
    try {
      return scmApiErrorHandlerMap.get(Pair.of(scmApi, repoProvider)).newInstance();
    } catch (Exception ex) {
      log.error(
          String.format("Error while getting handler for scmApi [%s] and repoProvider [%s]", scmApi, repoProvider), ex);
    }
    return new DefaultScmApiErrorHandler();
  }
}