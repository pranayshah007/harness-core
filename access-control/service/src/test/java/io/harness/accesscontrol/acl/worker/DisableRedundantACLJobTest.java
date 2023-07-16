/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.acl.worker;

import static io.harness.accesscontrol.acl.worker.DisableRedundantACLJob.REFERENCE_TIMESTAMP;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;

import static java.util.Map.of;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.accesscontrol.AccessControlTestBase;
import io.harness.accesscontrol.acl.persistence.ACL;
import io.harness.accesscontrol.acl.persistence.ACL.ACLKeys;
import io.harness.accesscontrol.acl.persistence.ACLOptimizationMigrationOffset;
import io.harness.accesscontrol.acl.persistence.repositories.ACLRepository;
import io.harness.accesscontrol.permissions.persistence.PermissionDBO;
import io.harness.accesscontrol.permissions.persistence.repositories.InMemoryPermissionRepository;
import io.harness.accesscontrol.resources.resourcetypes.persistence.ResourceTypeDBO;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.lock.PersistentLocker;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(PL)
public class DisableRedundantACLJobTest extends AccessControlTestBase {
  public static final String CORE_USERGROUP_MANAGE_PERMISSION = "core_usergroup_manage";
  public static final String USERGROUP_RESOURCE_NAME = "usergroup";
  public static final String USERGROUP_RESOURCE_IDENTIFIER = "USERGROUP";

  public static final String CORE_RESOURCEGROUP_MANAGE_PERMISSION = "core_resourcegroup_manage";
  public static final String CORE_RESOURCEGROUP_VIEW_PERMISSION = "core_resourcegroup_view";
  public static final String RESOURCEGROUP_RESOURCE_NAME = "resourcegroup";
  public static final String RESOURCEGROUP_RESOURCE_IDENTIFIER = "RESOURCEGROUP";

  public static final String CORE_USER_VIEW_PERMISSION = "core_user_view";
  public static final String USER_RESOURCE_NAME = "user";
  public static final String USER_RESOURCE_IDENTIFIER = "USER";

  public static final String CORE_SERVICE_VIEW_PERMISSION = "core_service_view";
  public static final String SERVICE_RESOURCE_NAME = "service";
  public static final String SERVICE_RESOURCE_IDENTIFIER = "SERVICE";

  @Inject private MongoTemplate mongoTemplate;
  @Inject @Named(ACL.PRIMARY_COLLECTION) private ACLRepository aclRepository;
  @Mock private PersistentLocker persistentLocker;
  private InMemoryPermissionRepository inMemoryPermissionRepository;
  private DisableRedundantACLJob disableRedundantACLJob;

