/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.servicenow;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;
import io.harness.servicenow.ServiceNowFieldNG;
import io.harness.servicenow.ServiceNowImportSetResponseNG;
import io.harness.servicenow.ServiceNowStagingTable;
import io.harness.servicenow.ServiceNowTemplate;
import io.harness.servicenow.ServiceNowTicketNG;
import io.harness.servicenow.ServiceNowTicketTypeDTO;

import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@OwnedBy(CDC)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ServiceNowTaskNGResponse implements DelegateTaskNotifyResponseData {
  DelegateMetaInfo delegateMetaInfo;
  List<ServiceNowFieldNG> serviceNowFieldNGList;
  ServiceNowTicketNG ticket;
  List<ServiceNowTemplate> serviceNowTemplateList;
  ServiceNowImportSetResponseNG serviceNowImportSetResponseNG;
  List<ServiceNowStagingTable> serviceNowStagingTableList;
  List<ServiceNowTicketTypeDTO> serviceNowTicketTypeList;
}
