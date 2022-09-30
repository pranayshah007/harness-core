/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.annotations.dev.HarnessModule._955_ACCOUNT_MGMT;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.FeatureName.SEND_SLACK_NOTIFICATION_FROM_DELEGATE;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.UTKARSH;
import static io.harness.rule.OwnerRule.VIKAS;

import static software.wings.dl.exportimport.WingsMongoExportImport.getCollectionName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureFlag;
import io.harness.beans.FeatureFlag.FeatureFlagKeys;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.scheduler.PersistentScheduler;

import software.wings.WingsBaseTest;
import software.wings.app.MainConfiguration;
import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.dl.WingsMongoPersistence;
import software.wings.dl.exportimport.ImportStatusReport;
import software.wings.dl.exportimport.WingsMongoExportImport;
import software.wings.licensing.LicenseService;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.UsageRestrictionsService;
import software.wings.service.intfc.UserService;
import software.wings.utils.AccountPermissionUtils;

import com.google.common.collect.Sets;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.inject.Inject;
import com.mongodb.DBObject;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.mapping.Mapper;
import org.mongodb.morphia.mapping.MapperOptions;

/**
 * @author marklu on 2019-03-01
 */

@OwnedBy(PL)
@TargetModule(_955_ACCOUNT_MGMT)
@Slf4j
public class AccountExportImportResourceTest extends WingsBaseTest {
  @Mock private WingsMongoPersistence wingsMongoPersistence;
  @Mock private Morphia morphia;
  @Inject private WingsMongoExportImport wingsMongoExportImport;
  @Mock private AppService appService;
  @Mock private AccountService accountService;
  @Mock private LicenseService licenseService;
  @Mock private UsageRestrictionsService usageRestrictionsService;
  @Mock private UserService userService;
  @Mock private AuthService authService;
  @Mock private AccountPermissionUtils accountPermissionUtils;
  @Mock private MainConfiguration mainConfiguration;
  @Mock private PersistentScheduler persistentScheduler;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private HPersistence persistence;

  @Mock private Account account;
  @Mock private User user;

  private JsonArray users;
  private String userId;
  private String accountId;
  private AccountExportImportResource accountExportImportResource;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    Mapper mapper = mock(Mapper.class);
    MapperOptions mapperOptions = mock(MapperOptions.class);
    when(mapper.getOptions()).thenReturn(mapperOptions);
    when(morphia.getMapper()).thenReturn(mapper);

    accountExportImportResource = new AccountExportImportResource(wingsMongoPersistence, morphia,
        wingsMongoExportImport, accountService, licenseService, appService, authService, userService,
        accountPermissionUtils, persistentScheduler, featureFlagService, mainConfiguration);

    accountId = UUIDGenerator.generateUuid();
    userId = UUIDGenerator.generateUuid();

    String email = "user@harness.io";

    when(mainConfiguration.getExportAccountDataBatchSize()).thenReturn(1000);
    JsonObject userObject = new JsonObject();
    userObject.add("_id", new JsonPrimitive(userId));
    userObject.add("name", new JsonPrimitive("Harness User"));
    userObject.add("email", new JsonPrimitive(email));

    users = new JsonArray();
    users.add(userObject);

