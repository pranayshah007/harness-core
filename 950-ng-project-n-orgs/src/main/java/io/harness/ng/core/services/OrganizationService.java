/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.services;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.dto.OrganizationFilterDTO;
import io.harness.ng.core.entities.Organization;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PL)
public interface OrganizationService {
  Organization create(String accountIdentifier, OrganizationDTO organization);

  List<String> getDistinctAccounts();

  Optional<Organization> get(String accountIdentifier, String identifier);

  Optional<Organization> getConsideringCase(String accountIdentifier, String identifier);

  Organization update(String accountIdentifier, String identifier, OrganizationDTO organization);

  /**
   * Use this method with caution, verify that the pageable sort is able to make use of the indexes.
   */
  Page<Organization> listPermittedOrgs(
      String accountIdentifier, Pageable pageable, OrganizationFilterDTO organizationFilterDTO);

  /**
   * Use this method with caution, verify that the criteria and pageable sort is able to make use of the indexes.
   */
  Page<Organization> list(Criteria criteria, Pageable pageable);

  /**
   * Use this method with caution, verify that the criteria is able to make use of the indexes.
   */
  List<Organization> list(Criteria criteria);

  boolean delete(String accountIdentifier, String identifier, Long version);

  boolean restore(String accountIdentifier, String identifier);

  Long countOrgs(String accountIdentifier);

  Set<String> getPermittedOrganizations(@NotNull String accountIdentifier, String orgIdentifier);
}
