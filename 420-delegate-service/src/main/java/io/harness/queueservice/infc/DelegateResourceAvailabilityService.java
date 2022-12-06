package io.harness.queueservice.infc;

import com.google.inject.ImplementedBy;
import io.harness.queueservice.impl.DelegateResourceAvailabilityServiceImpl;

import java.util.List;

@ImplementedBy(DelegateResourceAvailabilityServiceImpl.class)
public interface DelegateResourceAvailabilityService {

    String getMostAvailableDelegateInAccount(List<String> delegateId, String accountId);

    String getMostAvailableDelegateInAccountFromLocalCache(List<String> eligibleDelegateId, String accountId);

   boolean isDelegateInHealthyState(String delegateId, String accountId);


}