  @Before
  public void setup() {
    mongoTemplate.save(PermissionDBO.builder().identifier(CORE_USERGROUP_MANAGE_PERMISSION).build());
    mongoTemplate.save(PermissionDBO.builder().identifier(CORE_RESOURCEGROUP_MANAGE_PERMISSION).build());
    mongoTemplate.save(PermissionDBO.builder().identifier(CORE_USER_VIEW_PERMISSION).build());
    mongoTemplate.save(PermissionDBO.builder().identifier(CORE_RESOURCEGROUP_VIEW_PERMISSION).build());
    mongoTemplate.save(PermissionDBO.builder().identifier(CORE_SERVICE_VIEW_PERMISSION).build());

    mongoTemplate.save(ResourceTypeDBO.builder()
                           .identifier(USERGROUP_RESOURCE_IDENTIFIER)
                           .permissionKey(USERGROUP_RESOURCE_NAME)
                           .build());
    mongoTemplate.save(ResourceTypeDBO.builder()
                           .identifier(RESOURCEGROUP_RESOURCE_IDENTIFIER)
                           .permissionKey(RESOURCEGROUP_RESOURCE_NAME)
                           .build());
    mongoTemplate.save(
        ResourceTypeDBO.builder().identifier(USER_RESOURCE_IDENTIFIER).permissionKey(USER_RESOURCE_NAME).build());
    mongoTemplate.save(
        ResourceTypeDBO.builder().identifier(SERVICE_RESOURCE_IDENTIFIER).permissionKey(SERVICE_RESOURCE_NAME).build());

    inMemoryPermissionRepository =
        new InMemoryPermissionRepository(mongoTemplate, of("ccm_perspective_view", Set.of("CCM_FOLDER")));
    disableRedundantACLJob = new DisableRedundantACLJob(mongoTemplate, persistentLocker, inMemoryPermissionRepository);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testDisableRedundantACLs() {
    createEntities();

    disableRedundantACLJob.execute();

    List<ACL> disabledACLs = getDisabledACLs();

    assertThat(disabledACLs.size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testDisableRedundantACLsIfOffsetIsReferenceTimestamp() {
    mongoTemplate.save(ACLOptimizationMigrationOffset.builder().offset(REFERENCE_TIMESTAMP).build());
    createEntities();

    disableRedundantACLJob.execute();

    List<ACL> disabledACLs = getDisabledACLs();

    assertThat(disabledACLs.size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testDisableRedundantACLsIfOffsetIsSetToSomeACLId() {
    mongoTemplate.save(ACLOptimizationMigrationOffset.builder().offset("000000060000000000000000").build());
    createEntities();

    disableRedundantACLJob.execute();

    List<ACL> disabledACLs = getDisabledACLs();

    assertThat(disabledACLs.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testDisableRedundantACLsAndOffsetShouldSetToLastACLDisabled() {
    mongoTemplate.save(ACLOptimizationMigrationOffset.builder().offset("000000050000000000000000").build());
    createEntities();

    disableRedundantACLJob.execute();

    List<ACL> disabledACLs = getDisabledACLs();

    assertThat(disabledACLs.size()).isEqualTo(2);

    ACLOptimizationMigrationOffset newOffset = mongoTemplate.findOne(new Query(), ACLOptimizationMigrationOffset.class);
    assertThat(newOffset.getOffset()).isEqualTo("000000070000000000000000");
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testOffsetShouldSetToLastACLRead() {
    List<ACL> acls = new ArrayList<>();
    acls.add(ACL.builder()
                 .id("000000080000000000000000")
                 .permissionIdentifier(CORE_USER_VIEW_PERMISSION)
                 .resourceSelector("/*/*")
                 .enabled(true)
                 .build());
    aclRepository.insertAllIgnoringDuplicates(acls);

    disableRedundantACLJob.execute();

    List<ACL> disabledACLs = getDisabledACLs();
    assertThat(disabledACLs.size()).isEqualTo(0);

    ACLOptimizationMigrationOffset newOffset = mongoTemplate.findOne(new Query(), ACLOptimizationMigrationOffset.class);
    assertThat(newOffset.getOffset()).isEqualTo("000000080000000000000000");
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testUpdateInOffsetEvenIfNoRedundantACLToDisable() {
    disableRedundantACLJob.execute();

    List<ACL> disabledACLs = getDisabledACLs();
    assertThat(disabledACLs.size()).isEqualTo(0);

    ACLOptimizationMigrationOffset newOffset = mongoTemplate.findOne(new Query(), ACLOptimizationMigrationOffset.class);
    assertThat(newOffset.getOffset()).isEqualTo(REFERENCE_TIMESTAMP);
  }

  private void createEntities() {
    List<ACL> acls = new ArrayList<>();
    // These are valid ACLs
    acls.add(ACL.builder()
                 .id("000000010000000000000000")
                 .permissionIdentifier(CORE_USERGROUP_MANAGE_PERMISSION)
                 .resourceSelector("/*/*")
                 .enabled(true)
                 .build());
    acls.add(ACL.builder()
                 .id("000000020000000000000000")
                 .permissionIdentifier(CORE_RESOURCEGROUP_MANAGE_PERMISSION)
                 .resourceSelector("/*/*")
                 .enabled(true)
                 .build());
    acls.add(ACL.builder()
                 .id("000000030000000000000000")
                 .permissionIdentifier(CORE_USERGROUP_MANAGE_PERMISSION)
                 .resourceSelector("/ACCOUNT/account-id$/USERGROUP/*")
                 .enabled(true)
                 .build());
    acls.add(ACL.builder()
                 .id("000000040000000000000000")
                 .permissionIdentifier(CORE_USER_VIEW_PERMISSION)
                 .resourceSelector("/ACCOUNT/account-id$/USER/*")
                 .enabled(true)
                 .build());

    // These are redundant ACLs
    acls.add(ACL.builder()
                 .id("000000050000000000000000")
                 .permissionIdentifier(CORE_RESOURCEGROUP_MANAGE_PERMISSION)
                 .resourceSelector("/ACCOUNT/account-id$/USERGROUP/*")
                 .enabled(true)
                 .build());
    acls.add(ACL.builder()
                 .id("000000060000000000000000")
                 .permissionIdentifier(CORE_RESOURCEGROUP_VIEW_PERMISSION)
                 .resourceSelector("/ACCOUNT/account-id$/SERVICE/*")
                 .enabled(true)
                 .build());
    acls.add(ACL.builder()
                 .id("000000070000000000000000")
                 .permissionIdentifier(CORE_SERVICE_VIEW_PERMISSION)
                 .resourceSelector("/ACCOUNT/account-id$/RESOURCEGROUP/*")
                 .enabled(true)
                 .build());

    aclRepository.insertAllIgnoringDuplicates(acls);
  }

  private List<ACL> getDisabledACLs() {
    return mongoTemplate.find(new Query().addCriteria(Criteria.where(ACLKeys.enabled).is(false)), ACL.class);
  }
}
