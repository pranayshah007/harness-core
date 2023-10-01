/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.asg;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.git.GitFetchFilesConfig;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(CDP)
@RecasterAlias("io.harness.cdng.aws.asg.AsgManifestFetchData")
public class AsgManifestFetchData {
  Map<String, List<String>> harnessFetchedManifestContentMap;
  List<GitFetchFilesConfig> gitFetchFilesConfigs;
  Map<String, List<String>> gitFetchedManifestOutcomeIdentifiersMap;

  public GitFetchFilesConfig getNextGitFetchFilesConfig() {
    Set<String> outcomeIdentifiers = gitFetchedManifestOutcomeIdentifiersMap.keySet();
    Optional<GitFetchFilesConfig> gitFetchFilesConfig =
        gitFetchFilesConfigs.stream().filter(o -> !outcomeIdentifiers.contains(o.getIdentifier())).findFirst();

    if (gitFetchFilesConfig.isEmpty()) {
      return null;
    }

    return gitFetchFilesConfig.get();
  }
}
