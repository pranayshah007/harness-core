/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateEntityOwner;
import io.harness.delegate.service.intfc.DelegateNgTokenService;
import io.harness.delegate.utils.DelegateEntityOwnerHelper;
import io.harness.migrations.Migration;
import io.harness.organization.remote.OrganizationClient;
import io.harness.project.remote.ProjectClient;
import io.harness.remote.client.NGRestUtils;

import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(DEL)
public class CreateDefaultNgDelegateTokenMigration implements Migration {
  @Inject private AccountService accountService;
  @Inject @Named("PRIVILEGED") private OrganizationClient orgclient;
  @Inject @Named("PRIVILEGED") private ProjectClient projectClient;
  @Inject private DelegateNgTokenService delegateNgTokenService;

  @Override
  public void migrate() {
    log.info("Starting the migration for creating default ng delegate tokens for all accounts, orgs and projects.");
    try {
      accountService.listAllAccountsWithoutTheGlobalAccount().forEach(
          account -> createDefaultTokenPerAccount(account.getUuid()));
    } catch (Exception e) {
      log.error("Error occurred during trying to get the list of all accounts.", e);
    }
    log.info("Migration complete for creating default ng delegate tokens for all accounts, orgs and projects.");
  }

  private void createDefaultTokenPerAccount(String accountId) {
    try {
      log.info("Starting migration for creating default ng delegate tokens for account {}", accountId);
      delegateNgTokenService.upsertDefaultToken(accountId, null, true);
      List<DelegateEntityOwner> ownerList = new ArrayList<>();
      NGRestUtils.getResponse(orgclient.listAllOrganizations(accountId, Collections.emptyList(), null))
          .map(organizationResponse
              -> ownerList.add(
                  DelegateEntityOwnerHelper.buildOwner(organizationResponse.getOrganization().getIdentifier(), null)));
      for (DelegateEntityOwner owner : ownerList) {
        upsertDefaultDelegateToken(accountId, owner);
      }
      NGRestUtils.getResponse(projectClient.getProjectList(accountId, null))
          .forEach(projectDTO
              -> upsertDefaultDelegateToken(accountId,
                  DelegateEntityOwnerHelper.buildOwner(projectDTO.getOrgIdentifier(), projectDTO.getIdentifier())));
      log.info("Migration complete for creating default ng delegate tokens for account {}", accountId);
    } catch (Exception e) {
      log.error("Error occurred during fetching list of orgs and projects under account {}", accountId, e);
    }
  }

  private void upsertDefaultDelegateToken(String accountId, DelegateEntityOwner owner) {
    try {
      delegateNgTokenService.upsertDefaultToken(accountId, owner, true);
    } catch (Exception e) {
      log.error("Error occurred during migration for creating default ng delegate token for account {} and owner {} ",
          accountId, owner, e);
    }
  }
}
