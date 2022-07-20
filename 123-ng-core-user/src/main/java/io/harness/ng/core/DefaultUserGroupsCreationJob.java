package io.harness.ng.core;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import io.dropwizard.lifecycle.Managed;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.remote.client.RestClientUtils;
import io.harness.repositories.user.spring.UserMembershipRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.List;
import java.util.stream.Collectors;

import static io.harness.NGConstants.DEFAULT_ACCOUNT_LEVEL_USER_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_ORGANIZATION_LEVEL_USER_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_PROJECT_LEVEL_USER_GROUP_IDENTIFIER;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class DefaultUserGroupsCreationJob implements Managed {
    @Inject UserGroupService userGroupService;
    @Inject AccountClient accountClient;
    @Inject OrganizationService organizationService;
    @Inject ProjectService projectService;
    @Inject UserMembershipRepository userMembershipRepository;

    private static final String DEBUG_MESSAGE = "DefaultUserGroupsMigration: ";


    @Override
    public void start() throws Exception {
        log.info(DEBUG_MESSAGE + "User Groups creation started.");

        try {
            List<AccountDTO> accountDTOList = RestClientUtils.getResponse(accountClient.getAllAccounts());
            List<String> accountIdsToBeInserted = accountDTOList.stream()
                    .filter(AccountDTO::isNextGenEnabled)
                    .map(AccountDTO::getIdentifier)
                    .collect(Collectors.toList());
            createUserGroupForAccounts(accountIdsToBeInserted);
        }
        catch (Exception ex) {
            log.error(DEBUG_MESSAGE + " Fetching all accounts failed : ", ex);
        }

        log.info(DEBUG_MESSAGE + "User Groups creation completed.");
    }

    @Override
    public void stop() throws Exception {

    }

    private void createUserGroupForAccounts(List<String> accountIds) {
        for (String accountId : accountIds) {
            try {
                Scope scope = Scope.of(accountId, null, null);
                userGroupService.setUpDefaultUserGroup(scope);
                addUsersAtScopeToDefaultUserGroup(scope);
                createUserGroupForOrganizations(accountId);
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
            if (organizations.isEmpty()) {
                break;
            }
            for (Organization organization : organizations) {
                try {
                    Scope scope = Scope.of(accountId, organization.getId(), null);
                    userGroupService.setUpDefaultUserGroup(scope);
                    addUsersAtScopeToDefaultUserGroup(scope);
                    createUserGroupForProjects(accountId, organization.getId());
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
            Criteria criteria = Criteria.where("organizationId").is(organizationId);
            List<Project> projects = projectService.list(criteria, pageable).getContent();
            if (projects.isEmpty()) {
                break;
            }
            for (Project project : projects) {
                try {
                    Scope scope = Scope.of(accountId, organizationId, project.getId());
                    userGroupService.setUpDefaultUserGroup(scope);
                    addUsersAtScopeToDefaultUserGroup(scope);
                } catch (Exception ex) {
                    log.error(DEBUG_MESSAGE + "User Group Creation failed for Project: " + project.getId(), ex);
                }
            }
        }
        while (true);
    }

    private void addUsersAtScopeToDefaultUserGroup(Scope scope) {
        do {
            int pageIndex = 0;
            int pageSize = 1000;
            Pageable pageable = PageRequest.of(pageIndex, pageSize);
            Criteria criteria = Criteria.where("scope.accountIdentifier").is(scope.getAccountIdentifier());
            if (isNotEmpty(scope.getOrgIdentifier()))
            {
                criteria.andOperator(Criteria.where("scope.orgIdentifier").is(scope.getOrgIdentifier()));
            }
            if (isNotEmpty(scope.getProjectIdentifier())) {
                criteria.andOperator(Criteria.where("scope.projectIdentifier").is(scope.getProjectIdentifier()));
            }
            List<String> userIds = userMembershipRepository.findAllUserIds(criteria, pageable).getContent();
            if (userIds.isEmpty()) {
                break;
            }
            for(String userId : userIds) {
                userGroupService.addUserToUserGroups(scope, userId, ImmutableList.of(getDefaultUserGroupId(scope)));
            }
        }
        while(true);
    }

    private String getDefaultUserGroupId(Scope scope) {
        if (isNotEmpty(scope.getProjectIdentifier())) {
            return DEFAULT_PROJECT_LEVEL_USER_GROUP_IDENTIFIER;
        }
        else if (isNotEmpty(scope.getOrgIdentifier())) {
            return DEFAULT_ORGANIZATION_LEVEL_USER_GROUP_IDENTIFIER;
        }
        return  DEFAULT_ACCOUNT_LEVEL_USER_GROUP_IDENTIFIER;
    }
}
