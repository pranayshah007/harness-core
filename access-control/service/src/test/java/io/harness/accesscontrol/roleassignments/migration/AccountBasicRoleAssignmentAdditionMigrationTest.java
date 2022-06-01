package io.harness.accesscontrol.roleassignments.migration;

import io.harness.accesscontrol.AccessControlTestBase;
import io.harness.accesscontrol.commons.helpers.FeatureFlagHelperService;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roleassignments.persistence.repositories.RoleAssignmentRepository;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.List;

import static io.harness.rule.OwnerRule.KARAN;
import static junit.framework.TestCase.*;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;

@OwnedBy(HarnessTeam.PL)
public class AccountBasicRoleAssignmentAdditionMigrationTest extends AccessControlTestBase {
    @Inject private RoleAssignmentRepository roleAssignmentRepository;
    @Mock private FeatureFlagHelperService featureFlagHelperService;
    AccountBasicRoleAssignmentAdditionMigration accountBasicRoleAssignmentAdditionMigration;
    private static final String ACCOUNT_VIEWER_ROLE = "_account_viewer";
    private static final String ACCOUNT_BASIC_ROLE = "_account_basic";

    @Before
    public void setup() {
        accountBasicRoleAssignmentAdditionMigration = new AccountBasicRoleAssignmentAdditionMigration(roleAssignmentRepository, featureFlagHelperService);
    }

    @Test
    @Owner(developers = KARAN)
    @Category(UnitTests.class)
    public void migrateRoleAssignmentsWithAccountViewerRoleToAccountBasicRole() {
        RoleAssignmentDBO roleAssignmentDBO = getRoleAssignmentDBO();
        roleAssignmentRepository.save(roleAssignmentDBO);
        accountBasicRoleAssignmentAdditionMigration.migrate();
        Criteria newAccountBasicRoleCriteria = Criteria.where(RoleAssignmentDBO.RoleAssignmentDBOKeys.roleIdentifier)
                .is(ACCOUNT_BASIC_ROLE);
        List<RoleAssignmentDBO> roleAssignmentDBOList =
                roleAssignmentRepository.findAll(newAccountBasicRoleCriteria, PageRequest.of(0, 1000)).getContent();
        assertEquals(1, roleAssignmentDBOList.size());
        RoleAssignmentDBO savedAccountBasicRoleAssignment = roleAssignmentDBOList.get(0);
        assertEquals(roleAssignmentDBO.getPrincipalIdentifier() ,savedAccountBasicRoleAssignment.getPrincipalIdentifier());
        assertEquals(roleAssignmentDBO.getPrincipalType(), savedAccountBasicRoleAssignment.getPrincipalType());
        assertEquals(roleAssignmentDBO.getResourceGroupIdentifier(), savedAccountBasicRoleAssignment.getResourceGroupIdentifier());
        assertEquals(ACCOUNT_BASIC_ROLE, roleAssignmentDBO.getRoleIdentifier());
        assertTrue(roleAssignmentDBO.getManaged());

        Criteria existingAccountViewerRoleCriteria = Criteria.where(RoleAssignmentDBO.RoleAssignmentDBOKeys.roleIdentifier)
                .is(ACCOUNT_VIEWER_ROLE);
        roleAssignmentDBOList =
                roleAssignmentRepository.findAll(existingAccountViewerRoleCriteria, PageRequest.of(0, 1000)).getContent();
        assertEquals(1, roleAssignmentDBOList.size());
        RoleAssignmentDBO existingAccountBasicRoleAssignment = roleAssignmentDBOList.get(0);
        assertEquals(roleAssignmentDBO.getPrincipalIdentifier() ,existingAccountBasicRoleAssignment.getPrincipalIdentifier());
        assertEquals(roleAssignmentDBO.getPrincipalType(), existingAccountBasicRoleAssignment.getPrincipalType());
        assertEquals(roleAssignmentDBO.getResourceGroupIdentifier(), existingAccountBasicRoleAssignment.getResourceGroupIdentifier());
        assertEquals(ACCOUNT_VIEWER_ROLE, roleAssignmentDBO.getRoleIdentifier());
        assertFalse(roleAssignmentDBO.getManaged());
    }

    private  RoleAssignmentDBO getRoleAssignmentDBO() {
        return RoleAssignmentDBO.builder()
                .id(randomAlphabetic(10))
                .identifier(randomAlphabetic(10))
                .scopeLevel(ScopeLevel.ACCOUNT.name())
                .scopeIdentifier(randomAlphabetic(10))
                .principalType(PrincipalType.USER)
                .principalIdentifier(randomAlphabetic(10))
                .resourceGroupIdentifier(randomAlphabetic(10))
                .roleIdentifier(ACCOUNT_VIEWER_ROLE)
                .managed(true)
                .build();
    }
}
