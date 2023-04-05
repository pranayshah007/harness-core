/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.List;

@OwnedBy(HarnessTeam.IDP)
public class Constants {
  private Constants() {}

  public static final String IDP_PREFIX = "idp_";
  public static final String IDP_RESOURCE_TYPE = "IDP_SETTINGS";
  public static final String IDP_PERMISSION = "idp_idpsettings_manage";
  public static final List<String> pluginIds =
      List.of("github-pull-requests-board", "pager-duty", "snyk-security", "harness-ci-cd");
  public static final String GITHUB_TOKEN = "HARNESS_GITHUB_TOKEN";
  public static final String GITHUB_APP_ID = "HARNESS_GITHUB_APP_APPLICATION_ID";
  public static final String GITHUB_APP_PRIVATE_KEY_REF = "HARNESS_GITHUB_APP_PRIVATE_KEY_REF";
  public static final String GITLAB_TOKEN = "HARNESS_GITLAB_TOKEN";
  public static final String BITBUCKET_TOKEN = "HARNESS_BITBUCKET_TOKEN";
  public static final String AZURE_REPO_TOKEN = "HARNESS_AZURE_REPO_TOKEN";
}
