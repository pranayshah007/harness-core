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
public class NGCommonEntityConstants {
  public static final String ACCOUNT_KEY = "accountIdentifier";
  public static final String ORG_KEY = "orgIdentifier";
  public static final String ORGS_KEY = "orgIdentifiers";
  public static final String PROJECT_KEY = "projectIdentifier";
  public static final String VERSION_LABEL_KEY = "versionLabel";
  public static final String SERVICE_KEY = "serviceId";
  public static final String ENVIRONMENT_KEY = "envId";
  public static final String BUILDS_KEY = "buildIds";
  public static final String TIMESTAMP = "timestamp";
  public static final String IDENTIFIER_KEY = "identifier";
  public static final String GROUP_IDENTIFIER_KEY = "groupIdentifier";
  public static final String GROUP_IDENTIFIERS_KEY = "groupIdentifiers";
  public static final String NAME_KEY = "name";
  public static final String TAGS_KEY = "tags";
  public static final String DESCRIPTION_KEY = "description";
  public static final String DELETED_KEY = "deleted";
  public static final String PIPELINE_KEY = "pipelineIdentifier";
  public static final String INPUT_SET_IDENTIFIER_KEY = "inputSetIdentifier";
  public static final String ENVIRONMENT_IDENTIFIER_KEY = "environmentIdentifier";
  public static final String STATUS = "status";
  public static final String MONGODB_ID = "_id";
  public static final String CONNECTOR_IDENTIFIER_KEY = "connectorIdentifier";
  public static final String REPO_URL = "repoURL";
  public static final String REPO_NAME = "repoName";
  public static final String PLAN_KEY = "planExecutionId";
  public static final String TYPE_KEY = "type";
  public static final String TOKEN_KEY = "tokenId";
  public static final String REFERRED_ENTITY_TYPE = "referredEntityType";
  public static final String REFERRED_BY_ENTITY_TYPE = "referredByEntityType";
  public static final String MODULE_TYPE = "moduleType";
  public static final String ENTITY_TYPE = "entityType";
  public static final String SEARCH_TERM = "searchTerm";
  public static final String USER_ID = "userId";
  public static final String PAGE = "page";
  public static final String SIZE = "size";
  public static final String MASK_SECRETS = "maskSecrets";
  public static final String CONNECTOR_IDENTIFIER_REF = "connectorIdentifierRef";
  public static final String CONNECTOR_TYPE = "connectorType";
  public static final String PERPETUAL_TASK_ID = "perpetualTaskId";
  public static final String RETRY_STAGES = "retryStages";
  public static final String RUN_ALL_STAGES = "runAllStages";
  public static final String FILE_IDENTIFIER_KEY = "fileIdentifier";
  public static final Integer MAX_PAGE_SIZE = 10000;
  public static final String EXPIRY_TIME = "expiryTime";

  // TODO(UTSAV): Move to 340-ce-nextgen
  public static final String IS_EVENTS_ENABLED = "eventsEnabled";
  public static final String IS_OPTIMIZATION_ENABLED = "optimizationEnabled";
  public static final String IS_CUR_ENABLED = "curEnabled";

  public static final String ACCOUNT_PARAM_MESSAGE = "Account Identifier for the Entity.";
  public static final String ORG_PARAM_MESSAGE = "Organization Identifier for the Entity.";
  public static final String ORG_LIST_PARAM_MESSAGE = "List of Organization Identifiers for the Entities.";
  public static final String PROJECT_PARAM_MESSAGE = "Project Identifier for the Entity.";
  public static final String IDENTIFIER_PARAM_MESSAGE = "Identifier for the Entity.";
  public static final String TAGS = "Tags";
  public static final String DESCRIPTION = "Description of the entity";
  public static final String CREATED_AT_MESSAGE = "Time at which the entity was created";
  public static final String UPDATED_AT_MESSAGE = "Time at which the entity was last updated";
  public static final String NAME_PARAM_MESSAGE = "Name of the Entity";
  public static final String COLOR_PARAM_MESSAGE = "Color Code for the Entity";
  public static final String DELETED_PARAM_MESSAGE = "Deletion status for Entity";
  public static final String VERSION_PARAM_MESSAGE = "Version of Entity";
  public static final String FILE_PARAM_MESSAGE = "The file identifier";
  public static final String FILE_LIST_IDENTIFIERS_PARAM_MESSAGE =
      "This is the list of File IDs. Details specific to these IDs would be fetched.";
  public static final String FILE_SEARCH_TERM_PARAM_MESSAGE =
      "This would be used to filter Files. Any Files having the specified string in its Name, ID and Tag would be filtered.";

  public static final String GCP_CONNECTOR_IDENTIFIER = "GCP Connector Identifier";

  public static final String PAGE_PARAM_MESSAGE = "Page Index of the results to fetch."
      + "Default Value: 0";
  public static final String SIZE_PARAM_MESSAGE = "Results per page";
  public static final String SORT_PARAM_MESSAGE = "Sort criteria for the elements.";
  public static final String BAD_REQUEST_PARAM_MESSAGE = "Bad Request";
  public static final String INTERNAL_SERVER_ERROR_MESSAGE = "Internal Server Error";
  public static final String BAD_REQUEST_CODE = "400";
  public static final String INTERNAL_SERVER_ERROR_CODE = "500";
  public static final String APPLICATION_JSON_MEDIA_TYPE = "application/json";
  public static final String APPLICATION_YAML_MEDIA_TYPE = "application/yaml";

  // Environment Group
  public static final String ENVIRONMENT_GROUP_KEY = "envGroupIdentifier";
  public static final String ENV_PARAM_MESSAGE = "Environment Identifier for the Entity.";
}
