/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.acl.worker;

import static io.harness.accesscontrol.acl.worker.RemoveRedundantACLJob.REFERENCE_TIMESTAMP;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;

import static java.util.Map.of;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.accesscontrol.AccessControlTestBase;
import io.harness.accesscontrol.acl.persistence.ACL;
import io.harness.accesscontrol.acl.persistence.ACL.ACLKeys;
import io.harness.accesscontrol.acl.persistence.ACLOptimizationMigrationOffset;
import io.harness.accesscontrol.acl.persistence.RemoveRedundantACLJobState;
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
public class RemoveRedundantACLJobTest extends AccessControlTestBase {
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
  private RemoveRedundantACLJob removeRedundantACLJob;

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
    removeRedundantACLJob = new RemoveRedundantACLJob(mongoTemplate, persistentLocker, inMemoryPermissionRepository);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testRemoveRedundantACLs() {
    createEntities();

    removeRedundantACLJob.execute();

    List<ACL> currentACLs = getCurrentACLs();
    List<ACL> disabledACLs = getDisabledACLs();

    assertThat(currentACLs.size()).isEqualTo(4);
    assertThat(disabledACLs.size()).isEqualTo(0);

    RemoveRedundantACLJobState state = mongoTemplate.findOne(new Query(), RemoveRedundantACLJobState.class);
    assertThat(state.isJobCompleted()).isTrue();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testRemovedRedundantACLsIfOffsetIsReferenceTimestamp() {
    mongoTemplate.save(RemoveRedundantACLJobState.builder().offset(REFERENCE_TIMESTAMP).build());
    createEntities();

    removeRedundantACLJob.execute();

    List<ACL> currentACLs = getCurrentACLs();
    List<ACL> disabledACLs = getDisabledACLs();

    assertThat(currentACLs.size()).isEqualTo(4);
    assertThat(disabledACLs.size()).isEqualTo(0);

    RemoveRedundantACLJobState state = mongoTemplate.findOne(new Query(), RemoveRedundantACLJobState.class);
    assertThat(state.isJobCompleted()).isTrue();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testRemoveRedundantACLsIfOffsetIsSetToSomeACLId() {
    mongoTemplate.save(RemoveRedundantACLJobState.builder().offset("000000060000000000000000").build());
    createEntities();

    removeRedundantACLJob.execute();

    List<ACL> currentACLs = getCurrentACLs();
    List<ACL> disabledACLs = getDisabledACLs();

    assertThat(currentACLs.size()).isEqualTo(4);
    assertThat(disabledACLs.size()).isEqualTo(0);

    RemoveRedundantACLJobState state = mongoTemplate.findOne(new Query(), RemoveRedundantACLJobState.class);
    assertThat(state.isJobCompleted()).isTrue();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testRemoveRedundantACLsAndOffsetShouldSetToLastACLRemoved() {
    mongoTemplate.save(ACLOptimizationMigrationOffset.builder().offset("000000050000000000000000").build());
    mongoTemplate.save(RemoveRedundantACLJobState.builder().offset("000000050000000000000000").build());
    createEntities();

    removeRedundantACLJob.execute();

    List<ACL> currentACLs = getCurrentACLs();
    List<ACL> disabledACLs = getDisabledACLs();

    assertThat(currentACLs.size()).isEqualTo(4);
    assertThat(disabledACLs.size()).isEqualTo(0);

    RemoveRedundantACLJobState state = mongoTemplate.findOne(new Query(), RemoveRedundantACLJobState.class);
    assertThat(state.getOffset()).isEqualTo("000000070000000000000000");
    assertThat(state.isJobCompleted()).isTrue();
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
    acls.add(ACL.builder()
                 .id("000000090000000000000000")
                 .permissionIdentifier(CORE_USERGROUP_MANAGE_PERMISSION)
                 .resourceSelector("/*/*")
                 .enabled(true)
                 .build());
    aclRepository.insertAllIgnoringDuplicates(acls);

    removeRedundantACLJob.execute();

    List<ACL> currentACLs = getCurrentACLs();
    List<ACL> disabledACLs = getDisabledACLs();

    assertThat(currentACLs.size()).isEqualTo(2);
    assertThat(disabledACLs.size()).isEqualTo(0);

    RemoveRedundantACLJobState state = mongoTemplate.findOne(new Query(), RemoveRedundantACLJobState.class);
    assertThat(state.getOffset()).isEqualTo("000000090000000000000000");
    assertThat(state.isJobCompleted()).isTrue();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testUpdateInOffsetEvenIfNoRedundantACLToRemove() {
    removeRedundantACLJob.execute();

    List<ACL> currentACLs = getCurrentACLs();
    List<ACL> disabledACLs = getDisabledACLs();

    assertThat(currentACLs.size()).isEqualTo(0);
    assertThat(disabledACLs.size()).isEqualTo(0);

    RemoveRedundantACLJobState state = mongoTemplate.findOne(new Query(), RemoveRedundantACLJobState.class);
    assertThat(state.getOffset()).isEqualTo(REFERENCE_TIMESTAMP);
    assertThat(state.isJobCompleted()).isTrue();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void doNotRunJobIfItIsAlreadySuccessful() {
    mongoTemplate.save(RemoveRedundantACLJobState.builder().offset("some-offset").jobCompleted(true).build());
    List<ACL> acls = new ArrayList<>();
    acls.add(ACL.builder()
                 .id("000000110000000000000000")
                 .permissionIdentifier(CORE_USER_VIEW_PERMISSION)
                 .resourceSelector("/ACCOUNT/account-id$/SERVICE/*")
                 .enabled(false)
                 .build());
    aclRepository.insertAllIgnoringDuplicates(acls);

    removeRedundantACLJob.execute();

    List<ACL> currentACLs = getCurrentACLs();
    assertThat(currentACLs.size()).isEqualTo(1);

    RemoveRedundantACLJobState state = mongoTemplate.findOne(new Query(), RemoveRedundantACLJobState.class);
    assertThat(state.getOffset()).isEqualTo("some-offset");
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
                 .enabled(false)
                 .build());
    acls.add(ACL.builder()
                 .id("000000060000000000000000")
                 .permissionIdentifier(CORE_RESOURCEGROUP_VIEW_PERMISSION)
                 .resourceSelector("/ACCOUNT/account-id$/SERVICE/*")
                 .enabled(false)
                 .build());
    acls.add(ACL.builder()
                 .id("000000070000000000000000")
                 .permissionIdentifier(CORE_SERVICE_VIEW_PERMISSION)
                 .resourceSelector("/ACCOUNT/account-id$/RESOURCEGROUP/*")
                 .enabled(false)
                 .build());

    aclRepository.insertAllIgnoringDuplicates(acls);
  }

  private List<ACL> getCurrentACLs() {
    return mongoTemplate.find(new Query(), ACL.class);
  }

  private List<ACL> getDisabledACLs() {
    return mongoTemplate.find(new Query().addCriteria(Criteria.where(ACLKeys.enabled).is(false)), ACL.class);
  }
}
