package io.harness.queueservice.impl;

import io.harness.queueservice.infc.DelegateResourceAvailabilityService;

import java.util.List;

public class DelegateResourceAvailabilityServiceImpl implements DelegateResourceAvailabilityService {

    @Override
    public String getMostAvailableDelegateInAccount(List<String> delegateId, String accountId) {
        return null;
    }

    @Override
    public String getMostAvailableDelegateInAccountFromLocalCache(List<String> eligibleDelegateId, String accountId) {
        return null;
    }

    @Override
    public boolean isDelegateInHealthyState(String delegateId, String accountId) {
        return true;
    }
}
