/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.harness;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.steps.jira.JiraStepUtils.NULL_STRING;

import io.harness.common.ParameterFieldHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.exception.runtime.InvalidYamlRuntimeException;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.approval.step.harness.beans.AutoApprovalDTO;
import io.harness.steps.approval.step.harness.beans.AutoApprovalParams;
import io.harness.steps.approval.step.harness.beans.ScheduledDeadline;
import io.harness.steps.approval.step.harness.beans.ScheduledDeadlineDTO;
import io.harness.utils.TimeStampUtils;

import io.dropwizard.util.Duration;

public class HarnessApprovalUtils {
  private static final long MINIMUM_TIME_REQUIRED_FOR_AUTO_APPROVAL = Duration.minutes(15).toMilliseconds();

  public static void validateTimestampForAutoApproval(HarnessApprovalSpecParameters harnessApprovalSpecParameters) {
    if (harnessApprovalSpecParameters.getAutoApproval() != null
        && harnessApprovalSpecParameters.getAutoApproval().getScheduledDeadline() != null) {
      String timeZone = getTimeZoneFromSchedule(harnessApprovalSpecParameters);
      String time = getTimeStampFromSchedule(harnessApprovalSpecParameters);

      if (isNotEmpty(time) && isNotEmpty(timeZone)) {
        Long autoApprovalTimeout = TimeStampUtils.getTotalDurationWRTCurrentTimeFromTimeStamp(time, timeZone);
        validateAutoApprovalTimeDuration(autoApprovalTimeout);
      }
    }
  }

  private static String getTimeStampFromSchedule(HarnessApprovalSpecParameters harnessApprovalSpecParameters) {
    return harnessApprovalSpecParameters.getAutoApproval().getScheduledDeadline().getTime().getValue();
  }

  private static void validateAutoApprovalTimeDuration(Long autoApprovalTimeout) {
    if (autoApprovalTimeout <= MINIMUM_TIME_REQUIRED_FOR_AUTO_APPROVAL) {
      throw new InvalidYamlRuntimeException(
          "Time given for auto approval in approval step %s should be greater than 15 minutes with respect to current time");
    }
  }

  private static String getTimeZoneFromSchedule(HarnessApprovalSpecParameters harnessApprovalSpecParameters) {
    return harnessApprovalSpecParameters.getAutoApproval().getScheduledDeadline().getTimeZone().getValue();
  }
  public static void checkForNullOrThrowAutoApproval(AutoApprovalParams autoApprovalParams) {
    if (autoApprovalParams.getScheduledDeadline() != null) {
      checkForNullOrThrowAutoApprovalFromScheduledDeadline(autoApprovalParams.getScheduledDeadline());
    }
  }

  public static AutoApprovalDTO fromAutoApprovalParams(AutoApprovalParams autoApprovalParams) {
    if (autoApprovalParams == null) {
      return null;
    }

    return AutoApprovalDTO.builder()
        .action(autoApprovalParams.getAction())
        .comments(ParameterFieldHelper.getParameterFieldFinalValueString(autoApprovalParams.getComments()))
        .scheduledDeadline(fromScheduledDeadline(autoApprovalParams.getScheduledDeadline()))
        .build();
  }
  public static ScheduledDeadlineDTO fromScheduledDeadline(ScheduledDeadline scheduledDeadline) {
    if (scheduledDeadline == null) {
      return null;
    }
    return ScheduledDeadlineDTO.builder()
        .time(scheduledDeadline.getTime().getValue())
        .timeZone(scheduledDeadline.getTimeZone().getValue())
        .build();
  }

  public static void checkForNullOrThrowAutoApprovalFromScheduledDeadline(ScheduledDeadline scheduledDeadline) {
    if (ParameterField.isBlank(scheduledDeadline.getTime())) {
      throw new InvalidRequestException("Auto Approval parameter Time cannot be empty");
    }
    if (ParameterField.isBlank(scheduledDeadline.getTimeZone())) {
      throw new InvalidRequestException("Auto Approval parameter TimeZone cannot be empty");
    }
    if (NULL_STRING.equals(scheduledDeadline.getTimeZone().getValue())) {
      // Currently, unresolved expression are getting resolved as "null" string
      throw new InvalidRequestException("Auto Approval parameter TimeZone not resolved");
    }
    if (NULL_STRING.equals(scheduledDeadline.getTime().getValue())) {
      // Currently, unresolved expression are getting resolved as "null" string
      throw new InvalidRequestException("Auto Approval parameter Time not resolved");
    }
  }
}
