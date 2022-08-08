/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.steps.resourcerestraint.beans.ResourceRestraint;

import java.util.List;
import java.util.Set;
import javax.validation.Valid;

@OwnedBy(PIPELINE)
public interface ResourceRestraintService {
  ResourceRestraint get(String id);
  ResourceRestraint getByNameAndAccountId(String name, String accountId);
  ResourceRestraint save(@Valid ResourceRestraint resourceConstraint);
  List<ResourceRestraint> getConstraintsIn(Set<String> constraintIds);
}
