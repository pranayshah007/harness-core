/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.approval;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.eraro.ErrorCode.SERVICENOW_ERROR;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.ServiceNowException;

import software.wings.beans.servicenow.ServiceNowTicketType;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
@TargetModule(HarnessModule._957_CG_BEANS)
@BreakDependencyOn("software.wings.service.impl.servicenow.ServiceNowServiceImpl")
public class ServiceNowApprovalParams {
  private String snowConnectorId;
  private String issueNumber;
  private ServiceNowTicketType ticketType;
  private Criteria approval;
  private Criteria rejection;
  private boolean changeWindowPresent;
  private String changeWindowStartField;
  private String changeWindowEndField;

  public Set<String> getAllCriteriaFields() {
    Set<String> fields = new HashSet<>();
    if (approval != null) {
      approval.fetchConditions().keySet().forEach(field -> fields.add(field));
    }
    if (rejection != null) {
      rejection.fetchConditions().keySet().forEach(field -> fields.add(field));
    }
    return fields;
  }

  public Set<String> getChangeWindowTimeFields() {
    if (changeWindowPresent) {
      return new HashSet<>(Arrays.asList(changeWindowStartField, changeWindowEndField));
    }
    return new HashSet<>();
  }

  public boolean withinChangeWindow(Map<String, String> currentStatus) {
    if (changeWindowPresent) {
      return validateTimeWindow(changeWindowEndField, changeWindowStartField, currentStatus);
    }
    return true;
  }

  @SuppressWarnings("PMD")
  public static boolean validateTimeWindow(
      String endTimeField, String startTimeField, Map<String, String> currentStatus) {
    Instant nowInstant = Instant.now();
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    try {
      Objects.requireNonNull(currentStatus.get(startTimeField), "Change Window Start Time value in Ticket is invalid");
      Objects.requireNonNull(currentStatus.get(endTimeField), "Change Window End Time value in Ticket is invalid");
      Instant startTime = dateFormat.parse(addTimeIfNeeded(currentStatus.get(startTimeField))).toInstant();
      Instant endTime = dateFormat.parse(addTimeIfNeeded(currentStatus.get(endTimeField))).toInstant();
      log.info(
          "[CHANGE_WINDOW_TIME_LOG]: Start time: {}, End time: {}, Current time: {}", startTime, endTime, nowInstant);
      if (endTime.compareTo(startTime) <= 0) {
        throw new IllegalArgumentException("Start Window Time must be earlier than End Window Time");
      }
      if (endTime.compareTo(nowInstant) < 0) {
        throw new IllegalArgumentException("End Window Time must be greater than current time");
      }
      return startTime.compareTo(nowInstant) < 0 && endTime.compareTo(nowInstant) > 0;
    } catch (ParseException pe) {
      throw new ServiceNowException("Invalid approval Change Window values in ServiceNow", SERVICENOW_ERROR, USER, pe);
    } catch (NullPointerException | IllegalArgumentException ex) {
      log.error("");
      throw new ServiceNowException(ex.getMessage(), SERVICENOW_ERROR, USER, ex);
    }
  }

  private static String addTimeIfNeeded(String date) {
    if (date == null || date.contains(" ")) {
      return date;
    }
    return date + " 00:00:00";
  }
}
