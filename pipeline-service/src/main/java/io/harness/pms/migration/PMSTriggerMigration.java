/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.migration;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.BITBUCKET;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.GITHUB;
import static io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubTriggerEvent.PULL_REQUEST;

import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.NGMigration;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity.NGTriggerEntityKeys;
import io.harness.ngtriggers.beans.source.NGTriggerSourceV2;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.beans.source.webhook.v2.WebhookTriggerConfigV2;
import io.harness.ngtriggers.beans.source.webhook.v2.bitbucket.BitbucketSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.bitbucket.event.BitbucketTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.github.GithubSpec;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.utils.RetryUtils;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.time.Duration;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PIPELINE)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
public class PMSTriggerMigration implements NGMigration {
  @Inject private final MongoTemplate mongoTemplate;
  @Inject private NGTriggerElementMapper ngTriggerElementMapper;
  private final RetryPolicy<Object> updateRetryPolicy = RetryUtils.getRetryPolicy(
      "[Retrying]: Failed updating Trigger; attempt: {}", "[Failed]: Failed updating Trigger; attempt: {}",
      ImmutableList.of(OptimisticLockingFailureException.class, DuplicateKeyException.class), Duration.ofSeconds(1), 3,
      log);

  @Override
  public void migrate() {
    int pageIdx = 0;
    int pageSize = 20;
    int maxUsers = 10000;
    int maxPages = maxUsers / pageSize;

    while (pageIdx < maxPages) {
      Pageable pageable = PageRequest.of(pageIdx, pageSize);
      Query query = new Query().with(pageable);
      List<NGTriggerEntity> triggers = mongoTemplate.find(query, NGTriggerEntity.class);
      if (triggers.isEmpty()) {
        break;
      }
      for (NGTriggerEntity ngTriggerEntity : triggers) {
        try {
          String triggerYaml = ngTriggerEntity.getYaml();
          if (ngTriggerEntity.getType() == NGTriggerType.WEBHOOK) {
            NGTriggerConfigV2 ngTriggerConfig = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerEntity.getYaml());
            NGTriggerSourceV2 source = ngTriggerConfig.getSource();
            WebhookTriggerConfigV2 webhookTriggerConfigV2 = (WebhookTriggerConfigV2) source.getSpec();
            if (updateYAML(webhookTriggerConfigV2)) {
              String updatedYaml = triggerYaml.replace("type: branch", "type: PR")
                                       .replace("branch: <+trigger.branch>", "number: <+trigger.prNumber>");

              Update update = new Update();
              update.set(NGTriggerEntityKeys.yaml, updatedYaml);

              Query query1 = new Query(Criteria.where(NGTriggerEntityKeys.uuid).is(ngTriggerEntity.getUuid()));
              Failsafe.with(updateRetryPolicy)
                  .get(()
                           -> mongoTemplate.findAndModify(
                               query1, update, new FindAndModifyOptions().returnNew(true), NGTriggerEntity.class));
            }
          }
        } catch (Exception ex) {
          log.error("Trigger migration failed");
        }
      }

      pageIdx++;
      if (pageIdx % (maxPages / 5) == 0) {
        log.info("NGTrigger migration in process...");
      }
    }
  }
  private boolean updateYAML(WebhookTriggerConfigV2 webhookTriggerConfigV2) {
    if (webhookTriggerConfigV2.getType() == GITHUB) {
      GithubSpec githubSpec = (GithubSpec) webhookTriggerConfigV2.getSpec();
      return githubSpec.getType() == PULL_REQUEST;
    }

    if (webhookTriggerConfigV2.getType() == BITBUCKET) {
      BitbucketSpec bitbucketSpec = (BitbucketSpec) webhookTriggerConfigV2.getSpec();
      return bitbucketSpec.getType() == BitbucketTriggerEvent.PULL_REQUEST;
    }

    return false;
  }
}
