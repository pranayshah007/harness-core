/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.harness.entities;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.DbAliases;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.steps.approval.step.beans.ApprovalUserGroupDTO;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.harness.HarnessApprovalOutcome;
import io.harness.steps.approval.step.harness.HarnessApprovalSpecParameters;
import io.harness.steps.approval.step.harness.beans.ApproverInput;
import io.harness.steps.approval.step.harness.beans.ApproverInputInfoDTO;
import io.harness.steps.approval.step.harness.beans.ApproversDTO;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalAction;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalActivity;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalActivityDTO;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalActivityRequestDTO;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Transient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDC)
@Data
@Builder
@FieldNameConstants(innerTypeName = "HarnessApprovalInstanceKeys")
@EqualsAndHashCode(callSuper = true)
@StoreIn(DbAliases.PMS)
@Entity(value = "approvalInstances", noClassnameStored = true)
@Persistent
@TypeAlias("harnessApprovalInstance")
public class HarnessApprovalInstance extends ApprovalInstance {
  @NotNull String approvalMessage;
  boolean includePipelineExecutionHistory;

  @NotNull List<HarnessApprovalActivity> approvalActivities;
  @NotNull ApproversDTO approvers;
  List<ApproverInputInfoDTO> approverInputs;
  @Transient private transient List<UserGroupDTO> validatedUserGroups;
  List<ApprovalUserGroupDTO> validatedApprovalUserGroups;
  String approvalKey;
  @Builder.Default Boolean isAutoRejectEnabled = Boolean.FALSE;

  public Optional<HarnessApprovalActivity> fetchLastApprovalActivity() {
    if (EmptyPredicate.isEmpty(approvalActivities)) {
      return Optional.empty();
    }
    return Optional.of(approvalActivities.get(approvalActivities.size() - 1));
  }

  public void addApprovalActivity(EmbeddedUser user, HarnessApprovalActivityRequestDTO request) {
    if (request.getAction() == HarnessApprovalAction.APPROVE
        && !validateApprovalInputsRequest(request.getApproverInputs())) {
      throw new InvalidRequestException("Invalid approver inputs");
    }

    HarnessApprovalActivity approvalActivity =
        HarnessApprovalActivity.fromHarnessApprovalActivityRequestDTO(user, request);
    if (approvalActivity == null) {
      return;
    }

    if (EmptyPredicate.isEmpty(approvalActivities)) {
      approvalActivities = Collections.singletonList(approvalActivity);
    } else {
      approvalActivities = new ArrayList<>(approvalActivities);
      approvalActivities.add(approvalActivity);
    }
  }

  private boolean validateApprovalInputsRequest(List<ApproverInput> requestInputs) {
    if (EmptyPredicate.isEmpty(requestInputs) || EmptyPredicate.isEmpty(approverInputs)) {
      return EmptyPredicate.isEmpty(requestInputs) && EmptyPredicate.isEmpty(approverInputs);
    }
    if (requestInputs.size() != approverInputs.size()) {
      return false;
    }

    Set<String> names =
        approverInputs.stream().map(ApproverInputInfoDTO::getName).filter(Objects::nonNull).collect(Collectors.toSet());
    if (names.size() != approverInputs.size()) {
      return false;
    }

    for (ApproverInput requestInput : requestInputs) {
      if (requestInput != null && requestInput.getName() != null) {
        names.remove(requestInput.getName());
      }
    }
    return names.size() == 0;
  }

  public HarnessApprovalOutcome toHarnessApprovalOutcome() {
    List<ApproverInput> approverInputs =
        fetchLastApprovalActivity().map(HarnessApprovalActivity::getApproverInputs).orElse(null);
    return HarnessApprovalOutcome.builder()
        .approvalActivities(approvalActivities == null
                ? null
                : approvalActivities.stream()
                      .map(HarnessApprovalActivityDTO::fromHarnessApprovalActivity)
                      .collect(Collectors.toList()))
        .approverInputs(approverInputs == null
                ? Collections.emptyMap()
                : approverInputs.stream().collect(Collectors.toMap(ApproverInput::getName, ApproverInput::getValue)))
        .build();
  }

  public static HarnessApprovalInstance fromStepParameters(Ambiance ambiance, StepElementParameters stepParameters) {
    if (stepParameters == null) {
      return null;
    }

    String stageIdentifier = "";
    Optional<Level> stageLevel = AmbianceUtils.getStageLevelFromAmbiance(ambiance);
    if (stageLevel.isPresent()) {
      stageIdentifier = stageLevel.get().getIdentifier();
    }
    HarnessApprovalSpecParameters specParameters = (HarnessApprovalSpecParameters) stepParameters.getSpec();
    HarnessApprovalInstance instance =
        HarnessApprovalInstance.builder()
            .approvalMessage((String) specParameters.getApprovalMessage().fetchFinalValue())
            .includePipelineExecutionHistory(
                (boolean) specParameters.getIncludePipelineExecutionHistory().fetchFinalValue())
            .approvalActivities(new ArrayList<>())
            .approvers(ApproversDTO.fromApprovers(specParameters.getApprovers()))
            .approverInputs(specParameters.getApproverInputs() == null
                    ? null
                    : specParameters.getApproverInputs()
                          .stream()
                          .map(ApproverInputInfoDTO::fromApproverInputInfo)
                          .collect(Collectors.toList()))
            .isAutoRejectEnabled(specParameters.getIsAutoRejectEnabled() != null
                && specParameters.getIsAutoRejectEnabled().fetchFinalValue() != null
                && (boolean) specParameters.getIsAutoRejectEnabled().fetchFinalValue())
            .approvalKey(stageIdentifier + "#" + stepParameters.getIdentifier())
            .build();
    instance.updateFromStepParameters(ambiance, stepParameters);
    return instance;
  }
}
