/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.approval;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static software.wings.beans.approval.ServiceNowApprovalParams.validateTimeWindow;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.FdIndex;
import io.harness.persistence.AccountAccess;

import software.wings.beans.servicenow.ServiceNowTicketType;
import software.wings.sm.states.ApprovalState.ApprovalStateType;

import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@OwnedBy(CDC)
@FieldNameConstants(innerTypeName = "ApprovalPollingJobEntityKeys")
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@ToString(exclude = "scriptString")
@Entity(value = "approvalPollingJob")
@HarnessEntity(exportable = false)
@TargetModule(HarnessModule._957_CG_BEANS)
@Slf4j
public class ApprovalPollingJobEntity implements PersistentRegularIterable, AccountAccess {
  String appId;
  String accountId;
  String stateExecutionInstanceId;
  String workflowExecutionId;

  String connectorId;
  @Id String approvalId;
  String approvalField;
  String approvalValue;
  String rejectionField;
  String rejectionValue;

  // jira fields
  String issueId;

  // snow fields
  String issueNumber;
  ServiceNowTicketType issueType;
  Criteria approval;
  Criteria rejection;
  boolean changeWindowPresent;
  String changeWindowStartField;
  String changeWindowEndField;

  // shell script approval fields
  String scriptString;
  String activityId;
  long retryInterval;
  List<String> delegateSelectors;
  int expressionFunctorToken;

  ApprovalStateType approvalType;

  public boolean withinChangeWindow(Map<String, String> currentStatus) {
    if (changeWindowPresent) {
      return validateTimeWindow(changeWindowEndField, changeWindowStartField, currentStatus);
    }
    return true;
  }

  @FdIndex private Long nextIteration;

  @Override
  public Long obtainNextIteration(String fieldName) {
    return nextIteration;
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    this.nextIteration = nextIteration;
  }

  @Override
  public String getUuid() {
    return approvalId;
  }
}
