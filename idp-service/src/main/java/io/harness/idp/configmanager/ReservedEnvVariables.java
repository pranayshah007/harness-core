/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager;

import io.harness.idp.common.Constants;

import java.util.ArrayList;
import java.util.List;

public class ReservedEnvVariables {
  public static final List<String> reservedEnvVariables = new ArrayList<>(List.of(Constants.GITHUB_TOKEN,
      Constants.GITHUB_APP_ID, Constants.GITHUB_APP_PRIVATE_KEY_REF, Constants.GITLAB_TOKEN, Constants.BITBUCKET_TOKEN,
      Constants.AZURE_REPO_TOKEN, Constants.BACKEND_SECRET, Constants.PROXY_ENV_NAME));
}
