package io.harness.ccm.remote.resources.policies;

import java.util.List;

public interface PolicyService {
  boolean save(Policy policy);
  boolean delete(String accountId, String uuid);
  Policy update(Policy policy);
  List<Policy> list(String accountId);
  List<Policy> findByResource(String resource, String accountId);
  List<Policy> findByTag(String tag, String accountId);
  Policy listid(String accountId, String uuid);
  List<Policy> findByTagAndResource(String resource, String tag, String accountId);
  List<Policy> findByStability(String isStablePolicy, String accountId);
}
