package io.harness.ccm.remote.resources.policies;

import java.util.List;

public interface PolicyStoreService {
  boolean save(PolicyStore policyStore);
  boolean delete(String accountId, String uuid);
  PolicyStore update(PolicyStore policyStore);
  List<PolicyStore> list(String accountId);
  List<PolicyStore> findByResource(String resource, String accountId);
  List<PolicyStore> findByTag(String tag, String accountId);
  PolicyStore listid(String accountId, String uuid);
  List<PolicyStore> findByTagAndResource(String resource, String tag, String accountId);
}
