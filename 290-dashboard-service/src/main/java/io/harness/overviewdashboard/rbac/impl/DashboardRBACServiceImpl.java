/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.overviewdashboard.rbac.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.OrganizationResponse;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.organization.remote.OrganizationClient;
import io.harness.overviewdashboard.rbac.service.DashboardRBACService;
import io.harness.project.remote.ProjectClient;
import io.harness.remote.client.NGRestUtils;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OwnedBy(PL)
public class DashboardRBACServiceImpl implements DashboardRBACService {
  @Inject private OrganizationClient organizationClient;
  @Inject private ProjectClient projectClient;

  @Override
  public List<ProjectDTO> listAccessibleProject(String accountIdentifier, String userId) {
    return NGRestUtils.getResponse(projectClient.getProjectList(accountIdentifier));
  }

  @Override
  public PageResponse<OrganizationResponse> listAccessibleOrganizations(
      String accountIdentifier, List<String> orgIdentifiers) {
    return NGRestUtils.getResponse(organizationClient.listAllOrganizations(accountIdentifier, orgIdentifiers));
  }

  @Override
  public Map<String, String> getMapOfOrganizationIdentifierAndOrganizationName(
      String accountIdentifier, List<String> orgIdentifiers) {
    PageResponse<OrganizationResponse> listOfAccessibleOrganizations =
        listAccessibleOrganizations(accountIdentifier, orgIdentifiers);

    Map<String, String> mapOfOrganizationIdentifierAndOrganizationName = new HashMap<>();
    for (OrganizationResponse organizationResponse : listOfAccessibleOrganizations.getContent()) {
      mapOfOrganizationIdentifierAndOrganizationName.put(
          organizationResponse.getOrganization().getIdentifier(), organizationResponse.getOrganization().getName());
    }
    return mapOfOrganizationIdentifierAndOrganizationName;
  }
}
