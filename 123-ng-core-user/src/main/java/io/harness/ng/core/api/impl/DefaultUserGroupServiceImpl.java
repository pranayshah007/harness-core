package io.harness.ng.core.api.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.accesscontrol.principals.PrincipalDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentCreateRequestDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTO;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.beans.ScopeLevel;
import io.harness.ng.core.api.DefaultUserGroupService;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.remote.client.NGRestUtils;
import io.harness.utils.NGFeatureFlagHelperService;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static io.harness.NGConstants.ACCOUNT_BASIC_ROLE;
import static io.harness.NGConstants.ACCOUNT_VIEWER_ROLE;
import static io.harness.NGConstants.DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_ACCOUNT_LEVEL_USER_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_ORGANIZATION_LEVEL_USER_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_PROJECT_LEVEL_USER_GROUP_IDENTIFIER;
import static io.harness.NGConstants.ORGANIZATION_VIEWER_ROLE;
import static io.harness.NGConstants.PROJECT_VIEWER_ROLE;
import static io.harness.accesscontrol.principals.PrincipalType.USER_GROUP;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static java.util.Collections.emptyList;

@OwnedBy(PL)
@Singleton
@Slf4j
public class DefaultUserGroupServiceImpl implements DefaultUserGroupService {
    private final UserGroupService userGroupService;
    private final AccessControlAdminClient accessControlAdminClient;
    private final NGFeatureFlagHelperService ngFeatureFlagHelperService;

    @Inject
    public DefaultUserGroupServiceImpl(UserGroupService userGroupService, AccessControlAdminClient accessControlAdminClient,
                                       NGFeatureFlagHelperService ngFeatureFlagHelperService) {
        this.userGroupService = userGroupService;
        this.accessControlAdminClient = accessControlAdminClient;
        this.ngFeatureFlagHelperService = ngFeatureFlagHelperService;
    }

    @Override
    public UserGroup create(Scope scope, List<String> userIds) {
        String userGroupIdentifier = getUserGroupIdentifier(scope);
        String userGroupName = getUserGroupName(scope);
        String userGroupDescription = getUserGroupDescription(scope);

        UserGroupDTO userGroupDTO = UserGroupDTO.builder()
                .accountIdentifier(scope.getAccountIdentifier())
                .orgIdentifier(scope.getOrgIdentifier())
                .projectIdentifier(scope.getProjectIdentifier())
                .name(userGroupName)
                .description(userGroupDescription)
                .identifier(userGroupIdentifier)
                .isSsoLinked(false)
                .externallyManaged(false)
                .users(userIds  == null ? emptyList() : userIds)
                .harnessManaged(true)
                .build();

        UserGroup userGroup = userGroupService.createDefaultUserGroup(userGroupDTO);
        log.info("Creating all users usergroup {} at scope {}", userGroupIdentifier, scope);
        if (isNotEmpty(scope.getProjectIdentifier())) {
            createRoleAssignmentForProject(userGroupIdentifier, scope);
        }
        else if (isNotEmpty(scope.getOrgIdentifier())) {
            createRoleAssignmentsForOrganization(userGroupIdentifier, scope);
        }
        else {
            createRoleAssignmentsForAccount(userGroupIdentifier, scope);
        }
        log.info("Created all users usergroup {} at scope {}", userGroupIdentifier, scope);
        return userGroup;
    }

    @Override
    public String getUserGroupIdentifier(Scope scope) {
        String userGroupIdentifier = DEFAULT_ACCOUNT_LEVEL_USER_GROUP_IDENTIFIER;
        if (isNotEmpty(scope.getProjectIdentifier())) {
            userGroupIdentifier = DEFAULT_PROJECT_LEVEL_USER_GROUP_IDENTIFIER;
        } else if (isNotEmpty(scope.getOrgIdentifier())) {
            userGroupIdentifier = DEFAULT_ORGANIZATION_LEVEL_USER_GROUP_IDENTIFIER;
        }
        return userGroupIdentifier;
    }

    private String getUserGroupName(Scope scope) {
        String userGroupName = "Account All Users";
        if (isNotEmpty(scope.getProjectIdentifier())) {
            userGroupName = "Project All Users";
        } else if (isNotEmpty(scope.getOrgIdentifier())) {
            userGroupName = "Organization All Users";
        }
        return userGroupName;
    }

