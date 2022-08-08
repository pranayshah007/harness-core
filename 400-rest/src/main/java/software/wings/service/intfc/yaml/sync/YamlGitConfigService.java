/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.yaml.sync;

import software.wings.service.intfc.ownership.OwnedByAccount;
import software.wings.service.intfc.ownership.OwnedByApplication;
import software.wings.yaml.gitSync.YamlGitConfig;

import java.util.List;
import java.util.Set;

public interface YamlGitConfigService extends OwnedByAccount, OwnedByApplication {
  List<YamlGitConfig> getYamlGitConfigAccessibleToUserWithEntityName(String accountId);

  Set<String> getAppIdsForYamlGitConfig(List<String> yamlGitConfigIds);

  YamlGitConfig getYamlGitConfigFromAppId(String appId, String accountId);
}
