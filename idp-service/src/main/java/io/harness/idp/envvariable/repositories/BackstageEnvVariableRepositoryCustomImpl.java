/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.envvariable.repositories;

import io.harness.idp.envvariable.beans.entity.BackstageEnvConfigVariableEntity;
import io.harness.idp.envvariable.beans.entity.BackstageEnvConfigVariableEntity.BackstageEnvConfigVariableKeys;
import io.harness.idp.envvariable.beans.entity.BackstageEnvSecretVariableEntity;
import io.harness.idp.envvariable.beans.entity.BackstageEnvSecretVariableEntity.BackstageEnvSecretVariableKeys;
import io.harness.idp.envvariable.beans.entity.BackstageEnvVariableEntity;
import io.harness.idp.envvariable.beans.entity.BackstageEnvVariableEntity.BackstageEnvVariableKeys;
import io.harness.idp.envvariable.beans.entity.BackstageEnvVariableType;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class BackstageEnvVariableRepositoryCustomImpl implements BackstageEnvVariableRepositoryCustom {
  private MongoTemplate mongoTemplate;

  @Override
  public BackstageEnvVariableEntity update(BackstageEnvVariableEntity backstageEnvVariableEntity) {
    Criteria criteria = Criteria.where(BackstageEnvVariableKeys.accountIdentifier)
                            .is(backstageEnvVariableEntity.getAccountIdentifier())
                            .and(BackstageEnvVariableKeys.envName)
                            .is(backstageEnvVariableEntity.getEnvName());
    Query query = new Query(criteria);
    Update update = new Update();

    if (backstageEnvVariableEntity.getType() == BackstageEnvVariableType.CONFIG) {
      update.set(BackstageEnvConfigVariableKeys.value,
          ((BackstageEnvConfigVariableEntity) backstageEnvVariableEntity).getValue());
    } else {
      update.set(BackstageEnvSecretVariableKeys.harnessSecretIdentifier,
          ((BackstageEnvSecretVariableEntity) backstageEnvVariableEntity).getHarnessSecretIdentifier());
    }
    FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true);
    return mongoTemplate.findAndModify(query, update, options, BackstageEnvVariableEntity.class);
  }

  @Override
  public Optional<BackstageEnvVariableEntity> findByAccountIdentifierAndHarnessSecretIdentifier(
      String accountIdentifier, String harnessSecretIdentifier) {
    Criteria criteria = Criteria.where(BackstageEnvVariableKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(BackstageEnvSecretVariableKeys.harnessSecretIdentifier)
                            .is(harnessSecretIdentifier);
    Query query = new Query(criteria);
    return Optional.ofNullable(mongoTemplate.findOne(query, BackstageEnvVariableEntity.class));
  }

  @Override
  public List<BackstageEnvVariableEntity> findAllByAccountIdentifierAndMultipleEnvNames(
      String accountIdentifier, List<String> envNames) {
    Criteria criteria = Criteria.where(BackstageEnvVariableKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(BackstageEnvVariableKeys.envName)
                            .in(envNames);
    Query query = new Query(criteria);
    return mongoTemplate.find(query, BackstageEnvVariableEntity.class);
  }

  @Override
  public List<BackstageEnvVariableEntity> findIfEnvsExistByAccountIdentifier(
      String accountIdentifier, List<String> envNames) {
    Criteria criteria = Criteria.where(BackstageEnvVariableKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(BackstageEnvVariableKeys.envName)
                            .in(envNames);
    Query query = new Query(criteria);
    // TODO: Projection isn't working with inheritence in the entity. Have to debug further later.
    // query.fields().include(BackstageEnvVariableKeys.envName);
    // query.fields().exclude(BackstageEnvVariableKeys.id);
    return mongoTemplate.find(query, BackstageEnvVariableEntity.class);
  }

  @Override
  public void deleteAllByAccountIdentifierAndEnvNames(String accountIdentifier, List<String> envName) {
    Criteria criteria = Criteria.where(BackstageEnvVariableKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(BackstageEnvVariableKeys.envName)
                            .in(envName);
    Query query = new Query(criteria);
    mongoTemplate.findAllAndRemove(query, BackstageEnvVariableEntity.class);
  }
}
