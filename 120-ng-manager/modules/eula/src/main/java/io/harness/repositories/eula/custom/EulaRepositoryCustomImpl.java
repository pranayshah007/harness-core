/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.eula.custom;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eula.entity.Eula;
import io.harness.eula.entity.Eula.EulaKeys;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(PL)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class EulaRepositoryCustomImpl implements EulaRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public Eula upsert(Eula eula) {
    Criteria criteria = Criteria.where(EulaKeys.accountIdentifier).is(eula.getAccountIdentifier());
    Query query = new Query(criteria);
    FindAndReplaceOptions findAndReplaceOptions = FindAndReplaceOptions.options().upsert().returnNew();
    return mongoTemplate.findAndReplace(query, eula, findAndReplaceOptions);
  }
}