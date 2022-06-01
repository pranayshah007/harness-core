/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.servicenow;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import software.wings.api.ServiceNowExecutionData;
import software.wings.beans.SettingAttribute;
import software.wings.beans.approval.ApprovalPollingJobEntity;
import software.wings.beans.approval.ServiceNowApprovalParams;
import software.wings.beans.servicenow.ServiceNowFieldType;
import software.wings.beans.servicenow.ServiceNowMetaDTO;
import software.wings.beans.servicenow.ServiceNowTicketType;

import java.util.List;
import java.util.Map;

@OwnedBy(CDC)
public interface ServiceNowService {
  void validateCredential(SettingAttribute settingAttribute);
  List<ServiceNowMetaDTO> getStates(
      ServiceNowTicketType ticketType, String accountId, String connectorId, String appId);
  List<ServiceNowMetaDTO> getApprovalValues(
      ServiceNowTicketType ticketType, String accountId, String connectorId, String appId);
  Map<String, List<ServiceNowMetaDTO>> getCreateMeta(
      ServiceNowTicketType ticketType, String accountId, String connectorId, String appId);
  List<ServiceNowMetaDTO> getAdditionalFields(ServiceNowTicketType ticketType, String accountId, String connectorId,
      String appId, ServiceNowFieldType typeFilter);
  ServiceNowExecutionData getIssueUrl(String appId, String accountId, ServiceNowApprovalParams approvalParams);

  ServiceNowExecutionData getApprovalStatus(ApprovalPollingJobEntity entity);

  void handleServiceNowPolling(ApprovalPollingJobEntity entity);
}
