package io.harness.ng.core;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.exception.DuplicateFieldException;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.repositories.user.spring.UserMembershipRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.List;
import java.util.Optional;

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

    private static final String DEBUG_MESSAGE = "DefaultUserGroupCreationService: ";

    @Inject
    public DefaultUserGroupCreationService(UserGroupService userGroupService, OrganizationService organizationService,
                                           ProjectService projectService, UserMembershipRepository userMembershipRepository) {
        this.userGroupService = userGroupService;
        this.organizationService = organizationService;
        this.projectService = projectService;
        this.userMembershipRepository = userMembershipRepository;
    }

    @Override
    public void run() {
        createDefaultUserGroups();
    }

    private void createDefaultUserGroups() {
        log.info(DEBUG_MESSAGE + "User Groups creation started.");

        try {
            List<String> accountIdsToBeInserted = organizationService.getDistinctAccounts();
            if (isEmpty(accountIdsToBeInserted)) {
                return;
            }
            createUserGroupForAccounts(accountIdsToBeInserted);
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
        do {
            int pageIndex = 0;
            int pageSize = 100;
            Pageable pageable = PageRequest.of(pageIndex, pageSize);
            Criteria criteria = Criteria.where("accountIdentifier").is(accountId);
            List<Organization> organizations = organizationService.list(criteria, pageable).getContent();
            if (isEmpty(organizations)) {
                break;
            }
            for (Organization organization : organizations) {
                try {
                    Scope scope = Scope.of(accountId, organization.getId(), null);
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
        }
        while (true);
    }

    private void createUserGroupForProjects(String accountId, String organizationId) {
        do {
            int pageIndex = 0;
            int pageSize = 100;
            Pageable pageable = PageRequest.of(pageIndex, pageSize);
            Criteria criteria = Criteria.where("accountIdentifier").is(accountId).and("organizationId").is(organizationId);
            List<Project> projects = projectService.list(criteria, pageable).getContent();
            if (isEmpty(projects)) {
                break;
            }
            for (Project project : projects) {
                try {
                    Scope scope = Scope.of(accountId, organizationId, project.getId());
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
        }
        while (true);
    }

    private void addUsersAtScopeToDefaultUserGroup(Scope scope) {
        int pageIndex = 0;
        int pageSize = 1000;
        do {
            Pageable pageable = PageRequest.of(pageIndex, pageSize);
            Criteria criteria = Criteria.where("scope.accountIdentifier").is(scope.getAccountIdentifier());
            if (isNotEmpty(scope.getOrgIdentifier())) {
                criteria = criteria.and("scope.orgIdentifier").is(scope.getOrgIdentifier());
            }
            if (isNotEmpty(scope.getProjectIdentifier())) {
                criteria = criteria.and("scope.projectIdentifier").is(scope.getProjectIdentifier());
            }
            List<String> userIds = userMembershipRepository.findAllUserIds(criteria, pageable).getContent();
            if (isEmpty(userIds)) {
                break;
            }
            Optional<UserGroup> userGroupOptional = Optional.empty();
            try {
                userGroupOptional = userGroupService.get(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), getDefaultUserGroupId(scope));
            } catch (Exception ex) {
                log.error("Unable to fetch User Group at scope:" + scope.toString(), ex);
            }
            try {
                if (userGroupOptional.isPresent()) {
                    UserGroup userGroup = userGroupOptional.get();
                    List<String> users = ImmutableList.copyOf(userGroup.getUsers());
                    users.addAll(userIds);
                    userGroup.setUsers(users);
                    userGroupService.update(toDTO(userGroup));
                }
            } catch (Exception ex) {
                log.error("Failed to update User Group at scope: " + scope.toString(), ex);
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
