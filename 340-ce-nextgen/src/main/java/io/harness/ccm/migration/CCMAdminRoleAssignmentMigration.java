package io.harness.ccm.migration;

import static io.harness.accesscontrol.principals.PrincipalType.USER;
import static io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupConstants.DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.remote.client.NGRestUtils.getResponse;

import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.accesscontrol.commons.helpers.AccountHelperService;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentCreateRequestDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentFilterDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentResponseDTO;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.scopes.harness.HarnessScopeLevel;
import io.harness.migration.NGMigration;
import io.harness.ng.beans.PageResponse;
import io.harness.utils.CryptoUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
@Singleton
public class CCMAdminRoleAssignmentMigration implements NGMigration {
  @Inject private AccountHelperService accountHelperService;
  @Inject private AccessControlAdminClient accessControlAdminClient;
  private static final String ACCOUNT_VIEWER = "_account_viewer";
  private static final String CCM_ADMIN = "_ccm_admin";
  private static final int DEFAULT_PAGE_SIZE = 1000;

  @Override
  public void migrate() {
    log.info("CCMAdminRoleAssignmentAdditionMigration starts ...");
    int pageSize = 1000;
    int pageIndex = 0;
    List<String> ceEnabledAccountIds = accountHelperService.getCeEnabledNgAccounts();

    for (String accountId : ceEnabledAccountIds) {
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

        PageResponse<RoleAssignmentResponseDTO> roleAssignmentPage =
            getResponse(accessControlAdminClient.getFilteredRoleAssignments(accountId, null, null, 0, DEFAULT_PAGE_SIZE,
                RoleAssignmentFilterDTO.builder().roleFilter(Collections.singleton(ACCOUNT_VIEWER)).build()));
        List<RoleAssignmentResponseDTO> accountViewerRoleAssignments = roleAssignmentPage.getContent();

        if (isEmpty(accountViewerRoleAssignments)) {
          log.info("roleAssignmentList break");
          break;
        }
        List<RoleAssignmentDTO> roleAssignments = new ArrayList<>();
        accountViewerRoleAssignments.forEach(
            accountViewerRoleAssignment -> roleAssignments.add(buildRoleAssignmentDTO(accountViewerRoleAssignment)));

        try {
          RoleAssignmentCreateRequestDTO createRequestDTO =
              RoleAssignmentCreateRequestDTO.builder().roleAssignments(roleAssignments).build();
          getResponse(
              accessControlAdminClient.createMultiRoleAssignment(accountId, null, null, false, createRequestDTO));
        } catch (Exception exception) {
          log.error("[CCMAdminRoleAssignmentAdditionMigration] Unexpected error occurred.", exception);
        }
        pageIndex++;
      } while (true);
    }
    log.info("CCMAdminRoleAssignmentAdditionMigration completed.");
  }

  private RoleAssignmentDTO buildRoleAssignmentDTO(RoleAssignmentResponseDTO roleAssignmentResponseDTO) {
    return RoleAssignmentDTO.builder()
        .roleIdentifier("role_assignment_".concat(CryptoUtils.secureRandAlphaNumString(20)))
        .disabled(roleAssignmentResponseDTO.getRoleAssignment().isDisabled())
        .managed(false)
        .roleIdentifier(CCM_ADMIN)
        .resourceGroupIdentifier(roleAssignmentResponseDTO.getRoleAssignment().getResourceGroupIdentifier())
        .principal(roleAssignmentResponseDTO.getRoleAssignment().getPrincipal())
        .build();
  }
}
