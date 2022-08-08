/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.delegate;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.Delegate.DelegateBuilder;
import static io.harness.rule.OwnerRule.ARPIT;
import static io.harness.rule.OwnerRule.MARKO;
import static io.harness.rule.OwnerRule.NICOLAS;
import static io.harness.rule.OwnerRule.NIKOLA;
import static io.harness.rule.OwnerRule.SANJA;
import static io.harness.rule.OwnerRule.VLAD;
import static io.harness.rule.OwnerRule.VUK;

import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.service.impl.DelegateProfileServiceImpl.CG_PRIMARY_PROFILE_NAME;
import static software.wings.service.impl.DelegateProfileServiceImpl.NG_PRIMARY_PROFILE_NAME_TEMPLATE;
import static software.wings.service.impl.DelegateProfileServiceImpl.PRIMARY_PROFILE_DESCRIPTION;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EncryptedData;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateEntityOwner;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.delegate.beans.DelegateProfile;
import io.harness.delegate.beans.DelegateProfile.DelegateProfileBuilder;
import io.harness.delegate.beans.DelegateProfile.DelegateProfileKeys;
import io.harness.delegate.beans.DelegateProfileScopingRule;
import io.harness.delegate.utils.DelegateEntityOwnerHelper;
import io.harness.eventsframework.api.Producer;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.observer.Subject;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.secrets.SecretsDao;
import io.harness.service.intfc.DelegateProfileObserver;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.service.impl.AuditServiceHelper;
import software.wings.service.impl.DelegateProfileServiceImpl;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.DEL)
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
@BreakDependencyOn("software.wings.WingsBaseTest")
@BreakDependencyOn("software.wings.beans.LicenseInfo")
@BreakDependencyOn("software.wings.service.impl.AuditServiceHelper")
public class DelegateProfileServiceTest extends WingsBaseTest {
  public static final String ACCOUNT_ID = generateUuid();
  public static final String DELEGATE_PROFILE_NAME = "DELEGATE_PROFILE_NAME";
  public static final String DELEGATE_PROFILE_DESC = "DELEGATE_PROFILE_DESC";
  public static final String VERSION = "1.0.0";
  public static final String IP = "127.0.0.1";
  public static final String HOSTNAME = "delegate";
  public static final String COMPANY_NAME = "COMPANY_NAME";
  public static final String ACCOUNT_NAME = "ACCOUNT_NAME";
  public static final String ACCOUNT_KEY = "ACCOUNT_KEY";
  public static final String TEST_IDENTIFIER = "testIdentifier";
  private static final String ORG = "org";
  private static final String ORG_PRO = "org/pro";

  @Mock private Subject<DelegateProfileObserver> delegateProfileSubject;
  @Mock private AuditServiceHelper auditServiceHelper;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private Producer eventProducer;
  @InjectMocks @Inject private DelegateProfileServiceImpl delegateProfileService;
  @Inject private HPersistence persistence;
  @Inject private SecretsDao secretsDao;

  private DelegateProfileBuilder createDelegateProfileBuilder() {
    return DelegateProfile.builder()
        .accountId(ACCOUNT_ID)
        .name(DELEGATE_PROFILE_NAME)
        .description(DELEGATE_PROFILE_DESC);
  }

  private DelegateBuilder createDelegateBuilder() {
    return Delegate.builder()
        .accountId(ACCOUNT_ID)
        .ip(IP)
        .hostName(HOSTNAME)
        .version(VERSION)
        .status(DelegateInstanceStatus.ENABLED)
        .lastHeartBeat(System.currentTimeMillis());
  }

