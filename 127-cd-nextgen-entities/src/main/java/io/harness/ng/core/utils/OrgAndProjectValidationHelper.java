/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.utils;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;

import com.google.inject.Inject;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import lombok.AllArgsConstructor;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.PIPELINE)
public class OrgAndProjectValidationHelper {
  private final ProjectService projectService;
  private final OrganizationService organizationService;

  public boolean checkThatTheOrganizationAndProjectExists(
      String orgIdentifier, String projectIdentifier, String accountIdentifier) {
    if (isNotEmpty(orgIdentifier)) {
      // Needed since NG entities are considered unique with case-sensitive identifiers unlike org, project
      final Optional<Organization> organization =
          organizationService.getConsideringCase(accountIdentifier, orgIdentifier);
      if (organization.isEmpty()) {
        throw new NotFoundException(String.format("org [%s] not found.", orgIdentifier));
      }
    }

    if (isNotEmpty(orgIdentifier) && isNotEmpty(projectIdentifier)) {
      // Needed since NG entities are considered unique with case-sensitive identifiers unlike org, project
      final Optional<Project> project =
          projectService.getConsideringCase(accountIdentifier, orgIdentifier, projectIdentifier);
      if (project.isEmpty()) {
        throw new NotFoundException(String.format("project [%s] not found.", projectIdentifier));
      }
    }
    return true;
  }
}