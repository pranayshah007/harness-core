/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.services;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.pdcconnector.HostDTO;
import io.harness.delegate.beans.connector.pdcconnector.HostFilterDTO;
import io.harness.ng.beans.PageRequest;

import org.springframework.data.domain.Page;

@OwnedBy(CDP)
public interface NGHostService {
  /**
   * List of filtered hosts.
   *
   * @param accountIdentifier the account identifier
   * @param orgIdentifier the organization identifier
   * @param projectIdentifier the project identifier
   * @param scopedConnectorIdentifier scoped ConnectorDisconnectHandler identifier
   * @param filter host names or host attributes filter
   * @param pageRequest page
   * @return list of hosts
   */
  Page<HostDTO> filterHostsByConnector(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String scopedConnectorIdentifier, HostFilterDTO filter, PageRequest pageRequest);
}
