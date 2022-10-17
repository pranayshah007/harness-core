package io.harness.ccm.remote.resources.policies;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.harness.persistence.HPersistence;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
@Singleton
public class PolicySetDAO {
    @Inject
    private HPersistence hPersistence;

    public boolean save(PolicySet policySet) {
        log.info("created: {}", hPersistence.save(policySet));
        return hPersistence.save(policySet) != null;
    }

    public boolean delete(String accountId, String uuid) {
        Query<PolicySet> query = hPersistence.createQuery(PolicySet.class)
                .field(PolicySet.PolicySetId.accountId)
                .equal(accountId)
                .field(PolicySet.PolicySetId.uuid)
                .equal(uuid);
        log.info("deleted policy: {}", uuid);
        return hPersistence.delete(query);
    }


    public PolicySet update(PolicySet policy) {
        Query query = hPersistence.createQuery(Policy.class)
                .field(PolicySet.PolicySetId.accountId)
                .equal(policy.getAccountId())
                .field(PolicySet.PolicySetId.uuid)
                .equal(policy.getUuid());
        UpdateOperations<PolicySet> updateOperations =
                hPersistence.createUpdateOperations(PolicySet.class)
                        .set(PolicySet.PolicySetId.name, policy.getName())
                        .set(PolicySet.PolicySetId.tags, policy.getTags())
                        .set(PolicySet.PolicySetId.policySetPolicies, policy.getPolicySetPolicies())
                        .set(PolicySet.PolicySetId.policySetExecutionCron, policy.getPolicySetExecutionCron())
                        .set(PolicySet.PolicySetId.policySetTargetAccounts, policy.getPolicySetTargetAccounts())
                        .set(PolicySet.PolicySetId.policySetTargetRegions, policy.getPolicySetTargetRegions())
                        .set(PolicySet.PolicySetId.isEnabled, policy.getIsEnabled())
                        .set(PolicySet.PolicySetId.lastUpdatedAt, policy.getLastUpdatedAt());

        hPersistence.update(query, updateOperations);
        log.info("Updated policy: {}", policy.getUuid());
        return policy;
    }
}
