/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.account;

import static io.harness.beans.FeatureName.SEARCH_REQUEST;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.ANKIT;
import static io.harness.rule.OwnerRule.MEHUL;
import static io.harness.rule.OwnerRule.UJJAWAL;
import static io.harness.rule.OwnerRule.VOJIN;

import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.JOB_NAME;

import static java.util.Collections.EMPTY_LIST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureFlag;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.limits.checker.rate.UsageBucket;
import io.harness.ng.core.NGAccountAccess;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.HPersistence;
import io.harness.persistence.PersistentEntity;
import io.harness.rule.Owner;
import io.harness.scheduler.PersistentScheduler;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.AccountStatus;
import software.wings.beans.AccountType;
import software.wings.beans.Application;
import software.wings.beans.DeletedEntity;
import software.wings.beans.LicenseInfo;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.User;
import software.wings.beans.entityinterface.ApplicationAccess;
import software.wings.licensing.LicenseService;
import software.wings.scheduler.events.segment.SegmentGroupEventJobContext;
import software.wings.service.intfc.UserService;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import dev.morphia.Morphia;
import dev.morphia.annotations.Entity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.quartz.JobKey;
import org.quartz.SchedulerException;

@OwnedBy(HarnessTeam.PL)
public class DeleteAccountHelperTest extends WingsBaseTest {
  @InjectMocks @Inject private DeleteAccountHelper deleteAccountHelper;
  @Mock private UserService userService;
  @Mock private PerpetualTaskService perpetualTaskService;
  @Mock @Named("BackgroundJobScheduler") private PersistentScheduler persistentScheduler;
  @Mock private LicenseService licenseService;
  @Inject private Morphia morphia;
  @Inject FeatureFlagService featureFlagService;
  @Inject private HPersistence persistence;

  private final String appId = UUID.randomUUID().toString();
  private static final String GROUP_NAME = "GROUP_NAME";
  private static final String PERPETUAL_TASK_UUID = UUID.randomUUID().toString();
  private static final String USAGE_BUCKET_KEY_1 = "ACCOUNT_ID:DEPLOY";
  private static final String USAGE_BUCKET_KEY_2 = "RANDOM:DEPLOY";
  private static final String USAGE_BUCKET_KEY_3 = "ACCOUNT_ID:RANDOM";

  private Application createApplication() {
    return Application.Builder.anApplication()
        .createdAt(1L)
        .appId(appId)
        .accountId(ACCOUNT_ID)
        .uuid(appId)
        .name("app_name")
        .build();
  }

  private LicenseInfo getLicenseInfo(String accountStatus) {
    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setAccountStatus(accountStatus);
    licenseInfo.setAccountType(AccountType.PAID);
    licenseInfo.setLicenseUnits(100);
    return licenseInfo;
  }

  private Account createAccount(String accountStatus) {
    return anAccount()
        .withCompanyName("CompanyName 1")
        .withAccountName("Account Name 1")
        .withLicenseInfo(getLicenseInfo(accountStatus))
        .withUuid(ACCOUNT_ID)
        .build();
  }

