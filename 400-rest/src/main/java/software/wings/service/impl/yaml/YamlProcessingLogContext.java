/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml;

import io.harness.data.structure.NullSafeImmutableMap;
import io.harness.data.structure.NullSafeImmutableMap.NullSafeBuilder;
import io.harness.logging.AutoLogContext;

import com.google.common.collect.ImmutableMap;

public class YamlProcessingLogContext extends AutoLogContext {
  public static final String GIT_CONNECTOR_ID = "gitConnectorId";
  public static final String BRANCH_NAME = "branchName";
  public static final String WEBHOOK_TOKEN = "webhookToken";
  public static final String CHANGESET_ID = "changeSetId";
  public static final String REPO_NAME = "repoName";
  public static final String COMMIT_ID = "commitId";
  public static final String CHANGESET_QUEUE_KEY = "changeSetQueueKey";
  public static final String FILE_PATH = "filePath";
  public YamlProcessingLogContext(ImmutableMap<String, String> context, OverrideBehavior behavior) {
    super(context, behavior);
  }

  public static Builder builder() {
    return new Builder();
  }
  public static class Builder {
    private final NullSafeBuilder<String, String> nullSafeBuilder = NullSafeImmutableMap.builder();

    public Builder gitConnectorId(String gitConnectorId) {
      nullSafeBuilder.putIfNotNull(GIT_CONNECTOR_ID, gitConnectorId);
      return this;
    }

    public Builder branchName(String branchName) {
      nullSafeBuilder.putIfNotNull(BRANCH_NAME, branchName);
      return this;
    }

    public Builder webhookToken(String webhookToken) {
      nullSafeBuilder.putIfNotNull(WEBHOOK_TOKEN, webhookToken);
      return this;
    }

    public Builder changeSetId(String changeSetID) {
      nullSafeBuilder.putIfNotNull(CHANGESET_ID, changeSetID);
      return this;
    }

    public Builder repoName(String repoName) {
      nullSafeBuilder.putIfNotNull(REPO_NAME, repoName);
      return this;
    }

    public Builder commitId(String commitId) {
      nullSafeBuilder.putIfNotNull(COMMIT_ID, commitId);
      return this;
    }
    public Builder changeSetQueueKey(String changeSetQueueKey) {
      nullSafeBuilder.putIfNotNull(CHANGESET_QUEUE_KEY, changeSetQueueKey);
      return this;
    }

    public Builder filePath(String filePath) {
      nullSafeBuilder.putIfNotNull(FILE_PATH, filePath);
      return this;
    }

    public YamlProcessingLogContext build(OverrideBehavior behavior) {
      return new YamlProcessingLogContext(nullSafeBuilder.build(), behavior);
    }
  }
}
