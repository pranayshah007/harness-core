/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.eventsframework.impl.redis;

import static io.harness.NGConstants.BRANCH;
import static io.harness.NGConstants.REPO;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.context.GlobalContextData;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.manage.GlobalContextManager;
import io.harness.redis.RedisConfig;

import io.github.resilience4j.retry.Retry;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;

@OwnedBy(PL)
@Slf4j
public class GitAwareRedisProducer extends RedisProducer {
  private Retry retry;

  public GitAwareRedisProducer(
      String topicName, @NotNull RedisConfig redisConfig, int maxTopicSize, String producerName) {
    super(topicName, redisConfig, maxTopicSize, producerName);
  }

  public GitAwareRedisProducer(String topicName, @NotNull RedissonClient redisConfig, int maxTopicSize,
      String producerName, String envNamespace) {
    super(topicName, redisConfig, maxTopicSize, producerName, envNamespace);
  }

  @Override
  protected void populateOtherProducerSpecificData(Map<String, String> redisData) {
    // Populating the git details
    super.populateOtherProducerSpecificData(redisData);
    GlobalContextData globalContextData = GlobalContextManager.get(GitSyncBranchContext.NG_GIT_SYNC_CONTEXT);
    if (globalContextData != null) {
      final GitEntityInfo gitBranchInfo = ((GitSyncBranchContext) globalContextData).getGitBranchInfo();
      if (gitBranchInfo == null) {
        return;
      }
      if (gitBranchInfo.getYamlGitConfigId() != null) {
        redisData.put(REPO, gitBranchInfo.getYamlGitConfigId());
      }
      if (gitBranchInfo.getBranch() != null) {
        redisData.put(BRANCH, gitBranchInfo.getBranch());
      }
    }
  }

  public static GitAwareRedisProducer of(
      String topicName, @NotNull RedisConfig redisConfig, int maxTopicLength, String producerName) {
    return new GitAwareRedisProducer(topicName, redisConfig, maxTopicLength, producerName);
  }

  public static RedisProducer of(String topicName, @NotNull RedissonClient redissonClient, int maxTopicSize,
      String producerName, String envNamespace) {
    return new GitAwareRedisProducer(topicName, redissonClient, maxTopicSize, producerName, envNamespace);
  }
}
