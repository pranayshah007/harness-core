/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.validations.helper;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;

import com.google.inject.Inject;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import lombok.AllArgsConstructor;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.PIPELINE)
public class OrgAndProjectValidationHelper {
  private final ProjectService projectService;
  private final OrganizationService organizationService;

  public boolean checkThatTheOrganizationAndProjectExists(
      String orgIdentifier, String projectIdentifier, String accountIdentifier) {
    if (isNotEmpty(orgIdentifier)) {
      final Optional<Organization> organization = organizationService.get(accountIdentifier, orgIdentifier);
      if (organization.isEmpty()) {
        throw new NotFoundException(String.format("org [%s] not found.", orgIdentifier));
      }
    }

    if (isNotEmpty(orgIdentifier) && isNotEmpty(projectIdentifier)) {
      final Optional<Project> project = projectService.get(accountIdentifier, orgIdentifier, projectIdentifier);
      if (project.isEmpty()) {
        throw new NotFoundException(String.format("project [%s] not found.", projectIdentifier));
      }
    }
    return true;
  }
}