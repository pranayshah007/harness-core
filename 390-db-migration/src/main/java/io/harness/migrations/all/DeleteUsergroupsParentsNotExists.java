/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.migrations.all;

import static io.harness.governance.pipeline.service.GovernanceStatusEvaluator.EntityType.WORKFLOW;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Pipeline;
import software.wings.beans.Pipeline.PipelineKeys;
import software.wings.beans.UserGroupEntityReference;
import software.wings.beans.Workflow;
import software.wings.beans.Workflow.WorkflowKeys;
import software.wings.beans.security.UserGroup;
import software.wings.beans.security.UserGroup.UserGroupKeys;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import dev.morphia.query.UpdateOperations;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.SPG)
public class DeleteUsergroupsParentsNotExists implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  private static final String DEBUG_MESSAGE = "DELETE_USERGROUPS_PARENTS_NOT_EXISTS_MIGRATION: ";
  private static final String ACCOUNT_ID = "2zPKqzWjT3SUyklYJvslKg";

  public void migrate() {
    log.info(DEBUG_MESSAGE + "Starting migration");
    runMigration();
    log.info(DEBUG_MESSAGE + "Completed migration");
  }

  private void runMigration() {
    try (HIterator<UserGroup> userGroupHIterator = new HIterator<>(wingsPersistence.createQuery(UserGroup.class)
                                                                       .filter(UserGroupKeys.accountId, ACCOUNT_ID)
                                                                       .project(UserGroupKeys.accountId, true)
                                                                       .project(UserGroupKeys.parents, true)
                                                                       .fetch())) {
      while (userGroupHIterator.hasNext()) {
        UserGroup userGroup = userGroupHIterator.next();
        try {
          Set<UserGroupEntityReference> parents = userGroup.getParents();
          Set<UserGroupEntityReference> existsParents = parents.stream()
                                                            .filter(parent -> {
                                                              long query = 0L;
                                                              if (WORKFLOW.toString().equals(parent.getEntityType())) {
                                                                query = wingsPersistence.createQuery(Workflow.class)
                                                                            .filter(WorkflowKeys.accountId, ACCOUNT_ID)
                                                                            .filter(WorkflowKeys.uuid, parent.getId())
                                                                            .count();
                                                              } else {
                                                                query = wingsPersistence.createQuery(Pipeline.class)
                                                                            .filter(PipelineKeys.accountId, ACCOUNT_ID)
                                                                            .filter(PipelineKeys.uuid, parent.getId())
                                                                            .count();
                                                              }
                                                              return query != 0;
                                                            })
                                                            .collect(Collectors.toSet());

          if (!existsParents.isEmpty()) {
            UpdateOperations<UserGroup> operations = wingsPersistence.createUpdateOperations(UserGroup.class);
            operations.set("parents", existsParents);
            wingsPersistence.update(userGroup, operations);
          }

        } catch (Exception e) {
          log.error(DEBUG_MESSAGE + "Migration failed for usergroup with id {} in accountId: 2zPKqzWjT3SUyklYJvslKg",
              userGroup.getUuid(), e);
        }
      }
    } catch (Exception e) {
      log.error(DEBUG_MESSAGE + "Error creating query", e);
    }
  }
}
