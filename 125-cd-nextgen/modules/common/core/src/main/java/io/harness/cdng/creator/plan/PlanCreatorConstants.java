/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.CDC)
public class PlanCreatorConstants {
  public final String INFRA_SECTION_NODE_IDENTIFIER = "infrastructure";
  public final String INFRA_SECTION_NODE_NAME = "Infrastructure Section";
  public final String INFRA_DEFINITION_NODE_IDENTIFIER = "infrastructureDefinition";
  public final String STEP_GROUPS_ROLLBACK_NODE_IDENTIFIER = "stepGroupsRollback";
  public final String PARALLEL_STEP_GROUPS_ROLLBACK_NODE_IDENTIFIER = "parallelStepGroupsRollback";
  public final String PARALLEL_STEP_GROUPS_ROLLBACK_NODE_NAME = "Parallel StepGroups (Rollback)";
  public final String SERVICE_NODE_NAME = "Service";
  public final String ENVIRONMENT_NODE_NAME = "Environment";
  public final String SERVICE_DEFINITION_NODE_NAME = "ServiceDefinition";
  public final String SERVICE_SPEC_NODE_NAME = "ServiceSpec";
  public final String ARTIFACTS_NODE_NAME = "Artifacts";
  public final String SIDECARS_NODE_NAME = "Sidecars";
  public final String ARTIFACT_NODE_NAME = "Artifact";
  public final String MANIFESTS_NODE_NAME = "Manifests";
  public final String MANIFEST_NODE_NAME = "Manifest";
  public final String INFRA_NODE_NAME = "Infrastructure";
  public final String GITOPS_INFRA_NODE_NAME = "GitopsClusters";
  public final String SPEC_IDENTIFIER = "spec";
  public final String CONFIG_FILES_NODE_NAME = "ConfigFiles";
  public final String CONFIG_FILE_NODE_NAME = "ConfigFile";
  public final String STARTUP_COMMAND = "startupCommand";
  public final String APPLICATION_SETTINGS = "applicationSettings";
  public final String CONNECTION_STRINGS = "connectionStrings";
  public final String ELASTIGROUP_SERVICE_SETTINGS_NODE = "elastigroupService";
  public final String SERVICE_HOOKS_NODE_NAME = "ServiceHooks";

  // DependencyMetadata constants
  public final String PRIMARY_STEP_PARAMETERS = "primaryStepParameters";
  public final String SIDECARS_PARAMETERS_MAP = "sideCarsParametersMap";
  public final String IDENTIFIER = "identifier";
  public final String SIDECAR_STEP_PARAMETERS = "sideCarsStepParameters";
  public final String MANIFEST_STEP_PARAMETER = "manifestStepParameters";
  public final String CONFIG_FILE_STEP_PARAMETER = "configFileStepParameters";
  public final String STARTUP_COMMAND_STEP_PARAMETER = "startupCommandStepParameters";
  public final String APPLICATION_SETTINGS_STEP_PARAMETER = "applicationSettingsStepParameters";
  public final String CONNECTION_STRINGS_STEP_PARAMETER = "connectionStringsStepParameters";
}
