/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core;

import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.Boolean.FALSE;

import io.harness.account.utils.AccountUtils;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.ng.core.api.DefaultUserGroupService;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Organization.OrganizationKeys;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.entities.Project.ProjectKeys;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class DefaultUserGroupCreationService implements Runnable {
  private final DefaultUserGroupService defaultUserGroupService;
  private final OrganizationService organizationService;
  private final ProjectService projectService;
  private final PersistentLocker persistentLocker;
  private final AccountUtils accountUtils;
  private static final String DEBUG_MESSAGE = "DefaultUserGroupCreationService: ";
  private static final String LOCK_NAME = "DefaultUserGroupsCreationJobLock";

  @Inject
  public DefaultUserGroupCreationService(DefaultUserGroupService defaultUserGroupService,
      OrganizationService organizationService, ProjectService projectService, PersistentLocker persistentLocker,
      AccountUtils accountUtils) {
    this.defaultUserGroupService = defaultUserGroupService;
    this.organizationService = organizationService;
    this.projectService = projectService;
    this.persistentLocker = persistentLocker;
    this.accountUtils = accountUtils;
  }

  @Override
  public void run() {
    log.info(DEBUG_MESSAGE + "Started running...");
    log.info(DEBUG_MESSAGE + "Trying to acquire lock...");
    try (AcquiredLock<?> lock =
             persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(LOCK_NAME, Duration.ofSeconds(5))) {
      if (lock == null) {
        log.info(DEBUG_MESSAGE + "failed to acquire lock");
        return;
      }
      try {
        SecurityContextBuilder.setContext(new ServicePrincipal(NG_MANAGER.getServiceId()));
        log.info(DEBUG_MESSAGE + "Setting SecurityContext completed.");
        createDefaultUserGroups();
      } catch (Exception ex) {
        log.error(DEBUG_MESSAGE + " unexpected error occurred while Setting SecurityContext", ex);
      } finally {
        SecurityContextBuilder.unsetCompleteContext();
        log.info(DEBUG_MESSAGE + "Unsetting SecurityContext completed.");
      }
      log.info(DEBUG_MESSAGE + "Stopped running...");
    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE + " failed to acquire lock", ex);
    }
  }

  private void createDefaultUserGroups() {
    log.info(DEBUG_MESSAGE + "User Groups creation started.");

    try {
      List<String> distinctAccountIds = accountUtils.getAllNGAccountIds();
      if (isEmpty(distinctAccountIds)) {
        return;
      }
      log.info(
          DEBUG_MESSAGE + String.format("Fetched %s accounts having NextGen enabled...", distinctAccountIds.size()));
      createUserGroupForAccounts(distinctAccountIds);
    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE + " Fetching all accounts failed : ", ex);
    }

    log.info(DEBUG_MESSAGE + "User Groups creation completed.");
  }

  private void createUserGroupForAccounts(List<String> accountIds) {
    for (String accountId : accountIds) {
      Scope scope = Scope.of(accountId, null, null);
      createOrUpdateUserGroupAtScope(scope);
      createUserGroupForOrganizations(accountId);
    }
  }

  private void createUserGroupForOrganizations(String accountId) {
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
        for (Organization organization : organizations) {
          Scope scope = Scope.of(accountId, organization.getIdentifier(), null);
          createOrUpdateUserGroupAtScope(scope);
          createUserGroupForProjects(accountId, organization.getIdentifier());
        }
        pageIndex++;
      } while (true);
    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE + " Fetching Organizations failed : ", ex);
    }
  }

  private void createOrUpdateUserGroupAtScope(Scope scope) {
    try {
      defaultUserGroupService.createOrUpdateUserGroupAtScope(scope);
    } catch (Exception ex) {
      log.error(String.format("Something went wrong while create/update User Group at scope: %s", scope), ex);
    }
  }

  private void createUserGroupForProjects(String accountId, String orgIdentifier) {
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
        log.info(DEBUG_MESSAGE
            + String.format(
                "Fetched %s Projects at account %s orgIdentifier %s ...", projects.size(), accountId, orgIdentifier));
        for (Project project : projects) {
          Scope scope = Scope.of(accountId, orgIdentifier, project.getIdentifier());
          createOrUpdateUserGroupAtScope(scope);
        }
        pageIndex++;
      } while (true);
    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE + " Fetching Organizations failed : ", ex);
    }
  }
}
