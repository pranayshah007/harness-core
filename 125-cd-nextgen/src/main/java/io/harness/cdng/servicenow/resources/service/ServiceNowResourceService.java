/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.servicenow.resources.service;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.servicenow.ServiceNowFieldNG;
import io.harness.servicenow.ServiceNowStagingTable;
import io.harness.servicenow.ServiceNowTemplate;
import io.harness.servicenow.ServiceNowTicketNG;
import io.harness.servicenow.ServiceNowTicketTypeDTO;

import java.util.List;
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_APPROVALS})
public interface ServiceNowResourceService {
  List<ServiceNowFieldNG> getIssueCreateMetadata(
      IdentifierRef serviceNowConnectorRef, String orgId, String projectId, String ticketType);

  List<ServiceNowFieldNG> getMetadata(
      IdentifierRef serviceNowConnectorRef, String orgId, String projectId, String ticketType);

  List<ServiceNowTemplate> getTemplateList(IdentifierRef connectorRef, String orgId, String projectId, int limit,
      int offset, String templateName, String ticketType, String searchTerm);
  List<ServiceNowStagingTable> getStagingTableList(IdentifierRef connectorRef, String orgId, String projectId);
  List<ServiceNowTicketTypeDTO> getTicketTypesV2(IdentifierRef connectorRef, String orgId, String projectId);

  ServiceNowTicketNG getTicketDetails(IdentifierRef connectorRef, String orgId, String projectId, String ticketType,
      String ticketNumber, List<String> fieldsList);

  List<String> getStandardTemplateReadOnlyFields(IdentifierRef connectorRef, String orgId, String projectId);
}
