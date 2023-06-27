/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.delegate.task.shell.ShellScriptTaskNG.COMMAND_UNIT;
import static io.harness.security.dto.PrincipalType.USER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.IdentifierRef;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.exception.InvalidRequestException;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.dto.UserGroupFilterDTO;
import io.harness.ng.core.dto.UserGroupFilterDTO.UserGroupFilterDTOBuilder;
import io.harness.ng.core.user.UserInfo;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.helpers.CurrentUserHelper;
import io.harness.remote.client.CGRestUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.security.dto.Principal;
import io.harness.steps.approval.step.ApprovalInstanceResponseMapper;
import io.harness.steps.approval.step.ApprovalInstanceService;
import io.harness.steps.approval.step.beans.ApprovalInstanceResponseDTO;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalAction;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalActivityRequestDTO;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalInstanceAuthorizationDTO;
import io.harness.steps.approval.step.harness.entities.HarnessApprovalInstance;
import io.harness.user.remote.UserClient;
import io.harness.usergroups.UserGroupClient;
import io.harness.utils.IdentifierRefHelper;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(CDC)
@Slf4j
public class ApprovalResourceServiceImpl implements ApprovalResourceService {
  private final ApprovalInstanceService approvalInstanceService;
  private final ApprovalInstanceResponseMapper approvalInstanceResponseMapper;
  private final PlanExecutionService planExecutionService;
  private final UserGroupClient userGroupClient;
  private final CurrentUserHelper currentUserHelper;
  private final LogStreamingStepClientFactory logStreamingStepClientFactory;
  private final UserClient userClient;

  @Inject
  public ApprovalResourceServiceImpl(ApprovalInstanceService approvalInstanceService,
      ApprovalInstanceResponseMapper approvalInstanceResponseMapper, PlanExecutionService planExecutionService,
      UserGroupClient userGroupClient, CurrentUserHelper currentUserHelper, UserClient userClient,
      LogStreamingStepClientFactory logStreamingStepClientFactory) {
    this.approvalInstanceService = approvalInstanceService;
    this.approvalInstanceResponseMapper = approvalInstanceResponseMapper;
    this.planExecutionService = planExecutionService;
    this.userGroupClient = userGroupClient;
    this.currentUserHelper = currentUserHelper;
    this.userClient = userClient;
    this.logStreamingStepClientFactory = logStreamingStepClientFactory;
  }

  @Override
  public ApprovalInstanceResponseDTO get(String approvalInstanceId) {
    return approvalInstanceResponseMapper.toApprovalInstanceResponseDTO(
        approvalInstanceService.get(approvalInstanceId));
  }

  @Override
  public ApprovalInstanceResponseDTO addHarnessApprovalActivity(
      @NotNull String approvalInstanceId, @NotNull @Valid HarnessApprovalActivityRequestDTO request) {
    if (!getHarnessApprovalInstanceAuthorization(approvalInstanceId, false).isAuthorized()) {
      throw new InvalidRequestException("User not authorized to approve/reject");
    }

    HarnessApprovalInstance instance =
        approvalInstanceService.addHarnessApprovalActivity(approvalInstanceId, getEmbeddedUser(), request);
    if (request.getAction() == HarnessApprovalAction.APPROVE) {
      rejectPreviousExecutions(instance);
    }
    approvalInstanceService.closeHarnessApprovalStep(instance);
    return approvalInstanceResponseMapper.toApprovalInstanceResponseDTO(instance);
  }

  public void rejectPreviousExecutions(HarnessApprovalInstance instance) {
    if (instance.getIsAutoRejectEnabled() == null || !instance.getIsAutoRejectEnabled()) {
      return;
    }
    Ambiance ambiance = instance.getAmbiance();
    String accountId = instance.getAccountId();
    String orgId = instance.getOrgIdentifier();
    String projectId = instance.getProjectIdentifier();
    String pipelineId = instance.getPipelineIdentifier();
    String approvalKey = instance.getApprovalKey();
    List<String> rejectedApprovalIds = approvalInstanceService.findAllPreviousWaitingApprovals(
        accountId, orgId, projectId, pipelineId, approvalKey, ambiance);
    final long[] cnt = {0};
    rejectedApprovalIds.forEach(id -> {
      boolean unauthorized = !getHarnessApprovalInstanceAuthorization(id, true).isAuthorized();
      if (!unauthorized) {
        cnt[0]++;
      }
      approvalInstanceService.rejectPreviousExecutions(id, getEmbeddedUser(), unauthorized, ambiance);
    });
    NGLogCallback logCallback = new NGLogCallback(logStreamingStepClientFactory, ambiance, COMMAND_UNIT, false);
    logCallback.saveExecutionLog(String.format(
        "Successfully rejected %s previous executions waiting for approval on this step that the user was authorized to reject",
        cnt[0]));
  }

  private EmbeddedUser getEmbeddedUser() {
    Principal principal = currentUserHelper.getPrincipalFromSecurityContext();
    if (!USER.equals(principal.getType())) {
      // TODO: handle api key and service account approvals
      throw new InvalidRequestException(principal.getType() + " is not supported for Harness Approval Step yet");
    }

    String userId = principal.getName();
    Optional<UserInfo> userOptional = CGRestUtils.getResponse(userClient.getUserById(userId));
    if (!userOptional.isPresent()) {
      throw new InvalidRequestException(String.format("Invalid user: %s", userId));
    }
    UserInfo user = userOptional.get();
    return EmbeddedUser.builder().uuid(user.getUuid()).name(user.getName()).email(user.getEmail()).build();
  }

