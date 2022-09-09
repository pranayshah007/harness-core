/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateConnectionDetails;
import io.harness.persistence.HPersistence;
import lombok.extern.slf4j.Slf4j;
import org.jooq.tools.StringUtils;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;
import software.wings.beans.DelegateConnection;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.persistence.HPersistence.upToOne;
import static io.harness.persistence.HQuery.excludeAuthority;
import static java.lang.System.currentTimeMillis;
import static java.util.stream.Collectors.toList;
import static software.wings.beans.DelegateConnection.EXPIRY_TIME;

@Slf4j
@Singleton
@OwnedBy(DEL)
public class DelegateConnectionDetailsHelper {
    @Inject
    private HPersistence persistence;

    public void delegateDisconnected(final String delegateId) {
        log.info("Mark as disconnected for delegate with id: {}", delegateId);
        Query<Delegate> query = persistence.createQuery(Delegate.class)
            .filter(Delegate.DelegateKeys.uuid, delegateId);
        UpdateOperations<Delegate> updateOperations = persistence.createUpdateOperations(Delegate.class)
            .set(Delegate.DelegateKeys.disconnected, Boolean.TRUE);
        persistence.update(query, updateOperations);
    }

    public long numberOfActiveDelegateConnectionsPerVersion(String version, String accountId) {
        if (StringUtils.isEmpty(accountId)) {
            return createQueryForAllActiveDelegateConnections(version).count();
        }
        return createQueryForAllActiveDelegateConnections(version)
            .filter(Delegate.DelegateKeys.accountId, accountId)
            .count();
    }

    private Query<Delegate> createQueryForAllActiveDelegateConnections(String version) {
        return persistence.createQuery(Delegate.class, excludeAuthority)
            .field(Delegate.DelegateKeys.disconnected)
            .notEqual(Boolean.TRUE)
            .field(Delegate.DelegateKeys.lastHeartBeat)
            .greaterThan(currentTimeMillis() - EXPIRY_TIME.toMillis())
            .filter(Delegate.DelegateKeys.version, version);
    }

    // Slow of performance
    public long numberOfDelegateConnectionsPerVersion(String version, String accountId) {
        Query<Delegate> query = persistence.createQuery(Delegate.class, excludeAuthority)
            .filter(Delegate.DelegateKeys.version, version);
        if (StringUtils.isEmpty(accountId)) {
            return query.count();
        }
        return query.filter(DelegateConnection.DelegateConnectionKeys.accountId, accountId).count();
    }

    public Map<String, List<String>> obtainActiveDelegatesPerAccount(String version) {
        List<Delegate> connectedDelegates = persistence.createQuery(Delegate.class)
            .field(Delegate.DelegateKeys.disconnected)
            .notEqual(Boolean.TRUE)
            .field(Delegate.DelegateKeys.lastHeartBeat)
            .greaterThan(currentTimeMillis() - EXPIRY_TIME.toMillis())
            .filter(Delegate.DelegateKeys.version, version)
            .asList();

        return connectedDelegates.stream().collect(Collectors.groupingBy(delegate
                -> delegate.getAccountId(),
            Collectors.mapping(delegate -> delegate.getUuid(), toList())));
    }

    public Map<String, List<DelegateConnectionDetails>> obtainActiveDelegateConnections(String accountId) {
        List<Delegate> connectedDelegates = persistence.createQuery(Delegate.class)
            .filter(Delegate.DelegateKeys.accountId, accountId)
            .field(Delegate.DelegateKeys.disconnected)
            .notEqual(Boolean.TRUE)
            .field(Delegate.DelegateKeys.lastHeartBeat)
            .greaterThan(currentTimeMillis() - EXPIRY_TIME.toMillis())
            .project(Delegate.DelegateKeys.uuid, true)
            .project(Delegate.DelegateKeys.version, true)
            .project(Delegate.DelegateKeys.lastHeartBeat, true)
            .asList();

        return connectedDelegates.stream().collect(Collectors.groupingBy(delegate -> delegate.getUuid(),
            Collectors.mapping(delegate
                    -> DelegateConnectionDetails.builder()
                    .uuid(delegate.getUuid())
                    .lastHeartbeat(delegate.getLastHeartBeat())
                    .version(delegate.getVersion())
                    .build(), toList()
                )));
    }

