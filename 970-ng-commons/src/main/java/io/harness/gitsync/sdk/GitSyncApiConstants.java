/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.sdk;
import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@OwnedBy(DX)
@UtilityClass
public class GitSyncApiConstants {
  public static final String BRANCH_KEY = "branch";
  public static final String REPO_IDENTIFIER_KEY = "repoIdentifier";
  public static final String FILE_PATH_KEY = "filePath";
  public static final String COMMIT_MSG_KEY = "commitMsg";
  public static final String CREATE_PR_KEY = "createPr";
  public static final String LAST_OBJECT_ID_KEY = "lastObjectId";
  public static final String RESOLVED_CONFLICT_COMMIT_ID = "resolvedConflictCommitId";
  public static final String FOLDER_PATH = "rootFolder";
  public static final String NEW_BRANCH = "isNewBranch";
  public static final String FORCE_IMPORT = "isForceImport";

  public static final String TARGET_BRANCH_FOR_PR = "targetBranchForPr";
  public static final String DEFAULT_FROM_OTHER_REPO = "getDefaultFromOtherRepo";
  public static final String PARENT_ENTITY_REPO_URL = "parentEntityRepoURL";
  public static final String PARENT_ENTITY_CONNECTOR_REF = "parentEntityConnectorRef";
  public static final String PARENT_ENTITY_REPO_NAME = "parentEntityRepoName";
  public static final String PARENT_ENTITY_ACCOUNT_IDENTIFIER = "parentEntityAccountIdentifier";
  public static final String PARENT_ENTITY_ORG_IDENTIFIER = "parentEntityOrgIdentifier";
  public static final String PARENT_ENTITY_PROJECT_IDENTIFIER = "parentEntityProjectIdentifier";
  public static final String BASE_BRANCH = "baseBranch";
  public static final String PR_TITLE = "prTitle";
  public static final String ENTITY_TYPE = "entityType";
  public static final String SYNC_STATUS = "syncStatus";
  public static final String CONNECTOR_REF = "connectorRef";
  public static final String STORE_TYPE = "storeType";
  public static final String REPO_NAME = "repoName";
  public static final String LAST_COMMIT_ID = "lastCommitId";

  public static final String BRANCH_PARAM_MESSAGE = "Name of the branch.";
  public static final String FILEPATH_PARAM_MESSAGE = "File Path of the Entity.";
  public static final String REPOID_PARAM_MESSAGE = "Git Sync Config Id.";
  public static final String REPO_URL_PARAM_MESSAGE = "URL of the repository.";
  public static final String REPO_NAME_PARAM_MESSAGE = "Name of the repository.";
  public static final String FOLDER_PATH_PARAM_MESSAGE = "Path to the root folder of the Entity.";
  public static final String COMMIT_MESSAGE_PARAM_MESSAGE = "Commit Message to use for the merge commit.";
  public static final String DEFAULT_BRANCH_PARAM_MESSAGE = "Name of the default branch.";
  public static final String ENTITY_TYPE_PARAM_MESSAGE = "Entity Type.";
  public static final String SYNC_STATUS_PARAM_MESSAGE =
      "Sync Status of the Entity that may be QUEUED, SUCCESS or FAILED.";
  public static final String SEARCH_TERM_PARAM_MESSAGE = "Search Term.";
  public static final String REPO_NAME_SEARCH_TERM_PARAM_MESSAGE = "Repo Name Search Term.";
  public static final String USER_NAME_SEARCH_TERM_PARAM_MESSAGE = "User Name Search Term.";
  public static final String BRANCH_NAME_SEARCH_TERM_PARAM_MESSAGE = "Branch Name Search Term.";
  public static final String TRIM_LEADING_TRAILING_SPACES = "Any leading/trailing spaces will be removed.";
  public static final String ENTITY_GIT_URL_DESCRIPTION = "The url of the file in git";

  public static final String GIT_CONNECTOR_REF_PARAM_MESSAGE =
      "Identifier of Connector needed for CRUD operations on the respective Entity";
  public static final String STORE_TYPE_PARAM_MESSAGE = "Tells whether the Entity is to be saved on Git or not";
  public static final String STORE_TYPE_RESPONSE_PARAM_MESSAGE = "Tells whether the Entity is saved on Git or not";

  public static final String MOVE_CONFIG_PARAM_MESSAGE =
      "Tells weather the entity has to be moved from inline to remote or remote to inline";

  public static final String MOVE_CONFIG_KEY = "moveConfigType";

  public static final String GIT_CACHING_METADATA = "Gives us the Git caching metadata information.";
  public static final String APPLY_GITX_REPO_ALLOW_LIST_FILTER_PARAM_MESSAGE =
      "Filters repos based on allowed repos for Git Experience in default settings";
}