  @Override
  public List<ApprovalInstanceResponseDTO> getApprovalInstancesByExecutionId(@NotEmpty String planExecutionId,
      @Valid ApprovalStatus approvalStatus, @Valid ApprovalType approvalType, String nodeExecutionId) {
    List<ApprovalInstance> approvalInstances = approvalInstanceService.getApprovalInstancesByExecutionId(
        planExecutionId, approvalStatus, approvalType, nodeExecutionId);
    return approvalInstances.stream()
        .map(approvalInstanceResponseMapper::toApprovalInstanceResponseDTO)
        .collect(Collectors.toList());
  }

  @Override
  public HarnessApprovalInstanceAuthorizationDTO getHarnessApprovalInstanceAuthorization(
      @NotNull String approvalInstanceId, boolean skipHasAlreadyApprovedValidation) {
    EmbeddedUser user = getEmbeddedUser();
    HarnessApprovalInstance instance = approvalInstanceService.getHarnessApprovalInstance(approvalInstanceId);

    // Check if the user has already approved/rejected.
    if (alreadyHasApprovalActivity(instance, user) && !skipHasAlreadyApprovedValidation) {
      return HarnessApprovalInstanceAuthorizationDTO.builder()
          .authorized(false)
          .reason("You have already approved/rejected the pipeline")
          .build();
    }

    // Check if the user is the pipeline executor.
    if (instance.getApprovers().isDisallowPipelineExecutor()) {
      ExecutionMetadata metadata =
          planExecutionService.getExecutionMetadataFromPlanExecution(instance.getAmbiance().getPlanExecutionId());
      if (metadata != null && metadata.hasTriggerInfo() && metadata.getTriggerInfo().hasTriggeredBy()
          && metadata.getTriggerInfo().getTriggeredBy().getUuid().equals(user.getUuid())) {
        return HarnessApprovalInstanceAuthorizationDTO.builder()
            .authorized(false)
            .reason("Pipeline executor is not allowed to approve/reject")
            .build();
      }
    }

    // Check if the user is member of any user groups given in step parameters. If there are no user groups configures,
    // we do not allow approval/rejection.
    if (!isMemberOfUserGroups(instance, user)) {
      return HarnessApprovalInstanceAuthorizationDTO.builder()
          .authorized(false)
          .reason("You are not authorized to approve/reject")
          .build();
    }
    return HarnessApprovalInstanceAuthorizationDTO.builder().authorized(true).build();
  }

  @Override
  public String getYamlSnippet(ApprovalType approvalType, String accountId) throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    String yamlFile = approvalType.getDisplayName();

    return Resources.toString(
        Objects.requireNonNull(classLoader.getResource(String.format("approval_stage_yamls/%s.yaml", yamlFile))),
        StandardCharsets.UTF_8);
  }

  private boolean alreadyHasApprovalActivity(HarnessApprovalInstance instance, EmbeddedUser user) {
    if (EmptyPredicate.isEmpty(instance.getApprovalActivities())) {
      return false;
    }
    return instance.getApprovalActivities().stream().anyMatch(aa -> aa.getUser().getUuid().equals(user.getUuid()));
  }

  private boolean isMemberOfUserGroups(HarnessApprovalInstance instance, EmbeddedUser user) {
    List<String> userGroups = instance.getApprovers().getUserGroups();
    if (EmptyPredicate.isEmpty(userGroups)) {
      return false;
    }

    Ambiance ambiance = instance.getAmbiance();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    Map<Scope, List<IdentifierRef>> identifierRefs =
        new HashSet<>(userGroups)
            .stream()
            .map(ug -> IdentifierRefHelper.getIdentifierRef(ug, accountId, orgId, projectId))
            .collect(Collectors.groupingBy(IdentifierRef::getScope));

    List<UserGroupFilterDTO> userGroupFilters = ImmutableList.of(Scope.ACCOUNT, Scope.ORG, Scope.PROJECT)
                                                    .stream()
                                                    // Find user groups corresponding to each scope.
                                                    .map(identifierRefs::get)
                                                    // Create a user group filter for each scope.
                                                    .map(l -> prepareUserGroupFilter(l, user))
                                                    // Remove any scope that doesn't have any user group.
                                                    .filter(Optional::isPresent)
                                                    .map(Optional::get)
                                                    .collect(Collectors.toList());
    if (EmptyPredicate.isEmpty(userGroupFilters)) {
      return false;
    }

    for (UserGroupFilterDTO userGroupFilter : userGroupFilters) {
      if (EmptyPredicate.isNotEmpty(NGRestUtils.getResponse(userGroupClient.getFilteredUserGroups(userGroupFilter)))) {
        return true;
      }
    }
    return false;
  }

  private Optional<UserGroupFilterDTO> prepareUserGroupFilter(List<IdentifierRef> identifierRefs, EmbeddedUser user) {
    if (EmptyPredicate.isEmpty(identifierRefs)) {
      return Optional.empty();
    }

    IdentifierRef identifierRef = identifierRefs.get(0);
    UserGroupFilterDTOBuilder builder =
        UserGroupFilterDTO.builder()
            .identifierFilter(identifierRefs.stream().map(IdentifierRef::getIdentifier).collect(Collectors.toSet()))
            .userIdentifierFilter(Collections.singleton(user.getUuid()));
    switch (identifierRef.getScope()) {
      case ACCOUNT:
        builder.accountIdentifier(identifierRef.getAccountIdentifier());
        break;
      case ORG:
        builder.accountIdentifier(identifierRef.getAccountIdentifier()).orgIdentifier(identifierRef.getOrgIdentifier());
        break;
      case PROJECT:
        builder.accountIdentifier(identifierRef.getAccountIdentifier())
            .orgIdentifier(identifierRef.getOrgIdentifier())
            .projectIdentifier(identifierRef.getProjectIdentifier());
        break;
      default:
        return Optional.empty();
    }
    return Optional.of(builder.build());
  }
}
