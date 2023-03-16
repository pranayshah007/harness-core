/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HPersistence.upToOne;
import static io.harness.persistence.HQuery.excludeAuthority;

import static java.lang.System.currentTimeMillis;
import static java.time.Duration.ofMinutes;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jooq.tools.StringUtils;

@Slf4j
@Singleton
@OwnedBy(DEL)
public class DelegateDao {
  @Inject private HPersistence persistence;
  public static final Duration EXPIRY_TIME = ofMinutes(1);

  public void delegateDisconnected(String accountId, String delegateId) {
    log.info("Mark delegate as disconnected: {}", delegateId);
    Query<Delegate> query = persistence.createQuery(Delegate.class)
                                .filter(DelegateKeys.accountId, accountId)
                                .filter(DelegateKeys.uuid, delegateId);
    UpdateOperations<Delegate> updateOperations =
        persistence.createUpdateOperations(Delegate.class).set(DelegateKeys.disconnected, Boolean.TRUE);
    persistence.update(query, updateOperations);
  }

  public boolean checkDelegateConnected(String accountId, String delegateId, String version) {
    Query<Delegate> query = persistence.createQuery(Delegate.class)
                                .filter(DelegateKeys.accountId, accountId)
                                .filter(DelegateKeys.uuid, delegateId)
                                .filter(DelegateKeys.disconnected, Boolean.FALSE);
    if (isNotEmpty(version)) {
      query.filter(DelegateKeys.version, version);
    }
    return query.field(DelegateKeys.lastHeartBeat)
               .greaterThan(currentTimeMillis() - EXPIRY_TIME.toMillis())
               .count(upToOne)
        > 0;
  }

  public boolean checkAnyDelegateIsConnected(String accountId, List<String> delegateIdList) {
    return persistence.createQuery(Delegate.class)
               .filter(DelegateKeys.accountId, accountId)
               .field(DelegateKeys.uuid)
               .in(delegateIdList)
               .field(DelegateKeys.disconnected)
               .notEqual(Boolean.TRUE)
               .field(DelegateKeys.lastHeartBeat)
               .greaterThan(currentTimeMillis() - EXPIRY_TIME.toMillis())
               .count(upToOne)
        > 0;
  }

  public boolean checkDelegateLiveness(String accountId, String delegateId) {
    Query<Delegate> query = persistence.createQuery(Delegate.class)
                                .filter(DelegateKeys.accountId, accountId)
                                .filter(DelegateKeys.uuid, delegateId)
                                .field(DelegateKeys.lastHeartBeat)
                                .greaterThan(currentTimeMillis() - EXPIRY_TIME.toMillis());
    return query.count(upToOne) > 0;
  }

  public Map<String, List<String>> obtainActiveDelegatesGroupByAccount(String version) {
    List<Delegate> delegates = persistence.createQuery(Delegate.class)
                                   .field(DelegateKeys.disconnected)
                                   .notEqual(Boolean.TRUE)
                                   .field(DelegateKeys.lastHeartBeat)
                                   .greaterThan(currentTimeMillis() - EXPIRY_TIME.toMillis())
                                   .filter(DelegateKeys.version, version)
                                   .asList();

    return delegates.stream().collect(
        Collectors.groupingBy(Delegate::getAccountId, Collectors.mapping(Delegate::getUuid, toList())));
  }

  public Map<String, List<Delegate>> obtainActiveDelegates(String accountId) {
    List<Delegate> delegates = persistence.createQuery(Delegate.class)
                                   .filter(DelegateKeys.accountId, accountId)
                                   .field(DelegateKeys.disconnected)
                                   .notEqual(Boolean.TRUE)
                                   .field(DelegateKeys.lastHeartBeat)
                                   .greaterThan(currentTimeMillis() - EXPIRY_TIME.toMillis())
                                   .project(DelegateKeys.uuid, true)
                                   .project(DelegateKeys.version, true)
                                   .project(DelegateKeys.lastHeartBeat, true)
                                   .asList();

    return delegates.stream().collect(Collectors.groupingBy(Delegate::getUuid,
        Collectors.mapping(delegate
            -> Delegate.builder().lastHeartBeat(delegate.getLastHeartBeat()).version(delegate.getVersion()).build(),
            toList())));
  }

  public long numberOfActiveDelegatesPerVersion(String version, String accountId) {
    if (StringUtils.isEmpty(accountId)) {
      return createQueryForAllActiveDelegates(version).count();
    }
    return createQueryForAllActiveDelegates(version).filter(DelegateKeys.accountId, accountId).count();
  }

  public long numberOfDelegatesPerVersion(String version, String accountId) {
    Query<Delegate> query =
        persistence.createQuery(Delegate.class, excludeAuthority).filter(DelegateKeys.version, version);
    if (StringUtils.isEmpty(accountId)) {
      return query.count();
    }
    return query.filter(DelegateKeys.accountId, accountId).count();
  }

  private Query<Delegate> createQueryForAllActiveDelegates(String version) {
    return persistence.createQuery(Delegate.class, excludeAuthority)
        .field(DelegateKeys.disconnected)
        .notEqual(Boolean.TRUE)
        .field(DelegateKeys.lastHeartBeat)
        .greaterThan(currentTimeMillis() - EXPIRY_TIME.toMillis())
        .filter(DelegateKeys.version, version);
  }
}
