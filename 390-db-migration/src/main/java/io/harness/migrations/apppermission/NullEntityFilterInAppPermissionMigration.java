package io.harness.migrations.apppermission;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;
import lombok.extern.slf4j.Slf4j;
import software.wings.beans.security.AppPermission;
import software.wings.beans.security.UserGroup;
import software.wings.dl.WingsPersistence;
import software.wings.security.EnvFilter;
import software.wings.security.Filter;
import software.wings.security.GenericEntityFilter;
import software.wings.security.WorkflowFilter;
import software.wings.service.intfc.UserGroupService;
import software.wings.security.PermissionAttribute.PermissionType;

import java.util.Set;

import static software.wings.security.EnvFilter.FilterType.NON_PROD;
import static software.wings.security.EnvFilter.FilterType.PROD;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class NullEntityFilterInAppPermissionMigration implements Migration {
    private final String DEBUG_MESSAGE = "NullEntityFilterInAppPermissionMigration: ";
    @Inject
    private WingsPersistence wingsPersistence;
    @Inject
    private UserGroupService userGroupService;

    @Override
    public void migrate() {
        log.info(DEBUG_MESSAGE + "Starting migration");
        int userGroupsUpdatedCount = 0;
        try (HIterator<UserGroup> userGroupHIterator =
                     new HIterator<>(wingsPersistence.createQuery(UserGroup.class).fetch())) {
            while (userGroupHIterator.hasNext()) {
                UserGroup userGroup = userGroupHIterator.next();
                try {
                    Set<AppPermission> appPermissions = userGroup.getAppPermissions();
                    boolean shouldUpdate = false;
                    for (AppPermission appPermission : appPermissions) {
                        if (appPermission != null) {
                            PermissionType permissionType = appPermission.getPermissionType();
                            Filter entityFilter = appPermission.getEntityFilter();
                            if (entityFilter == null) {
                                switch (permissionType) {
                                    case SERVICE:
                                    case PROVISIONER:
                                    case APP_TEMPLATE:
                                        GenericEntityFilter genericEntityFilter = GenericEntityFilter.builder().filterType(GenericEntityFilter.FilterType.ALL).build();
                                        appPermission.setEntityFilter(genericEntityFilter);
                                        shouldUpdate = true;
                                        break;
                                    case ENV:
                                    case PIPELINE:
                                        EnvFilter envFilter = EnvFilter.builder().filterTypes(ImmutableSet.of(EnvFilter.FilterType.PROD, EnvFilter.FilterType.NON_PROD)).build();
                                        appPermission.setEntityFilter(envFilter);
                                        shouldUpdate = true;
                                        break;
                                    case WORKFLOW:
                                    case DEPLOYMENT:
                                        WorkflowFilter  workflowFilter = new WorkflowFilter();
                                        workflowFilter.setFilterTypes(Sets.newHashSet(PROD, NON_PROD, WorkflowFilter.FilterType.TEMPLATES));;
                                        appPermission.setEntityFilter(workflowFilter);
                                        shouldUpdate = true;
                                        break;
                                    default:
                                        break;
                                }
                            }
                        }
                    }
                    if (shouldUpdate) {
                        log.info("{} Migration: User group with id {} will be updated in account {}", DEBUG_MESSAGE,
                                userGroup.getUuid(), userGroup.getAccountId());
                        userGroupService.updatePermissions(userGroup);
                        userGroupsUpdatedCount++;
                    }
                } catch (Exception e) {
                    log.error("{} Migration failed for user group with id {} in account {}", DEBUG_MESSAGE, userGroup.getUuid(),
                            userGroup.getAccountId(), e);
                }
            }
        }
        catch (Exception e) {
            log.error(DEBUG_MESSAGE + "Error creating query", e);
        }
        log.info(DEBUG_MESSAGE + "Completed migration of {} User Groups", userGroupsUpdatedCount);
    }
}
