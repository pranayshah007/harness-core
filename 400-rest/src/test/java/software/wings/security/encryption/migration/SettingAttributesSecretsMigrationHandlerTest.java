/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.security.encryption.migration;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.UTKARSH;
import static io.harness.security.encryption.EncryptionType.KMS;

import static software.wings.service.impl.SettingServiceHelper.ATTRIBUTES_USING_REFERENCES;
import static software.wings.settings.SettingVariableTypes.APP_DYNAMICS;
import static software.wings.settings.SettingVariableTypes.SECRET_TEXT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.EncryptedData;
import io.harness.category.element.UnitTests;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator.MongoPersistenceIteratorBuilder;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.SecretManagementTestHelper;
import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.AccountType;
import software.wings.beans.AppDynamicsConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
import software.wings.security.UsageRestrictions;
import software.wings.service.impl.SettingValidationService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.UsageRestrictionsService;
import software.wings.service.intfc.newrelic.NewRelicService;

import com.google.inject.Inject;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import java.time.Duration;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SettingAttributesSecretsMigrationHandlerTest extends WingsBaseTest {
  @Inject private HPersistence persistence;
  @Inject private UsageRestrictionsService usageRestrictionsService;
  @Mock private PersistenceIteratorFactory persistenceIteratorFactory;
  @Mock private NewRelicService newRelicService;
  @Inject @InjectMocks private SettingAttributesSecretsMigrationHandler settingAttributesSecretsMigrationHandler;
  @Inject @InjectMocks private SettingValidationService settingValidationService;
  @Inject @InjectMocks private SettingsService settingsService;
  private SettingAttribute settingAttribute;
  private EncryptedData encryptedData;

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRegisterIterators_featureFlagEnabled() {
    ArgumentCaptor<MongoPersistenceIteratorBuilder> captor =
        ArgumentCaptor.forClass(MongoPersistenceIteratorBuilder.class);
    settingAttributesSecretsMigrationHandler.createAndStartIterator(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name("SettingAttributesSecretsMigrationHandler")
            .poolSize(2)
            .interval(Duration.ofSeconds(30))
            .build(),
        Duration.ofMinutes(30));
    verify(persistenceIteratorFactory, times(1))
        .createPumpIteratorWithDedicatedThreadPool(any(), eq(SettingAttribute.class), captor.capture());
    MongoPersistenceIteratorBuilder mongoPersistenceIteratorBuilder = captor.getValue();
    assertThat(mongoPersistenceIteratorBuilder).isNotNull();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testCreateQuery() {
    Query<SettingAttribute> query = mock(Query.class);
    FieldEnd fieldEnd = mock(FieldEnd.class);

    when(query.field(SettingAttributeKeys.value_type)).thenReturn(fieldEnd);
    when(fieldEnd.in(ATTRIBUTES_USING_REFERENCES)).thenReturn(query);

    settingAttributesSecretsMigrationHandler.createQuery(query);

    verify(query, times(1)).field(any());
    verify(fieldEnd, times(1)).in(any());
  }

  private void createSettingAttribute() {
    Account account = getAccount(AccountType.PAID);
    String accountId = persistence.save(account);
    account.setUuid(accountId);
    UsageRestrictions usageRestrictions =
        usageRestrictionsService.getDefaultUsageRestrictions(account.getUuid(), "appId", "envId");
    encryptedData = EncryptedData.builder()
                        .name(generateUuid())
                        .encryptionKey("plainTextKey")
                        .encryptedValue("encryptedValue".toCharArray())
                        .encryptionType(KMS)
                        .type(APP_DYNAMICS)
                        .kmsId(generateUuid())
                        .enabled(true)
                        .accountId(accountId)
                        .hideFromListing(true)
                        .build();

    String secretId = persistence.save(encryptedData);
    encryptedData.setUuid(secretId);
    AppDynamicsConfig appDynamicsConfig = SecretManagementTestHelper.getAppDynamicsConfig(accountId, null, secretId);
    settingAttribute = SecretManagementTestHelper.getSettingAttribute(appDynamicsConfig);
    settingAttribute.setName("testAttribute");
    settingAttribute.setUsageRestrictions(usageRestrictions);
    String settingAttributeId = persistence.save(settingAttribute);
    settingAttribute.setUuid(settingAttributeId);

    encryptedData = persistence.get(EncryptedData.class, secretId);
    settingAttribute = persistence.get(SettingAttribute.class, settingAttributeId);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testHandle_shouldSucceed() {
    createSettingAttribute();
    settingAttributesSecretsMigrationHandler.handle(settingAttribute);
    SettingAttribute updatedSettingAttribute = persistence.get(SettingAttribute.class, settingAttribute.getUuid());
    assertThat(updatedSettingAttribute).isNotNull();
    assertThat(updatedSettingAttribute.isSecretsMigrated()).isTrue();

    EncryptedData updatedEncryptedData = persistence.get(EncryptedData.class, encryptedData.getUuid());
    assertThat(updatedEncryptedData).isNotNull();
    assertThat(updatedEncryptedData.getType()).isEqualTo(SECRET_TEXT);
    assertThat(updatedEncryptedData.getUsageRestrictions()).isEqualTo(settingAttribute.getUsageRestrictions());
    assertThat(updatedEncryptedData.getName()).isEqualTo("testAttribute_APP_DYNAMICS_password");
    assertThat(updatedEncryptedData.getParents()).hasSize(1);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testHandle_shouldPass_secretAlreadyMigrated() {
    createSettingAttribute();
    encryptedData.setType(SECRET_TEXT);
    encryptedData.setHideFromListing(false);
    persistence.save(encryptedData);

    settingAttributesSecretsMigrationHandler.handle(settingAttribute);
    SettingAttribute updatedSettingAttribute = persistence.get(SettingAttribute.class, settingAttribute.getUuid());
    assertThat(updatedSettingAttribute).isNotNull();
    assertThat(updatedSettingAttribute.isSecretsMigrated()).isTrue();

    EncryptedData updatedEncryptedData = persistence.get(EncryptedData.class, encryptedData.getUuid());
    assertThat(updatedEncryptedData).isNotNull();
    assertThat(updatedEncryptedData.getType()).isEqualTo(SECRET_TEXT);
    assertThat(updatedEncryptedData.getUsageRestrictions()).isNull();
    assertThat(updatedEncryptedData.getName()).isEqualTo(encryptedData.getName());
    assertThat(updatedEncryptedData.getParents()).hasSize(1);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testHandle_shouldPass_secretNotFound() {
    createSettingAttribute();
    ((AppDynamicsConfig) settingAttribute.getValue()).setEncryptedPassword(generateUuid());
    persistence.save(settingAttribute);

    settingAttributesSecretsMigrationHandler.handle(settingAttribute);
    SettingAttribute updatedSettingAttribute = persistence.get(SettingAttribute.class, settingAttribute.getUuid());
    assertThat(updatedSettingAttribute).isNotNull();
    assertThat(updatedSettingAttribute.isSecretsMigrated()).isTrue();

    EncryptedData updatedEncryptedData = persistence.get(EncryptedData.class, encryptedData.getUuid());
    assertThat(updatedEncryptedData.getParents()).isEmpty();
  }
}