    when(wingsMongoPersistence.get(Account.class, accountId)).thenReturn(account);
    when(userService.getUserByEmail(email)).thenReturn(user);
    when(user.getUuid()).thenReturn(UUIDGenerator.generateUuid());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testFindClashedUserIdMapping() {
    Map<String, String> userIdMapping = accountExportImportResource.findClashedUserIdMapping(accountId, users);
    assertThat(userIdMapping).hasSize(1);
    assertThat(userIdMapping.containsKey(userId)).isTrue();
    verify(wingsMongoPersistence).save(user);

    String usersJson = users.toString();
    String newUsersJson = accountExportImportResource.replaceClashedUserIds(usersJson, userIdMapping);
    log.info(userIdMapping.toString());
    log.info(usersJson);
    log.info(newUsersJson);

    assertThat(newUsersJson).isNotEqualTo(usersJson);
    assertThat(newUsersJson.indexOf(userId) < 0).isTrue();
  }

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testFindFeatureFlagExportCondition() {
    FeatureFlag globallyEnabledFeatureFlag = createGloballyEnabledFeatureFlag(SEND_SLACK_NOTIFICATION_FROM_DELEGATE);
    String globallyEnabledFeatureFlagId = persistence.save(globallyEnabledFeatureFlag);

    FeatureFlag obsoleteGloballyEnabledFeatureFlag = createObsoleteGloballyEnabledFeatureFlag();
    persistence.save(obsoleteGloballyEnabledFeatureFlag);

    FeatureFlag enabledFeatureFlagForAccount =
        createEnabledFeatureFlagForAccount(accountId, SEND_SLACK_NOTIFICATION_FROM_DELEGATE);
    String enabledFeatureFlagForAccountId = persistence.save(enabledFeatureFlagForAccount);

    DBObject dbObject = accountExportImportResource.getFeatureFlagExportFilter(accountId);
    String collectionName = getCollectionName(FeatureFlag.class);

    List<String> results = wingsMongoExportImport.exportRecords(dbObject, collectionName);
    assertThat(results.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testFindFeatureFlagExportForZeroMatchingCondition() {
    FeatureFlag obsoleteGloballyEnabledFeatureFlag = createObsoleteGloballyEnabledFeatureFlag();
    persistence.save(obsoleteGloballyEnabledFeatureFlag);

    DBObject dbObject = accountExportImportResource.getFeatureFlagExportFilter(accountId);
    String collectionName = getCollectionName(FeatureFlag.class);

    List<String> results = wingsMongoExportImport.exportRecords(dbObject, collectionName);
    assertThat(results.isEmpty()).isTrue();
  }

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testFindFeatureFlagExportForSingleMatchingCondition() {
    FeatureFlag enabledFeatureFlagForAccount =
        createEnabledFeatureFlagForAccount(accountId, SEND_SLACK_NOTIFICATION_FROM_DELEGATE);
    persistence.save(enabledFeatureFlagForAccount);

    DBObject dbObject = accountExportImportResource.getFeatureFlagExportFilter(accountId);
    String collectionName = getCollectionName(FeatureFlag.class);

    List<String> results = wingsMongoExportImport.exportRecords(dbObject, collectionName);
    assertThat(results.size()).isEqualTo(1);
    assertThat(results.get(0).contains(accountId)).isTrue();
  }

  private FeatureFlag createGloballyEnabledFeatureFlag(FeatureName featureEnum) {
    return FeatureFlag.builder().name(featureEnum.name()).enabled(true).build();
  }

  private FeatureFlag createObsoleteGloballyEnabledFeatureFlag() {
    return FeatureFlag.builder().enabled(true).obsolete(true).build();
  }

  private FeatureFlag createEnabledFeatureFlagForAccount(String accountId, FeatureName featureName) {
    return FeatureFlag.builder().name(featureName.name()).enabled(false).accountIds(Sets.newHashSet(accountId)).build();
  }

  /**
   * Test the scenario when the feature flag being imported is already enabled in PAID cluster
   */
  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testFeatureFlagImportWhenFeatureIsAlreadyEnabledGlobally() {
    FeatureFlag enabledFeatureFlagForAccount = createGloballyEnabledFeatureFlag(SEND_SLACK_NOTIFICATION_FROM_DELEGATE);
    persistence.save(enabledFeatureFlagForAccount);

    DBObject dbObject = accountExportImportResource.getFeatureFlagExportFilter(accountId);
    String collectionName = getCollectionName(FeatureFlag.class);
    List<String> results = wingsMongoExportImport.exportRecords(dbObject, collectionName);

    // featureFlagService.isEnabled(FeatureName.SEND_SLACK_NOTIFICATION_FROM_DELEGATE, accountId);
    ImportStatusReport.ImportStatus importStatus =
        accountExportImportResource.enableFeatureFlagForAccount(accountId, collectionName, results);
    assertThat(importStatus.getIdClashes()).isEqualTo(1);
    assertThat(importStatus.getImported()).isEqualTo(0);

    FeatureFlag featureFlagEntity = persistence.createQuery(FeatureFlag.class, excludeAuthority)
                                        .filter(FeatureFlagKeys.name, SEND_SLACK_NOTIFICATION_FROM_DELEGATE.name())
                                        .get();

    assertThat(featureFlagEntity).isNotNull();
    assertThat(featureFlagEntity.isEnabled()).isTrue();
  }

  /**
   * Test the scenario when the feature flag being imported is already enabled in PAID cluster
   */
  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testFeatureFlagImportWhenFeatureIsNotPresentInDB() {
    FeatureFlag enabledFeatureFlagForAccount = createGloballyEnabledFeatureFlag(SEND_SLACK_NOTIFICATION_FROM_DELEGATE);
    persistence.save(enabledFeatureFlagForAccount);

    DBObject dbObject = accountExportImportResource.getFeatureFlagExportFilter(accountId);
    String collectionName = getCollectionName(FeatureFlag.class);
    List<String> results = wingsMongoExportImport.exportRecords(dbObject, collectionName);

    // Now delete the record from the db.
    persistence.delete(persistence.createQuery(FeatureFlag.class, excludeAuthority)
                           .filter(FeatureFlagKeys.name, SEND_SLACK_NOTIFICATION_FROM_DELEGATE.name()));

    // featureFlagService.isEnabled(FeatureName.SEND_SLACK_NOTIFICATION_FROM_DELEGATE, accountId);
    ImportStatusReport.ImportStatus importStatus =
        accountExportImportResource.enableFeatureFlagForAccount(accountId, collectionName, results);
    assertThat(importStatus.getIdClashes()).isEqualTo(0);
    assertThat(importStatus.getImported()).isEqualTo(1);

    FeatureFlag featureFlagEntity = persistence.createQuery(FeatureFlag.class, excludeAuthority)
                                        .filter(FeatureFlagKeys.name, SEND_SLACK_NOTIFICATION_FROM_DELEGATE.name())
                                        .get();

    assertThat(featureFlagEntity).isNotNull();
    assertThat(featureFlagEntity.isEnabled()).isFalse();
    assertThat(featureFlagEntity.getAccountIds()).contains(accountId);
  }

  /**
   * Test the scenario when the feature flag being imported is not enabled for accountId in question
   * In this case we add the accounId to featureFlag.
   */
  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testFeatureFlagImportWhenFeatureIsNotEnabledForAccount() {
    FeatureFlag enabledFeatureFlagForAccount =
        createEnabledFeatureFlagForAccount(accountId, SEND_SLACK_NOTIFICATION_FROM_DELEGATE);
    persistence.save(enabledFeatureFlagForAccount);

    DBObject dbObject = accountExportImportResource.getFeatureFlagExportFilter(accountId);
    String collectionName = getCollectionName(FeatureFlag.class);
    List<String> results = wingsMongoExportImport.exportRecords(dbObject, collectionName);

    persistence.delete(persistence.createQuery(FeatureFlag.class, excludeAuthority)
                           .filter(FeatureFlagKeys.name, SEND_SLACK_NOTIFICATION_FROM_DELEGATE.name()));

    FeatureFlag enabledFeatureFlagForDifferentAccount =
        createEnabledFeatureFlagForAccount("testAccount", SEND_SLACK_NOTIFICATION_FROM_DELEGATE);
    persistence.save(enabledFeatureFlagForDifferentAccount);

    // featureFlagService.isEnabled(FeatureName.SEND_SLACK_NOTIFICATION_FROM_DELEGATE, accountId);
    ImportStatusReport.ImportStatus importStatus =
        accountExportImportResource.enableFeatureFlagForAccount(accountId, collectionName, results);
    assertThat(importStatus.getIdClashes()).isEqualTo(0);
    assertThat(importStatus.getImported()).isEqualTo(1);

    FeatureFlag featureFlagEntity = persistence.createQuery(FeatureFlag.class, excludeAuthority)
                                        .filter(FeatureFlagKeys.name, SEND_SLACK_NOTIFICATION_FROM_DELEGATE.name())
                                        .get();

    assertThat(featureFlagEntity).isNotNull();
    assertThat(featureFlagEntity.getAccountIds()).contains(accountId, "testAccount");
    assertThat(featureFlagEntity.isEnabled()).isFalse();
  }
}
