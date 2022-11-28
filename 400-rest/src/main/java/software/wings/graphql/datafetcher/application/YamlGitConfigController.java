/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.application;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;

import software.wings.graphql.schema.type.QLGitSyncConfig.QLGitSyncConfigBuilder;
import software.wings.yaml.gitSync.beans.YamlGitConfig;

import lombok.experimental.UtilityClass;

@UtilityClass
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class YamlGitConfigController {
  public static QLGitSyncConfigBuilder populateQLGitConfig(
      YamlGitConfig yamlGitConfig, QLGitSyncConfigBuilder builder) {
    return builder.gitConnectorId(yamlGitConfig.getGitConnectorId())
        .branch(yamlGitConfig.getBranchName())
        .repositoryName(yamlGitConfig.getRepositoryName())
        .syncEnabled(yamlGitConfig.isEnabled());
  }
}
