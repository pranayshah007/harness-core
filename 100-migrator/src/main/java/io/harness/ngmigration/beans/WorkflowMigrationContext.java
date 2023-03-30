/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.beans;

import io.harness.ngmigration.expressions.step.StepExpressionFunctor;
import io.harness.ngmigration.utils.CaseFormat;

import software.wings.beans.Workflow;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowMigrationContext {
  private List<StepExpressionFunctor> stepExpressionFunctors;
  private Map<CgEntityId, CgEntityNode> entities;
  private Map<CgEntityId, NGYamlFile> migratedEntities;
  private Workflow workflow;
  private CaseFormat identifierCaseFormat;
  private boolean templatizeStepParams;

  public static WorkflowMigrationContext newInstance(MigrationContext migrationContext, Workflow workflow) {
    return WorkflowMigrationContext.builder()
        .workflow(workflow)
        .entities(migrationContext.getEntities())
        .migratedEntities(migrationContext.getMigratedEntities())
        .stepExpressionFunctors(new ArrayList<>())
        .identifierCaseFormat(migrationContext.getInputDTO().getIdentifierCaseFormat())
        .templatizeStepParams(false)
        .build();
  }
}
