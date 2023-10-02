/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
public class NGConstants {
  public static final String SECRET_MANAGER_KEY = "secretManager";
  public static final String FILE_KEY = "file";
  public static final String FILE_METADATA_KEY = "fileMetadata";
  public static final String HARNESS_SECRET_MANAGER_IDENTIFIER = "harnessSecretManager";
  public static final String DEFAULT_ORG_IDENTIFIER = "default";
  public static final String DEFAULT_PROJECT_IDENTIFIER = "default_project";
  public static final String DEFAULT_PROJECT_NAME = "Default Project";
  public static final String DEPRECATED_ALL_RESOURCES_RESOURCE_GROUP_IDENTIFIER = "_all_resources";
  public static final String ALL_RESOURCES_INCLUDING_CHILD_SCOPES_RESOURCE_GROUP_IDENTIFIER =
      "_all_resources_including_child_scopes";
  public static final String ALL_RESOURCES_INCLUDING_CHILD_SCOPES_RESOURCE_GROUP_NAME =
      "All Resources Including Child Scopes";
  public static final String DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER = "_all_account_level_resources";
  public static final String DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER = "_all_organization_level_resources";
  public static final String DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER = "_all_project_level_resources";
  public static final String ACCOUNT_ADMIN_ROLE = "_account_admin";
  public static final String ACCOUNT_ADMIN_ROLE_NAME = "Account Admin";
  public static final String ACCOUNT_VIEWER_ROLE = "_account_viewer";
  public static final String ACCOUNT_BASIC_ROLE = "_account_basic";
  public static final String ORGANIZATION_ADMIN_ROLE = "_organization_admin";
  public static final String ORGANIZATION_VIEWER_ROLE = "_organization_viewer";
  public static final String ORGANIZATION_BASIC_ROLE = "_organization_basic";
  public static final String PROJECT_ADMIN_ROLE = "_project_admin";
  public static final String PROJECT_VIEWER_ROLE = "_project_viewer";
  public static final String LICENSE_PERMISSION = "core_license_view";
  public static final String ORGANIZATION_VIEW_PERMISSION = "core_organization_view";
  public static final String PROJECT_VIEW_PERMISSION = "core_project_view";
  public static final String LICENSE_RESOURCE = "LICENSE";
  public static final String ORGANIZATION_RESOURCE = "ORGANIZATION";
  public static final String PROJECT_RESOURCE = "PROJECT";

  public static final String PROJECT_BASIC_ROLE = "_project_basic";
  public static final String ENTITY_REFERENCE_LOG_PREFIX = "ENTITY_REFERENCE :";
  public static final String HARNESS_BLUE = "#0063F7";
  public static final String STRING_CONNECTOR = ":";
  public static final String CONNECTOR_STRING = "connector [%s] in account [%s], org [%s], project [%s]";
  public static final String SETTINGS_STRING = "settings [%s] in account [%s], org [%s], project [%s]";
  public static final String CONNECTOR_HEARTBEAT_LOG_PREFIX = "Connector Heartbeat :";
  public static final String CONNECTOR_TYPE_NAME = "connectorType";
  public static final String REFERRED_ENTITY_FQN = "referredEntityFQN";
  public static final String REFERRED_ENTITY_TYPE = "referredEntityType";
  public static final String REFERRED_ENTITY_FQN1 = "referredEntityFQN1";
  public static final String REFERRED_ENTITY_FQN2 = "referredEntityFQN2";
  public static final String REFERRED_BY_ENTITY_FQN = "referredByEntityFQN";
  public static final String REFERRED_BY_ENTITY_TYPE = "referredByEntityType";

  public static final String BRANCH = "branch";
  public static final String REPO = "repoIdentifier";
  public static final String DEFAULT_ACCOUNT_LEVEL_USER_GROUP_IDENTIFIER = "_account_all_users";
  public static final String DEFAULT_ORGANIZATION_LEVEL_USER_GROUP_IDENTIFIER = "_organization_all_users";
  public static final String DEFAULT_PROJECT_LEVEL_USER_GROUP_IDENTIFIER = "_project_all_users";
  public static final String X_API_KEY = "X-Api-Key";

  /* SCIM related Constants*/
  public static final String GIVEN_NAME = "givenName";
  public static final String FAMILY_NAME = "familyName";
  public static final String DISPLAY_NAME = "displayName";
  public static final String FORMATTED_NAME = "formatted";
  public static final String RESOURCE_TYPE = "resourceType";
  public static final String CREATED = "created";
  public static final String LAST_MODIFIED = "lastModified";
  public static final String VERSION = "version";
  public static final String LOCATION = "location";
  public static final String VALUE = "value";
  public static final String PRIMARY = "primary";
  public static final String EMAILS = "emails";
}
