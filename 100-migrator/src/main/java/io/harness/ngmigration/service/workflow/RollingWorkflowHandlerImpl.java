/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.workflow;

import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.WorkflowMigrationContext;

import software.wings.beans.Workflow;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.service.impl.yaml.handler.workflow.RollingWorkflowYamlHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import java.util.Map;

public class RollingWorkflowHandlerImpl extends WorkflowHandler {
  @Inject RollingWorkflowYamlHandler rollingWorkflowYamlHandler;

  //  .failureStrategies(Collections.singletonList(
  //      FailureStrategyConfig.builder()
  //                        .onFailure(OnFailureConfig.builder()
  //                                       .errors(Collections.singletonList(NGFailureType.ALL_ERRORS))
  //      .action(StageRollbackFailureActionConfig.builder().build())
  //      .build())
  //      .build()))

  public JsonNode getTemplateSpec(
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities, Workflow workflow) {
    return getDeploymentStageTemplateSpec(WorkflowMigrationContext.newInstance(entities, migratedEntities, workflow));
  }

  @Override
  public ServiceDefinitionType inferServiceDefinitionType(Workflow workflow) {
    // We can infer the type based on the service, infra & sometimes based on the steps used.
    // TODO: Deepak Puthraya
    return ServiceDefinitionType.KUBERNETES;
  }
}
