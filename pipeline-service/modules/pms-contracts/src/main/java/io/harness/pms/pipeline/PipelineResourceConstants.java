/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline;

public interface PipelineResourceConstants {
  String ACCOUNT_PARAM_MESSAGE = "Account Identifier for the Entity.";
  String ORG_PARAM_MESSAGE = "Organization Identifier for the Entity.";
  String PROJECT_PARAM_MESSAGE = "Project Identifier for the Entity.";
  String PIPELINE_ID_PARAM_MESSAGE = "Pipeline Identifier";
  String PIPELINE_NAME_PARAM_MESSAGE = "Pipeline Name";

  String PIPELINE_DRAFT_PARAM_MESSAGE = "Save the pipeline as Draft";

  String PIPELINE_DESCRIPTION_PARAM_MESSAGE = "Pipeline Description";
  String STAGE_NODE_ID_PARAM_MESSAGE = "Stage Node Identifier for which Stage Graph needs to be Rendered";
  String STAGE_NODE_EXECUTION_PARAM_MESSAGE =
      "Stage Node Execution ID for which Stage Graph needs to be Rendered. (Needed only when there are Multiple Runs for a Given Stage. It can be Extracted from LayoutNodeMap Field)";

  String GENERATE_FULL_GRAPH_PARAM_MESSAGE = "Generate Graph for all the Stages including Steps in each Stage";
  String INPUT_INSTANCE_PARAM = "Input Instance Identifier for Execution Input";

  String PIPELINE_ID_LIST_PARAM_MESSAGE = "Pipeline Identifier filter if exact pipelines needs to be filtered.";
  String PIPELINE_SEARCH_TERM_PARAM_MESSAGE =
      "Search term to filter out pipelines based on pipeline name, identifier, tags.";
  String INPUT_SET_SEARCH_TERM_PARAM_MESSAGE = "Search term to filter out Input Sets based on name, identifier, tags.";
  String IF_MATCH_PARAM_MESSAGE = "Version of Entity to match";
  String MODULE_TYPE_PARAM_MESSAGE = "The module from which execution was triggered.";
  String ORIGINAL_EXECUTION_ID_PARAM_MESSAGE = "Id of the execution from which we are running";
  String USE_FQN_IF_ERROR_RESPONSE_ERROR_MESSAGE = "Use FQN in error response";
  String INPUT_SET_ID_PARAM_MESSAGE = "Identifier for the Input Set";
  String OVERLAY_INPUT_SET_ID_PARAM_MESSAGE = "Identifier for the Overlay Input Set";
  String CREATED_AT_MESSAGE = "Time at which the entity was created";
  String UPDATED_AT_MESSAGE = "Time at which the entity was last updated";
  String GIT_DETAILS_MESSAGE = "This contains the Git Details of the entity if the Project is Git enabled";
  String GIT_VALIDITY_MESSAGE =
      "For git synced entities, this field tells if the entity synced from git is valid or not";

  String START_TIME_EPOCH_PARAM_MESSAGE = "Start Date Epoch time in ms";
  String END_TIME_EPOCH_PARAM_MESSAGE = "End Date Epoch time in ms";
  String GET_METADATA_ONLY_PARAM_KEY = "getMetadataOnly";
  String NOTES_FOR_PLAN_EXECUTION_PARAM_MESSAGE = "Notes of a pipeline execution";
}
