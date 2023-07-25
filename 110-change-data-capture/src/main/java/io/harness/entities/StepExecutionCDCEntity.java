/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.entities;
import io.harness.ChangeHandler;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.changehandlers.HarnessApprovalStepExecutionHandler;
import io.harness.changehandlers.JiraStepExecutionHandler;
import io.harness.changehandlers.StepExecutionHandler;
import io.harness.execution.step.StepExecutionEntity;

import com.google.inject.Inject;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
public class StepExecutionCDCEntity implements CDCEntity<StepExecutionEntity> {
  @Inject private StepExecutionHandler stepExecutionHandler;
  @Inject private HarnessApprovalStepExecutionHandler harnessApprovalStepExecutionHandler;
  @Inject private JiraStepExecutionHandler jiraStepExecutionHandler;

  @Override
  public ChangeHandler getChangeHandler(String handlerClass) {
    if (handlerClass.contentEquals("StepExecutionHandler")) {
      return stepExecutionHandler;
    } else if (handlerClass.contentEquals("HarnessApprovalStepExecutionHandler")) {
      return harnessApprovalStepExecutionHandler;
    } else if (handlerClass.contentEquals("JiraStepExecutionHandler")) {
      return jiraStepExecutionHandler;
    }
    return null;
  }

  @Override
  public Class<StepExecutionEntity> getSubscriptionEntity() {
    return StepExecutionEntity.class;
  }
}
