/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.internal;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;
import static java.util.Objects.isNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.common.NGExpressionUtils;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.filters.GenericStepPMSFilterJsonCreatorV2;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.exception.runtime.InvalidYamlRuntimeException;
import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.approval.step.harness.HarnessApprovalSpecParameters;
import io.harness.steps.approval.step.harness.HarnessApprovalStepNode;
import io.harness.steps.approval.step.harness.beans.Approvers;
import io.harness.timeout.TimeoutParameters;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.TimeStampUtils;
import io.harness.yaml.core.timeout.Timeout;

import com.google.common.collect.Sets;
import io.dropwizard.util.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Slf4j
public class HarnessApprovalStepFilterJsonCreatorV2 extends GenericStepPMSFilterJsonCreatorV2 {
  private final long MINIMUM_TIME_REQUIRED_FOR_AUTO_APPROVAL = Duration.minutes(15).toMilliseconds();

  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.HARNESS_APPROVAL);
  }

  @Override
  public FilterCreationResponse handleNode(FilterCreationContext filterCreationContext, AbstractStepNode yamlField) {
    super.handleNode(filterCreationContext, yamlField);
    HarnessApprovalStepNode harnessApprovalStepNode = (HarnessApprovalStepNode) yamlField;
    HarnessApprovalSpecParameters harnessApprovalSpecParameters =
        (HarnessApprovalSpecParameters) harnessApprovalStepNode.getHarnessApprovalStepInfo().getSpecParameters();

    validateUserGroups(harnessApprovalSpecParameters, filterCreationContext);
    validateTimestampForAutoApproval(harnessApprovalSpecParameters, filterCreationContext, harnessApprovalStepNode);
    return FilterCreationResponse.builder().build();
  }

  private void validateTimestampForAutoApproval(HarnessApprovalSpecParameters harnessApprovalSpecParameters,
      FilterCreationContext filterCreationContext, HarnessApprovalStepNode harnessApprovalStepNode) {
    if (harnessApprovalSpecParameters.getAutoApproval() != null
        && harnessApprovalSpecParameters.getAutoApproval().getScheduledDeadline() != null) {
      String timeZone = getTimeZoneFromSchedule(harnessApprovalSpecParameters);
      String time = getTimeStampFromSchedule(harnessApprovalSpecParameters);

      if (isNotEmpty(time) && isNotEmpty(timeZone) && harnessApprovalStepNode.getTimeout().getValue() != null) {
        Long timeoutForStep = getTimeoutForStep(harnessApprovalStepNode.getTimeout().getValue());
        Long autoApprovalTimeout = TimeStampUtils.getTotalDurationWRTCurrentTimeFromTimeStamp(time, timeZone);
        validateAutoApprovalTimeDuration(timeoutForStep, autoApprovalTimeout, filterCreationContext);
      }
    }
  }

  private void validateUserGroups(
      HarnessApprovalSpecParameters harnessApprovalSpecParameters, FilterCreationContext filterCreationContext) {
    Scope contextScopeLevel = Scope.builder()
                                  .accountIdentifier(filterCreationContext.getSetupMetadata().getAccountId())
                                  .orgIdentifier(filterCreationContext.getSetupMetadata().getOrgId())
                                  .projectIdentifier(filterCreationContext.getSetupMetadata().getProjectId())
                                  .build();
    Approvers approvers = harnessApprovalSpecParameters.getApprovers();
    if (isNull(approvers) || ParameterField.isNull(approvers.getUserGroups())
        || approvers.getUserGroups().isExpression() || isEmpty(approvers.getUserGroups().getValue())) {
      return;
    }
    List<String> userGroupsIds = approvers.getUserGroups().getValue();
    List<String> userGroupsIdsWithInvalidScope = new ArrayList<>();
    userGroupsIds.forEach(ug -> {
      try {
        if (NGExpressionUtils.isRuntimeOrExpressionField(ug)) {
          return;
        }
        IdentifierRefHelper.validateEntityScopes(contextScopeLevel.getAccountIdentifier(),
            contextScopeLevel.getOrgIdentifier(), contextScopeLevel.getProjectIdentifier(), ug, "user group");
      } catch (InvalidRequestException ex) {
        log.error(ExceptionUtils.getMessage(ex), ex);
        userGroupsIdsWithInvalidScope.add(ug);
      }
    });
    if (isNotEmpty(userGroupsIdsWithInvalidScope)) {
      throw new InvalidYamlRuntimeException(format(
          "User groups %s provided for step %s are either in invalid format or belong to scope higher than the current scope. Please correct them & try again.",
          userGroupsIdsWithInvalidScope,
          YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
    }
  }

  private String getTimeZoneFromSchedule(HarnessApprovalSpecParameters harnessApprovalSpecParameters) {
    return harnessApprovalSpecParameters.getAutoApproval().getScheduledDeadline().getTimeZone();
  }

  private String getTimeStampFromSchedule(HarnessApprovalSpecParameters harnessApprovalSpecParameters) {
    return harnessApprovalSpecParameters.getAutoApproval().getScheduledDeadline().getTime();
  }

  private void validateAutoApprovalTimeDuration(
      Long timeoutForStep, Long autoApprovalTimeout, FilterCreationContext filterCreationContext) {
    if (autoApprovalTimeout <= MINIMUM_TIME_REQUIRED_FOR_AUTO_APPROVAL) {
      throw new InvalidYamlRuntimeException(format(
          "Time given for auto approval in approval step %s should be greater than 15 minutes with respect to current time",
          YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
    }
  }

  private Long getTimeoutForStep(Timeout timeout) {
    return isNull(timeout) ? TimeoutParameters.DEFAULT_TIMEOUT_IN_MILLIS : timeout.getTimeoutInMillis();
  }
}
