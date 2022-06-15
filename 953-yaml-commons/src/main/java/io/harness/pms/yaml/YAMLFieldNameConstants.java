/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(CDC)
public class YAMLFieldNameConstants {
  public final String EXECUTION = "execution";
  public final String PIPELINE = "pipeline";
  public final String CI_CODE_BASE = "codebase";
  public final String PROPERTIES = "properties";
  public final String CI = "ci";
  public final String SECURITY = "security";
  public final String PARALLEL = "parallel";
  public final String SPEC = "spec";
  public final String STAGE = "stage";
  public final String STAGES = "stages";
  public final String STRATEGY = "strategy";

  public final String STEP = "step";
  public final String STEPS = "steps";
  public final String STEP_GROUP = "stepGroup";
  public final String ROLLBACK_STEPS = "rollbackSteps";
  public final String FAILURE_STRATEGIES = "failureStrategies";
  public final String NAME = "name";
  public final String IDENTIFIER = "identifier";
  public final String DESCRIPTION = "description";
  public final String TAGS = "tags";
  public final String VARIABLES = "variables";
  public final String SERVICE_VARIABLES = "serviceVariables";
  public final String TYPE = "type";
  public final String KEY = "key";
  public final String VALUE = "value";
  public final String UUID = YamlNode.UUID_FIELD_NAME;
  public final String TIMEOUT = "timeout";
  public final String OUTPUT_VARIABLES = "outputVariables";
  public final String HEADERS = "headers";
  public final String OUTPUT = "output";
  public final String INPUT = "input";
  public final String ENVIRONMENT = "environment";
  public final String PROVISIONER = "provisioner";
  public final String CONNECTOR_REF = "connectorRef";
  public final String FILE_REF = "fileRef";
  public final String CODEBASE_CONNECTOR_REF = "ciCodebase.connectorRef";
  public final String USE_ROLLBACK_STRATEGY = "useRollbackStrategy";
  public final String FAILED_CHILDREN_OUTPUT = "failedChildrenOutput";
  public final String STOP_STAGE_SEQUENCE = "stopStageSequence";

  public final String STORE = "store";
  public final String PIPELINE_INFRASTRUCTURE = "infrastructure";
  public final String COMMAND_TYPE = "commandType";
  public final String STOP_STEPS_SEQUENCE = "stopStepsSequence";
  public final String DELEGATE_SELECTORS = "delegateSelectors";

  public final String CONFIGURATION = "configuration";
  public final String TEMPLATE = "template";
  public final String TEMPLATE_INPUTS = "templateInputs";

  public final String BASE_IMAGE_CONNECTOR_REFS = "baseImageConnectorRefs";
  public final String HARNESS_IMAGE_CONNECTOR_REF = "harnessImageConnectorRef";

  public final String CHILD_NODE_OF_SPEC = "childNodeOfSpec";
  public final String GITOPS_ENABLED = "gitOpsEnabled";

  public final String ORG_IDENTIFIER = "orgIdentifier";
  public final String PROJECT_IDENTIFIER = "projectIdentifier";
}
