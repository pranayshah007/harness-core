package io.harness.ccm.views.service;

import io.harness.ccm.views.entities.PolicyEnforcement;
import java.util.List;

public interface PolicyEnforcementService {

    boolean save(PolicyEnforcement policyEnforcement);
    boolean delete(String accountId, String uuid);
    PolicyEnforcement update(PolicyEnforcement policyEnforcement);
    PolicyEnforcement listid(String accountId, String uuid);
    void check(String accountId, List<String> policyIds , List<String> policyPackIDs);
    List<PolicyEnforcement> list(String accountId);
}
