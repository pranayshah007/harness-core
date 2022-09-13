/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.NGMigration;
import io.harness.ng.core.entities.Organization;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.core.spring.OrganizationRepository;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class NGDefaultTokenForOrganizationsMigration implements NGMigration {
  private final OrganizationRepository organizationRepository;
  private final AccountClient accountClient;

  @Inject
  public NGDefaultTokenForOrganizationsMigration(
      OrganizationRepository organizationRepository, AccountClient accountClient) {
    this.organizationRepository = organizationRepository;
    this.accountClient = accountClient;
  }

  @Override
  public void migrate() {
    try {
      log.info("[NGDefaultTokenForOrganizationsMigration] Generating default NG Tokens for organizations");
      Iterable<Organization> organizations =
          organizationRepository.findAll(Criteria.where("deleted").is(Boolean.FALSE), Pageable.unpaged(), true);
      Map<String, List<String>> orgsWithActiveDefaultDelegateToken =
          StreamSupport.stream(organizations.spliterator(), false)
              .map(org -> org.getAccountIdentifier())
              .filter(Objects::nonNull)
              .distinct()
              .collect(Collectors.toMap(Function.identity(),
                  accountId
                  -> NGRestUtils.getCgResponse(accountClient.getOrgsWithActiveDefaultDelegateToken(accountId))));
      for (Organization org : organizations) {
        if (orgsWithActiveDefaultDelegateToken.get(org.getAccountIdentifier()) != null
            && orgsWithActiveDefaultDelegateToken.get(org.getAccountIdentifier())
                   .stream()
                   .anyMatch(i -> i.equals(org.getIdentifier()))) {
          continue;
        }
        try {
          NGRestUtils.getCgResponse(
              accountClient.upsertDefaultToken(org.getAccountIdentifier(), org.getIdentifier(), null, true));
        } catch (Exception e) {
          log.error(
              "[NGDefaultTokenForOrganizationsMigration] Failed to create default Delegate Token for account {} and organization {}",
              org.getAccountIdentifier(), org.getIdentifier());
        }
      }
    } catch (Exception e) {
      log.error(
          "[NGDefaultTokenForOrganizationsMigration] Migration for generating default Delegate NG Token for organizations failed with error {}. Ignoring the error.",
          e);
    }
  }
}
