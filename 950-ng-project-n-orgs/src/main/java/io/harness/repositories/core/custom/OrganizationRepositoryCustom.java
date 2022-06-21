/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.core.custom;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.ng.core.entities.Organization;

import com.mongodb.client.result.UpdateResult;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PL)
public interface OrganizationRepositoryCustom {
  Page<Organization> findAll(Criteria criteria, Pageable pageable, boolean ignoreCase);

  List<String> findDistinctAccounts();

  Organization update(Query query, Update update);

  Organization delete(String accountIdentifier, String identifier, Long version);

  List<Organization> findAll(Criteria criteria);

  Organization restore(String accountIdentifier, String identifier);

  List<Scope> findAllOrgs(Criteria criteria);

  UpdateResult updateMultiple(Query query, Update update);
}
