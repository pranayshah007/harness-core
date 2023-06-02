/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator.repositories;

import io.harness.accesscontrol.aggregator.api.SecondarySyncStatus;
import io.harness.aggregator.models.AggregatorSecondarySyncState;
import io.harness.aggregator.models.AggregatorSecondarySyncState.AggregatorSecondarySyncStateKeys;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import javax.validation.executable.ValidateOnExecution;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(HarnessTeam.PL)
@Singleton
@ValidateOnExecution
public class AggregatorSecondarySyncStateRepository {
  private final MongoTemplate mongoTemplate;

  @Inject
  public AggregatorSecondarySyncStateRepository(@Named("mongoTemplate") MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  public AggregatorSecondarySyncState create(AggregatorSecondarySyncState aggregatorSecondarySyncState) {
    return mongoTemplate.save(aggregatorSecondarySyncState);
  }

  public Optional<AggregatorSecondarySyncState> findByIdentifier(@NotEmpty String identifier) {
    Criteria criteria = Criteria.where(AggregatorSecondarySyncStateKeys.identifier).is(identifier);
    return Optional.ofNullable(mongoTemplate.findOne(new Query(criteria), AggregatorSecondarySyncState.class));
  }

  public AggregatorSecondarySyncState updateStatus(
      @NotNull AggregatorSecondarySyncState obj, @NotNull SecondarySyncStatus status) {
    obj.setSecondarySyncStatus(status);
    return mongoTemplate.save(obj);
  }

  public void removeByIdentifier(@NotEmpty String identifier) {
    Criteria criteria = Criteria.where(AggregatorSecondarySyncStateKeys.identifier).is(identifier);
    mongoTemplate.remove(new Query(criteria), AggregatorSecondarySyncState.class);
  }
}