  private ServiceTemplate createServiceTemplate(Application application) {
    return ServiceTemplate.Builder.aServiceTemplate()
        .withName("service_template")
        .withAppId(application.getUuid())
        .build();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testDeleteExportableAccountData() {
    Account account = createAccount(AccountStatus.ACTIVE);
    User user = User.Builder.anUser()
                    .uuid("userId1")
                    .name("name1")
                    .email("user1@harness.io")
                    .accounts(Arrays.asList(account))
                    .build();
    Application application = createApplication();
    ServiceTemplate serviceTemplate = createServiceTemplate(application);
    persistence.save(account);
    persistence.save(application);
    persistence.save(serviceTemplate);
    when(userService.getUsersOfAccount(ACCOUNT_ID)).thenReturn(Arrays.asList(user));
    boolean deleted = deleteAccountHelper.deleteExportableAccountData(ACCOUNT_ID);
    assertThat(deleted).isTrue();
    assertThat(persistence.get(Account.class, account.getUuid())).isNull();
  }

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  public void testGetCollectionName() {
    assertThat(deleteAccountHelper.getCollectionName(Account.class)).isEqualTo("accounts");
    assertThat(deleteAccountHelper.getCollectionName(Application.class)).isEqualTo("applications");
  }

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  public void testDeleteEntityUsingAccountId() {
    List<String> collections = new ArrayList<>();
    morphia.getMapper().getMappedClasses().forEach(mc -> {
      Entity entityAnnotation = mc.getEntityAnnotation();
      if (PersistentEntity.class.isAssignableFrom(mc.getClazz()) && entityAnnotation != null) {
        if (!AccountAccess.class.isAssignableFrom(mc.getClazz())
            && !ApplicationAccess.class.isAssignableFrom(mc.getClazz())
            && !NGAccountAccess.class.isAssignableFrom(mc.getClazz())) {
          collections.add(entityAnnotation.value());
        }
      }
    });
    collections.sort(String::compareTo);
    System.out.println(String.join(System.lineSeparator(), collections));
    Application application = createApplication();
    persistence.save(application);
    assertThat(persistence.get(Application.class, appId)).isNotNull();
    assertThat(deleteAccountHelper.deleteEntityUsingAccountId(ACCOUNT_ID, Application.class)).isTrue();
    assertThat(persistence.get(Application.class, appId)).isNull();
  }

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  public void testDeleteEntityUsingAppId() {
    Account account = createAccount(AccountStatus.EXPIRED);
    Application application = createApplication();
    ServiceTemplate serviceTemplate = createServiceTemplate(application);
    persistence.save(account);
    persistence.save(application);
    persistence.save(serviceTemplate);
    assertThat(persistence.get(ServiceTemplate.class, serviceTemplate.getUuid())).isNotNull();
    assertThat(deleteAccountHelper.deleteEntityUsingAppId(ACCOUNT_ID, ServiceTemplate.class)).isTrue();
    assertThat(persistence.get(ServiceTemplate.class, serviceTemplate.getUuid())).isNull();
  }

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  public void testDeleteApplicationAccessEntities() {
    Account account = createAccount(AccountStatus.EXPIRED);
    Application application = createApplication();
    ServiceTemplate serviceTemplate = createServiceTemplate(application);
    persistence.save(account);
    persistence.save(application);
    persistence.save(serviceTemplate);
    assertThat(persistence.get(Account.class, ACCOUNT_ID)).isNotNull();
    assertThat(persistence.get(ServiceTemplate.class, serviceTemplate.getUuid())).isNotNull();
    assertThat(persistence.get(Application.class, application.getUuid())).isNotNull();
    assertThat(deleteAccountHelper.deleteApplicationAccessEntities(ACCOUNT_ID)).isEmpty();
    assertThat(persistence.get(ServiceTemplate.class, serviceTemplate.getUuid())).isNull();
    assertThat(persistence.get(Application.class, application.getUuid())).isNotNull();
    assertThat(persistence.get(Account.class, ACCOUNT_ID)).isNotNull();
  }

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  public void testDeleteAccountAccessEntities() {
    Account account = createAccount(AccountStatus.EXPIRED);
    Application application = createApplication();
    persistence.save(account);
    persistence.save(application);
    assertThat(persistence.get(Account.class, ACCOUNT_ID)).isNotNull();
    assertThat(persistence.get(Application.class, application.getUuid())).isNotNull();
    assertThat(deleteAccountHelper.deleteAccountAccessEntities(ACCOUNT_ID)).isEmpty();
    assertThat(persistence.get(Application.class, application.getUuid())).isNull();
    assertThat(persistence.get(Account.class, ACCOUNT_ID)).isNotNull();
  }

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  @Ignore("CI-6355: TI team to follow up")
  public void testDeleteOwnedByAccountEntities() {
    Account account = createAccount(AccountStatus.EXPIRED);
    Application application = createApplication();
    persistence.save(account);
    persistence.save(application);
    assertThat(persistence.get(Account.class, ACCOUNT_ID)).isNotNull();
    assertThat(persistence.get(Application.class, application.getUuid())).isNotNull();
    assertThat(deleteAccountHelper.deleteOwnedByAccountEntities(ACCOUNT_ID)).isEmpty();
    assertThat(persistence.get(Application.class, application.getUuid())).isNull();
    assertThat(persistence.get(Account.class, ACCOUNT_ID)).isNotNull();
  }

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  @Ignore("CI-6355: TI team to follow up")
  public void testDeleteAllEntities() {
    Account account = createAccount(AccountStatus.EXPIRED);
    Application application = createApplication();
    ServiceTemplate serviceTemplate = createServiceTemplate(application);
    UsageBucket usageBucket1 = new UsageBucket(USAGE_BUCKET_KEY_1, EMPTY_LIST);
    FeatureName featureName = SEARCH_REQUEST;
    FeatureFlag featureFlag = FeatureFlag.builder()
                                  .name(featureName.name())
                                  .enabled(false)
                                  .accountIds(Sets.newHashSet(ACCOUNT_ID, "abc", "def", "ghi"))
                                  .obsolete(false)
                                  .build();
    persistence.save(account);
    persistence.save(application);
    persistence.save(serviceTemplate);
    persistence.save(usageBucket1);
    persistence.save(featureFlag);

    assertThat(persistence.get(Account.class, ACCOUNT_ID)).isNotNull();
    assertThat(persistence.get(ServiceTemplate.class, serviceTemplate.getUuid())).isNotNull();
    assertThat(persistence.get(Application.class, application.getUuid())).isNotNull();
    assertThat(deleteAccountHelper.deleteAllEntities(ACCOUNT_ID)).isEmpty();
    assertThat(persistence.get(ServiceTemplate.class, serviceTemplate.getUuid())).isNull();
    assertThat(persistence.get(Application.class, application.getUuid())).isNull();
    assertThat(persistence.get(Account.class, ACCOUNT_ID)).isNotNull();
    assertThat(persistence.get(UsageBucket.class, USAGE_BUCKET_KEY_1)).isNull();

    // It is not a proper use to take the FeatureFlag and keep it. It should be always obtained from the service
    // right before it is used.
  }

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  public void testDeleteUsageBucketsCollectionForAccount() {
    UsageBucket usageBucket1 = new UsageBucket(USAGE_BUCKET_KEY_1, EMPTY_LIST);
    UsageBucket usageBucket2 = new UsageBucket(USAGE_BUCKET_KEY_2, EMPTY_LIST);
    UsageBucket usageBucket3 = new UsageBucket(USAGE_BUCKET_KEY_3, EMPTY_LIST);
    persistence.save(usageBucket1);
    persistence.save(usageBucket2);
    persistence.save(usageBucket3);
    deleteAccountHelper.removeAccountFromUsageBucketsCollection(ACCOUNT_ID);
    assertThat(persistence.get(UsageBucket.class, USAGE_BUCKET_KEY_1)).isNull();
    assertThat(persistence.get(UsageBucket.class, USAGE_BUCKET_KEY_2)).isNotNull();
    assertThat(persistence.get(UsageBucket.class, USAGE_BUCKET_KEY_3)).isNull();
  }

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  public void testDeleteAccount_MarkedForDeletion() throws SchedulerException {
    Account account = createAccount(AccountStatus.MARKED_FOR_DELETION);
    JobKey jobKey = new JobKey(JOB_NAME, GROUP_NAME);
    PerpetualTaskRecord perpetualTaskRecord =
        PerpetualTaskRecord.builder().accountId(ACCOUNT_ID).uuid(PERPETUAL_TASK_UUID).build();
    List<String> entitiesRemainingForDeletion = Arrays.asList("accounts", "applications");
    DeleteAccountHelper deleteAccountHelperSpy = spy(deleteAccountHelper);

    when(persistentScheduler.getAllJobKeysForAccount(ACCOUNT_ID)).thenReturn(Collections.singletonList(jobKey));
    when(perpetualTaskService.listAllTasksForAccount(ACCOUNT_ID))
        .thenReturn(Collections.singletonList(perpetualTaskRecord));
    doReturn(entitiesRemainingForDeletion).when(deleteAccountHelperSpy).deleteAllEntities(ACCOUNT_ID);
    when(licenseService.updateAccountLicense(anyString(), any())).thenReturn(true);
    persistence.save(account);

    deleteAccountHelperSpy.deleteAccount(ACCOUNT_ID);

    verify(persistentScheduler, times(1)).deleteAllQuartzJobsForAccount(ACCOUNT_ID);
    verify(perpetualTaskService, times(1)).deleteAllTasksForAccount(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = VOJIN)
  @Category(UnitTests.class)
  public void testDeletedEntityInsertion() throws SchedulerException {
    Account account = createAccount(AccountStatus.MARKED_FOR_DELETION);
    JobKey jobKey = new JobKey(JOB_NAME, GROUP_NAME);
    PerpetualTaskRecord perpetualTaskRecord =
        PerpetualTaskRecord.builder().accountId(ACCOUNT_ID).uuid(PERPETUAL_TASK_UUID).build();
    DeleteAccountHelper deleteAccountHelperSpy = spy(deleteAccountHelper);

    when(deleteAccountHelperSpy.deleteAllEntities(ACCOUNT_ID)).thenReturn(EMPTY_LIST);
    when(persistentScheduler.getAllJobKeysForAccount(ACCOUNT_ID)).thenReturn(Collections.singletonList(jobKey));
    when(perpetualTaskService.listAllTasksForAccount(ACCOUNT_ID))
        .thenReturn(Collections.singletonList(perpetualTaskRecord));
    when(licenseService.updateAccountLicense(anyString(), any())).thenReturn(true);
    persistence.save(account);

    deleteAccountHelperSpy.deleteAccount(ACCOUNT_ID);

    List<DeletedEntity> deletedEntities =
        persistence.createQuery(DeletedEntity.class, excludeAuthority).field("entityId").equal(ACCOUNT_ID).asList();

    assertThat(deletedEntities.size()).isEqualTo(1);
    assertThat(deletedEntities.get(0).getEntityId()).isEqualTo(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void testRemoveSegmentGroupEventJobContextForDeletedAccounts() {
    SegmentGroupEventJobContext segmentGroupEventJobContext1 =
        new SegmentGroupEventJobContext(0L, Collections.singletonList("accountId1"));
    SegmentGroupEventJobContext segmentGroupEventJobContext2 =
        new SegmentGroupEventJobContext(0L, Arrays.asList("accountId1", "accountId2"));
    persistence.save(segmentGroupEventJobContext1);
    persistence.save(segmentGroupEventJobContext2);

    deleteAccountHelper.removeAccountFromSegmentGroupEventContextCollection("accountId1");

    assertThat(persistence.get(SegmentGroupEventJobContext.class, segmentGroupEventJobContext1.getUuid())).isNull();
    assertThat(
        persistence.get(SegmentGroupEventJobContext.class, segmentGroupEventJobContext2.getUuid()).getAccountIds())
        .hasSameElementsAs(Collections.singletonList("accountId2"));
  }
}
