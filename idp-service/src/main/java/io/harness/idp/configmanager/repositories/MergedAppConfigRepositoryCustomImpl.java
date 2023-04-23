/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.repositories;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.configmanager.beans.entity.MergedAppConfigEntity;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class MergedAppConfigRepositoryCustomImpl implements MergedAppConfigRepositoryCustom {
  private MongoTemplate mongoTemplate;

  @Override
  public MergedAppConfigEntity saveOrUpdate(MergedAppConfigEntity mergedAppConfigEntity) {
    Criteria criteria = Criteria.where(MergedAppConfigEntity.MergedAppConfigEntityKeys.accountIdentifier)
                            .is(mergedAppConfigEntity.getAccountIdentifier());
    MergedAppConfigEntity entityForFindByAccountId = findOneBasedOnCriteria(criteria);
    if (entityForFindByAccountId == null) {
      return mongoTemplate.save(mergedAppConfigEntity);
    }
    Query query = new Query(criteria);
    Update update = new Update();
    update.set(MergedAppConfigEntity.MergedAppConfigEntityKeys.config, mergedAppConfigEntity.getConfig());
    update.set(MergedAppConfigEntity.MergedAppConfigEntityKeys.lastModifiedAt, System.currentTimeMillis());
    FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true);
    return mongoTemplate.findAndModify(query, update, options, MergedAppConfigEntity.class);
  }

  private MergedAppConfigEntity findOneBasedOnCriteria(Criteria criteria) {
    return mongoTemplate.findOne(Query.query(criteria), MergedAppConfigEntity.class);
  }
}
