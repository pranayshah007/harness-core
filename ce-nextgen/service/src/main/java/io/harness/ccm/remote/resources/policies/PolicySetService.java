package io.harness.ccm.remote.resources.policies;

public interface PolicySetService {
    boolean save(PolicySet policySet);
    boolean delete(String accountId, String uuid);
    PolicySet update(PolicySet policySet);
}
