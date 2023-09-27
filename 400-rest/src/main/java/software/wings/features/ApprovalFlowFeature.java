/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.features;

import static software.wings.features.utils.PipelineUtils.getPipelinesPageRequest;
import static software.wings.features.utils.PipelineUtils.getPipelinesWithApprovalSteps;
import static software.wings.features.utils.WorkflowUtils.getWorkflowsPageRequest;
import static software.wings.features.utils.WorkflowUtils.getWorkflowsWithApprovalSteps;
import static software.wings.features.utils.WorkflowUtils.toDisallowedApprovalSteps;

import static java.util.stream.Collectors.toList;

import software.wings.beans.Pipeline;
import software.wings.beans.Workflow;
import software.wings.features.api.AbstractPremiumFeature;
import software.wings.features.api.ComplianceByRefactoringUsage;
import software.wings.features.api.FeatureRestrictions;
import software.wings.features.api.Usage;
import software.wings.features.utils.PipelineUtils;
import software.wings.features.utils.WorkflowUtils;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.WorkflowService;
import software.wings.sm.states.ApprovalState.ApprovalStateType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Singleton
public class ApprovalFlowFeature extends AbstractPremiumFeature implements ComplianceByRefactoringUsage {
  public static final String FEATURE_NAME = "APPROVAL_FLOW";
  public static final String ALLOWED_APPROVAL_STEPS_KEY = "allowedApprovalStepTypes";

  private final PipelineService pipelineService;
  private final WorkflowService workflowService;

  @Inject
  public ApprovalFlowFeature(AccountService accountService, FeatureRestrictions featureRestrictions,
      PipelineService pipelineService, WorkflowService workflowService) {
    super(accountService, featureRestrictions);
    this.pipelineService = pipelineService;
    this.workflowService = workflowService;
  }

  @Override
  public boolean isAvailable(String accountType) {
    List<String> allowedApprovalStepTypes =
        (List<String>) getRestrictions(accountType).getOrDefault("allowedApprovalStepTypes", Collections.emptyList());

    return allowedApprovalStepTypes.isEmpty();
  }

  @Override
  public boolean isBeingUsed(String accountId) {
    return !getUsages(accountId, toDisallowedApprovalSteps(getRestrictionsForAccount(accountId))).isEmpty();
  }

  @Override
  public Collection<Usage> getDisallowedUsages(String accountId, String targetAccountType) {
    if (isAvailable(targetAccountType)) {
      return Collections.emptyList();
    }

    return getUsages(accountId, toDisallowedApprovalSteps(getRestrictionsForAccount(accountId)));
  }

  @Override
  public String getFeatureName() {
    return FEATURE_NAME;
  }

  private Set<Usage> getUsages(String accountId, Set<ApprovalStateType> approvalStepTypes) {
    Set<Usage> usages = new HashSet<>();

    usages.addAll(getWorkflowsWithApprovalSteps(getAllWorkflowsByAccountId(accountId), approvalStepTypes)
                      .stream()
                      .map(WorkflowUtils::toUsage)
                      .collect(toList()));

    usages.addAll(getPipelinesWithApprovalSteps(getAllPipelinesByAccountId(accountId), approvalStepTypes)
                      .stream()
                      .map(PipelineUtils::toUsage)
                      .collect(toList()));

    return usages;
  }

  private List<Pipeline> getAllPipelinesByAccountId(String accountId) {
    return pipelineService.listPipelines(getPipelinesPageRequest(accountId), true, accountId);
  }

  private List<Workflow> getAllWorkflowsByAccountId(String accountId) {
    return workflowService.listWorkflows(getWorkflowsPageRequest(accountId)).getResponse();
  }
}
