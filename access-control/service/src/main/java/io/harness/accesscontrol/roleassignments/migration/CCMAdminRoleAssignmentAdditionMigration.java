package io.harness.accesscontrol.roleassignments.migration;

import static io.harness.accesscontrol.principals.PrincipalType.USER;
import static io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupConstants.DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static org.springframework.data.mongodb.core.query.Update.update;

import io.harness.accesscontrol.commons.helpers.AccountHelperService;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roleassignments.persistence.repositories.RoleAssignmentRepository;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.accesscontrol.scopes.harness.HarnessScopeLevel;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.NGMigration;
import io.harness.utils.CryptoUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CE)
public class CCMAdminRoleAssignmentAdditionMigration implements NGMigration {
  private final RoleAssignmentRepository roleAssignmentRepository;
  private final ScopeService scopeService;
  private final AccountHelperService accountHelperService;
  private static final String ACCOUNT_VIEWER = "_account_viewer";
  private static final String CCM_ADMIN = "_ccm_admin";

  @Inject
  public CCMAdminRoleAssignmentAdditionMigration(RoleAssignmentRepository roleAssignmentRepository,
      ScopeService scopeService, AccountHelperService accountHelperService) {
    this.roleAssignmentRepository = roleAssignmentRepository;
    this.scopeService = scopeService;
    this.accountHelperService = accountHelperService;
  }

  @Override
  public void migrate() {
    log.info("CCMAdminRoleAssignmentAdditionMigration starts ...");
    int pageSize = 1000;
    int pageIndex = 0;

    do {
      Pageable pageable = PageRequest.of(pageIndex, pageSize);
      Criteria criteria = Criteria.where(RoleAssignmentDBO.RoleAssignmentDBOKeys.roleIdentifier)
                              .is(ACCOUNT_VIEWER)
                              .and(RoleAssignmentDBO.RoleAssignmentDBOKeys.resourceGroupIdentifier)
                              .is(DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER)
                              .and(RoleAssignmentDBO.RoleAssignmentDBOKeys.scopeLevel)
                              .is(HarnessScopeLevel.ACCOUNT.getName())
                              .and(RoleAssignmentDBO.RoleAssignmentDBOKeys.principalType)
                              .is(USER);

      List<RoleAssignmentDBO> roleAssignmentList =
          roleAssignmentRepository
              .findAll(
                  criteria, pageable, Sort.by(Sort.Direction.ASC, RoleAssignmentDBO.RoleAssignmentDBOKeys.createdAt))
              .getContent();

      List<String> ceEnabledAccountIds = accountHelperService.getCeEnabledNgAccounts();
      if (isEmpty(roleAssignmentList)) {
        break;
      }
      for (RoleAssignmentDBO roleAssignment : roleAssignmentList) {
        if (roleAssignment.isManaged()) {
          String accountId =
              scopeService.buildScopeFromScopeIdentifier(roleAssignment.getScopeIdentifier()).getInstanceId();
          if (ceEnabledAccountIds.contains(accountId)) {
            try {
              RoleAssignmentDBO newRoleAssignmentDBO = buildRoleAssignmentDBO(roleAssignment);
              try {
                roleAssignmentRepository.save(newRoleAssignmentDBO);
              } catch (DuplicateKeyException e) {
                log.error("Corresponding ccm admin was already created {}", newRoleAssignmentDBO.toString(), e);
              }
              roleAssignmentRepository.updateById(
                  roleAssignment.getId(), update(RoleAssignmentDBO.RoleAssignmentDBOKeys.managed, false));

            } catch (Exception exception) {
              log.error("[CCMAdminRoleAssignmentAdditionMigration] Unexpected error occurred.", exception);
            }
          }
        }
      }
      pageIndex++;
    } while (true);
    log.info("CCMAdminRoleAssignmentAdditionMigration completed.");
  }

  private RoleAssignmentDBO buildRoleAssignmentDBO(RoleAssignmentDBO roleAssignmentDBO) {
    return RoleAssignmentDBO.builder()
        .identifier("role_assignment_".concat(CryptoUtils.secureRandAlphaNumString(20)))
        .scopeIdentifier(roleAssignmentDBO.getScopeIdentifier())
        .scopeLevel(roleAssignmentDBO.getScopeLevel())
        .disabled(roleAssignmentDBO.isDisabled())
        .managed(true)
        .roleIdentifier(CCM_ADMIN)
        .resourceGroupIdentifier(roleAssignmentDBO.getResourceGroupIdentifier())
        .principalScopeLevel(roleAssignmentDBO.getPrincipalScopeLevel())
        .principalIdentifier(roleAssignmentDBO.getPrincipalIdentifier())
        .principalType(roleAssignmentDBO.getPrincipalType())
        .build();
  }
}
