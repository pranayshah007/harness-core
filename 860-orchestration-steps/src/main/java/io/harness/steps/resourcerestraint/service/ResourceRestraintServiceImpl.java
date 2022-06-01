/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint.service;

import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.repositories.ResourceRestraintRepository;
import io.harness.steps.resourcerestraint.beans.ResourceRestraint;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;

@OwnedBy(HarnessTeam.PIPELINE)
public class ResourceRestraintServiceImpl implements ResourceRestraintService {
  @Inject private ResourceRestraintRepository resourceRestraintRepository;

  @Override
  public List<ResourceRestraint> getConstraintsIn(Set<String> constraintIds) {
    return resourceRestraintRepository.findByUuidIn(constraintIds);
  }

  @Override
  public ResourceRestraint get(String resourceConstraintId) {
    final Optional<ResourceRestraint> resourceConstraint = resourceRestraintRepository.findById(resourceConstraintId);
    return resourceConstraint.orElse(null);
  }

  @Override
  public ResourceRestraint getByNameAndAccountId(String name, String accountId) {
    return resourceRestraintRepository.findByNameAndAccountId(name, accountId).orElse(null);
  }

  @Override
  public ResourceRestraint save(ResourceRestraint resourceConstraint) {
    try {
      resourceRestraintRepository.save(resourceConstraint);
      return resourceConstraint;
    } catch (DuplicateKeyException exception) {
      throw new InvalidRequestException("The resource constraint name cannot be reused.", exception, USER);
    }
  }
}
