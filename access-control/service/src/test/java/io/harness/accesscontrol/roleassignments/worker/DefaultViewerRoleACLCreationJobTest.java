/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.worker;

import static io.harness.NGConstants.ACCOUNT_VIEWER_ROLE;
import static io.harness.NGConstants.DEFAULT_ACCOUNT_LEVEL_USER_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_ORGANIZATION_LEVEL_USER_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_PROJECT_LEVEL_USER_GROUP_IDENTIFIER;
import static io.harness.NGConstants.ORGANIZATION_VIEWER_ROLE;
import static io.harness.NGConstants.PROJECT_VIEWER_ROLE;
import static io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupConstants.DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.FeatureName.PL_REGENERATE_ACL_FOR_DEFAULT_VIEWER_ROLE;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.AccessControlTestBase;
import io.harness.accesscontrol.acl.persistence.ACL;
import io.harness.accesscontrol.acl.persistence.repositories.ACLRepository;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roleassignments.persistence.repositories.RoleAssignmentRepository;
import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.harness.HarnessScopeLevel;
import io.harness.accesscontrol.scopes.harness.ScopeMapper;
import io.harness.aggregator.consumers.ACLGeneratorService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.lock.PersistentLocker;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.HashSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(PL)
public class DefaultViewerRoleACLCreationJobTest extends AccessControlTestBase {
  @Inject private MongoTemplate mongoTemplate;
  @Inject private RoleAssignmentRepository roleAssignmentRepository;
  @Inject @Named(ACL.PRIMARY_COLLECTION) private ACLRepository aclRepository;
  @Mock private ACLGeneratorService aclGeneratorService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private PersistentLocker persistentLocker;
  private DefaultViewerRoleACLCreationJob defaultViewerRoleACLCreationJob;

  RoleAssignmentDBO accountRoleAssignmentDBO;
  RoleAssignmentDBO organizationRoleAssignmentDBO;
  RoleAssignmentDBO projectRoleAssignmentDBO;

