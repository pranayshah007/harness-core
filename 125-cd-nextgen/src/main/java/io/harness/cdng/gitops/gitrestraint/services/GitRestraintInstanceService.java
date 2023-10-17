/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.gitrestraint.services;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.distribution.constraint.Constraint;
import io.harness.distribution.constraint.ConstraintRegistry;
import io.harness.gitopsprovider.entity.GitRestraintInstance;

import java.util.List;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITOPS})
public interface GitRestraintInstanceService extends ConstraintRegistry {
  Constraint createAbstraction(String tokenRef);
  List<GitRestraintInstance> getAllActiveAndBlockedByResourceUnit(String resourceUnit);

  int getMaxOrder(String resourceUnit);

  List<GitRestraintInstance> findAllActiveAndBlockedByReleaseEntityId(String releaseEntityId);

  GitRestraintInstance finishInstance(String uuid);

  void updateBlockedConstraints(String constraintUnit);

  GitRestraintInstance save(GitRestraintInstance resourceRestraintInstance);
  void activateBlockedInstance(String uuid, String resourceUnit);
}
