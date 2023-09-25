/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.githubrestraint.services;

import io.harness.distribution.constraint.Constraint;
import io.harness.gitopsprovider.entity.GithubRestraintInstance;

import java.util.List;

public interface GithubRestraintInstanceService {
  Constraint createAbstraction(String tokenRef);
  List<GithubRestraintInstance> getAllActiveAndBlockedByResourceUnit(String resourceUnit);

  int getMaxOrder(String resourceUnit);

  List<GithubRestraintInstance> findAllActiveAndBlockedByReleaseEntityId(String releaseEntityId);

  GithubRestraintInstance finishInstance(String uuid);

  void updateBlockedConstraints(String constraintUnit);

  GithubRestraintInstance save(GithubRestraintInstance resourceRestraintInstance);
  GithubRestraintInstance activateBlockedInstance(String uuid, String resourceUnit);

  void processRestraint(GithubRestraintInstance instance);

  boolean updateActiveConstraintsForInstance(GithubRestraintInstance instance);
}
