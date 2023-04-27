/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator.repositories;

import io.harness.aggregator.models.BlockedAccount;
import io.harness.aggregator.models.BlockedAccount.BlockedACLEntityKeys;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Optional;
import javax.validation.executable.ValidateOnExecution;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(HarnessTeam.PL)
@Singleton
@ValidateOnExecution
public class BlockedEntityRepository {
  private final MongoTemplate mongoTemplate;

  @Inject
  public BlockedEntityRepository(@Named("mongoTemplate") MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  public BlockedAccount create(BlockedAccount blockedAccount) {
    return mongoTemplate.save(blockedAccount);
  }

  public Optional<BlockedAccount> find(String accountIdentifier) {
    Criteria criteria = Criteria.where(BlockedACLEntityKeys.accountIdentifier).is(accountIdentifier);
    return Optional.ofNullable(mongoTemplate.findOne(new Query(criteria), BlockedAccount.class));
  }

  public List<BlockedAccount> findAll() {
    Criteria criteria = Criteria.where(BlockedACLEntityKeys.accountIdentifier).exists(true);
    return mongoTemplate.find(new Query(criteria), BlockedAccount.class);
  }

  public void delete(String accountIdentifier) {
    Criteria criteria = Criteria.where(BlockedACLEntityKeys.accountIdentifier).is(accountIdentifier);
    mongoTemplate.remove(new Query(criteria), BlockedAccount.class);
  }
}