    public List<DelegateConnectionDetails> list(final String delegateId) {
        return persistence.createQuery(Delegate.class)
            .filter(Delegate.DelegateKeys.uuid, delegateId)
            .filter(Delegate.DelegateKeys.disconnected, Boolean.FALSE)
            .field(Delegate.DelegateKeys.lastHeartBeat)
            .greaterThan(currentTimeMillis() - EXPIRY_TIME.toMillis())
            .project(Delegate.DelegateKeys.uuid, true)
            .project(Delegate.DelegateKeys.version, true)
            .project(Delegate.DelegateKeys.lastHeartBeat, true)
            .asList().stream().map(delegate -> DelegateConnectionDetails.builder()
                .uuid(delegate.getUuid())
                .lastHeartbeat(delegate.getLastHeartBeat())
                .version(delegate.getVersion())
                .build()).collect(toList());
    }



    public boolean checkDelegateConnected(String delegateId, String version) {
        return persistence.createQuery(Delegate.class)
            .filter(Delegate.DelegateKeys.uuid, delegateId)
            .filter(Delegate.DelegateKeys.version, version)
            .filter(Delegate.DelegateKeys.disconnected, Boolean.FALSE)
            .field(Delegate.DelegateKeys.lastHeartBeat)
            .greaterThan(currentTimeMillis() - EXPIRY_TIME.toMillis())
            .count(upToOne)
            > 0;
    }

    public boolean checkAnyDelegateIsConnected(String accountId, List<String> delegateIdList) {
        return persistence.createQuery(DelegateConnection.class)
            .filter(Delegate.DelegateKeys.accountId, accountId)
            .field(Delegate.DelegateKeys.uuid)
            .in(delegateIdList)
            .filter(Delegate.DelegateKeys.disconnected, Boolean.FALSE)
            .field(Delegate.DelegateKeys.lastHeartBeat)
            .greaterThan(currentTimeMillis() - EXPIRY_TIME.toMillis())
            .count(upToOne)
            > 0;
    }
/*
    public DelegateConnection upsertCurrentConnection(
        String accountId, String delegateId, String delegateConnectionId, String version, String location) {
        Query<DelegateConnection> query = persistence.createQuery(DelegateConnection.class)
            .filter(DelegateConnection.DelegateConnectionKeys.accountId, accountId)
            .filter(DelegateConnection.DelegateConnectionKeys.uuid, delegateConnectionId);

        UpdateOperations<DelegateConnection> updateOperations =
            persistence.createUpdateOperations(DelegateConnection.class)
                .set(DelegateConnection.DelegateConnectionKeys.accountId, accountId)
                .set(DelegateConnection.DelegateConnectionKeys.uuid, delegateConnectionId)
                .set(DelegateConnection.DelegateConnectionKeys.delegateId, delegateId)
                .set(DelegateConnection.DelegateConnectionKeys.version, version)
                .set(DelegateConnection.DelegateConnectionKeys.lastHeartbeat, currentTimeMillis())
                .set(DelegateConnection.DelegateConnectionKeys.disconnected, Boolean.FALSE)
                .set(DelegateConnection.DelegateConnectionKeys.validUntil,
                    Date.from(OffsetDateTime.now().plusMinutes(TTL.toMinutes()).toInstant()));
        if (location != null) {
            updateOperations.set(DelegateConnection.DelegateConnectionKeys.location, location);
        }

        return persistence.upsert(query, updateOperations, HPersistence.upsertReturnOldOptions);
    }

    public DelegateConnection findAndDeletePreviousConnections(
        String accountId, String delegateId, String delegateConnectionId, String version) {
        return persistence.findAndDelete(persistence.createQuery(DelegateConnection.class)
                .filter(DelegateConnection.DelegateConnectionKeys.accountId, accountId)
                .filter(DelegateConnection.DelegateConnectionKeys.delegateId, delegateId)
                .filter(DelegateConnection.DelegateConnectionKeys.version, version)
                .field(DelegateConnection.DelegateConnectionKeys.uuid)
                .notEqual(delegateConnectionId),
            HPersistence.returnOldOptions);
    }

    public void replaceWithNewerConnection(String delegateConnectionId, DelegateConnection existingConnection) {
        persistence.delete(DelegateConnection.class, delegateConnectionId);
        persistence.save(existingConnection);
    }

    public void updateLastGrpcHeartbeat(String accountId, String delegateId, String version) {
        persistence.update(persistence.createQuery(DelegateConnection.class)
                .filter(DelegateConnection.DelegateConnectionKeys.accountId, accountId)
                .filter(DelegateConnection.DelegateConnectionKeys.delegateId, delegateId)
                .filter(DelegateConnection.DelegateConnectionKeys.version, version),
            persistence.createUpdateOperations(DelegateConnection.class)
                .set(DelegateConnection.DelegateConnectionKeys.lastGrpcHeartbeat, System.currentTimeMillis()));
    }

 */
}
