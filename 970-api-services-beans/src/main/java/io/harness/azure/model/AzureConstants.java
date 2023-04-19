/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.model;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public interface AzureConstants {
  Pattern failureContainerLogPattern =
      Pattern.compile("ERROR - Container .* didn't respond to HTTP pings on port:", Pattern.CASE_INSENSITIVE);
  Pattern failureContainerSetupPattern = Pattern.compile("Stopping site .* because it failed during startup");
  Pattern deploymentLogPattern = Pattern.compile("Deployment successful\\.", Pattern.CASE_INSENSITIVE);
  Pattern containerSuccessPattern =
      Pattern.compile("initialized successfully and is ready to serve requests\\.", Pattern.CASE_INSENSITIVE);
  Pattern windowsServicePlanContainerSuccessPattern =
      Pattern.compile(".* Container start-up and configuration completed successfully.*", Pattern.CASE_INSENSITIVE);
  Pattern tomcatSuccessPattern =
      Pattern.compile("Deployment of web application directory .* has finished", Pattern.CASE_INSENSITIVE);

  String TIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss";
  String DDMMYYYY_TIME_PATTERN = "dd/MM/yyyy' 'HH:mm:ss.SSS";
  String TIME_STAMP_REGEX =
      "(\\d{2}\\/\\d{2}\\/\\d{4} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s*|(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})\\s*";

  int DEFAULT_SYNC_AZURE_VMSS_TIMEOUT_MIN = 2;
  int DEFAULT_SYNC_AZURE_RESOURCE_TIMEOUT_MIN = 2;
  String NEW_VIRTUAL_MACHINE_SCALE_SET = "New Virtual Machine Scale Set";
  String OLD_VIRTUAL_MACHINE_SCALE_SET = "Old Virtual Machine Scale Set";
  String STAGE_BACKEND_POOL = "Stage Backend Pool";
  String PROD_BACKEND_POOL = "Production Backend Pool";
  String MIN_INSTANCES = "minInstancesExpr";
  String MAX_INSTANCES = "maxInstancesExpr";
  String DESIRED_INSTANCES = "targetInstancesExpr";
  String AUTO_SCALING_VMSS_TIMEOUT = "autoScalingSteadyStateVMSSTimeout";
  String TRAFFIC_WEIGHT_EXPR = "trafficWeightExpr";
  String BLUE_GREEN = "blueGreen";
  String AZURE_VMSS_SETUP_COMMAND_NAME = "Azure VMSS Setup";
  String AZURE_VMSS_DEPLOY_COMMAND_NAME = "Resize Azure Virtual Machine Scale Set";
  String ACTIVITY_ID = "activityId";
  int NUMBER_OF_LATEST_VERSIONS_TO_KEEP = 3;
  String STEADY_STATE_TIMEOUT_REGEX = "w|d|h|m|s|ms";
  String ZIP_EXTENSION = ".zip";
  String JAR_EXTENSION = ".jar";
  String FILE_RENAME_FAILURE = "Failed to rename the file - [%s] to [%s]";
  String DEFAULT_JAR_ARTIFACT_NAME = "app.jar";

  // VMSS Tags names and values
  String HARNESS_AUTOSCALING_GROUP_TAG_NAME = "HARNESS_REVISION";
  String DYNAMIC_BASE_VMSS_PROVISIONING_PREFIX = "azure:";
  String NAME_TAG = "Name";
  String BG_VERSION_TAG_NAME = "BG_VERSION";
  String BG_GREEN_TAG_VALUE = "GREEN";
  String BG_BLUE_TAG_VALUE = "BLUE";
  String VMSS_CREATED_TIME_STAMP_TAG_NAME = "Created";

  // User VM Auth types
  String VMSS_AUTH_TYPE_DEFAULT = "PASSWORD";
  String VMSS_AUTH_TYPE_SSH_PUBLIC_KEY = "SSH_PUBLIC_KEY";

  // Default Azure VMSS values
  int DEFAULT_AZURE_VMSS_MAX_INSTANCES = 10;
  int DEFAULT_AZURE_VMSS_MIN_INSTANCES = 0;
  int DEFAULT_AZURE_VMSS_DESIRED_INSTANCES = 6;
  int DEFAULT_AZURE_VMSS_TIMEOUT_MIN = 20;

  // Command unit Names
  String SETUP_COMMAND_UNIT = "Setup Virtual Machine Scale Set";
  String UP_SCALE_COMMAND_UNIT = "Upscale Virtual Machine Scale Set";
  String UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT = "Upscale wait for steady state";
  String DELETE_OLD_VIRTUAL_MACHINE_SCALE_SETS_COMMAND_UNIT = "Delete Old Virtual Machine Scale Sets";
  String DOWN_SCALE_COMMAND_UNIT = "Downscale Virtual Machine Scale Set";
  String DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT = "Downscale wait for steady state";
  String CREATE_NEW_VMSS_COMMAND_UNIT = "Create New Virtual Machine Scale Set";
  String DEPLOYMENT_STATUS = "Deployment status";
  String DEPLOYMENT_ERROR = "Failed Deployment status";
  String DELETE_NEW_VMSS = "Delete New Virtual Machine Scale Set";
  String AZURE_VMSS_SWAP_BACKEND_POOL = "Swap VMSS Backend Pool";
  String BG_SWAP_ROUTES_COMMAND_UNIT = "Swap Routes";
  String BG_ROLLBACK_COMMAND_UNIT = "Rollback Swap Routes";

  // Activity command names
  String AZURE_WEBAPP_SLOT_SETUP_ACTIVITY_COMMAND_NAME = "AZURE_WEBAPP_SLOT_SETUP";

  // Messaging
  String SKIP_VMSS_DEPLOY = "No Azure VMSS setup context element found. Skipping deploy";
  String SKIP_VMSS_ROLLBACK = "No Azure VMSS setup context element found. Skipping rollback";
  String SKIP_RESIZE_SCALE_SET = "No scale set found with the name = [%s], hence skipping";
  String REQUEST_DELETE_SCALE_SET = "Sending request to delete newly created Virtual Machine Scale Set: [%s]";
  String SUCCESS_DELETE_SCALE_SET = "Virtual Machine Scale Set: [%s] deleted successfully";
  String SWAP_ROUTE_FAILURE = "Azure Virtual Machine Scale Set swap route failed with error ";
  String SETUP_ELEMENT_NOT_FOUND = "Did not find Setup element of class AzureVMSSSetupContextElement";
  String NO_VMSS_FOR_DOWN_SIZING = "No old Virtual Machine Scale Set was found with non-zero capacity for down scale";
  String NO_VMSS_FOR_DELETION = "No old Virtual Machine Scale Set was found for deletion";
  String NO_SCALING_POLICY_DURING_DOWN_SIZING = "Not attaching scaling policy to VMSS: [%s] while down sizing it";
  String CLEAR_SCALING_POLICY = "Clearing scaling policy for scale set: [%s]";
  String START_BLUE_GREEN_SWAP = "Starting Swap Backend pool step during blue green deployment";
  String END_BLUE_GREEN_SWAP = "Swap backend pool completed successfully";
  String DOWNSIZING_FLAG_DISABLED = "Skipping downsizing of VMSS: [%s] as downsize was not requested";
  String NO_VMSS_FOR_UPSCALE_DURING_ROLLBACK = "There is no old Virtual machine for up scaling during rollback";

  // Validation messages
  String RESOURCE_GROUP_NAME_NULL_VALIDATION_MSG = "Parametermeter resourceGroupName is required and cannot be null";
  String RESOURCE_ID_NAME_NULL_VALIDATION_MSG = "Parameter resourceId is required and cannot be null";
  String LOAD_BALANCER_NAME_NULL_VALIDATION_MSG = "Parameter loadBalancerName is required and cannot be null";
  String BACKEND_POOL_NAME_NULL_VALIDATION_MSG = "Parameter backendPoolName is required and cannot be null";
  String TARGET_RESOURCE_ID_NULL_VALIDATION_MSG = "Parameter targetResourceId is required and cannot be null";
  String AUTOSCALE_SETTINGS_RESOURCE_JSON_NULL_VALIDATION_MSG =
      "Parameter autoScaleSettingResourceInnerJson is required and cannot be null";
  String AZURE_MANAGEMENT_CLIENT_NULL_VALIDATION_MSG = "Azure management client can't be null";
  String VIRTUAL_MACHINE_SCALE_SET_NULL_VALIDATION_MSG =
      "Parameter virtualMachineScaleSet is required and cannot be null";
  String PRIMARY_INTERNET_FACING_LOAD_BALANCER_NULL_VALIDATION_MSG =
      "Parameter primaryInternetFacingLoadBalancer is required and cannot be null";
  String SUBSCRIPTION_ID_NULL_VALIDATION_MSG = "Parameter subscriptionId is required and cannot be empty or null";
  String VIRTUAL_MACHINE_SCALE_SET_ID_NULL_VALIDATION_MSG =
      "Parameter virtualMachineScaleSetId is required and cannot be null";
  String VIRTUAL_SCALE_SET_NAME_NULL_VALIDATION_MSG = "Parameter virtualScaleSetName is required and cannot be null";
  String BASE_VIRTUAL_MACHINE_SCALE_SET_IS_NULL_VALIDATION_MSG =
      "Parameter baseVirtualMachineScaleSet is required and cannot be null";
  long AUTOSCALING_REQUEST_STATUS_CHECK_INTERVAL = TimeUnit.SECONDS.toSeconds(15);
  String NEW_VIRTUAL_MACHINE_SCALE_SET_NAME_IS_NULL_VALIDATION_MSG =
      "Parameter newVirtualMachineScaleSetName is required and cannot be null.";
  String HARNESS_REVISION_IS_NULL_VALIDATION_MSG = "Parameter harnessRevision is required and cannot be null";
  String VMSS_IDS_IS_NULL_VALIDATION_MSG = "Parameter vmssIds is required and cannot be null";
  String NUMBER_OF_VM_INSTANCES_VALIDATION_MSG = "Required number of VM instances can't have negative value";
  String BACKEND_POOLS_LIST_EMPTY_VALIDATION_MSG = "Backend pools list cannot be empty";
  String VM_INSTANCE_IDS_LIST_EMPTY_VALIDATION_MSG = "Virtual Machine instances ids list cannot be empty";
  String VM_INSTANCE_IDS_NOT_NUMBERS_VALIDATION_MSG = "Virtual Machine instances ids must be '*' or numbers";
  String NEW_VMSS_NAME_NULL_VALIDATION_MSG = "Parameter newVMSSName is required and cannot be null";
  String OLD_VMSS_NAME_NULL_VALIDATION_MSG = "Parameter oldVMSSName is required and cannot be null";
  String AZURE_LOAD_BALANCER_DETAIL_NULL_VALIDATION_MSG =
      "Parameter azureLoadBalancerDetail is required and cannot be null";
  String UNRECOGNIZED_PARAMETERS = "Parameters of unrecognized class: [%s] found while executing deploy step.";
  String UNRECOGNIZED_TASK = "Unrecognized task params while running azure vmss task: [%s]";
  String GALLERY_NAME_NULL_VALIDATION_MSG = "Parameter galleryName is required and cannot be null";
  String GALLERY_IMAGE_NAME_NULL_VALIDATION_MSG = "Parameter imageName is required and cannot be null";
  String DEPLOYMENT_NAME_BLANK_VALIDATION_MSG = "Parameter deploymentName is required and cannot be null or empty";
  String LOCATION_SET_AT_RESOURCE_GROUP_VALIDATION_MSG = "Location cannot be set at resource group scope";
  String LOCATION_BLANK_VALIDATION_MSG = "Parameter location cannot be null or empty";
  String MANAGEMENT_GROUP_ID_BLANK_VALIDATION_MSG = "Parameter groupId cannot be null or empty";
  String DEPLOYMENT_DOES_NOT_EXIST_RESOURCE_GROUP = "The deployment - [%s] does not exist in resource group - [%s]";
  String DEPLOYMENT_DOES_NOT_EXIST_SUBSCRIPTION = "The deployment - [%s] does not exist in subscription - [%s]";
  String DEPLOYMENT_DOES_NOT_EXIST_MANAGEMENT_GROUP = "The deployment - [%s] does not exist in management group - [%s]";
  String DEPLOYMENT_DOES_NOT_EXIST_TENANT = "The deployment - [%s] does not exist in tenant - [%s]";
  String RESOURCE_SCOPE_BLANK_VALIDATION_MSG = "Parameter resourceScope cannot be empty or null";
  String RESOURCE_SCOPE_IS_NOT_VALIDATION_MSG = "Parameter resourceScope is not valid, resourceScope: %s";
  String BLUEPRINT_NAME_BLANK_VALIDATION_MSG = "Parameter blueprintName cannot be empty or null";
  String BLUEPRINT_JSON_BLANK_VALIDATION_MSG = "Parameter blueprintJSON cannot be empty or null";
  String ARTIFACT_NAME_BLANK_VALIDATION_MSG = "Parameter artifactName cannot be empty or null";
  String ARTIFACT_JSON_BLANK_VALIDATION_MSG = "Parameter artifactJSON cannot be empty or null";
  String VERSION_ID_BLANK_VALIDATION_MSG = "Parameter versionId cannot be empty or null";
  String ASSIGNMENT_NAME_BLANK_VALIDATION_MSG = "Parameter assignmentName cannot be empty or null";
  String ASSIGNMENT_JSON_BLANK_VALIDATION_MSG = "Parameter assignmentJSON cannot be empty or null";
  String NEXT_PAGE_LINK_BLANK_VALIDATION_MSG = "Parameter nextPageLink is required and cannot be null.";
  String BLUEPRINT_ID_BLANK_VALIDATION_MSG = "Parameter blueprintId cannot be null or empty";
  String AZURE_CONFIG_BLANK_VALIDATION_MSG = "Parameter azureConfig cannot be null";
  String ASSIGNMENT_IDENTITY_NULL_VALIDATION_MSG = "Assignment identity property cannot be null";
  String ASSIGNMENT_LOCATION_BLANK_VALIDATION_MSG = "Assignment location property cannot be null or empty";
  String PROPERTIES_BLUEPRINT_ID_VALIDATION_MSG = "Assignment properties.blueprintId cannot be null or empty";
  String PROPERTIES_SCOPE_BLANK_VALIDATION_MSG =
      "Assignment properties.scope cannot be null or empty for management group resource scope";
  String OBJECT_ID_NAME_BLANK_VALIDATION_MSG = "Parameter objectId cannot be empty or null";
  String ROLE_ASSIGNMENT_NAME_BLANK_VALIDATION_MSG = "Parameter roleAssignmentName cannot be empty or null";
  String BLUEPRINT_ID_IS_NOT_VALIDATION_MSG = "Parameter blueprintId is not valid, blueprintId: %s";
  String ASSIGNMENT_SUBSCRIPTION_ID_BLANK_VALIDATION_MSG = "Parameter assignmentSubscriptionId cannot be empty or null";
  String ASSIGNMENT_BLANK_VALIDATION_MSG = "Parameter assignment cannot be empty or null";
  String BLUEPRINT_JSON_FILE_BLANK_VALIDATION_MSG = "Blueprints blueprint json file cannot be empty or null";
  String ASSIGN_JSON_FILE_BLANK_VALIDATION_MSG = "Blueprints assign json file cannot be empty or null";
  String REPOSITORY_NAME_BLANK_VALIDATION_MSG = "Parameter repositoryName cannot be null or empty";
  String REGISTRY_HOST_BLANK_VALIDATION_MSG = "Parameter registryHost cannot be null or empty";
  String REGISTRY_NAME_BLANK_VALIDATION_MSG = "Parameter registryName cannot be null or empty";
  String OS_TYPE_NULL_VALIDATION_MSG = "Parameter osType is required and cannot be null";

  // Patterns
  String GALLERY_IMAGE_ID_PATTERN =
      "/subscriptions/%s/resourceGroups/%s/providers/Microsoft.Compute/galleries/%s/images/%s/versions/%s";
  String VMSS_AUTOSCALE_SUFIX = "_Autoscale";

  // VM provisioning statuses
  String VM_PROVISIONING_SUCCEEDED_STATUS = "Provisioning succeeded";
  String VM_PROVISIONING_SPECIALIZED_STATUS = "VM specialized";

  // VM power statuses
  String VM_POWER_STATE_PREFIX = "PowerState/";

  // Azure App Service
  String COMMAND_TYPE_BLANK_VALIDATION_MSG = "Parameter commandType is required and cannot be empty or null";
  String WEB_APP_NAME_BLANK_VALIDATION_MSG = "Parameter webAppName is required and cannot be empty or null";
  String SLOT_NAME_BLANK_VALIDATION_MSG = "Parameter slotName is required and cannot be empty or null";
  String ACR_USERNAME_BLANK_VALIDATION_MSG = "Parameter username cannot be null or empty";
  String ACR_ACCESS_KEYS_BLANK_VALIDATION_MSG = "Primary and secondary ACR access keys cannot be null or empty";
  String DOCKER_REGISTRY_URL_BLANK_VALIDATION_MSG = "Parameter dockerRegistryUrl cannot be empty or null";
  String ACR_REGISTRY_NAME_BLANK_VALIDATION_MSG = "Parameter registryName cannot be empty or null";
  String DEPLOYMENT_SLOT_PRODUCTION_NAME = "production";
  String DOCKER_REGISTRY_SERVER_URL_PROPERTY_NAME = "DOCKER_REGISTRY_SERVER_URL";
  String DOCKER_REGISTRY_SERVER_USERNAME_PROPERTY_NAME = "DOCKER_REGISTRY_SERVER_USERNAME";
  String DOCKER_REGISTRY_SERVER_SECRET_PROPERTY_NAME = "DOCKER_REGISTRY_SERVER_PASSWORD";
  String DOCKER_CUSTOM_IMAGE_NAME_PROPERTY_NAME = "DOCKER_CUSTOM_IMAGE_NAME";
  String DOCKER_IMAGE_FULL_PATH_PATTERN = "DOCKER|%s";
  String DOCKER_FX_IMAGE_PREFIX = "DOCKER|";
  String DOCKER_IMAGE_AND_TAG_PATH_PATTERN = "%s:%s";
  String WEB_APP_NAME_BLANK_ERROR_MSG = "Parameter webAppName cannot be null or empty";
  String SLOT_NAME_BLANK_ERROR_MSG = "Parameter slotName cannot be null or empty";
  String TARGET_SLOT_CANNOT_BE_IN_STOPPED_STATE = "The swap slot - [%s] must be in running state for swap to start";
  String IMAGE_AND_TAG_BLANK_ERROR_MSG = "Parameter imageAndTag cannot be null or empty";
  String SHIFT_TRAFFIC_SLOT_NAME_BLANK_ERROR_MSG = "Parameter shiftTrafficSlotName cannot be null or empty";
  String TRAFFIC_WEIGHT_IN_PERCENTAGE_INVALID_ERROR_MSG =
      "Parameter trafficWeightInPercentage cannot be less then 0% or higher then 100%";
  String SOURCE_SLOT_NAME_BLANK_ERROR_MSG = "Parameter sourceSlotName cannot be null or empty";
  String TARGET_SLOT_NAME_BLANK_ERROR_MSG = "Parameter targetSlotName cannot be null or empty";
  String FILE_BLANK_ERROR_MSG = "Parameter file is required and cannot be null";
  String ARTIFACT_FILE_BLANK_ERROR_MSG = "Parameter artifactFile cannot be null";
  String ARTIFACT_TYPE_BLANK_ERROR_MSG = "Parameter artifactType cannot be null";
  String ACTIVITY_LOG_EVENT_DATA_TEMPLATE = "Operation name : [%s]%n"
      + "Event initiated by : [%s]%n"
      + "Status : [%s]%n"
      + "Description : [%s]";
  String SLOT_SWAP_JOB_PROCESSOR_STR = "SlotSwapJobProcessor";
  String SUCCESS_REQUEST = "Request sent successfully";
  String DEPLOYMENT_SLOT_FULL_NAME_PATTERN = "%s-%s";
  String DEPLOYMENT_SLOT_NAME_PREFIX_PATTERN = "(?i)^%s-";
  String DEPLOYMENT_SLOT_NON_PRODUCTION_TYPE = "non-production";
  String DEPLOYMENT_SLOT_PRODUCTION_TYPE = "production";
  String UPDATING_SLOT_CONFIGURATIONS = "Start updating configurations settings for  slot - [%s]";
  String UPDATE_SLOT_CONFIGURATIONS_SUCCESS = "%nAll configurations settings for  slot - [%s] update successfully";
  String UPDATE_APPLICATION_CONFIGURATIONS = "Updating application configurations for slot - [%s]";
  String FAIL_UPDATE_SLOT_CONFIGURATIONS = "Failed to update slot configurations - [%s]";

  String DELETE_APPLICATION_SETTINGS = "Deleting following Application settings: %n[%s]";
  String SUCCESS_DELETE_APPLICATIONS_SETTINGS = "Application settings deleted successfully";
  String ADD_APPLICATION_SETTINGS = "%nAdding following Application settings: %n[%s]";
  String SUCCESS_ADD_APPLICATION_SETTINGS = "Application settings updated successfully";

  String DELETE_CONNECTION_STRINGS = "Deleting following Connection strings: %n[%s]";
  String SUCCESS_DELETE_CONNECTION_STRINGS = "Connection strings deleted successfully";
  String ADD_CONNECTION_STRINGS = "%nAdding following Connection strings: %n[%s]";
  String SUCCESS_ADD_CONNECTION_STRINGS = "Connection strings updated successfully";

  String DELETE_CONTAINER_SETTINGS = "Cleaning existing container settings";
  String SUCCESS_DELETE_CONTAINER_SETTINGS = "Existing container settings deleted successfully";
  String DELETE_IMAGE_SETTINGS = "Cleaning existing image settings";
  String SUCCESS_DELETE_IMAGE_SETTINGS = "Existing image settings deleted successfully";
  String UPDATE_IMAGE_SETTINGS = "Updating container image and tag: %n[%s], web app hosting OS [%s]";
  String SUCCESS_UPDATE_IMAGE_SETTINGS = "Image and tag updated successfully for slot [%s]";
  String UPDATE_CONTAINER_SETTINGS = "%nUpdating Container settings for slot - [%s]";
  String SUCCESS_UPDATE_CONTAINER_SETTINGS = "Deployment slot container settings updated successfully";
  String FAIL_UPDATE_CONTAINER_SETTINGS = "Failed to update Container settings - [%s]";
  String EMPTY_DOCKER_SETTINGS = "Docker settings list for updating slot configuration is empty, slot name [%s]";

  String REQUEST_START_SLOT = "Sending request for starting deployment slot - [%s]";
  String SUCCESS_START_SLOT = "Deployment slot started successfully";
  String FAIL_START_SLOT = "Failed to start deployment slot - [%s]";
  String START_SLOT_DEPLOYMENT = "Starting deployment to slot - [%s]";

  String REQUEST_STOP_SLOT = "%nSending request for stopping deployment slot - [%s]";
  String SUCCESS_STOP_SLOT = "Deployment slot stopped successfully";
  String FAIL_STOP_SLOT = "Failed to stop deployment slot - [%s]";

  String REQUEST_TRAFFIC_SHIFT = "Sending request to shift [%.2f] traffic to deployment slot: [%s]";
  String SUCCESS_TRAFFIC_SHIFT = "Traffic percentage updated successfully";
  String NO_TRAFFIC_SHIFT_REQUIRED = "Traffic percentage update not required";

  String UPDATE_STARTUP_COMMAND = "Start updating slot configuration with startup command, %n"
      + "App name: [%s]%nSlot name: [%s]";
  String SUCCESS_UPDATE_STARTUP_COMMAND = "Startup command updated successfully";
  String SWAP_SLOT_SUCCESS = "Swapping slots done successfully";
  String SWAP_SLOT_FAILURE = "Swapping slots failed for slot - [%s] due to: %s";

  String SUCCESS_SLOT_DEPLOYMENT = "Deployment to slot - [%s] is successful";
  String FAIL_DEPLOYMENT = "Deployment failed for slot - [%s]";
  String FAIL_DEPLOYMENT_ERROR_MSG = "Deployment on slot - [%s] failed. %s";

  String LOG_STREAM_SUCCESS_MSG = "Receive deployment success event from log stream for slot - [%s]";
  String FAIL_LOG_STREAMING = "Failed to stream the deployment logs from slot - [%s] "
      + "due to %n [%s]. %nPlease verify the status of deployment manually";
  String START_ARTIFACT_DEPLOY = "Start deploying artifact file";
  String ARTIFACT_DEPLOY_STARTED = "Deployment started. This operation can take a while to complete ...";
  String UNSUPPORTED_ARTIFACT = "Unsupported package deployment for artifact type, artifactType: %s, slotName: %s";
  String ZIP_DEPLOY = "Deploying artifact ZIP file on slot, %nArtifact file: %s%nApp name: %s%nSlot name: %s";
  String WAR_DEPLOY = "Deploying artifact WAR file on slot, %nArtifact file: %s%nApp name: %s%nSlot name: %s";

  // Azure App Service Command Units
  String SAVE_EXISTING_CONFIGURATIONS = "Save App Service Configurations";
  String STOP_DEPLOYMENT_SLOT = "Stop Slot";
  String UPDATE_SLOT_CONFIGURATION_SETTINGS = "Update Slot Configuration Settings";
  String DEPLOY_TO_SLOT = "Deploy to Slot";
  String DEPLOY_DOCKER_IMAGE = "Deploy Docker Image";
  String UPDATE_DEPLOYMENT_SLOT_CONTAINER_SETTINGS = "Update Slot Container Settings";
  String START_DEPLOYMENT_SLOT = "Start Slot";
  String SLOT_TRAFFIC_PERCENTAGE = "Update Slot Traffic Percentage";
  String SLOT_SWAP = "Swap Slots";
  String FETCH_ARTIFACT_FILE = "Download artifact file";
  long SLOT_STARTING_STATUS_CHECK_INTERVAL = TimeUnit.SECONDS.toSeconds(15);
  long SLOT_STOPPING_STATUS_CHECK_INTERVAL = TimeUnit.SECONDS.toSeconds(15);
  long ARM_DEPLOYMENT_STATUS_CHECK_INTERVAL = TimeUnit.SECONDS.toSeconds(15);

  // Azure Docker Registry Type
  String ACR = "ACR";
  String ECR = "ECR";
  String GCR = "GCR";
  String DOCKER_HUB_PRIVATE = "DOCKER_HUB_PRIVATE";
  String DOCKER_HUB_PUBLIC = "DOCKER_HUB_PUBLIC";
  String ARTIFACTORY_PRIVATE_REGISTRY = "ARTIFACTORY_PRIVATE_REGISTRY";
  String NEXUS_PRIVATE_REGISTRY = "NEXUS_PRIVATE_REGISTRY";

  // Web App Instance STATUS
  String WEB_APP_INSTANCE_STATUS_RUNNING = "Running";

  // App Service Manifest Utils
  Pattern IS_SETTING_SECRET_REGEX =
      Pattern.compile("^\\$\\{secrets\\.getValue\\(['\"]+(?<secretName>[^~!@#$%^&*'\"/?<>,;.]+)['\"]+\\)}$");
  String SECRET_REF_FIELS_NAME = "passwordRef";
  Pattern HTTPS_OR_HTTP_PREFIX_REGEX = Pattern.compile("^(https?)://.*$");

  double INVALID_TRAFFIC = -1;
  // Azure REST client settings
  int REST_CLIENT_CONNECT_TIMEOUT_SECONDS = 15;
  int REST_CLIENT_READ_TIMEOUT_SECONDS = 30;
  int REST_CLIENT_WRITE_TIMEOUT_SECONDS = 30;
  int REST_CLIENT_IDLE_TIMEOUT_SECONDS = 60;
  int REST_CONNECTION_POOL_SIZE = 10;
  Duration REST_CLIENT_RESPONSE_TIMEOUT = Duration.ofSeconds(30);

  String MANAGEMENT_GROUP_PROVIDERS_PREFIX = "/providers/Microsoft.Management/managementGroups/";
  String DEPLOYMENT_VALIDATION_FAILED_MSG_PATTERN = "Code: %s, Message: %s, Target: %s";

  String FETCH_FILES = "Download Files";
  String BLUEPRINT_JSON_FILE_NAME = "blueprint.json";
  String ASSIGN_JSON_FILE_NAME = "assign.json";
  String ARTIFACTS_FOLDER_NAME = "artifacts";
  String UNIX_SEPARATOR = "/";

  // ARM & Blueprint command units
  String EXECUTE_ARM_DEPLOYMENT = "Execute ARM Deployment";
  String ARM_DEPLOYMENT_STEADY_STATE = "ARM Deployment Steady state";
  String FETCH_RESOURCE_GROUP_TEMPLATE = "Fetch Resource Group Template";
  String ARM_DEPLOYMENT_OUTPUTS = "ARM Deployment Outputs";
  String BLUEPRINT_DEPLOYMENT = "Execute Blueprint Deployment";
  String BLUEPRINT_DEPLOYMENT_STEADY_STATE = "Blueprint Deployment Steady state";

  String ARM_DEPLOYMENT_NAME_PATTERN = "%s-%s";
  String RESOURCE_SCOPE_MNG_GROUP_PATTERN = "/providers/Microsoft.Management/managementGroups/";
  String RESOURCE_SCOPE_SUBSCRIPTION_PATTERN = "/subscriptions/";
  String ASSIGNMENT_NAME_PATTERN = "Assignment-%s-%s";
  Pattern BLUEPRINT_ID_REGEX = Pattern.compile(
      "^(?<resourceScope>\\S+)(?<providerName>/providers/Microsoft.Blueprint/blueprints/)(?<blueprintName>\\S+)(?<versionsPath>/versions/)(?<versionId>\\S+)$");
  String DEPLOYMENT_NAME_PATTERN = "harness_%s_%s";

  // Azure Cloud Error codes
  String ROLE_ASSIGNMENT_EXISTS_CLOUD_ERROR_CODE = "RoleAssignmentExists";

  String ARTIFACT_PATH_PREFIX = "artifact/";
  String REPOSITORY_DIR_PATH = "./repository";
  String AZURE_APP_SVC_ARTIFACT_DOWNLOAD_DIR_PATH = "./repository/azureappsvcartifacts";
  String AZURE_AUTH_CERT_DIR_PATH = "./repository/azureauthcert";
  String DEFAULT_CERT_FILE_NAME = "azure-cert";

  // Azure REST API field names
  String TENANT_ID = "tenantId";
  String GRANT_TYPE = "grant_type";
  String CLIENT_ID = "client_id";
  String SCOPE = "scope";
  String CLIENT_SECRET = "client_secret";
  String CLIENT_ASSERTION = "client_assertion";
  String CLIENT_ASSERTION_TYPE = "client_assertion_type";
  String ACCESS_TOKEN = "access_token";
  String REFRESH_TOKEN = "refresh_token";
  String SERVICE = "service";
  String CLIENT_CREDENTIALS_GRANT_TYPE = "client_credentials";
  String JWT_BEARER_CLIENT_ASSERTION_TYPE = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";
  String TOKEN_TYPE = "token_type";
  String TOKEN_EXPIRES_IN = "expires_in";
  String TOKEN_EXT_EXPIRES_IN = "ext_expires_in";
  String SUBSCRIPTION = "subscription";
  String RESOURCE_GROUP = "resourceGroup";
  String FORMAT = "format";
  String AKS_CLUSTER_NAME = "aksClusterName";
  String KUBECFG_AUTH_PROVIDER = "auth-provider";
  String KUBECFG_CLIENT_KEY_DATA = "client-key-data";
  String KUBECFG_CLIENT_CERTIFICATE_DATA = "client-certificate-data";
  String KUBECFG_CERTIFICATE_AUTHORITY_DATA = "certificate-authority-data";
  String KUBECFG_CURRENT_CONTEXT = "current-context";
  String KUBECFG_API_SERVER_ID = "apiserver-id";
  String KUBECFG_CLIENT_ID = "client-id";
  String KUBECFG_CONFIG_MODE = "config-mode";
  String KUBECFG_TENANT_ID = "tenant-id";
  String KUBECFG_ARGS_SERVER_ID = "--server-id";
  String KUBECFG_ARGS_CLIENT_ID = "--client-id";
  String KUBECFG_ARGS_TENANT_ID = "--tenant-id";
  String KUBECFG_ARGS_ENV = "--environment";
  String KUBECFG_ARGS_CLIENT_SECRET = "--client-secret";
  String KUBECFG_ARGS_LOGIN = "--login";
  String KUBECFG_ARGS_MSI = "msi";
  String KUBECFG_ARGS_SPN = "spn";
  String KUBECFG_ARGS_AZURE_CLI = "azurecli";
  String KUBECFG_ARGS_GET_TOKEN = "get-token";
  String AZURE_CLI_CMD = "az";
  String AZURE_CONFIG_DIR = "AZURE_CONFIG_DIR";
  String AZURE_LOGIN_CONFIG_DIR_PATH = ".azure";
  String ACR_DEFAULT_DOCKER_USERNAME = "00000000-0000-0000-0000-000000000000";
  String AZURE_ARM_ROLLBACK_PATTERN = "rollback_";
  String ERROR_CODE_LOCATION_NOT_FOUND = "LocationNotAvailableForDeployment";
  String ERROR_LOCATION_NOT_FOUND = "The location %s is not a valid region.";
  String ERROR_INVALID_MANAGEMENT_GROUP_ID = "Invalid credentials for Management Group ID";
  String ERROR_INVALID_TENANT_CREDENTIALS = "Invalid credentials at Tenant Level";
  String AUTHORIZATION_ERROR = "does not have authorization";
  String AZURE_AUTH_PLUGIN_INSTALL_HINT = "kubelogin is required to authenticate to the current cluster.\n"
      + "It can be installed on the delegate by creating an immutable delegate and updating the following commands in INIT_SCRIPT\n"
      + "https://github.com/Azure/kubelogin/releases\n"
      + "\n"
      + "// Download kubelogin\n"
      + "curl https://github.com/Azure/kubelogin/releases/download/v0.0.27/kubelogin-linux-amd64.zip -L -o kubelogin.zip\n"
      + "unzip kubelogin.zip\n"
      + "chmod 755 /opt/harness-delegate/bin/linux_amd64/kubelogin\n"
      + "\n"
      + "// Add the binary to PATH\n"
      + "mv ./bin/linux_amd64/kubelogin /usr/local/bin\n"
      + "\n"
      + "// Verify the binary\n"
      + "kubelogin --version";
}
