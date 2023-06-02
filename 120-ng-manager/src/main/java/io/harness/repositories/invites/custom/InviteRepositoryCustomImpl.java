/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.invites.custom;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.invites.entities.Invite;
import io.harness.ng.core.invites.entities.Invite.InviteKeys;
import io.harness.springdata.PersistenceUtils;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.repository.support.PageableExecutionUtils;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PL)
public class InviteRepositoryCustomImpl implements InviteRepositoryCustom {
  private final MongoTemplate mongoTemplate;
  private final RetryPolicy<Object> updateRetryPolicy = PersistenceUtils.getRetryPolicy(
      "[Retrying]: Failed updating Invite; attempt: {}", "[Failed]: Failed updating Invite; attempt: {}");

  @Override
  public Page<Invite> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    List<Invite> invites = mongoTemplate.find(query, Invite.class);
    return PageableExecutionUtils.getPage(
        invites, pageable, () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), Invite.class));
  }

  @Override
  public Invite updateInvite(String inviteId, Update update) {
    Criteria criteria = Criteria.where(InviteKeys.id).is(inviteId);
    Query query = new Query(criteria);
    return Failsafe.with(updateRetryPolicy)
        .get(
            () -> mongoTemplate.findAndModify(query, update, new FindAndModifyOptions().returnNew(true), Invite.class));
  }

  @Override
  public DeleteResult deleteAll(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.remove(query, Invite.class);
  }
}
