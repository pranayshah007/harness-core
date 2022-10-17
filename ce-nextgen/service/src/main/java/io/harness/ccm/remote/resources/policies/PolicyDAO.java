package io.harness.ccm.remote.resources.policies;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.harness.persistence.HPersistence;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

import java.util.List;

@Slf4j
@Singleton
public class PolicyDAO {
    @Inject
    private HPersistence hPersistence;

    public boolean save(Policy policy) {
        log.info("created: {}", hPersistence.save(policy));
        return hPersistence.save(policy) != null;
    }

    public boolean delete(String accountId, String uuid) {
        Query<Policy> query = hPersistence.createQuery(Policy.class)
                .field(Policy.PolicyId.accountId)
                .equal(accountId)
                .field(Policy.PolicyId.uuid)
                .equal(uuid);
        log.info("deleted policy: {}", uuid);
        return hPersistence.delete(query);
    }

    public List<Policy> list(String accountId) {
        return hPersistence.createQuery(Policy.class).field(Policy.PolicyId.accountId).equal(accountId).asList();
    }

    public List<Policy> findByResource(String resource, String accountId) {
        Query<Policy> query = hPersistence.createQuery(Policy.class)
                .filter(Policy.PolicyId.resource, resource)
                .filter(Policy.PolicyId.accountId, accountId);
        return query.asList();
    }

    public List<Policy> findByTag(String tag, String accountId) {
        Query<Policy> query = hPersistence.createQuery(Policy.class)
                .filter(Policy.PolicyId.tags, tag)
                .filter(Policy.PolicyId.accountId, accountId);
        return query.asList();
    }

    public Policy listid(String accountId, String uuid) {
        return hPersistence.createQuery(Policy.class)
                .field(Policy.PolicyId.accountId)
                .equal(accountId)
                .field(Policy.PolicyId.uuid)
                .equal(uuid)
                .asList()
                .get(0);
    }

    public List<Policy> findByTagAndResource(String resource, String tag, String accountId) {
        Query<Policy> query = hPersistence.createQuery(Policy.class)
                .filter(Policy.PolicyId.resource, resource)
                .filter(Policy.PolicyId.tags, tag)
                .filter(Policy.PolicyId.accountId, accountId);
        log.info("Query: {}", query);
        return query.asList();
    }

    public Policy update(Policy policy) {
        Query query = hPersistence.createQuery(Policy.class)
                .field(Policy.PolicyId.accountId)
                .equal(policy.getAccountId())
                .field(Policy.PolicyId.uuid)
                .equal(policy.getUuid());
        UpdateOperations<Policy> updateOperations =
                hPersistence.createUpdateOperations(Policy.class)
                        .set(Policy.PolicyId.resource, policy.getResource())
                        .set(Policy.PolicyId.name, policy.getName())
                        .set(Policy.PolicyId.description, policy.getDescription())
                        .set(Policy.PolicyId.policyYaml, policy.getPolicyYaml())
                        .set(Policy.PolicyId.isStablePolicy, policy.getIsStablePolicy())
                        .set(Policy.PolicyId.isOOTBPolicy, policy.getIsOOTBPolicy())
                        .set(Policy.PolicyId.tags, policy.getTags())
                        .set(Policy.PolicyId.lastUpdatedAt, policy.getLastUpdatedAt());

        hPersistence.update(query, updateOperations);
        log.info("Updated policy: {}", policy.getUuid());
        return policy;
    }

    public List<Policy> findByStability(String isStablePolicy, String accountId) {
        Query<Policy> query = hPersistence.createQuery(Policy.class)
                .filter(Policy.PolicyId.isStablePolicy, isStablePolicy)
                .filter(Policy.PolicyId.accountId, accountId);
        return query.asList();
    }
}
