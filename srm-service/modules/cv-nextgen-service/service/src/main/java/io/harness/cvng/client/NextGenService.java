/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.client;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.environment.dto.EnvironmentResponse;
import io.harness.ng.core.environment.dto.EnvironmentResponseDTO;
import io.harness.ng.core.service.dto.ServiceResponse;
import io.harness.ng.core.service.dto.ServiceResponseDTO;

import java.util.List;
import java.util.Map;
import java.util.Optional;
@OwnedBy(HarnessTeam.CV)
public interface NextGenService {
  ConnectorResponseDTO create(ConnectorDTO connectorRequestDTO, String accountIdentifier);

  Optional<ConnectorInfoDTO> get(
      String accountIdentifier, String connectorIdentifier, String orgIdentifier, String projectIdentifier);

  EnvironmentResponseDTO getEnvironment(
      String accountId, String orgIdentifier, String projectIdentifier, String environmentIdentifier);

  ServiceResponseDTO getService(
      String accountId, String orgIdentifier, String projectIdentifier, String serviceIdentifier);

  List<ServiceResponse> listService(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> serviceIdentifiers);

  List<EnvironmentResponse> listEnvironment(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> environmentIdentifier);

  int getServicesCount(String accountId, String orgIdentifier, String projectIdentifier);

  int getEnvironmentCount(String accountId, String orgIdentifier, String projectIdentifier);

  ProjectDTO getProject(String accountIdentifier, String orgIdentifier, String projectIdentifier);

  ProjectDTO getCachedProject(String accountIdentifier, String orgIdentifier, String projectIdentifier);

  OrganizationDTO getOrganization(String accountIdentifier, String orgIdentifier);

  Map<String, String> getServiceIdNameMap(ProjectParams projectParams, List<String> serviceIdentifiers);

  Map<String, String> getEnvironmentIdNameMap(ProjectParams projectParams, List<String> environmentIdentifiers);
}
