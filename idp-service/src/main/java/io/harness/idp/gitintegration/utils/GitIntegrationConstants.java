/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.gitintegration.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.IDP)
public class GitIntegrationConstants {
  public static final String ACCOUNT_SCOPED = "account.";
  public static final String GITHUB_CONNECTOR_TYPE = "Github";
  public static final String GITHUB_ENTERPRISE_URL_PREFIX = "ghe.";
  public static final String USERNAME_TOKEN_AUTH_TYPE = "UsernameToken";
  public static final String USERNAME_PASSWORD_AUTH_TYPE = "UsernamePassword";
  public static final String GITHUB_APP_CONNECTOR_TYPE = "GithubApp";
  public static final String GITLAB_CONNECTOR_TYPE = "Gitlab";
  public static final String BITBUCKET_CONNECTOR_TYPE = "Bitbucket";
  public static final String BITBUCKET_API_ACCESS_TYPE = "UsernameToken";
  public static final String AZURE_REPO_CONNECTOR_TYPE = "AzureRepo";
  public static final String AZURE_HOST = "dev.azure.com";
  public static final String CATALOG_INFRA_CONNECTOR_TYPE_DIRECT = "DIRECT";
  public static final String CATALOG_INFRA_CONNECTOR_TYPE_PROXY = "PROXY";
  public static final String HARNESS_ENTITIES_IMPORT_COMMIT_MESSAGE = "Importing Harness Entities to IDP";
  public static final String HOST_FOR_BITBUCKET_CLOUD = "bitbucket.org";
}
