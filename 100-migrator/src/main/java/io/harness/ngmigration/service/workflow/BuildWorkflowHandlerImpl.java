/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.workflow;

import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.WorkflowMigrationContext;

import software.wings.beans.Workflow;
import software.wings.service.impl.yaml.handler.workflow.BuildWorkflowYamlHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;

public class BuildWorkflowHandlerImpl extends WorkflowHandler {
  @Inject BuildWorkflowYamlHandler buildWorkflowYamlHandler;

  @Override
  public JsonNode getTemplateSpec(MigrationContext migrationContext, Workflow workflow) {
    return getCustomStageTemplateSpec(
        migrationContext, WorkflowMigrationContext.newInstance(migrationContext, workflow));
  }
}
