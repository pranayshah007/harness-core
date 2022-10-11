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
public class PolicyStoreServiceImpl implements PolicyStoreService {
  @Inject private PolicyStoreDao policyStoreDao;

  @Override
  public boolean save(PolicyStore policyStore) {
    return policyStoreDao.save(policyStore);
  }

  @Override
  public boolean delete(String accountId, String uuid) {
    return policyStoreDao.delete(accountId, uuid);
  }

  @Override
  public PolicyStore update(PolicyStore policyStore) {
    return policyStoreDao.update(policyStore);
  }

  @Override
  public List<PolicyStore> list(String accountId) {
    return policyStoreDao.list(accountId);
  }

  @Override
  public List<PolicyStore> findByResource(String resource, String accountId) {
    return policyStoreDao.findByResource(resource, accountId);
  }
  @Override
  public List<PolicyStore> findByTag(String tag, String accountId) {
    return policyStoreDao.findByTag(tag, accountId);
  }
  @Override
  public PolicyStore listid(String accountId, String uuid) {
    return policyStoreDao.listid(accountId, uuid);
  }
  @Override
  public List<PolicyStore> findByTagAndResource(String resource, String tag, String accountId) {
    return policyStoreDao.findByTagAndResource(resource, tag, accountId);
  }
}
