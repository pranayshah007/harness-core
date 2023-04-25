/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.services.impl;

import io.harness.ng.core.service.entity.ServiceSequence;
import io.harness.ng.core.service.entity.ServiceSequence.ServiceSequenceKeys;
import io.harness.ng.core.service.services.ServiceSequenceService;
import io.harness.repositories.service.spring.ServiceSequenceRepository;

import com.google.inject.Inject;
import java.util.Optional;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

public class ServiceSequenceServiceImpl implements ServiceSequenceService {
  @Inject ServiceSequenceRepository serviceSequenceRepository;
  @Override
  public Optional<ServiceSequence> get(
      String accountId, String orgIdentifier, String projectIdentifier, String serviceIdentifier) {
    return serviceSequenceRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndServiceIdentifier(
        accountId, orgIdentifier, projectIdentifier, serviceIdentifier);
  }

  @Override
  public ServiceSequence upsertDefaultSequence(ServiceSequence requestServiceSequence) {
    Criteria criteria = getCriteriaForSequenceUpdate(requestServiceSequence);
    Update update = new Update();
    update.set(ServiceSequenceKeys.defaultSequence, requestServiceSequence.getDefaultSequence());
    return serviceSequenceRepository.upsert(criteria, update, requestServiceSequence);
  }

  private Criteria getCriteriaForSequenceUpdate(ServiceSequence requestServiceSequence) {
    return Criteria.where(ServiceSequenceKeys.accountId)
        .is(requestServiceSequence.getAccountId())
        .and(ServiceSequenceKeys.orgIdentifier)
        .is(requestServiceSequence.getOrgIdentifier())
        .and(ServiceSequenceKeys.projectIdentifier)
        .is(requestServiceSequence.getProjectIdentifier())
        .and(ServiceSequenceKeys.serviceIdentifier)
        .is(requestServiceSequence.getServiceIdentifier());
  }

  @Override
  public ServiceSequence upsertCustomSequence(ServiceSequence requestServiceSequence) {
    Criteria criteria = getCriteriaForSequenceUpdate(requestServiceSequence);
    Update update = new Update();
    update.set(ServiceSequenceKeys.customSequence, requestServiceSequence.getCustomSequence());
    return serviceSequenceRepository.upsert(criteria, update, requestServiceSequence);
  }

  @Override
  public boolean delete(String accountId, String orgIdentifier, String projectIdentifier, String serviceIdentifier) {
    Criteria criteria = Criteria.where(ServiceSequenceKeys.accountId)
                            .is(accountId)
                            .and(ServiceSequenceKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(ServiceSequenceKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(ServiceSequenceKeys.serviceIdentifier)
                            .is(serviceIdentifier);

    return serviceSequenceRepository.delete(criteria);
  }
}
