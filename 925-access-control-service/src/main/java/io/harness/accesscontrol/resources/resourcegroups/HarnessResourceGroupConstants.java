package io.harness.accesscontrol.resources.resourcegroups;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
public class HarnessResourceGroupConstants {
  public static final String DEFAULT_RESOURCE_GROUP_IDENTIFIER = "_all_resources";
  public static final String DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER = "_all_account_level_resources";
  public static final String DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER = "_all_organization_level_resources";
  public static final String DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER = "_all_project_level_resources";
}
