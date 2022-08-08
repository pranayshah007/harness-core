/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.distribution.constraint.Constraint;
import io.harness.distribution.constraint.Consumer;
import io.harness.steps.resourcerestraint.beans.HoldingScope;
import io.harness.steps.resourcerestraint.beans.ResourceRestraint;
import io.harness.steps.resourcerestraint.beans.ResourceRestraintInstance;

import java.util.List;
import java.util.Set;

@OwnedBy(PIPELINE)
public interface ResourceRestraintInstanceService {
  ResourceRestraintInstance save(ResourceRestraintInstance resourceRestraintInstance);

  ResourceRestraintInstance activateBlockedInstance(String uuid, String resourceUnit);

  ResourceRestraintInstance finishInstance(String uuid, String resourceUnit);

  boolean updateActiveConstraintsForInstance(ResourceRestraintInstance instance);

  void updateBlockedConstraints(Set<String> constraints);

  List<ResourceRestraintInstance> getAllByRestraintIdAndResourceUnitAndStates(
      String resourceRestraintId, String resourceUnit, List<Consumer.State> states);

  Constraint createAbstraction(ResourceRestraint resourceRestraint);

  int getMaxOrder(String resourceRestraintId);

  int getAllCurrentlyAcquiredPermits(HoldingScope scope, String releaseEntityId, String resourceUnit);

  List<ResourceRestraintInstance> findAllActiveAndBlockedByReleaseEntityId(String releaseEntityId);

  void processRestraint(ResourceRestraintInstance instance);
}
