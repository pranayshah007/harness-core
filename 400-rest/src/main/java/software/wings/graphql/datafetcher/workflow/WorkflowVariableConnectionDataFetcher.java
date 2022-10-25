/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.workflow;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.logging.logcontext.AccountLogContext;
import io.harness.logging.logcontext.AutoLogContext;

import software.wings.beans.Variable;
import software.wings.beans.Workflow;
import software.wings.graphql.datafetcher.AbstractArrayDataFetcher;
import software.wings.graphql.datafetcher.VariableController;
import software.wings.graphql.schema.query.QLWorkflowVariableQueryParam;
import software.wings.graphql.schema.type.QLVariable;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.WorkflowService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class WorkflowVariableConnectionDataFetcher
    extends AbstractArrayDataFetcher<QLVariable, QLWorkflowVariableQueryParam> {
  @Inject WorkflowService workflowService;

  @Override
  @AuthRule(permissionType = PermissionAttribute.PermissionType.WORKFLOW, action = PermissionAttribute.Action.READ)
  protected List<QLVariable> fetch(QLWorkflowVariableQueryParam parameters, String accountId) {
    try (AutoLogContext ignore0 = new AccountLogContext(accountId, AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
      String workflowId = parameters.getWorkflowId();
      String appId = parameters.getApplicationId();
      Workflow workflow = workflowService.readWorkflow(appId, workflowId);
      notNullCheck("Workflow " + workflowId + " doesn't exist in the specified application " + appId, workflow, USER);
      notNullCheck(
          "Error reading workflow " + workflowId + " Might be deleted", workflow.getOrchestrationWorkflow(), USER);
      if (!workflow.isTemplatized()) {
        log.info("No non-fixed variables present in workflow: " + workflowId);
        return new ArrayList<>();
      }
      List<Variable> userVariables = workflow.getOrchestrationWorkflow().getUserVariables();
      List<QLVariable> qlVariables = new ArrayList<>();
      VariableController.populateVariables(userVariables, qlVariables);
      return qlVariables;
    }
  }

  @Override
  protected QLVariable unusedReturnTypePassingDummyMethod() {
    return null;
  }
}
