package io.harness.ccm.views.dao;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.harness.ccm.views.entities.Policy;
import io.harness.ccm.views.entities.PolicyEnforcement;
import io.harness.ccm.views.entities.PolicyEnforcement.PolicyEnforcementId;
import io.harness.ccm.views.entities.PolicyPack;
import io.harness.exception.InvalidRequestException;
import io.harness.persistence.HPersistence;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

import java.util.List;

@Slf4j
@Singleton

public class PolicyEnforcementDAO {
    @Inject
    private HPersistence hPersistence;
    private PolicyPackDAO policyPackDAO;
    private PolicyDAO policyDAO;

    public boolean save(PolicyEnforcement PolicyEnforcement) {
        log.info("created: {}", hPersistence.save(PolicyEnforcement));
        return hPersistence.save(PolicyEnforcement) != null;
    }

    public boolean delete(String accountId, String uuid) {
        Query<PolicyPack> query = hPersistence.createQuery(PolicyPack.class)
                .field(PolicyEnforcementId.accountId)
                .equal(accountId)
                .field(PolicyEnforcementId.uuid)
                .equal(uuid);
        log.info("deleted policy: {}", uuid);
        return hPersistence.delete(query);
    }

    public PolicyEnforcement update(PolicyEnforcement policy) {
        Query query = hPersistence.createQuery(Policy.class)
                .field(PolicyEnforcementId.accountId)
                .equal(policy.getAccountId())
                .field(PolicyEnforcementId.uuid)
                .equal(policy.getUuid());
        UpdateOperations<PolicyEnforcement> updateOperations =
                hPersistence.createUpdateOperations(PolicyEnforcement.class)
                        .set(PolicyEnforcementId.name, policy.getName())
                        .set(PolicyEnforcementId.policyIds, policy.getPolicyIds())
                        .set(PolicyEnforcementId.policyPackIDs, policy.getPolicyPackIDs())
                        .set(PolicyEnforcementId.executionSchedule, policy.getExecutionSchedule())
                        .set(PolicyEnforcementId.executionTimezone, policy.getExecutionTimezone())
                        .set(PolicyEnforcementId.targetAccounts, policy.getTargetAccounts())
                        .set(PolicyEnforcementId.targetRegions, policy.getTargetRegions())
                        .set(PolicyEnforcementId.executionTimezone, policy.getIsDryRun())
                        .set(PolicyEnforcementId.deleted, policy.getDeleted())
                        .set(PolicyEnforcementId.isEnabled, policy.getIsEnabled())
                    .set(PolicyEnforcementId.lastUpdatedAt, policy.getLastUpdatedAt());

        hPersistence.update(query, updateOperations);
        log.info("Updated policy: {}", policy.getUuid());
        return policy;
    }

    public PolicyEnforcement listid(String accountId, String uuid){
        try {
            return hPersistence.createQuery(PolicyEnforcement.class)
                    .field(PolicyEnforcementId.accountId)
                    .equal(accountId)
                    .field(PolicyEnforcementId.uuid)
                    .equal(uuid)
                    .asList()
                    .get(0);
        } catch (IndexOutOfBoundsException e) {
            log.error("No such policy pack exists,{} accountId{} uuid{}", e,accountId,uuid);
            throw new InvalidRequestException("No such policy pack exists");
        }
    }

    public void check(String accountId, List<String> policyIds , List<String> policyPackIDs)
    {
        for(String identifiers: policyIds )
        {
            policyDAO.listid(accountId,identifiers);
        }
        for(String identifiers: policyPackIDs )
        {
            policyPackDAO.listid(accountId,identifiers);
        }
    }

    public List<PolicyEnforcement> list(String accountId) {
        return hPersistence.createQuery(PolicyEnforcement.class).field(PolicyEnforcementId.accountId).equal(accountId).asList();
    }

}