  @Before
  public void setup() {
    defaultViewerRoleACLCreationJob = new DefaultViewerRoleACLCreationJob(
        featureFlagService, aclRepository, aclGeneratorService, mongoTemplate, persistentLocker);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testAclGenerationForDefaultViewerRoleForConfiguredAccount() {
    String accountIdentifier = "04Iq9MDcT9WOBwwS6C4oKw";
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    createEntities(accountIdentifier, orgIdentifier, projectIdentifier);

    when(featureFlagService.isEnabled(PL_REGENERATE_ACL_FOR_DEFAULT_VIEWER_ROLE, accountIdentifier)).thenReturn(true);
    defaultViewerRoleACLCreationJob.execute();

    verify(aclGeneratorService, times(0)).createACLsForRoleAssignment(accountRoleAssignmentDBO);
    verify(aclGeneratorService, times(0))
        .createImplicitACLsForRoleAssignment(accountRoleAssignmentDBO, new HashSet<>(), new HashSet<>());

    verify(aclGeneratorService, times(1)).createACLsForRoleAssignment(organizationRoleAssignmentDBO);
    verify(aclGeneratorService, times(1))
        .createImplicitACLsForRoleAssignment(organizationRoleAssignmentDBO, new HashSet<>(), new HashSet<>());

    verify(aclGeneratorService, times(1)).createACLsForRoleAssignment(projectRoleAssignmentDBO);
    verify(aclGeneratorService, times(1))
        .createImplicitACLsForRoleAssignment(projectRoleAssignmentDBO, new HashSet<>(), new HashSet<>());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testNoAclGenerationForDefaultViewerRoleForNotConfiguredAccount() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    createEntities(accountIdentifier, orgIdentifier, projectIdentifier);

    when(featureFlagService.isEnabled(PL_REGENERATE_ACL_FOR_DEFAULT_VIEWER_ROLE, accountIdentifier)).thenReturn(true);

    defaultViewerRoleACLCreationJob.execute();

    verifyNoInteractions(aclGeneratorService);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testNoAclGenerationForDefaultViewerRoleForConfiguredAccountWhenFeatureFlagIsDisabled() {
    String accountIdentifier = "04Iq9MDcT9WOBwwS6C4oKw";
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    createEntities(accountIdentifier, orgIdentifier, projectIdentifier);

    when(featureFlagService.isEnabled(PL_REGENERATE_ACL_FOR_DEFAULT_VIEWER_ROLE, accountIdentifier)).thenReturn(false);

    defaultViewerRoleACLCreationJob.execute();

    verifyNoInteractions(aclGeneratorService);
  }

  private void createEntities(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    when(featureFlagService.isEnabled(PL_REGENERATE_ACL_FOR_DEFAULT_VIEWER_ROLE, accountIdentifier)).thenReturn(true);

    when(aclGeneratorService.createACLsForRoleAssignment(any())).thenReturn(1L);

    accountRoleAssignmentDBO = createAccountScopeEntity(accountIdentifier);
    organizationRoleAssignmentDBO = createOrganizationScopeEntity(accountIdentifier, orgIdentifier);
    projectRoleAssignmentDBO = createProjectScopeEntity(accountIdentifier, orgIdentifier, projectIdentifier);
  }

  private RoleAssignmentDBO createAccountScopeEntity(String accountIdentifier) {
    Scope scope = ScopeMapper.fromDTO(
        ScopeDTO.builder().accountIdentifier(accountIdentifier).orgIdentifier(null).projectIdentifier(null).build());

    String roleAssignmentIdentifier = randomAlphabetic(10);

    RoleAssignmentDBO existingRoleAssignmentDBO =
        RoleAssignmentDBO.builder()
            .scopeIdentifier(scope.toString())
            .scopeLevel(scope.getLevel().toString())
            .identifier(roleAssignmentIdentifier)
            .principalIdentifier(DEFAULT_ACCOUNT_LEVEL_USER_GROUP_IDENTIFIER)
            .principalType(PrincipalType.USER_GROUP)
            .principalScopeLevel(HarnessScopeLevel.ACCOUNT.getName())
            .roleIdentifier(ACCOUNT_VIEWER_ROLE)
            .resourceGroupIdentifier(DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER)
            .build();

    roleAssignmentRepository.save(existingRoleAssignmentDBO);
    return existingRoleAssignmentDBO;
  }

  private RoleAssignmentDBO createOrganizationScopeEntity(String accountIdentifier, String orgIdentifier) {
    Scope scope = ScopeMapper.fromDTO(ScopeDTO.builder()
                                          .accountIdentifier(accountIdentifier)
                                          .orgIdentifier(orgIdentifier)
                                          .projectIdentifier(null)
                                          .build());
    String roleAssignmentIdentifier = randomAlphabetic(10);
    RoleAssignmentDBO existingRoleAssignmentDBO =
        RoleAssignmentDBO.builder()
            .scopeIdentifier(scope.toString())
            .scopeLevel(scope.getLevel().toString())
            .identifier(roleAssignmentIdentifier)
            .principalIdentifier(DEFAULT_ORGANIZATION_LEVEL_USER_GROUP_IDENTIFIER)
            .managed(true)
            .principalType(PrincipalType.USER_GROUP)
            .principalScopeLevel(HarnessScopeLevel.ORGANIZATION.getName())
            .roleIdentifier(ORGANIZATION_VIEWER_ROLE)
            .resourceGroupIdentifier(DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER)
            .build();

    roleAssignmentRepository.save(existingRoleAssignmentDBO);
    return existingRoleAssignmentDBO;
  }

  private RoleAssignmentDBO createProjectScopeEntity(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Scope scope = ScopeMapper.fromDTO(ScopeDTO.builder()
                                          .accountIdentifier(accountIdentifier)
                                          .orgIdentifier(orgIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .build());
    String roleAssignmentIdentifier = randomAlphabetic(10);
    RoleAssignmentDBO existingRoleAssignmentDBO =
        RoleAssignmentDBO.builder()
            .scopeIdentifier(scope.toString())
            .scopeLevel(scope.getLevel().toString())
            .identifier(roleAssignmentIdentifier)
            .principalIdentifier(DEFAULT_PROJECT_LEVEL_USER_GROUP_IDENTIFIER)
            .managed(true)
            .principalType(PrincipalType.USER_GROUP)
            .principalScopeLevel(HarnessScopeLevel.PROJECT.getName())
            .roleIdentifier(PROJECT_VIEWER_ROLE)
            .resourceGroupIdentifier(DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER)
            .build();

    roleAssignmentRepository.save(existingRoleAssignmentDBO);
    return existingRoleAssignmentDBO;
  }
}
