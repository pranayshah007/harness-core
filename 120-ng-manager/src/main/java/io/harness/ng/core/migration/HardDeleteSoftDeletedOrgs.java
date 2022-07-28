package io.harness.ng.core.migration;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.NGMigration;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.services.OrganizationService;
import io.harness.remote.client.RestClientUtils;
import io.harness.repositories.core.spring.OrganizationRepository;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PL)
@Slf4j
public class HardDeleteSoftDeletedOrgs implements NGMigration {
  @Inject private OrganizationRepository organizationRepository;
  @Inject AccountClient accountClient;
  @Inject OrganizationService organizationService;

  public void migrate() {
    try {
      log.info("Starting to hard delete soft deleted Organizations.");
      List<AccountDTO> accountDTOList = RestClientUtils.getResponse(accountClient.getAllAccounts());
      for (AccountDTO accountDTO : accountDTOList) {
        log.info("Starting to delete soft deleted Orgs for account {}.", accountDTO.getName());
        deleteSoftDeletedOrgsForAccount(accountDTO);
        log.info("Successfully deleted soft deleted Orgs for account {}.", accountDTO.getName());
      }
    } catch (Exception ex) {
      log.error("Background job for deleting soft Deleted organizations failed while fetching the accounts", ex);
    }
  }

  private void deleteSoftDeletedOrgsForAccount(AccountDTO accountDTO) {
    try {
      Criteria criteria = new Criteria();
      criteria.and(Organization.OrganizationKeys.accountIdentifier).is(accountDTO.getIdentifier());
      criteria.and(Organization.OrganizationKeys.deleted).is(true);
      List<Organization> allOrgs = organizationRepository.findAll(criteria);
      for (Organization organization : allOrgs) {
        organizationService.delete(accountDTO.getIdentifier(), organization.getIdentifier(), null);
      }
    } catch (Exception ex) {
      log.error(
          String.format(
              "Background job for deleting soft Deleted organizations failed while soft deleting Orgs with the accountId %s",
              accountDTO.getIdentifier()),
          ex);
    }
  }
}
