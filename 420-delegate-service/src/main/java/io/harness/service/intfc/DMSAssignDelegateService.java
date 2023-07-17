package io.harness.service.intfc;

import io.harness.beans.DelegateTask;

import java.util.List;

public interface DMSAssignDelegateService {
  boolean shouldValidate(DelegateTask task, String delegateId);

  List<String> retrieveActiveDelegates(String accountId, DelegateTask task);

  List<String> connectedWhitelistedDelegates(DelegateTask task);

  boolean isWhitelisted(DelegateTask task, String delegateId);
}
