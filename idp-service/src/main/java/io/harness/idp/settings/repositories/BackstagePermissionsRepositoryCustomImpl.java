/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.settings.repositories;

import io.harness.idp.settings.beans.entity.BackstagePermissionsEntity;
import io.harness.idp.settings.beans.entity.BackstagePermissionsEntity.BackstagePermissionsEntityKeys;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class BackstagePermissionsRepositoryCustomImpl implements BackstagePermissionsRepositoryCustom {
  private MongoTemplate mongoTemplate;

  @Override
  public BackstagePermissionsEntity update(BackstagePermissionsEntity backstagePermissionsEntity) {
    Criteria criteria = Criteria.where(BackstagePermissionsEntityKeys.accountIdentifier)
                            .is(backstagePermissionsEntity.getAccountIdentifier());
    Query query = new Query(criteria);
    Update update = new Update();
    update.set(BackstagePermissionsEntityKeys.permissions, backstagePermissionsEntity.getPermissions());
    update.set(BackstagePermissionsEntityKeys.userGroup, backstagePermissionsEntity.getUserGroup());
    FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true);
    return mongoTemplate.findAndModify(query, update, options, BackstagePermissionsEntity.class);
  }
}
