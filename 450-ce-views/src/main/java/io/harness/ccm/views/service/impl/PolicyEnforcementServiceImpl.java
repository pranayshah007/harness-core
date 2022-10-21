package io.harness.ccm.views.service.impl;

import io.harness.ccm.views.dao.PolicyEnforcementDAO;
import io.harness.ccm.views.entities.PolicyEnforcement;
import io.harness.ccm.views.entities.PolicyPack;
import io.harness.ccm.views.service.PolicyEnforcementService;

import com.google.inject.Inject;
import java.util.List;

public class PolicyEnforcementServiceImpl implements PolicyEnforcementService {
  @Inject private PolicyEnforcementDAO policyEnforcementDAO;

  @Override

  public boolean save(PolicyEnforcement policyEnforcement) {
    return policyEnforcementDAO.save(policyEnforcement);
  }

  @Override
  public boolean delete(String accountId, String uuid) {
    return policyEnforcementDAO.delete(accountId, uuid);
  }

  @Override
  public PolicyEnforcement update(PolicyEnforcement policyEnforcement) {
    { return policyEnforcementDAO.update(policyEnforcement); }
  }

  @Override
  public PolicyEnforcement listid(String accountId, String name, boolean create) {
    { return policyEnforcementDAO.listid(accountId, name, create); }
  }

  @Override
  public List<PolicyEnforcement> list(String accountId) {
    { return policyEnforcementDAO.list(accountId); }
  }
}