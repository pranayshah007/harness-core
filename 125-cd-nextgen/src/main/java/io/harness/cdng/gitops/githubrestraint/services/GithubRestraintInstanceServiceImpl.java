/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.githubrestraint.services;

import static io.harness.distribution.constraint.Consumer.State.ACTIVE;
import static io.harness.distribution.constraint.Consumer.State.BLOCKED;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.distribution.constraint.Constraint;
import io.harness.distribution.constraint.ConstraintId;
import io.harness.distribution.constraint.Consumer;
import io.harness.gitopsprovider.entity.GithubRestraintInstance;
import io.harness.gitopsprovider.entity.GithubRestraintInstance.GithubRestraintInstanceKeys;
import io.harness.repositories.GithubRestraintInstanceRepository;

import com.google.inject.Inject;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public class GithubRestraintInstanceServiceImpl implements GithubRestraintInstanceService {
  @Inject private MongoTemplate mongoTemplate;
  @Inject private GithubRestraintInstanceRepository githubRestraintInstanceRepository;
  @Override
  public Constraint createAbstraction(String tokenRef) {
    return Constraint.builder()
        .spec(Constraint.Spec.builder().limits(1).strategy(Constraint.Strategy.FIFO).build())
        .id(new ConstraintId(tokenRef))
        .build();
  }

  @Override
  public List<GithubRestraintInstance> getAllByRestraintIdAndStates(
      String resourceRestraintId, List<Consumer.State> states) {
    Query query =
        query(new Criteria().andOperator(where(GithubRestraintInstanceKeys.resourceRestraintId).is(resourceRestraintId),
            where(GithubRestraintInstanceKeys.state).in(BLOCKED, ACTIVE)));

    return mongoTemplate.find(query, GithubRestraintInstance.class);
  }

  @Override
  public int getMaxOrder(String resourceRestraintId) {
    Optional<GithubRestraintInstance> instance =
        githubRestraintInstanceRepository.findFirstByResourceRestraintIdOrderByOrderDesc(resourceRestraintId);

    return instance.map(GithubRestraintInstance::getOrder).orElse(0);
  }

  @Override
  public List<GithubRestraintInstance> findAllActiveAndBlockedByReleaseEntityId(String releaseEntityId) {
    return githubRestraintInstanceRepository.findAllByReleaseEntityIdAndStateIn(
        releaseEntityId, EnumSet.of(ACTIVE, BLOCKED));
  }
}
