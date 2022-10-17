package io.harness.ccm.remote.resources.policies;


import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.harness.annotations.dev.OwnedBy;
import lombok.extern.slf4j.Slf4j;

import static io.harness.annotations.dev.HarnessTeam.CE;

@Slf4j
@Singleton
@OwnedBy(CE)
public class PolicySetServiceImpl implements PolicySetService {

    @Inject
    private PolicySetDAO policySetDAO;

    @Override

    public boolean save(PolicySet policySet) {
        return policySetDAO.save(policySet);
    }

    @Override
    public boolean delete(String accountId, String uuid) {
        return policySetDAO.delete(accountId, uuid);
    }

    @Override
    public PolicySet update(PolicySet policySet) {
        {
            return policySetDAO.update(policySet);
        }

    }
}
