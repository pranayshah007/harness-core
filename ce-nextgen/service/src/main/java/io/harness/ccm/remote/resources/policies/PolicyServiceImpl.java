package io.harness.ccm.remote.resources.policies;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(CE)
public class PolicyServiceImpl implements PolicyService {
  @Inject private PolicyDAO policyDao;

  @Override
  public boolean save(Policy policy) {
    return policyDao.save(policy);
  }

  @Override
  public boolean delete(String accountId, String uuid) {
    return policyDao.delete(accountId, uuid);
  }

  @Override
  public Policy update(Policy policy) {
    return policyDao.update(policy);
  }

  @Override
  public List<Policy> list(String accountId) {
    return policyDao.list(accountId);
  }

  @Override
  public List<Policy> findByResource(String resource, String accountId) {
    return policyDao.findByResource(resource, accountId);
  }
  @Override
  public List<Policy> findByTag(String tag, String accountId) {
    return policyDao.findByTag(tag, accountId);
  }
  @Override
  public Policy listid(String accountId, String uuid) {
    return policyDao.listid(accountId, uuid);
  }
  @Override
  public List<Policy> findByTagAndResource(String resource, String tag, String accountId) {
    return policyDao.findByTagAndResource(resource, tag, accountId);
  }
  @Override
  public List<Policy> findByStability(String isStablePolicy, String accountId) {
    return policyDao.findByStability(isStablePolicy, accountId);
  }
}
