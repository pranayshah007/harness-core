/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.remote.client.NGRestUtils.getResponse;

import static java.lang.Boolean.FALSE;

import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.accesscontrol.roles.api.RoleDTO;
import io.harness.accesscontrol.roles.api.RoleResponseDTO;
import io.harness.account.utils.AccountUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.migration.NGMigration;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Organization.OrganizationKeys;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.entities.Project.ProjectKeys;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.inject.Inject;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
public class CopyTemplatesPermissionRoleUpdate implements NGMigration {
  @Inject private AccessControlAdminClient accessControlAdminClient;
  @Inject private OrganizationService organizationService;
  @Inject private ProjectService projectService;
  @Inject private AccountUtils accountUtils;
  private static final String DEBUG_MESSAGE = "CopyTemplatesPermissionRoleUpdate: ";
  private static final int DEFAULT_PAGE_SIZE = 100;

  @Override
  public void migrate() {
    log.info(DEBUG_MESSAGE + "Started running...");
    try {
      SecurityContextBuilder.setContext(new ServicePrincipal(NG_MANAGER.getServiceId()));
      log.info(DEBUG_MESSAGE + "Setting SecurityContext completed.");
      addCopyTemplatesPermissionToRequiredRoles();
    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE + "unexpected error occurred while while running migration.", ex);
    } finally {
      SecurityContextBuilder.unsetCompleteContext();
      log.info(DEBUG_MESSAGE + "Unsetting SecurityContext completed.");
    }
  }

  private void addCopyTemplatesPermissionToRequiredRoles() {
    List<String> nextgenAccountIds = null;
    try {
      nextgenAccountIds = accountUtils.getAllNGAccountIds();
      log.info(
          DEBUG_MESSAGE + String.format("Fetched %s accounts having NextGen enabled...", nextgenAccountIds.size()));

      if (isEmpty(nextgenAccountIds)) {
        return;
      }
    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE + " Fetching all accounts failed : ", ex);
      throw new InvalidRequestException("Failed to fetch NG accounts");
    }

    log.info(DEBUG_MESSAGE + "Adding copy templates permission to required Existing roles started.");
    try {
      addCopyTemplatesPermissionForAccountRoles(nextgenAccountIds);
    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE + "Failed adding permissions to nextGen accounts", ex);
      throw ex;
    }
    log.info(DEBUG_MESSAGE + "Adding copy templates permission to required Existing roles completed.");
  }

  private void addCopyTemplatesPermissionForAccountRoles(List<String> accountIds) {
    for (String accountId : accountIds) {
      updatePermissionsAtScope(accountId, null, null);
      addCopyTemplatesPermissionForOrganizationRoles(accountId);
    }
  }

  private void addCopyTemplatesPermissionForOrganizationRoles(String accountId) {
    int pageIndex = 0;
    int pageSize = 100;
    try {
      do {
        Pageable pageable = PageRequest.of(pageIndex, pageSize);
        Criteria criteria = Criteria.where("accountIdentifier").is(accountId).and(OrganizationKeys.deleted).is(FALSE);
        List<Organization> organizations = organizationService.list(criteria, pageable).getContent();
        if (isEmpty(organizations)) {
          break;
        }
        log.info(DEBUG_MESSAGE
            + String.format("Fetched %s Organizations at account %s ...", organizations.size(), accountId));
        pageIndex++;
        for (Organization organization : organizations) {
          updatePermissionsAtScope(accountId, organization.getIdentifier(), null);
          addCopyTemplatesPermissionForProjectRoles(accountId, organization.getIdentifier());
        }

      } while (true);
    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE + " Fetching Organizations failed : ", ex);
    }
  }

  private void addCopyTemplatesPermissionForProjectRoles(String accountId, String orgIdentifier) {
    int pageIndex = 0;
    int pageSize = 100;
    try {
      do {
        Pageable pageable = PageRequest.of(pageIndex, pageSize);
        Criteria criteria = Criteria.where("accountIdentifier")
                                .is(accountId)
                                .and("orgIdentifier")
                                .is(orgIdentifier)
                                .and(ProjectKeys.deleted)
                                .is(FALSE);
        List<Project> projects = projectService.list(criteria, pageable).getContent();
        if (isEmpty(projects)) {
          break;
        }
        pageIndex++;
        log.info(DEBUG_MESSAGE
            + String.format(
                "Fetched %s Projects at account %s orgIdentifier %s ...", projects.size(), accountId, orgIdentifier));
        for (Project project : projects) {
          updatePermissionsAtScope(accountId, orgIdentifier, project.getIdentifier());
        }

      } while (true);
    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE + " Fetching Projects failed : ", ex);
    }
  }

  private void updatePermissionsAtScope(String accountId, String orgIdentifier, String projectIdentifier) {
    try {
      if (projectIdentifier != null) {
        log.info(DEBUG_MESSAGE
            + String.format("Starting the migration for %s account %s organisation %s project", accountId,
                orgIdentifier, projectIdentifier));
      } else if (orgIdentifier != null) {
        log.info(DEBUG_MESSAGE
            + String.format("Starting the migration for %s account %s organisation", accountId, orgIdentifier));
      } else {
        log.info(DEBUG_MESSAGE + String.format("Starting the migration for %s account", accountId));
      }

      int pageIndex = 0;
      do {
        PageResponse<RoleResponseDTO> rolesListPage = getResponse(accessControlAdminClient.getRoles(
            pageIndex, DEFAULT_PAGE_SIZE, accountId, orgIdentifier, projectIdentifier, null));

        if (isEmpty(rolesListPage.getContent())) {
          log.info(String.format("There are no roles to be updated for account - %s %s %s", accountId,
              isNotEmpty(orgIdentifier) ? "org - " + orgIdentifier : "",
              isNotEmpty(projectIdentifier) ? "project - " + projectIdentifier : ""));
          break;
        }

        pageIndex++;

        List<RoleResponseDTO> rolesList = rolesListPage.getContent();
        for (RoleResponseDTO roleResponseDTO : rolesList) {
          /**
           * Skipping harness managed roles.
           */

          if (roleResponseDTO.isHarnessManaged()) {
            log.info(String.format("Skipping %s role in account %s as it is harness managed",
                roleResponseDTO.getRole().getName(), accountId));
            continue;
          }

          /**
           * Skipping if there are no permissions for any existing role.
           */

          if (isEmpty(roleResponseDTO.getRole().getPermissions())) {
            log.info(String.format("Skipping %s role in account %s as there are no permissions for this role",
                roleResponseDTO.getRole().getName(), accountId));
            continue;
          }

          /**
           * If this role already has core_template_access permission then provide core_template_copy permission to this
           * role.
           */

          if (roleResponseDTO.getRole().getPermissions().contains("core_template_access")
              && !roleResponseDTO.getRole().getPermissions().contains("core_template_copy")) {
            RoleDTO roleDto = roleResponseDTO.getRole();
            Set<String> permissions = roleResponseDTO.getRole().getPermissions();
            permissions.add("core_template_copy");
            try {
              RoleDTO updatedRole = RoleDTO.builder()
                                        .identifier(roleDto.getIdentifier())
                                        .name(roleDto.getName())
                                        .permissions(permissions)
                                        .allowedScopeLevels(roleDto.getAllowedScopeLevels())
                                        .description(roleDto.getDescription())
                                        .tags(roleDto.getTags())
                                        .build();
              RoleResponseDTO response = getResponse(accessControlAdminClient.updateRole(
                  updatedRole.getIdentifier(), accountId, orgIdentifier, projectIdentifier, updatedRole));
              TimeUnit.SECONDS.sleep(2);
              log.info(DEBUG_MESSAGE + response.toString());
              log.info(String.format("Updated %s role in account %s.", roleResponseDTO.getRole().getName(), accountId));
            } catch (Exception exception) {
              log.error(String.format("%s Unexpected error occurred while updating role: %s", DEBUG_MESSAGE,
                            roleDto.getIdentifier()),
                  exception);
            }
          }
        }

      } while (true);

      if (projectIdentifier != null) {
        log.info(DEBUG_MESSAGE
            + String.format("Completed the migration for %s account %s organisation %s project", accountId,
                orgIdentifier, projectIdentifier));
      } else if (orgIdentifier != null) {
        log.info(DEBUG_MESSAGE
            + String.format("Completed the migration for %s account %s organisation", accountId, orgIdentifier));
      } else {
        log.info(DEBUG_MESSAGE + String.format("Completed the migration for %s account", accountId));
      }

    } catch (Exception e) {
      log.error(String.format("%s Exception while running CopyTemplatesPermissionRoleUpdate for account %s",
                    DEBUG_MESSAGE, accountId),
          e);
    }
  }
}
