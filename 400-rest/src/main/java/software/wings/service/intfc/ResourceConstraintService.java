/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.ResourceConstraint;
import io.harness.distribution.constraint.Constraint;
import io.harness.distribution.constraint.ConstraintRegistry;
import io.harness.validation.Update;

import software.wings.beans.ResourceConstraintInstance;
import software.wings.beans.ResourceConstraintUsage;
import software.wings.service.intfc.ownership.OwnedByAccount;

import java.util.List;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import ru.vyarus.guice.validator.group.annotation.ValidationGroups;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public interface ResourceConstraintService extends OwnedByAccount {
  PageResponse<ResourceConstraint> list(PageRequest<ResourceConstraint> pageRequest);

  @ValidationGroups(Update.class) void update(@Valid ResourceConstraint resourceConstraint);

  ResourceConstraint getById(String id);

  ResourceConstraint getByName(@NotNull String accountId, @NotNull String resourceConstraintName);

  ResourceConstraint ensureResourceConstraintForConcurrency(String accountId, String name);

  void delete(String accountId, String resourceConstraintId);

  ConstraintRegistry getRegistry();

  int getMaxOrder(String resourceConstraintId);

  Constraint createAbstraction(ResourceConstraint resourceConstraint);

  Set<String> updateActiveConstraints(String appId, String workflowExecution);

  boolean updateActiveConstraintForInstance(ResourceConstraintInstance instance);

  Set<String> selectBlockedConstraints();

  void updateBlockedConstraints(Set<String> constraintIds);

  void updateBlockedConstraintsV2(Set<String> constraintIds, boolean hitSecondaryNode);

  List<ResourceConstraintUsage> usage(String accountId, List<String> resourceConstraintIds);

  List<ResourceConstraintInstance> fetchResourceConstraintInstancesForUnitAndEntityType(
      String appId, String resourceConstraintId, String unit, String entityType);

  ResourceConstraintInstance fetchResourceConstraintInstanceForUnitAndWFExecution(
      String appId, String resourceConstraintId, String unit, String releaseEntityId, String entityType);

  static String workflowExecutionIdFromReleaseEntityId(String releaseEntityId) {
    return releaseEntityId.split("[|]")[0];
  }

  static String phaseNameFromReleaseEntityId(String releaseEntityId) {
    return releaseEntityId.split("[|]")[1];
  }

  static String releaseEntityId(String workflowExecutionId) {
    return workflowExecutionId;
  }

  static String releaseEntityId(String workflowExecutionId, String phaseName) {
    return workflowExecutionId + "|" + phaseName;
  }

  int getAllCurrentlyAcquiredPermits(String holdingScope, String releaseEntityId, String appId);

  ResourceConstraint get(String ownerId, String id);

  ResourceConstraint save(@Valid ResourceConstraint resourceConstraint);
}
