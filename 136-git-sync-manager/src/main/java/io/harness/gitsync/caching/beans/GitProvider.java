/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.caching.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidArgumentsException;

import java.util.HashMap;
import java.util.Map;

@OwnedBy(HarnessTeam.PIPELINE)
public enum GitProvider {
  UNKNOWN("UNKNOWN"),
  GITHUB_SAAS("GITHUB_SAAS"),
  BITBUCKET_SAAS("BITBUCKET_SAAS"),
  BITBUCKET_ON_PREM("BITBUCKET_ON_PREM"),
  AZURE_SAAS("AZURE_SAAS"),
  GITLAB_SAAS("GITLAB_SAAS");

  private static final Map<String, GitProvider> map = new HashMap<>(values().length, 1);
  static {
    for (GitProvider c : values()) {
      map.put(c.name, c);
    }
  }

  private final String name;

  GitProvider(String name) {
    this.name = name;
  }

  public static GitProvider getByName(String name) {
    GitProvider gitProvider = map.get(name);
    if (gitProvider == null) {
      throw new InvalidArgumentsException("Invalid git provider name as input : " + name);
    }
    return gitProvider;
  }
}
