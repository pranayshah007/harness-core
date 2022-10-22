package io.harness.ccm.views.dao;

import io.harness.ccm.views.entities.PolicyExecution;
import io.harness.ccm.views.entities.PolicyExecution.PolicyExecutionId;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class PolicyExecutionDAO {
  @Inject private HPersistence hPersistence;

  public boolean save(PolicyExecution policyExecution) {
    log.info("created: {}", hPersistence.save(policyExecution));
    return hPersistence.save(policyExecution) != null;
  }

  public List<PolicyExecution> list(String accountId) {
    return hPersistence.createQuery(PolicyExecution.class).field(PolicyExecutionId.accountId).equal(accountId).asList();
  }
}
