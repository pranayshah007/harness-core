package io.harness.ccm.migration;

import static io.harness.AuthorizationServiceHeader.CE_NEXT_GEN;
import static io.harness.accesscontrol.principals.PrincipalType.USER;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.remote.client.NGRestUtils.getResponse;

import io.harness.ModuleType;
import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentCreateRequestDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentFilterDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentResponseDTO;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.remote.NgLicenseHttpClient;
import io.harness.migration.NGMigration;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import io.harness.utils.CryptoUtils;
import io.harness.utils.RestCallToNGManagerClientUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Call;

@Slf4j
@Singleton
public class CCMAdminRoleAssignmentMigration implements NGMigration {
  @Inject private AccessControlAdminClient accessControlAdminClient;
  @Inject private NgLicenseHttpClient ngLicenseHttpClient;
  private static final String ACCOUNT_VIEWER = "_account_viewer";
  private static final String CCM_ADMIN = "_ccm_admin";
  private static final String DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER = "_all_account_level_resources";
  private static final int DEFAULT_PAGE_SIZE = 1000;

  @Override
  public void migrate() {
    try {
      SecurityContextBuilder.setContext(new ServicePrincipal(CE_NEXT_GEN.getServiceId()));
      log.info("CCMAdminRoleAssignmentAdditionMigration starts ...");
      List<String> ceEnabledAccountIds = getCeEnabledNgAccounts();

      for (String accountId : ceEnabledAccountIds) {
        int pageIndex = 0;
        do {
          PageResponse<RoleAssignmentResponseDTO> roleAssignmentPage = getResponse(
              accessControlAdminClient.getFilteredRoleAssignments(accountId, null, null, pageIndex, DEFAULT_PAGE_SIZE,
                  RoleAssignmentFilterDTO.builder()
                      .resourceGroupFilter(Collections.singleton(DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER))
                      .principalTypeFilter(Collections.singleton(USER))
                      .roleFilter(Collections.singleton(ACCOUNT_VIEWER))
                      .build()));
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
            log.error("[CCMAdminRoleAssignmentMigration] Unexpected error occurred.", exception);
          }
          pageIndex++;
        } while (true);
      }
      log.info("CCMAdminRoleAssignmentMigration completed.");
    } catch (Exception e) {
      log.error("Exception while running CCMAdminRoleAssignmentMigration", e);
    } finally {
      SecurityContextBuilder.unsetCompleteContext();
    }
  }

  private RoleAssignmentDTO buildRoleAssignmentDTO(RoleAssignmentResponseDTO roleAssignmentResponseDTO) {
    return RoleAssignmentDTO.builder()
        .identifier("role_assignment_".concat(CryptoUtils.secureRandAlphaNumString(20)))
        .disabled(roleAssignmentResponseDTO.getRoleAssignment().isDisabled())
        .managed(false)
        .roleIdentifier(CCM_ADMIN)
        .resourceGroupIdentifier(roleAssignmentResponseDTO.getRoleAssignment().getResourceGroupIdentifier())
        .principal(roleAssignmentResponseDTO.getRoleAssignment().getPrincipal())
        .build();
  }

  public List<String> getCeEnabledNgAccounts() {
    long expiryTime = Instant.now().minus(15, ChronoUnit.DAYS).toEpochMilli();
    try {
      Call<ResponseDTO<List<ModuleLicenseDTO>>> moduleLicensesByModuleType =
          ngLicenseHttpClient.getModuleLicensesByModuleType(ModuleType.CE, expiryTime);
      List<ModuleLicenseDTO> ceEnabledLicenses = RestCallToNGManagerClientUtils.execute(moduleLicensesByModuleType);
      return ceEnabledLicenses.stream().map(ModuleLicenseDTO::getAccountIdentifier).collect(Collectors.toList());
    } catch (Exception ex) {
      log.error("Exception in account shard ", ex);
    }
    return Collections.emptyList();
  }
}