    private String getUserGroupDescription(Scope scope) {
        String userGroupDescription = "Account all users user group";
        if (isNotEmpty(scope.getProjectIdentifier())) {
            userGroupDescription = "Project all users user group";
        } else if (isNotEmpty(scope.getOrgIdentifier())) {
            userGroupDescription = "Organization all users user group";
        }
        return userGroupDescription;
    }

    private void createRoleAssignmentsForOrganization(String userGroupIdentifier, Scope scope) {
        createRoleAssignment(userGroupIdentifier, scope, true, ORGANIZATION_VIEWER_ROLE, DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER);
    }

    private void createRoleAssignmentForProject(String userGroupIdentifier, Scope scope) {
        createRoleAssignment(userGroupIdentifier, scope, true, PROJECT_VIEWER_ROLE, DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER);
    }

    private void createRoleAssignment(String userGroupIdentifier, Scope scope, boolean managed, String roleIdentifier, String resourceGroupIdentifier) {
        List<RoleAssignmentDTO> roleAssignmentDTOList = new ArrayList<>();
        roleAssignmentDTOList.add(RoleAssignmentDTO.builder()
                .resourceGroupIdentifier(resourceGroupIdentifier)
                .roleIdentifier(roleIdentifier)
                .disabled(false)
                .managed(managed)
                .principal(PrincipalDTO.builder().identifier(userGroupIdentifier)
                        .scopeLevel(ScopeLevel.of(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier()).name().toLowerCase())
                        .type(USER_GROUP)
                        .build())
                .build());

        RoleAssignmentCreateRequestDTO roleAssignmentCreateRequestDTO = RoleAssignmentCreateRequestDTO.builder()
                .roleAssignments(roleAssignmentDTOList).build();

        log.info("Creating role assignment for all users usergroup {} at scope {}", userGroupIdentifier, scope);
        NGRestUtils.getResponse(accessControlAdminClient.createMultiRoleAssignment(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), managed, roleAssignmentCreateRequestDTO));
        log.info("Created role assignment for all users usergroup {} at scope {}", userGroupIdentifier, scope);
    }

    private void createRoleAssignmentsForAccount(String principalIdentifier, Scope scope) {
        createRoleAssignment(principalIdentifier, scope, true, ACCOUNT_BASIC_ROLE, DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER);
        boolean isAccountBasicFeatureFlagEnabled = ngFeatureFlagHelperService.isEnabled(scope.getAccountIdentifier(), FeatureName.ACCOUNT_BASIC_ROLE_ONLY);
        if (!isAccountBasicFeatureFlagEnabled) {
            createRoleAssignment(principalIdentifier, scope, false, ACCOUNT_VIEWER_ROLE, DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER);
        }
    }

    @Override
    public void addUserToDefaultUserGroups(Scope scope, String userId) {
        addUserToDefaultUserGroup(scope.getAccountIdentifier(), null, null, DEFAULT_ACCOUNT_LEVEL_USER_GROUP_IDENTIFIER, userId);
        if (!isEmpty(scope.getOrgIdentifier())) {
            addUserToDefaultUserGroup(scope.getAccountIdentifier(), scope.getOrgIdentifier(), null, DEFAULT_ORGANIZATION_LEVEL_USER_GROUP_IDENTIFIER, userId);
        }
        if (!isEmpty(scope.getProjectIdentifier())) {
            addUserToDefaultUserGroup(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), DEFAULT_PROJECT_LEVEL_USER_GROUP_IDENTIFIER, userId);
        }
    }

    private void addUserToDefaultUserGroup(String accountIdentifier, String orgIdentifier, String projectIdentifier, String userGroupId, String userId) {
        Optional<UserGroup> userGroupOptional = userGroupService.get(accountIdentifier, orgIdentifier, projectIdentifier, userGroupId);
        if (!userGroupOptional.isPresent())
        {
            Scope scope = Scope.of(accountIdentifier, orgIdentifier, projectIdentifier);
            List<String> userIds = new ArrayList<>();
            userIds.add(userId);
            create(scope, userIds);
        }
        else if (!userGroupService.checkMember(accountIdentifier, orgIdentifier, projectIdentifier, userGroupId, userId)) {
            userGroupService.addMemberToDefaultUserGroup(accountIdentifier, orgIdentifier, projectIdentifier, userGroupId, userId);
        }
    }

    @Override
    public UserGroup update(UserGroup userGroup) {
        return userGroupService.updateDefaultUserGroup(userGroup);
    }

    @Override
    public Optional<UserGroup> get(String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
        return userGroupService.get(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    }
}
