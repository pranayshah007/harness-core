package io.harness.ng.core;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.exception.DuplicateFieldException;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.repositories.user.spring.UserMembershipRepository;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import io.harness.ng.core.user.entities.UserMembership.UserMembershipKeys;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static io.harness.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.NGConstants.DEFAULT_ACCOUNT_LEVEL_USER_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_ORGANIZATION_LEVEL_USER_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_PROJECT_LEVEL_USER_GROUP_IDENTIFIER;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.utils.UserGroupMapper.toDTO;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class DefaultUserGroupCreationService implements Runnable {
    private final UserGroupService userGroupService;
    private final OrganizationService organizationService;
    private final ProjectService projectService;
    private final UserMembershipRepository userMembershipRepository;
    private final PersistentLocker persistentLocker;

    private static final String DEBUG_MESSAGE = "DefaultUserGroupCreationService: ";
    private static final String LOCK_NAME = "DefaultUserGroupsCreationJobLock";

    @Inject
    public DefaultUserGroupCreationService(UserGroupService userGroupService, OrganizationService organizationService,
                                           ProjectService projectService, UserMembershipRepository userMembershipRepository, PersistentLocker persistentLocker) {
        this.userGroupService = userGroupService;
        this.organizationService = organizationService;
        this.projectService = projectService;
        this.userMembershipRepository = userMembershipRepository;
        this.persistentLocker = persistentLocker;
    }

    @Override
    public void run() {
        log.info(DEBUG_MESSAGE + "Started running...");
        log.info(DEBUG_MESSAGE + "Trying to acquire lock...");
        try (AcquiredLock<?> lock = persistentLocker.tryToAcquireLock(LOCK_NAME, Duration.ofMinutes(10))) {
            if (lock == null) {
                log.info(DEBUG_MESSAGE + "failed to acquire lock");
                return;
            }
            try {
                log.info(DEBUG_MESSAGE + "Setting SecurityContext.");
                SecurityContextBuilder.setContext(new ServicePrincipal(NG_MANAGER.getServiceId()));
                log.info(DEBUG_MESSAGE + "Setting SecurityContext completed.");
                createDefaultUserGroups();
            } catch (Exception ex) {
                log.error(DEBUG_MESSAGE + " unexpected error occurred while Setting SecurityContext", ex);
            } finally {
                log.info(DEBUG_MESSAGE + "Unsetting SecurityContext.");
                SecurityContextBuilder.unsetCompleteContext();
                log.info(DEBUG_MESSAGE + "Unsetting SecurityContext completed.");
            }
            log.info(DEBUG_MESSAGE + "Stopped running...");
        }
        catch (Exception ex) {
            log.error(DEBUG_MESSAGE + " failed to acquire lock", ex);
        }
    }

    private void createDefaultUserGroups() {
        log.info(DEBUG_MESSAGE + "User Groups creation started.");

        try {
            List<String> distinctAccountIds = organizationService.getDistinctAccounts();
            if (isEmpty(distinctAccountIds)) {
                return;
            }
            createUserGroupForAccounts(distinctAccountIds);
        } catch (Exception ex) {
            log.error(DEBUG_MESSAGE + " Fetching all accounts failed : ", ex);
        }

        log.info(DEBUG_MESSAGE + "User Groups creation completed.");
    }

    private void createUserGroupForAccounts(List<String> accountIds) {
        for (String accountId : accountIds) {
            try {
                Scope scope = Scope.of(accountId, null, null);
                if (Boolean.FALSE.equals(userGroupService.exists(scope, getDefaultUserGroupId(scope)))) {
                    userGroupService.setUpDefaultUserGroup(scope);
                }
                addUsersAtScopeToDefaultUserGroup(scope);
                createUserGroupForOrganizations(accountId);
            } catch (DuplicateFieldException ex) {
                log.error(DEBUG_MESSAGE + "User Group Creation failed for Account:" + accountId + "as User Group already exists", ex);
            } catch (Exception ex) {
                log.error(DEBUG_MESSAGE + "User Group Creation failed for Account: " + accountId, ex);
            }
        }
    }

    private void createUserGroupForOrganizations(String accountId) {
        int pageIndex = 0;
        int pageSize = 100;
        do {
            Pageable pageable = PageRequest.of(pageIndex, pageSize);
            Criteria criteria = Criteria.where("accountIdentifier").is(accountId);
            List<Organization> organizations = organizationService.list(criteria, pageable).getContent();
            if (isEmpty(organizations)) {
                break;
            }
            for (Organization organization : organizations) {
                try {
                    Scope scope = Scope.of(accountId, organization.getIdentifier(), null);
                    if (Boolean.FALSE.equals(userGroupService.exists(scope, getDefaultUserGroupId(scope)))) {
                        userGroupService.setUpDefaultUserGroup(scope);
                    }
                    addUsersAtScopeToDefaultUserGroup(scope);
                    createUserGroupForProjects(accountId, organization.getId());
                } catch (DuplicateFieldException ex) {
                    log.error(DEBUG_MESSAGE + "User Group Creation failed for Organization:" + organization.getId() + "as User Group already exists", ex);
                } catch (Exception ex) {
                    log.error(DEBUG_MESSAGE + "User Group Creation failed for Organization: " + organization.getId(), ex);
                }
            }
            pageIndex++;
        }
        while (true);
    }

    private void createUserGroupForProjects(String accountId, String organizationId) {
        int pageIndex = 0;
        int pageSize = 100;
        do {
            Pageable pageable = PageRequest.of(pageIndex, pageSize);
            Criteria criteria = Criteria.where("accountIdentifier").is(accountId).and("organizationId").is(organizationId);
            List<Project> projects = projectService.list(criteria, pageable).getContent();
            if (isEmpty(projects)) {
                break;
            }
            for (Project project : projects) {
                try {
                    Scope scope = Scope.of(accountId, organizationId, project.getIdentifier());
                    if (Boolean.FALSE.equals(userGroupService.exists(scope, getDefaultUserGroupId(scope)))) {
                        userGroupService.setUpDefaultUserGroup(scope);
                    }
                    addUsersAtScopeToDefaultUserGroup(scope);
                } catch (DuplicateFieldException ex) {
                    log.error(DEBUG_MESSAGE + "User Group Creation failed for Project:" + project.getId() + "as User Group already exists", ex);
                } catch (Exception ex) {
                    log.error(DEBUG_MESSAGE + "User Group Creation failed for Project: " + project.getId(), ex);
                }
            }
            pageIndex++;
        }
        while (true);
    }

    private void addUsersAtScopeToDefaultUserGroup(Scope scope) {
        int pageIndex = 0;
        int pageSize = 1000;
        do {
            Pageable pageable = PageRequest.of(pageIndex, pageSize);
            Criteria criteria = Criteria.where(UserMembershipKeys.ACCOUNT_IDENTIFIER_KEY).is(scope.getAccountIdentifier());
            if (isNotEmpty(scope.getOrgIdentifier())) {
                criteria = criteria.and(UserMembershipKeys.ORG_IDENTIFIER_KEY).is(scope.getOrgIdentifier());
            }
            if (isNotEmpty(scope.getProjectIdentifier())) {
                criteria = criteria.and(UserMembershipKeys.PROJECT_IDENTIFIER_KEY).is(scope.getProjectIdentifier());
            }
            List<String> userIds = userMembershipRepository.findAllUserIds(criteria, pageable).getContent();
            if (isEmpty(userIds)) {
                break;
            }
            Optional<UserGroup> userGroupOptional = Optional.empty();
            try {
                userGroupOptional = userGroupService.get(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), getDefaultUserGroupId(scope));
            } catch (Exception ex) {
                log.error("Unable to fetch User Group at scope:" + scope, ex);
            }
            try {
                if (userGroupOptional.isPresent()) {
                    UserGroup userGroup = userGroupOptional.get();
                    List<String> currentUsers = userGroup.getUsers();
                    HashSet<String> existingUsers = new HashSet<>(currentUsers);
                    HashSet<String> newUsers = new HashSet<>(userIds);
                    HashSet<String> usersToAdd =  new HashSet<>(Sets.difference(newUsers, existingUsers));
                    if(isNotEmpty(usersToAdd))
                    {
                        log.info(DEBUG_MESSAGE + String.format("Existing %s users in user group at scope %s", currentUsers.size(), scope));
                        log.info(DEBUG_MESSAGE + String.format("Adding %s users to user group at scope %s", usersToAdd.size(), scope));
                        currentUsers = new ArrayList<>(currentUsers);
                        currentUsers.addAll(usersToAdd);
                        userGroup.setUsers(currentUsers);
                        userGroupService.update(toDTO(userGroup));
                        log.info(DEBUG_MESSAGE + String.format("Added %s users to user group at scope %s", usersToAdd.size(), scope));
                    }
                }
            } catch (Exception ex) {
                log.error("Failed to update User Group at scope: " + scope, ex);
            }
            pageIndex++;
        }
        while (true);
    }

    private String getDefaultUserGroupId(Scope scope) {
        if (isNotEmpty(scope.getProjectIdentifier())) {
            return DEFAULT_PROJECT_LEVEL_USER_GROUP_IDENTIFIER;
        } else if (isNotEmpty(scope.getOrgIdentifier())) {
            return DEFAULT_ORGANIZATION_LEVEL_USER_GROUP_IDENTIFIER;
        }
        return DEFAULT_ACCOUNT_LEVEL_USER_GROUP_IDENTIFIER;
    }
}