  @Before
  public void setup() throws IllegalAccessException {
    initMocks(this);
    FieldUtils.writeField(delegateProfileService, "delegateProfileSubject", delegateProfileSubject, true);
    persistence.getDatastore(DelegateProfile.class).ensureIndexes(DelegateProfile.class);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testGetShouldFetchFromDb() {
    String accountId = generateUuid();
    String uuid = generateUuid();
    DelegateProfile delegateProfile = createDelegateProfileBuilder().accountId(accountId).uuid(uuid).build();
    persistence.save(delegateProfile);

    DelegateProfile fetchedProfile = delegateProfileService.get(accountId, uuid);
    assertThat(delegateProfile).isEqualTo(fetchedProfile);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testGetShouldReturnNull() {
    DelegateProfile fetchedProfile = delegateProfileService.get(generateUuid(), null);
    assertThat(fetchedProfile).isNull();

    fetchedProfile = delegateProfileService.get(generateUuid(), "");
    assertThat(fetchedProfile).isNull();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testOnAccountCreatedShouldCreatePrimaryProfile() {
    Account account = anAccount()
                          .withUuid(generateUuid())
                          .withCompanyName(COMPANY_NAME)
                          .withAccountName(ACCOUNT_NAME)
                          .withAccountKey(ACCOUNT_KEY)
                          .withLicenseInfo(getLicenseInfo())
                          .build();

    delegateProfileService.onAccountCreated(account);

    DelegateProfile cgPrimaryProfile = persistence.createQuery(DelegateProfile.class)
                                           .field(DelegateProfileKeys.accountId)
                                           .equal(account.getUuid())
                                           .field(DelegateProfileKeys.primary)
                                           .equal(true)
                                           .filter(DelegateProfileKeys.ng, false)
                                           .get();

    assertThat(cgPrimaryProfile).isNotNull();
    assertThat(cgPrimaryProfile.getUuid()).isNotNull();
    assertThat(cgPrimaryProfile.getAccountId()).isEqualTo(account.getUuid());
    assertThat(cgPrimaryProfile.getName()).isEqualTo(CG_PRIMARY_PROFILE_NAME);
    assertThat(cgPrimaryProfile.getDescription())
        .isEqualTo(String.format("%s %s", PRIMARY_PROFILE_DESCRIPTION, "account"));
    assertThat(cgPrimaryProfile.isPrimary()).isTrue();
    assertThat(cgPrimaryProfile.isNg()).isFalse();

    DelegateProfile ngPrimaryProfile = persistence.createQuery(DelegateProfile.class)
                                           .field(DelegateProfileKeys.accountId)
                                           .equal(account.getUuid())
                                           .field(DelegateProfileKeys.primary)
                                           .equal(true)
                                           .filter(DelegateProfileKeys.ng, true)
                                           .get();

    assertThat(ngPrimaryProfile).isNull();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testOnAccountCreatedShouldNotCreatePrimaryProfile() {
    Account account = anAccount()
                          .withUuid(generateUuid())
                          .withCompanyName(COMPANY_NAME)
                          .withAccountName(ACCOUNT_NAME)
                          .withAccountKey(ACCOUNT_KEY)
                          .withLicenseInfo(getLicenseInfo())
                          .build();

    account.setForImport(true);

    delegateProfileService.onAccountCreated(account);

    DelegateProfile primaryProfile = persistence.createQuery(DelegateProfile.class)
                                         .field(DelegateProfileKeys.accountId)
                                         .equal(account.getUuid())
                                         .field(DelegateProfileKeys.primary)
                                         .equal(true)
                                         .get();

    assertThat(primaryProfile).isNull();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testFetchCgPrimaryProfileShouldFetchFromDb() {
    String accountId = "existingAccountId";

    DelegateProfile primaryProfile = DelegateProfile.builder()
                                         .uuid(generateUuid())
                                         .accountId(accountId)
                                         .name(CG_PRIMARY_PROFILE_NAME)
                                         .description(PRIMARY_PROFILE_DESCRIPTION)
                                         .primary(true)
                                         .ng(false)
                                         .build();

    persistence.save(primaryProfile);

    DelegateProfile fetchedProfile = delegateProfileService.fetchCgPrimaryProfile(accountId);

    assertThat(fetchedProfile).isNotNull();
    assertThat(fetchedProfile.getUuid()).isNotNull();
    assertThat(fetchedProfile.getAccountId()).isEqualTo(accountId);
    assertThat(fetchedProfile.getName()).isEqualTo(CG_PRIMARY_PROFILE_NAME);
    assertThat(fetchedProfile.getDescription()).isEqualTo(PRIMARY_PROFILE_DESCRIPTION);
    assertThat(fetchedProfile.isPrimary()).isTrue();
    assertThat(fetchedProfile.isNg()).isFalse();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testFetchCgPrimaryProfileShouldCreateProfile() {
    String accountId = "nonExistingAccountId";

    DelegateProfile fetchedProfile = delegateProfileService.fetchCgPrimaryProfile(accountId);

    assertThat(fetchedProfile).isNotNull();
    assertThat(fetchedProfile.getUuid()).isNotNull();
    assertThat(fetchedProfile.getAccountId()).isEqualTo(accountId);
    assertThat(fetchedProfile.getName()).isEqualTo(CG_PRIMARY_PROFILE_NAME);
    assertThat(fetchedProfile.getDescription())
        .isEqualTo(String.format("%s %s", PRIMARY_PROFILE_DESCRIPTION, "account"));
    assertThat(fetchedProfile.isPrimary()).isTrue();
    assertThat(fetchedProfile.isNg()).isFalse();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testFetchNgPrimaryProfileShouldFetchFromDb() {
    final String accountId = "existingAccountId";
    final String orgId = "existingOrgId";
    final String projectId = "existingProjectId";

    final DelegateEntityOwner owner = DelegateEntityOwnerHelper.buildOwner(orgId, projectId);
    final String profileName = String.format("%s for %s", NG_PRIMARY_PROFILE_NAME_TEMPLATE, owner.getIdentifier());

    DelegateProfile primaryProfile = DelegateProfile.builder()
                                         .uuid(generateUuid())
                                         .accountId(accountId)
                                         .name(profileName)
                                         .owner(owner)
                                         .description(PRIMARY_PROFILE_DESCRIPTION)
                                         .primary(true)
                                         .ng(true)
                                         .build();

    persistence.save(primaryProfile);

    DelegateProfile fetchedProfile = delegateProfileService.fetchNgPrimaryProfile(accountId, owner);

    assertThat(fetchedProfile).isNotNull();
    assertThat(fetchedProfile.getUuid()).isNotNull();
    assertThat(fetchedProfile.getAccountId()).isEqualTo(accountId);
    assertThat(fetchedProfile.getName()).isEqualTo(profileName);
    assertThat(fetchedProfile.getDescription()).isEqualTo(PRIMARY_PROFILE_DESCRIPTION);
    assertThat(fetchedProfile.isPrimary()).isTrue();
    assertThat(fetchedProfile.isNg()).isTrue();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testFetchNgPrimaryAccountProfileShouldFetchFromDb() {
    final String accountId = "existingAccountId";
    final String profileName = String.format(NG_PRIMARY_PROFILE_NAME_TEMPLATE, "Account");

    DelegateProfile primaryProfile = DelegateProfile.builder()
                                         .uuid(generateUuid())
                                         .accountId(accountId)
                                         .name(profileName)
                                         .owner(null)
                                         .description(PRIMARY_PROFILE_DESCRIPTION)
                                         .primary(true)
                                         .ng(true)
                                         .build();

    persistence.save(primaryProfile);

    DelegateProfile fetchedProfile = delegateProfileService.fetchNgPrimaryProfile(accountId, null);

    assertThat(fetchedProfile).isNotNull();
    assertThat(fetchedProfile.getUuid()).isNotNull();
    assertThat(fetchedProfile.getAccountId()).isEqualTo(accountId);
    assertThat(fetchedProfile.getName()).isEqualTo(profileName);
    assertThat(fetchedProfile.getDescription()).isEqualTo(PRIMARY_PROFILE_DESCRIPTION);
    assertThat(fetchedProfile.isPrimary()).isTrue();
    assertThat(fetchedProfile.isNg()).isTrue();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testFetchNgPrimaryProfileShouldCreateProfile() {
    final String accountId = "nonExistingAccountId";
    final String orgId = "existingOrgId";
    final String projectId = "existingProjectId";

    final DelegateEntityOwner owner = DelegateEntityOwnerHelper.buildOwner(orgId, projectId);

    DelegateProfile fetchedProfile = delegateProfileService.fetchNgPrimaryProfile(accountId, owner);

    assertThat(fetchedProfile).isNotNull();
    assertThat(fetchedProfile.getUuid()).isNotNull();
    assertThat(fetchedProfile.getAccountId()).isEqualTo(accountId);
    assertThat(fetchedProfile.getName()).isEqualTo("Primary Project Configuration");
    assertThat(fetchedProfile.getDescription())
        .isEqualTo(String.format("%s %s project", PRIMARY_PROFILE_DESCRIPTION, owner.getIdentifier()));
    assertThat(fetchedProfile.isPrimary()).isTrue();
    assertThat(fetchedProfile.isNg()).isTrue();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testFetchNgPrimaryProfileShouldCreateOrgProfile() {
    final String accountId = "accountId";
    final String orgId = "orgId";

    final DelegateEntityOwner owner = DelegateEntityOwnerHelper.buildOwner(orgId, null);

    DelegateProfile fetchedProfile = delegateProfileService.fetchNgPrimaryProfile(accountId, owner);

    assertThat(fetchedProfile).isNotNull();
    assertThat(fetchedProfile.getUuid()).isNotNull();
    assertThat(fetchedProfile.getAccountId()).isEqualTo(accountId);
    assertThat(fetchedProfile.getName()).isEqualTo("Primary Organization Configuration");
    assertThat(fetchedProfile.getDescription())
        .isEqualTo(String.format("%s %s organization", PRIMARY_PROFILE_DESCRIPTION, owner.getIdentifier()));
    assertThat(fetchedProfile.isPrimary()).isTrue();
    assertThat(fetchedProfile.isNg()).isTrue();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testDeleteWithNonExistingProfileShouldExitWithoutAction() {
    delegateProfileService.delete(ACCOUNT_ID, generateUuid());

    verify(auditServiceHelper, never())
        .reportDeleteForAuditingUsingAccountId(eq(ACCOUNT_ID), any(DelegateProfile.class));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testDeleteWithAssignedProfileShouldThrowInvalidRequestException() {
    DelegateProfileBuilder delegateProfileBuilder = createDelegateProfileBuilder();
    DelegateProfile assignedDelegateProfile = delegateProfileBuilder.uuid(generateUuid()).build();
    persistence.save(assignedDelegateProfile);

    DelegateBuilder delegateBuilder = createDelegateBuilder();
    Delegate delegate = delegateBuilder.delegateProfileId(assignedDelegateProfile.getUuid()).build();
    persistence.save(delegate);

    delegateProfileService.delete(ACCOUNT_ID, assignedDelegateProfile.getUuid());
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testDeleteWithNonAssignedProfileShouldDeleteProfile() {
    DelegateProfileBuilder delegateProfileBuilder = createDelegateProfileBuilder();
    DelegateProfile nonAssignedDelegateProfile = delegateProfileBuilder.uuid(generateUuid()).build();
    persistence.save(nonAssignedDelegateProfile);

    delegateProfileService.delete(ACCOUNT_ID, nonAssignedDelegateProfile.getUuid());

    assertThat(persistence.get(DelegateProfile.class, nonAssignedDelegateProfile.getUuid())).isNull();
    verify(eventProducer).send(any());
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testUpdateShouldUpdateProfile() {
    String updatedName = "updatedName";
    String updatedDescription = "updatedDescription";
    String updatedScript =
        "export AWS_ACCESS_KEY_ID=${secrets.getValue(\"dummySecret\")} export AWS_SECRET_ACCESS_KEY=${secrets.getValue(\"dummySecret2\")}";
    List<String> profileSelectors = Arrays.asList("selector1", "selector2");

    EncryptedData dummySecret =
        EncryptedData.builder().name("dummySecret").accountId(ACCOUNT_ID).scopedToAccount(true).build();
    EncryptedData dummySecret2 =
        EncryptedData.builder().name("dummySecret2").accountId(ACCOUNT_ID).scopedToAccount(true).build();
    secretsDao.saveSecret(dummySecret);
    secretsDao.saveSecret(dummySecret2);

    DelegateProfile delegateProfile =
        createDelegateProfileBuilder().startupScript("script").approvalRequired(false).build();
    DelegateProfileScopingRule rule = DelegateProfileScopingRule.builder().description("test").build();
    delegateProfile.setScopingRules(Collections.singletonList(rule));
    persistence.save(delegateProfile);

    delegateProfile.setName(updatedName);
    delegateProfile.setDescription(updatedDescription);
    delegateProfile.setStartupScript(updatedScript);
    delegateProfile.setApprovalRequired(true);
    delegateProfile.setSelectors(profileSelectors);

    DelegateProfile updatedDelegateProfile = delegateProfileService.update(delegateProfile);

    assertThat(updatedDelegateProfile.getName()).isEqualTo(updatedName);
    assertThat(updatedDelegateProfile.getDescription()).isEqualTo(updatedDescription);
    assertThat(updatedDelegateProfile.getStartupScript()).isEqualTo(updatedScript);
    assertThat(updatedDelegateProfile.isApprovalRequired()).isTrue();
    assertThat(updatedDelegateProfile.getSelectors()).isEqualTo(profileSelectors);
    assertThat(updatedDelegateProfile.getScopingRules()).containsExactly(rule);

    verify(delegateProfileSubject).fireInform(any(), any(DelegateProfile.class), any(DelegateProfile.class));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void testShouldNotUpdateProfileIfSecretUsedIsNotPresent() {
    String updatedName = "updatedName";
    String updatedDescription = "updatedDescription";
    String updatedScript = "export AWS_ACCESS_KEY_ID=${secrets.getValue(\"dummySecret\")}";
    DelegateProfile delegateProfile =
        createDelegateProfileBuilder().startupScript("script").approvalRequired(false).build();
    persistence.save(delegateProfile);

    delegateProfile.setName(updatedName);
    delegateProfile.setDescription(updatedDescription);
    delegateProfile.setStartupScript(updatedScript);
    delegateProfileService.update(delegateProfile);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void testShouldNotUpdateProfileIfSecretUsedIsNotScopedToAccount() {
    String updatedName = "updatedName";
    String updatedDescription = "updatedDescription";
    String updatedScript = "export AWS_ACCESS_KEY_ID=${secrets.getValue(\"dummySecret\")}";
    EncryptedData dummySecret =
        EncryptedData.builder().name("dummySecret").accountId(ACCOUNT_ID).scopedToAccount(false).build();
    secretsDao.saveSecret(dummySecret);
    DelegateProfile delegateProfile =
        createDelegateProfileBuilder().startupScript("script").approvalRequired(false).build();
    persistence.save(delegateProfile);

    delegateProfile.setName(updatedName);
    delegateProfile.setDescription(updatedDescription);
    delegateProfile.setStartupScript(updatedScript);
    delegateProfileService.update(delegateProfile);
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void testShouldAddProfile() {
    String profileName = "testProfileName";
    String profileName2 = "testProfileName2";
    String profileDescription = "testProfileDescription";
    List<String> profileSelectors = Arrays.asList("testSelector1", "testSelector2");

    DelegateProfile delegateCgProfileHavingSecret =
        createDelegateProfileBuilder()
            .accountId(ACCOUNT_ID)
            .name(profileName)
            .description(profileDescription)
            .startupScript(
                "script export AWS_ACCESS_KEY_ID=${secrets.getValue('dummySecret')} export AWS_SECRET_ACCESS_KEY=${secrets.getValue(\"dummySecret2\")}")
            .selectors(profileSelectors)
            .ng(false)
            .build();

    EncryptedData dummySecret =
        EncryptedData.builder().name("dummySecret").accountId(ACCOUNT_ID).scopedToAccount(true).build();
    EncryptedData dummySecret2 =
        EncryptedData.builder().name("dummySecret2").accountId(ACCOUNT_ID).scopedToAccount(true).build();
    secretsDao.saveSecret(dummySecret);
    secretsDao.saveSecret(dummySecret2);

    DelegateProfile delegateNgProfileWithoutSecret = createDelegateProfileBuilder()
                                                         .accountId(ACCOUNT_ID)
                                                         .name(profileName2)
                                                         .description(profileDescription)
                                                         .startupScript("script")
                                                         .selectors(profileSelectors)
                                                         .ng(true)
                                                         .build();

    DelegateProfile savedCgDelegateProfile = delegateProfileService.add(delegateCgProfileHavingSecret);
    DelegateProfile savedNgDelegateProfile = delegateProfileService.add(delegateNgProfileWithoutSecret);

    assertThat(savedCgDelegateProfile).isNotNull();
    assertThat(savedCgDelegateProfile.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(savedCgDelegateProfile.getName()).isEqualTo(profileName);
    assertThat(savedCgDelegateProfile.getDescription()).isEqualTo(profileDescription);
    assertThat(savedCgDelegateProfile.getSelectors().size()).isEqualTo(2);
    assertThat(savedCgDelegateProfile.getSelectors()).isEqualTo(profileSelectors);

    assertThat(savedNgDelegateProfile).isNotNull();
    assertThat(savedNgDelegateProfile.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(savedNgDelegateProfile.getName()).isEqualTo(profileName2);
    assertThat(savedNgDelegateProfile.getDescription()).isEqualTo(profileDescription);
    assertThat(savedNgDelegateProfile.getSelectors().size()).isEqualTo(2);
    assertThat(savedNgDelegateProfile.getSelectors()).isEqualTo(profileSelectors);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void testShouldNotAddCgProfileIfSecretUsedIsNotScopedToAccount() {
    List<String> profileSelectors = Arrays.asList("testSelector1", "testSelector2");

    DelegateProfile delegateCgProfileHavingSecret =
        createDelegateProfileBuilder()
            .accountId(ACCOUNT_ID)
            .name("testProfileName")
            .description("testProfileDescription")
            .startupScript("script export AWS_ACCESS_KEY_ID=${secrets.getValue(\"dummySecret\")}")
            .selectors(profileSelectors)
            .ng(false)
            .build();
    EncryptedData dummySecret =
        EncryptedData.builder().name("dummySecret").accountId(ACCOUNT_ID).scopedToAccount(false).build();
    secretsDao.saveSecret(dummySecret);
    delegateProfileService.add(delegateCgProfileHavingSecret);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void testShouldNotAddCgProfileIfSecretUsedIsNotPresent() {
    List<String> profileSelectors = Arrays.asList("testSelector1", "testSelector2");

    DelegateProfile delegateProfile =
        createDelegateProfileBuilder()
            .accountId(ACCOUNT_ID)
            .name("testProfileName")
            .description("testProfileDescription")
            .startupScript("script export AWS_ACCESS_KEY_ID=${secrets.getValue(\"dummySecret\")}")
            .selectors(profileSelectors)
            .ng(false)
            .build();

    delegateProfileService.add(delegateProfile);
  }

  @Test
  @Owner(developers = NICOLAS)
  @Category(UnitTests.class)
  public void testShouldAddProfileWithIdentifier() {
    String profileName = "testProfileName";
    String profileDescription = "testProfileDescription";
    String profileIdentifier = "testProfileIdentifier";
    String accountId = generateUuid();
    List<String> profileSelectors = Arrays.asList("testSelector1", "testSelector2");

    DelegateProfile delegateProfile = createDelegateProfileBuilder()
                                          .accountId(accountId)
                                          .name(profileName)
                                          .description(profileDescription)
                                          .startupScript("script")
                                          .selectors(profileSelectors)
                                          .identifier(profileIdentifier)
                                          .build();

    DelegateProfile savedDelegateProfile = delegateProfileService.add(delegateProfile);

    assertThat(savedDelegateProfile).isNotNull();
    assertThat(savedDelegateProfile.getAccountId()).isEqualTo(accountId);
    assertThat(savedDelegateProfile.getName()).isEqualTo(profileName);
    assertThat(savedDelegateProfile.getDescription()).isEqualTo(profileDescription);
    assertThat(savedDelegateProfile.getSelectors().size()).isEqualTo(2);
    assertThat(savedDelegateProfile.getSelectors()).isEqualTo(profileSelectors);
    assertThat(savedDelegateProfile.getIdentifier()).isEqualTo(profileIdentifier);
  }

  @Test
  @Owner(developers = SANJA)
  @Category(UnitTests.class)
  public void testUpdateShouldUpdateScopingRulesWithNull() {
    DelegateProfileScopingRule rule = DelegateProfileScopingRule.builder().description("test").build();
    DelegateProfile delegateProfile = createDelegateProfileBuilder()
                                          .startupScript("script")
                                          .scopingRules(Collections.singletonList(rule))
                                          .approvalRequired(false)
                                          .build();
    persistence.save(delegateProfile);

    DelegateProfile updatedDelegateProfile =
        delegateProfileService.updateScopingRules(delegateProfile.getAccountId(), delegateProfile.getUuid(), null);

    assertThat(updatedDelegateProfile.getScopingRules()).isNull();
  }

  @Test
  @Owner(developers = SANJA)
  @Category(UnitTests.class)
  public void testUpdateShouldUpdateScopingRulesWithEmptyList() {
    DelegateProfileScopingRule rule = DelegateProfileScopingRule.builder().description("test").build();
    DelegateProfile delegateProfile = createDelegateProfileBuilder()
                                          .startupScript("script")
                                          .scopingRules(Collections.singletonList(rule))
                                          .approvalRequired(false)
                                          .build();
    persistence.save(delegateProfile);

    DelegateProfile updatedDelegateProfile = delegateProfileService.updateScopingRules(
        delegateProfile.getAccountId(), delegateProfile.getUuid(), emptyList());

    assertThat(updatedDelegateProfile.getScopingRules()).isNull();
  }

  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void testShouldUpdateDelegateProfileSelectorsWithNull() {
    String uuid = generateUuid();
    String accountId = generateUuid();
    List<String> profileSelectors =
        Arrays.asList("testProfileSelector1", "testProfileSelector2", "testProfileSelector3");

    DelegateProfile delegateProfile = DelegateProfile.builder()
                                          .uuid(uuid)
                                          .accountId(accountId)
                                          .startupScript("script")
                                          .approvalRequired(false)
                                          .selectors(profileSelectors)
                                          .build();

    persistence.save(delegateProfile);

    delegateProfileService.updateDelegateProfileSelectors(uuid, accountId, null);

    DelegateProfile retrievedDelegateProfile = persistence.createQuery(DelegateProfile.class)
                                                   .filter(DelegateProfileKeys.uuid, delegateProfile.getUuid())
                                                   .get();

    assertThat(retrievedDelegateProfile).isNotNull();
    assertThat(retrievedDelegateProfile.getSelectors()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void testShouldNotUpdateDelegateProfileSelectorsWithEmptyList() {
    String uuid = generateUuid();
    String accountId = generateUuid();
    List<String> profileSelectors =
        Arrays.asList("testProfileSelector1", "testProfileSelector2", "testProfileSelector3");

    DelegateProfile delegateProfile = DelegateProfile.builder()
                                          .uuid(uuid)
                                          .accountId(accountId)
                                          .startupScript("script")
                                          .approvalRequired(false)
                                          .selectors(profileSelectors)
                                          .build();

    persistence.save(delegateProfile);

    List<String> profileSelectorsUpdated = emptyList();

    delegateProfileService.updateDelegateProfileSelectors(uuid, accountId, profileSelectorsUpdated);

    DelegateProfile retrievedDelegateProfile = persistence.createQuery(DelegateProfile.class)
                                                   .filter(DelegateProfileKeys.uuid, delegateProfile.getUuid())
                                                   .get();

    assertThat(retrievedDelegateProfile).isNotNull();
    assertThat(retrievedDelegateProfile.getSelectors()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = NIKOLA)
  @Category(UnitTests.class)
  public void shouldDeleteByAccountId() {
    DelegateProfileBuilder delegateProfileBuilder = createDelegateProfileBuilder();
    DelegateProfile assignedDelegateProfile = delegateProfileBuilder.uuid(generateUuid()).build();
    persistence.save(assignedDelegateProfile);

    delegateProfileService.deleteByAccountId(ACCOUNT_ID);
    assertThat(delegateProfileService.get(ACCOUNT_ID, assignedDelegateProfile.getUuid())).isNull();
  }

  @Test
  @Owner(developers = NICOLAS)
  @Category(UnitTests.class)
  public void isValidIdentifier_valid() {
    DelegateProfile delegateProfile =
        createDelegateProfileBuilder().uuid(generateUuid()).identifier(TEST_IDENTIFIER).build();

    persistence.save(delegateProfile);

    assertThat(delegateProfileService.identifierExists(ACCOUNT_ID, null, TEST_IDENTIFIER + "_1")).isFalse();
  }

  @Test
  @Owner(developers = NICOLAS)
  @Category(UnitTests.class)
  public void isValidIdentifier_invalid() {
    DelegateProfile delegateProfile =
        createDelegateProfileBuilder().uuid(generateUuid()).identifier(TEST_IDENTIFIER).build();

    persistence.save(delegateProfile);

    assertThat(delegateProfileService.identifierExists(ACCOUNT_ID, null, TEST_IDENTIFIER)).isTrue();
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldIdentifierExistWhenOwnerNull() {
    DelegateProfile delegateProfile =
        createDelegateProfileBuilder().uuid(generateUuid()).identifier(TEST_IDENTIFIER).build();

    persistence.save(delegateProfile);

    assertThat(delegateProfileService.identifierExists(ACCOUNT_ID, null, TEST_IDENTIFIER)).isTrue();
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldIdentifierExistWithOwner() {
    DelegateEntityOwner owner = DelegateEntityOwner.builder().identifier(ORG_PRO).build();
    DelegateProfile delegateProfile =
        createDelegateProfileBuilder().uuid(generateUuid()).identifier(TEST_IDENTIFIER).owner(owner).build();

    persistence.save(delegateProfile);

    assertThat(delegateProfileService.identifierExists(ACCOUNT_ID, owner, TEST_IDENTIFIER)).isTrue();
  }

  @Test
  @Owner(developers = NICOLAS)
  @Category(UnitTests.class)
  public void getDelegatesForProfile() {
    String accountId = generateUuid();
    String uuid = generateUuid();
    DelegateProfile delegateProfile = createDelegateProfileBuilder().accountId(accountId).uuid(uuid).build();
    persistence.save(delegateProfile);

    DelegateBuilder delegateBuilder = createDelegateBuilder();
    Delegate delegate = delegateBuilder.delegateProfileId(delegateProfile.getUuid()).accountId(accountId).build();
    persistence.save(delegate);

    List<String> delegatesForProfile =
        delegateProfileService.getDelegatesForProfile(delegateProfile.getAccountId(), delegateProfile.getUuid());

    assertThat(delegatesForProfile).isNotNull();
    assertThat(delegatesForProfile.size()).isEqualTo(1);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void testShouldAddProfileWithExistingIdentifier() {
    String profileName = "testProfileName";
    String profileIdentifier = "testProfileIdentifier";
    String accountId = generateUuid();

    DelegateProfile delegateProfile1 =
        createDelegateProfileBuilder().accountId(accountId).name(profileName).identifier(profileIdentifier).build();
    DelegateProfile delegateProfile2 =
        createDelegateProfileBuilder().accountId(accountId).name(profileName).identifier(profileIdentifier).build();

    delegateProfileService.add(delegateProfile1);
    delegateProfileService.add(delegateProfile2);
  }

  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void testShouldAddProfileWithSameAccountAndName() {
    String profileName = "testProfileName";
    String profileIdentifier1 = "_123";
    String profileIdentifier2 = "_1234";
    String accountId = generateUuid();

    DelegateProfile delegateProfile1 =
        createDelegateProfileBuilder().accountId(accountId).name(profileName).identifier(profileIdentifier1).build();
    DelegateProfile delegateProfile2 =
        createDelegateProfileBuilder().accountId(accountId).name(profileName).identifier(profileIdentifier2).build();

    delegateProfileService.add(delegateProfile1);
    delegateProfileService.add(delegateProfile2);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void testShouldAddProfileWithEmptyIdentifier() {
    String profileName = "testProfileName";
    String profileIdentifier = "";
    String accountId = generateUuid();

    DelegateProfile delegateProfile =
        createDelegateProfileBuilder().accountId(accountId).name(profileName).identifier(profileIdentifier).build();

    delegateProfileService.add(delegateProfile);
    delegateProfileService.add(delegateProfile);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void testShouldAddProfileWithNoIdentifier() {
    String profileName = "testProfileName";
    String accountId = generateUuid();

    DelegateProfile delegateProfile = createDelegateProfileBuilder().accountId(accountId).name(profileName).build();

    delegateProfileService.add(delegateProfile);
    delegateProfileService.add(delegateProfile);
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void testShouldAddProfileWithOwner() {
    String accountId = generateUuid();
    String identifier = "";
    DelegateEntityOwner owner1 = DelegateEntityOwner.builder().identifier(ORG).build();
    DelegateEntityOwner owner2 = DelegateEntityOwner.builder().identifier(ORG_PRO).build();

    DelegateProfile delegateProfile1 =
        createDelegateProfileBuilder().accountId(accountId).identifier(identifier).owner(owner1).build();

    DelegateProfile delegateProfile2 =
        createDelegateProfileBuilder().accountId(accountId).identifier(identifier).owner(owner2).build();

    delegateProfileService.add(delegateProfile1);
    delegateProfileService.add(delegateProfile2);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void testShouldAddProfileWithTheSameOwner() {
    String identifier = "identifier";
    String accountId = generateUuid();
    DelegateEntityOwner owner1 = DelegateEntityOwner.builder().identifier(ORG_PRO).build();
    DelegateEntityOwner owner2 = DelegateEntityOwner.builder().identifier(ORG_PRO).build();

    DelegateProfile delegateProfile1 =
        createDelegateProfileBuilder().accountId(accountId).identifier(identifier).owner(owner1).build();

    DelegateProfile delegateProfile2 =
        createDelegateProfileBuilder().accountId(accountId).identifier(identifier).owner(owner2).build();

    delegateProfileService.add(delegateProfile1);
    delegateProfileService.add(delegateProfile2);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void testShouldAddProfileWithTheSameOwnerNoIdentifier() {
    String accountId = generateUuid();
    DelegateEntityOwner owner1 = DelegateEntityOwner.builder().identifier(ORG_PRO).build();
    DelegateEntityOwner owner2 = DelegateEntityOwner.builder().identifier(ORG_PRO).build();

    DelegateProfile delegateProfile1 = createDelegateProfileBuilder().accountId(accountId).owner(owner1).build();

    DelegateProfile delegateProfile2 = createDelegateProfileBuilder().accountId(accountId).owner(owner2).build();

    delegateProfileService.add(delegateProfile1);
    delegateProfileService.add(delegateProfile2);
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldAddProfileWithoutOwnerAndWithOwner() {
    String accountId = generateUuid();
    DelegateEntityOwner owner = DelegateEntityOwner.builder().identifier(ORG_PRO).build();
    String identifier = "_123";

    DelegateProfile delegateProfile1 =
        createDelegateProfileBuilder().accountId(accountId).identifier(identifier).build();

    DelegateProfile delegateProfile2 =
        createDelegateProfileBuilder().accountId(accountId).owner(owner).identifier(identifier).build();

    delegateProfileService.add(delegateProfile1);
    delegateProfileService.add(delegateProfile2);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldAddSameProfilesWithoutOwners() {
    String accountId = generateUuid();
    String identifier = "_123";

    DelegateProfile delegateProfile1 =
        createDelegateProfileBuilder().accountId(accountId).identifier(identifier).build();

    DelegateProfile delegateProfile2 =
        createDelegateProfileBuilder().accountId(accountId).identifier(identifier).build();

    delegateProfileService.add(delegateProfile1);
    delegateProfileService.add(delegateProfile2);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldAddProfileWithNameSibling() {
    String accountId = generateUuid();
    String name1 = "name_1";
    String name2 = "name-1";

    DelegateProfile delegateProfile1 = createDelegateProfileBuilder().accountId(accountId).name(name1).build();

    DelegateProfile delegateProfile2 = createDelegateProfileBuilder().accountId(accountId).name(name2).build();

    delegateProfileService.add(delegateProfile1);
    delegateProfileService.add(delegateProfile2);
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldAddProfileWithIllegalCharactersInName() {
    String accountId = generateUuid();
    String name = "1 ~`!@#$%^&*()_-+={}[]|\\:;\"'<>,.?///";

    DelegateProfile delegateProfile = createDelegateProfileBuilder().accountId(accountId).name(name).ng(true).build();

    delegateProfileService.add(delegateProfile);
  }
}
