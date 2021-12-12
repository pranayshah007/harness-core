package io.harness.migrations.apppermission;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.security.AppPermission;
import software.wings.beans.security.UserGroup;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.UserGroupService;

import com.google.inject.Inject;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class NullAppFilterPermissionMigration implements Migration {
  private final String DEBUG_MESSAGE = "NULL_APP_FILTER_PERMISSION_MIGRATION: ";
  @Inject private WingsPersistence wingsPersistence;
  @Inject private UserGroupService userGroupService;

  @Override
  public void migrate() {
    log.info(DEBUG_MESSAGE + "Starting migration");
    runMigration();
    log.info(DEBUG_MESSAGE + "Completed migration");
  }

  private void runMigration() {
    try (HIterator<UserGroup> userGroupHIterator =
             new HIterator<>(wingsPersistence.createQuery(UserGroup.class).fetch())) {
      while (userGroupHIterator.hasNext()) {
        UserGroup userGroup = userGroupHIterator.next();
        try {
          Set<AppPermission> appPermissions = userGroup.getAppPermissions();
          Set<AppPermission> updatedPermissions = new HashSet<>();
          boolean shouldUpdate = false;
          if (isNotEmpty(appPermissions)) {
            for (AppPermission appPermission : appPermissions) {
              if (appPermission != null) {
                if (appPermission.getAppFilter() == null) {
                  shouldUpdate = true;
                } else {
                  updatedPermissions.add(appPermission);
                }
              }
            }
          }
          if (shouldUpdate) {
            log.info("{} Migration: User group will be updated for id {} in account {}", DEBUG_MESSAGE,
                userGroup.getUuid(), userGroup.getAccountId());
            userGroup.setAppPermissions(updatedPermissions);
            userGroupService.updatePermissions(userGroup);
          }
        } catch (Exception e) {
          log.error("{} Migration failed for user group with id {} in account {}", DEBUG_MESSAGE, userGroup.getUuid(),
              userGroup.getAccountId(), e);
        }
      }
    } catch (Exception e) {
      log.error(DEBUG_MESSAGE + "Error creating query", e);
    }
  }
}
