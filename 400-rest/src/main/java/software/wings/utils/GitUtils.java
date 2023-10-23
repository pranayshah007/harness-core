/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils;

import static io.harness.validation.Validator.notNullCheck;

import io.harness.exception.InvalidRequestException;

import software.wings.beans.GitConfig;
import software.wings.beans.SettingAttribute;
import software.wings.service.impl.GitConfigHelperService;
import software.wings.service.intfc.SettingsService;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class GitUtils {
  @Inject private GitConfigHelperService gitConfigHelperService;
  @Inject private SettingsService settingsService;
  public GitConfig getGitConfig(String sourceRepoSettingId) {
    SettingAttribute gitSettingAttribute = settingsService.get(sourceRepoSettingId);
    notNullCheck("Git ConnectorDisconnectHandler not found", gitSettingAttribute);
    if (!(gitSettingAttribute.getValue() instanceof GitConfig)) {
      throw new InvalidRequestException("Invalid Git Repo");
    }

    GitConfig gitConfig = (GitConfig) gitSettingAttribute.getValue();
    gitConfigHelperService.setSshKeySettingAttributeIfNeeded(gitConfig);
    return gitConfig;
  }
}
