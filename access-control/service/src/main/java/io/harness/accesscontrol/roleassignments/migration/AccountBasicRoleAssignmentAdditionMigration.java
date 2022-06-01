package io.harness.accesscontrol.roleassignments.migration;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.harness.accesscontrol.commons.helpers.FeatureFlagHelperService;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roleassignments.persistence.repositories.RoleAssignmentRepository;
import io.harness.accesscontrol.scopes.harness.HarnessScopeLevel;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.migration.NGMigration;
import io.harness.utils.CryptoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.query.Criteria;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO.RoleAssignmentDBOKeys;

import java.util.HashMap;
import java.util.List;

import static io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupConstants.DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static org.springframework.data.mongodb.core.query.Update.update;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PL)
public class AccountBasicRoleAssignmentAdditionMigration implements NGMigration {
    private final RoleAssignmentRepository roleAssignmentRepository;
    private  final FeatureFlagHelperService featureFlagHelperService;
    HashMap<String, Boolean> featureFlagForAccounts;

    @Inject
    public AccountBasicRoleAssignmentAdditionMigration(RoleAssignmentRepository roleAssignmentRepository, FeatureFlagHelperService featureFlagHelperService) {
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.featureFlagHelperService = featureFlagHelperService;
        this.featureFlagForAccounts = new HashMap<>();
    }

    @Override
    public void migrate() {
        log.info("AccountBasicRoleAssignmentAdditionMigration starts ...");
        int pageSize = 1000;
        int pageIndex = 0;

        do {
            Pageable pageable = PageRequest.of(pageIndex, pageSize);
            Criteria criteria = Criteria.where(RoleAssignmentDBOKeys.roleIdentifier).is("_account_viewer")
                    .and(RoleAssignmentDBOKeys.resourceGroupIdentifier).is(DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER)
                    .and(RoleAssignmentDBOKeys.scopeLevel).is(HarnessScopeLevel.ACCOUNT.getName())
                    .and(RoleAssignmentDBOKeys.managed).is(true);

            List<RoleAssignmentDBO> roleAssignmentList = roleAssignmentRepository.findAll(criteria, pageable).getContent();
            if (isEmpty(roleAssignmentList)) {
                break;
            }
            for (RoleAssignmentDBO roleAssignment : roleAssignmentList) {
                String accountId = roleAssignment.getScopeIdentifier().replace("/ACCOUNT/", "");
                try {
                    if (!featureFlagForAccounts.containsKey(accountId)) {
                        boolean response = featureFlagHelperService.isEnabled(FeatureName.ACCOUNT_BASIC_ROLE, accountId);
                        featureFlagForAccounts.put(accountId, response);
                    }
                    if (Boolean.TRUE.equals(featureFlagForAccounts.get(accountId))) {
                        RoleAssignmentDBO newRoleAssignmentDBO = buildRoleAssignmentDBO(roleAssignment);
                        roleAssignmentRepository.save(newRoleAssignmentDBO);
                        roleAssignmentRepository.updateById(
                                roleAssignment.getId(), update(RoleAssignmentDBOKeys.managed, false));
                    }
                } catch (Exception exception) {
                    log.error("[AccountBasicRoleAssignmentAdditionMigration] Unexpected error occurred.", exception);
                }
            }
            pageIndex++;
        }
        while (true);
        log.info("AccountBasicRoleAssignmentAdditionMigration completed.");
    }

    private RoleAssignmentDBO buildRoleAssignmentDBO(RoleAssignmentDBO roleAssignmentDBO) {
        return   RoleAssignmentDBO.builder()
                .identifier("role_assignment_".concat(CryptoUtils.secureRandAlphaNumString(20)))
                .scopeIdentifier(roleAssignmentDBO.getScopeIdentifier())
                .scopeLevel(roleAssignmentDBO.getScopeLevel())
                .disabled(roleAssignmentDBO.isDisabled())
                .managed(true)
                .roleIdentifier("_account_basic")
                .resourceGroupIdentifier(roleAssignmentDBO.getResourceGroupIdentifier())
                .principalScopeLevel(roleAssignmentDBO.getPrincipalScopeLevel())
                .principalIdentifier(roleAssignmentDBO.getPrincipalIdentifier())
                .principalType(roleAssignmentDBO.getPrincipalType())
                .build();
    }
}
