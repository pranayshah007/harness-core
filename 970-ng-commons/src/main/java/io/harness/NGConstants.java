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
  public static final String DEFAULT_RESOURCE_GROUP_IDENTIFIER = "_all_resources";
  public static final String DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER = "_all_account_level_resources";
  public static final String DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER = "_all_organization_level_resources";
  public static final String DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER = "_all_project_level_resources";
  public static final String ENTITY_REFERENCE_LOG_PREFIX = "ENTITY_REFERENCE :";
  public static final String HARNESS_BLUE = "#0063F7";
  public static final String STRING_CONNECTOR = ":";
  public static final String CONNECTOR_STRING = "connector [%s] in account [%s], org [%s], project [%s]";
  public static final String CONNECTOR_HEARTBEAT_LOG_PREFIX = "Connector Heartbeat :";
  public static final String REFERRED_ENTITY_FQN = "referredEntityFQN";
  public static final String REFERRED_ENTITY_TYPE = "referredEntityType";
  public static final String REFERRED_BY_ENTITY_FQN = "referredByEntityFQN";
  public static final String REFERRED_BY_ENTITY_TYPE = "referredByEntityType";

  public static final String BRANCH = "branch";
  public static final String REPO = "repoIdentifier";
}
