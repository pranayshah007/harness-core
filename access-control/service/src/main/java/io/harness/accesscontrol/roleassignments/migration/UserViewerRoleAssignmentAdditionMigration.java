/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.migration;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.principals.users.persistence.UserDBO;
import io.harness.accesscontrol.principals.users.persistence.UserRepository;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roleassignments.persistence.repositories.RoleAssignmentRepository;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.accesscontrol.scopes.harness.HarnessScopeParams;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.NGMigration;
import io.harness.utils.CryptoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static io.harness.NGConstants.ACCOUNT_VIEWER_ROLE;
import static io.harness.NGConstants.DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.NGConstants.ORGANIZATION_VIEWER_ROLE;
import static io.harness.NGConstants.PROJECT_VIEWER_ROLE;
import static io.harness.accesscontrol.scopes.harness.ScopeMapper.fromParams;
import static io.harness.accesscontrol.scopes.harness.ScopeMapper.toParams;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PL)
public class UserViewerRoleAssignmentAdditionMigration implements NGMigration {
    private final RoleAssignmentRepository roleAssignmentRepository;
    private static final String DEBUG_MESSAGE = "UserViewerRoleAssignmentAdditionMigration: ";
    private final UserRepository userRepository;
    private ScopeService scopeService;

    @Inject
    public UserViewerRoleAssignmentAdditionMigration(RoleAssignmentRepository roleAssignmentRepository, UserRepository userRepository,
                                                     ScopeService scopeService) {
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.userRepository = userRepository;
        this.scopeService = scopeService;
    }

    @Override
    public void migrate() {
        log.info(DEBUG_MESSAGE + "started.");
        int pageSize = 1000;
        int pageIndex = 0;

        try {
            do {
                Pageable pageable = PageRequest.of(pageIndex, pageSize);
                try {
                    List<UserDBO> userDBOList = userRepository.findAll(pageable).getContent();
                    if (isEmpty(userDBOList)) {
                        break;
                    }
                    for (UserDBO userDBO : userDBOList) {
                        try {
                            Scope scope = scopeService.buildScopeFromScopeIdentifier(userDBO.getScopeIdentifier());
                            HarnessScopeParams scopeParams = toParams(scope);
                            RoleAssignmentDBO roleAssignmentDBO;
                            if (!isEmpty(scopeParams.getProjectIdentifier())) {
                                roleAssignmentDBO = buildRoleAssignmentDBO(PROJECT_VIEWER_ROLE, DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER,
                                        userDBO.getIdentifier(), scopeParams);
                            } else if (!isEmpty(scopeParams.getOrgIdentifier())) {
                                roleAssignmentDBO = buildRoleAssignmentDBO(ORGANIZATION_VIEWER_ROLE, DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER,
                                        userDBO.getIdentifier(), scopeParams);
                            } else {
                                roleAssignmentDBO = buildRoleAssignmentDBO(ACCOUNT_VIEWER_ROLE, DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER,
                                        userDBO.getIdentifier(), scopeParams);
                            }
                            roleAssignmentRepository.save(roleAssignmentDBO);
                        }
                        catch (Exception ex) {
                            log.error(DEBUG_MESSAGE + "failed to save role assignment for user: " + userDBO, ex);
                        }
                    }
                }
                catch (Exception ex){
                    log.error(DEBUG_MESSAGE + "failed to load users for pageIndex:" + pageIndex, ex);
                }
                pageIndex++;
            } while (true);
        }
        catch (Exception ex) {
            log.error(DEBUG_MESSAGE + "failed.", ex);
        }
        log.info(DEBUG_MESSAGE + "completed.");
    }

    private RoleAssignmentDBO buildRoleAssignmentDBO(String roleIdentifier, String resourceGroupIdentifier,
                                                     String principalIdentifier, HarnessScopeParams scopeParams) {
        Scope scope = fromParams(scopeParams);
        return RoleAssignmentDBO.builder()
                .identifier("role_assignment_".concat(CryptoUtils.secureRandAlphaNumString(20)))
                .scopeIdentifier(scope.toString())
                .scopeLevel(scope.getLevel().getResourceType().toLowerCase())
                .disabled(false)
                .managed(true)
                .internal(false)
                .roleIdentifier(roleIdentifier)
                .resourceGroupIdentifier(resourceGroupIdentifier)
                .principalIdentifier(principalIdentifier)
                .principalType(PrincipalType.USER)
                .build();
    }
}
