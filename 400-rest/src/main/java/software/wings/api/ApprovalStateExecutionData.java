/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.ExecutionStatus;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;

import software.wings.beans.NameValuePair;
import software.wings.beans.approval.Criteria;
import software.wings.beans.security.UserGroup;
import software.wings.beans.servicenow.ServiceNowTicketType;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.sm.StateExecutionData;
import software.wings.sm.states.ApprovalState.ApprovalStateType;

import com.google.inject.Inject;
import dev.morphia.annotations.Transient;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CDC)
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants(innerTypeName = "ApprovalStateExecutionDataKeys")
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class ApprovalStateExecutionData extends StateExecutionData implements DelegateTaskNotifyResponseData {
  public static final String USER_GROUP_NAMES = "userGroupNames";
  public static final String USER_GROUPS_DISPLAY_NAME = "Approval User Groups";
  public static final String AUTHORIZATION_STATUS = "authorizationStatus";
  public static final String AUTHORIZATION_STATUS_DISPLAY_NAME = "Authorization Status";
  public static final String AUTHORIZATION_STATUS_VALUE = "User is not authorized to approve or reject";
  public static final String IS_USER_AUTHORIZED = "isUserAuthorized";
  public static final String AUTHORIZED_DISPLAY_NAME = "Authorized";

  private EmbeddedUser approvedBy;
  private Long approvedOn;
  private String comments;
  private String approvalId;
  private String workflowId;
  private String appId;
  private String description;
  private Integer timeoutMillis;
  private EmbeddedUser triggeredBy;

  private ApprovalStateType approvalStateType;

  /** User group Approval */
  private List<String> userGroups;
  private boolean autoRejectPreviousDeployments;

  /** Jira Approval */
  private String issueUrl;
  private String issueKey;
  private String currentStatus;
  private String approvalField;
  private String approvalValue;
  private String rejectionField;
  private String rejectionValue;

  /** ServiceNow Approval */
  private String ticketUrl;
  private ServiceNowTicketType ticketType;
  private Criteria snowApproval;
  private Criteria snowRejection;
  private boolean waitingForChangeWindow;

  /** Slack approval */
  private boolean approvalFromSlack;

  /** GraphQL approval supported only for User group Approval */
  private boolean approvalFromGraphQL;

  /** Approved via API Key */
  private boolean approvalViaApiKey;

  /** Shell Script approval */
  private Integer retryInterval;

  // Shell script approval
  private String activityId;

  // Setting these variables for pipeline executions with only approval state
  @Transient private transient List<UserGroup> userGroupList;
  @Transient private transient boolean isAuthorized;

  // Used to return information in graphQL Apis for approval Data
  private String stageName;
  @Transient private transient String executionUuid;

  @Transient @Inject private transient WorkflowExecutionService workflowExecutionService;
  @Transient @Inject private transient UserGroupService userGroupService;

  private String skipAssertionResponse;
  private List<NameValuePair> variables;

  @Transient private transient String approvalStateIdentifier;

  @Override
  public Map<String, ExecutionDataValue> getExecutionSummary() {
    Map<String, ExecutionDataValue> executionDetails = super.getExecutionSummary();
    putNotNull(executionDetails, "approvalStateType",
        ExecutionDataValue.builder().displayName("approvalStateType").value(approvalStateType).build());
    return setExecutionData(executionDetails);
  }

  @Override
  public Map<String, ExecutionDataValue> getExecutionDetails() {
    Map<String, ExecutionDataValue> executionDetails = super.getExecutionDetails();
    populateApprovalStateAuthorizationData(executionDetails);
    return setExecutionData(executionDetails);
  }

  private Map<String, ExecutionDataValue> setExecutionData(Map<String, ExecutionDataValue> executionDetails) {
    putNotNull(executionDetails, "activityId",
        ExecutionDataValue.builder().displayName("Activity Id").value(activityId).build());
    putNotNull(executionDetails, "approvalId",
        ExecutionDataValue.builder().displayName("Approval Id").value(approvalId).build());
    putNotNull(executionDetails, "status",
        ExecutionDataValue.builder().displayName("Approval Status").value(getStatus()).build());
    putNotNull(
        executionDetails, "issueUrl", ExecutionDataValue.builder().displayName("Issue URL").value(issueUrl).build());
    putNotNull(executionDetails, "description",
        ExecutionDataValue.builder().displayName("Description").value(description).build());

    if (ticketUrl != null && ticketType != null) {
      putNotNull(executionDetails, "issueUrl",
          ExecutionDataValue.builder().displayName(ticketType.getDisplayName() + " URL").value(ticketUrl).build());
    }

    if (userGroupService != null) {
      userGroupList = userGroupService.fetchUserGroupNamesFromIds(userGroups);
    }

    if (isNotEmpty(userGroupList)) {
      putNotNull(executionDetails, USER_GROUP_NAMES,
          ExecutionDataValue.builder().displayName(USER_GROUPS_DISPLAY_NAME).value(userGroupList).build());
    }

    if (timeoutMillis != null && !waitingForChangeWindow) {
      putNotNull(executionDetails, "timeoutMillis",
          ExecutionDataValue.builder().displayName("Timeout").value(timeoutMillis).build());
      if (getStatus() == ExecutionStatus.PAUSED) {
        putNotNull(executionDetails, "expiryTs",
            ExecutionDataValue.builder().displayName("Will Expire At").value(timeoutMillis + getStartTs()).build());
      }
    }

    putNotNull(executionDetails, "triggeredBy",
        ExecutionDataValue.builder().displayName("Triggered By").value(triggeredBy).build());

    if (isNotEmpty(approvalField) && isNotEmpty(approvalValue)) {
      putNotNull(executionDetails, "approvalCriteria",
          ExecutionDataValue.builder().displayName("Approval Criteria").value(getJiraApprovalCriteria()).build());
    }

    if (snowApproval != null) {
      putNotNull(executionDetails, "approvalCriteria",
          ExecutionDataValue.builder()
              .displayName("Approval Criteria")
              .value(snowApproval.conditionsString("\n"))
              .build());
    }

    if (isNotEmpty(currentStatus)) {
      String statusString = getApprovalCurrentStatus();
      putNotNull(executionDetails, "currentStatus",
          ExecutionDataValue.builder().displayName("Current value").value(statusString).build());
    }

    if (isNotEmpty(rejectionField) && isNotEmpty(rejectionValue)) {
      putNotNull(executionDetails, "rejectionCriteria",
          ExecutionDataValue.builder().displayName("Rejection Criteria").value(getJiraRejectionCriteria()).build());
    }

    if (snowRejection != null) {
      String rejectionMessage = snowRejection.conditionsString("\n");
      if (isNotEmpty(rejectionMessage)) {
        executionDetails.put("rejectionCriteria",
            ExecutionDataValue.builder().displayName("Rejection Criteria").value(rejectionMessage).build());
      }
    }

    putNotNull(executionDetails, "approvalViaApiKey", ExecutionDataValue.builder().value(approvalViaApiKey).build());
    putNotNull(executionDetails, "autoRejectPreviousDeployments",
        ExecutionDataValue.builder()
            .displayName("Auto Reject Previous Deployments")
            .value(autoRejectPreviousDeployments)
            .build());

    if (approvedBy != null) {
      String approvalDisplayName =
          approvalFromGraphQL ? approvalViaApiKey ? "Approved via API using" : "Approved via API By" : "Approved By";
      String rejectedDisplayName =
          approvalFromGraphQL ? approvalViaApiKey ? "Rejected via API using" : "Rejected via API By" : "Rejected By";
      if (getStatus() == ExecutionStatus.SUCCESS) {
        putNotNull(executionDetails, "approvedBy",
            ExecutionDataValue.builder().displayName(approvalDisplayName).value(approvedBy).build());
      } else if (getStatus() == ExecutionStatus.REJECTED) {
        putNotNull(executionDetails, "approvedBy",
            ExecutionDataValue.builder().displayName(rejectedDisplayName).value(approvedBy).build());
      }
    }

    putNotNull(executionDetails, "approvedOn",
        ExecutionDataValue.builder()
            .displayName(getStatus() == ExecutionStatus.SUCCESS ? "Approved On" : "Rejected On")
            .value(approvedOn)
            .build());
    putNotNull(executionDetails, ApprovalStateExecutionDataKeys.variables,
        ExecutionDataValue.builder()
            .displayName(StringUtils.capitalize(ApprovalStateExecutionDataKeys.variables))
            .value(variables)
            .build());
    if (isNotEmpty(comments)) {
      executionDetails.put("comments", ExecutionDataValue.builder().displayName("Comments").value(comments).build());
    }

    putNotNull(executionDetails, "retryInterval",
        ExecutionDataValue.builder().displayName("Retry Interval").value(retryInterval).build());

    return executionDetails;
  }

  private void populateApprovalStateAuthorizationData(Map<String, ExecutionDataValue> executionDetails) {
    if (workflowExecutionService != null) {
      isAuthorized = workflowExecutionService.verifyAuthorizedToAcceptOrReject(userGroups, appId, workflowId);
    }

    if (isAuthorized) {
      putNotNull(executionDetails, IS_USER_AUTHORIZED,
          ExecutionDataValue.builder().displayName(AUTHORIZED_DISPLAY_NAME).value(true).build());
    } else {
      putNotNull(executionDetails, AUTHORIZATION_STATUS,
          ExecutionDataValue.builder()
              .displayName(AUTHORIZATION_STATUS_DISPLAY_NAME)
              .value(AUTHORIZATION_STATUS_VALUE)
              .build());
      putNotNull(executionDetails, IS_USER_AUTHORIZED,
          ExecutionDataValue.builder().displayName(AUTHORIZED_DISPLAY_NAME).value(false).build());
    }
  }

  public String getApprovalCurrentStatus() {
    return approvalStateType == ApprovalStateType.SERVICENOW
        ? currentStatus
        : StringUtils.capitalize(approvalField) + " is equal to '" + StringUtils.capitalize(currentStatus) + "'";
  }

  public String getJiraApprovalCriteria() {
    return StringUtils.capitalize(approvalField) + " should be '" + StringUtils.capitalize(approvalValue) + "'";
  }

  public String getJiraRejectionCriteria() {
    return StringUtils.capitalize(rejectionField) + " should be '" + StringUtils.capitalize(rejectionValue) + "'";
  }
}
