/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraformcloud.resources.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.provision.terraformcloud.resources.dtos.OrganizationsDTO;
import io.harness.cdng.provision.terraformcloud.resources.dtos.WorkspacesDTO;

@OwnedBy(HarnessTeam.CDP)
public interface TerraformCloudResourceService {
  OrganizationsDTO getOrganizations(IdentifierRef connectorRef, String orgIdentifier, String projectIdentifier);

  WorkspacesDTO getWorkspaces(
      IdentifierRef connectorRef, String orgIdentifier, String projectIdentifier, String organization);
}
