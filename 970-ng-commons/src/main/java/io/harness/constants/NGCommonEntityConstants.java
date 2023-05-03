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
  public static final String ACCOUNT_HEADER = "Harness-Account";
  public static final String ACCOUNT = "account";
  public static final String ACCOUNT_KEY = "accountIdentifier";
  public static final String EMAIL_KEY = "emailIdentifier";
  public static final String ORG_KEY = "orgIdentifier";
  public static final String ORGS_KEY = "orgIdentifiers";
  public static final String PROJECT_KEY = "projectIdentifier";

  public static final String UUID = "uuid";
  public static final String SEARCH_KEY = "searchKey";
  public static final String SORT_TYPE = "sortType";
  public static final String SORT_ORDER = "sortOrder";
  public static final String LIMIT = "limit";
  public static final String OFFSET = "offset";

  public static final String FORCE_DELETE = "forceDelete";
  public static final String VERSION_LABEL_KEY = "versionLabel";
  public static final String SERVICE_KEY = "serviceId";
  public static final String ENVIRONMENT_KEY = "envId";
  public static final String INFRA_DEFINITION_KEY = "infraDefinitionId";

  public static final String SUBSCRIPTION_ID = "subscriptionId";

  public static final String BUILDS_KEY = "buildIds";
  public static final String BUILD_KEY = "buildId";
  public static final String TIMESTAMP = "timestamp";
  public static final String IDENTIFIER_KEY = "identifier";
  public static final String GROUP_IDENTIFIER_KEY = "groupIdentifier";
  public static final String GROUP_IDENTIFIERS_KEY = "groupIdentifiers";
  public static final String NAME_KEY = "name";
  public static final String DELEGATE_IDENTIFIER_KEY = "delegateIdentifier";
  public static final String DRAFT_KEY = "skipValidation";
  public static final String TAGS_KEY = "tags";
  public static final String DESCRIPTION_KEY = "description";
  public static final String DELETED_KEY = "deleted";
  public static final String PIPELINE_KEY = "pipelineIdentifier";
  public static final String PIPELINE_EXECUTION_ID = "pipelineExecutionId";
  public static final String LAST_DEPLOYED_AT = "lastDeployedAt";
  public static final String INPUT_SET_IDENTIFIER_KEY = "inputSetIdentifier";
  public static final String ENVIRONMENT_IDENTIFIER_KEY = "environmentIdentifier";
  public static final String ENVIRONMENT_TYPE_KEY = "environmentType";
  public static final String STATUS = "status";
  public static final String MONGODB_ID = "_id";
  public static final String CONNECTOR_IDENTIFIER_KEY = "connectorIdentifier";
  public static final String REPO_URL = "repoURL";
  public static final String REPO_NAME = "repoName";
  public static final String PLAN_KEY = "planExecutionId";
  public static final String STAGE_KEY = "stageExecutionId";
  public static final String TYPE_KEY = "type";
  public static final String TOKEN_KEY = "tokenId";
  public static final String REFERRED_ENTITY_TYPE = "referredEntityType";
  public static final String REFERRED_BY_ENTITY_TYPE = "referredByEntityType";
  public static final String MODULE_TYPE = "moduleType";
  public static final String REFERER = "referer";
  public static final String GA_CLIENT_ID = "ga_client_id";
  public static final String ENTITY_TYPE = "entityType";
  public static final String SEARCH_TERM = "searchTerm";
  public static final String USER_ID = "userId";
  public static final String PAGE = "page";
  public static final String PAGE_SIZE = "page_size";
  public static final String SELF_REL = "self";
  public static final String PREVIOUS_REL = "previous";
  public static final String NEXT_REL = "next";
  public static final String SIZE = "size";
  public static final String OPERATOR = "criteriaOperator";
  public static final String MASK_SECRETS = "maskSecrets";
  public static final String CONNECTOR_IDENTIFIER_REF = "connectorIdentifierRef";
  public static final String CONNECTOR_TYPE = "connectorType";
  public static final String PERPETUAL_TASK_ID = "perpetualTaskId";
  public static final String RETRY_STAGES = "retryStages";
  public static final String RUN_ALL_STAGES = "runAllStages";
  public static final String FILE_IDENTIFIER_KEY = "fileIdentifier";
  public static final Integer MAX_PAGE_SIZE = 10000;
  public static final String EXPIRY_TIME = "expiryTime";
  public static final String SORT = "sort";
  public static final String FILE_PATH_KEY = "path";
  public static final String LICENSE_TYPE_KEY = "licenseType";

  // TODO(UTSAV): Move to ce-nextgen
  public static final String IS_EVENTS_ENABLED = "eventsEnabled";
  public static final String IS_OPTIMIZATION_ENABLED = "optimizationEnabled";
  public static final String IS_CUR_ENABLED = "curEnabled";

  public static final String ACCOUNT_PARAM_MESSAGE = "Account Identifier for the Entity.";
  public static final String ORG_PARAM_MESSAGE = "Organization Identifier for the Entity.";
  public static final String ORG_LIST_PARAM_MESSAGE = "List of Organization Identifiers for the Entities.";
  public static final String PROJECT_PARAM_MESSAGE = "Project Identifier for the Entity.";
  public static final String FORCE_DELETE_MESSAGE =
      "If true, the Entity will be forced delete, without checking any references/usages";
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
      "This will be used to filter files or folders. Any file or folder having the specified search term in its Name or Identifier will be filtered";
  public static final String FILE_TAGS_MESSAGE = "The File or Folder tags";
  public static final String FILE_CONTENT_MESSAGE = "The content of the File as InputStream";
  public static final String FILE_FILTER_PROPERTIES_MESSAGE = "Details of the File filter properties to be applied";
  public static final String FILE_PATH_PARAM_MESSAGE = "The file path";
  public static final String FILTER_IDENTIFIER_MESSAGE = "Filter identifier";
  public static final String ENTITY_TYPE_MESSAGE = "Entity type";
  public static final String FOLDER_DETAILS_MESSAGE = "Folder details";
  public static final String FILE_YAML_DEFINITION_MESSAGE = "YAML definition of File or Folder";
  public static final String FOLDER_NODE_MESSAGE = "Folder node for which to return the list of nodes";

  public static final String GCP_CONNECTOR_IDENTIFIER = "GCP Connector Identifier";

  public static final String PAGE_PARAM_MESSAGE = "Page Index of the results to fetch."
      + "Default Value: 0";
  public static final String PAGE_TOKEN_PARAM_MESSAGE = "Page Token of the next results to fetch."
      + "Default Value: ''";
  public static final String SIZE_PARAM_MESSAGE = "Results per page";
  public static final String SORT_PARAM_MESSAGE = "Sort criteria for the elements.";
  public static final String BAD_REQUEST_PARAM_MESSAGE = "Bad Request";
  public static final String INTERNAL_SERVER_ERROR_MESSAGE = "Internal Server Error";
  public static final String BAD_REQUEST_CODE = "400";
  public static final String INTERNAL_SERVER_ERROR_CODE = "500";
  public static final String APPLICATION_JSON_MEDIA_TYPE = "application/json";
  public static final String APPLICATION_YAML_MEDIA_TYPE = "application/yaml";
  public static final String OPERATOR_PARAM_MESSAGE = "Operator Criteria for Criterias ";
  public static final String RESOURCE = "Resource it targets (For icons)";
  public static final String POLICY = "Get YAML of the policy";
  public static final String LICENSE_TYPE_PARAM_MESSAGE = "CD License type";

  // Environment Group
  public static final String ENVIRONMENT_GROUP_KEY = "envGroupIdentifier";
  public static final String ENV_PARAM_MESSAGE = "Environment Identifier for the Entity.";

  public static final String SERVICE_OVERRIDES_IDENTIFIER = "Service Overrides Identifier for Entity.";

  public static final String ENV_REF_PARAM_MESSAGE = "Environment Reference for the Entity.";

  public static final String INFRADEF_PARAM_MESSAGE = "Infrastructure Definition Identifier for the Entity.";
  // Service Overrides
  public static final String SERVICE_IDENTIFIER_KEY = "serviceIdentifier";
  public static final String SERVICE_PARAM_MESSAGE = "Service Identifier for the Entity.";
  public static final String SERVICE_REF_PARAM_MESSAGE = "Service Reference for Entity";
  public static final String VARIABLE_OVERRIDE_PARAM_MESSAGE = "Variable Overrides for an Environment";

  // Infrastructures
  public static final String DEPLOY_TO_ALL = "deployToAll";
  public static final String INFRA_IDENTIFIERS = "infraIdentifiers";
  public static final String INFRA_IDENTIFIER = "infraIdentifier";

  public static final String CLUSTER_IDENTIFIER = "clusterIdentifier";
  public static final String INFRA = "infra";
  public static final String OVERRIDES_ONLY = "overridesOnly";
  public static final String RUNTIME_INPUTS_TEMPLATE = "Runtime inputs template YAML";
  public static final String ACCOUNT_SCOPED_REQUEST_NON_NULL_ORG_PROJECT =
      "Account scoped request is having non null org or project";
  public static final String ORG_SCOPED_REQUEST_NON_NULL_PROJECT = "Org scoped request is having non null project";
  public static final String DIFFERENT_IDENTIFIER_IN_PAYLOAD_AND_PARAM =
      "Request is having different identifier in payload and param";
  public static final String DIFFERENT_ORG_IN_PAYLOAD_AND_PARAM =
      "Request is having different org in payload and param";
  public static final String DIFFERENT_PROJECT_IN_PAYLOAD_AND_PARAM =
      "Request is having different project in payload and param";
  public static final String JOB_NAME = "jobName";
  public static final String FQN_PATH = "fqnPath";
  public static final String ARTIFACT_PATH = "artifactPath";
  public static final String ARTIFACT_VERSION = "artifactVersion";
  public static final String ARTIFACT = "artifact";
  public static final String PARENT_JOB_NAME = "parentJobName";
  public static final String HARNESS_IMAGE = "harnessImage";
  public static final String METHOD_NAME = "methodName";
  public static final String AGENT_KEY = "agentIdentifier";
  public static final String IS_STABLE_TEMPLATE = "isStableTemplate";
  public static final String CONFIG_FILE_FUNCTOR = "configFile";
  public static final String FUNCTOR_STRING_METHOD_NAME = "getAsString";
  public static final String FUNCTOR_BASE64_METHOD_NAME = "getAsBase64";
  public static final String FILE_STORE_FUNCTOR = "fileStore";
  public static final String PLAN_NAME = "planName";
  public static final String TARGET_IDENTIFIER_KEY = "targetIdentifier";
  public static final String NOTES_FOR_PIPELINE_EXECUTION = "notesForPipelineExecution";
}