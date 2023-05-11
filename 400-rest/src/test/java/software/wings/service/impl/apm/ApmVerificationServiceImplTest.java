/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.apm;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.RAGHU;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.settings.SettingVariableTypes.APM_VERIFICATION;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

import io.harness.beans.EncryptedData;
import io.harness.beans.EncryptedDataParent;
import io.harness.beans.SecretText;
import io.harness.category.element.UnitTests;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.secrets.setupusage.SecretSetupUsage;
import io.harness.secrets.setupusage.SecretSetupUsageService;

import software.wings.WingsBaseTest;
import software.wings.beans.APMValidateCollectorConfig;
import software.wings.beans.APMVerificationConfig;
import software.wings.beans.SettingAttribute;
import software.wings.resources.secretsmanagement.SecretManagementResource;
import software.wings.service.impl.SettingValidationService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.apm.ApmVerificationService;
import software.wings.service.intfc.newrelic.NewRelicService;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class ApmVerificationServiceImplTest extends WingsBaseTest {
  @Mock private NewRelicService newRelicService;

  @Inject private ApmVerificationService apmVerificationService;
  @Inject private SecretManagementResource secretManagementResource;
  @Inject private SecretSetupUsageService secretSetupUsageService;
  @Inject private SettingsService settingsService;
  @Inject private SettingValidationService settingValidationService;
  @Inject private HPersistence persistence;

  private String accountId;
  private String appId;
  private List<String> secretTextIds = new ArrayList<>();

  @Before
  public void setup() throws IOException, IllegalAccessException {
    accountId = generateUuid();
    appId = generateUuid();
    for (int i = 0; i < 10; i++) {
      String secretName = "secret-" + i;
      String secretValue = generateUuid();
      String secretId = secretManagementResource
                            .saveSecret(accountId, SecretText.builder().name(secretName).value(secretValue).build())
                            .getResource();
      secretTextIds.add(secretId);
    }

    doNothing()
        .when(newRelicService)
        .validateAPMConfig(any(SettingAttribute.class), any(APMValidateCollectorConfig.class));
    FieldUtils.writeField(settingValidationService, "newRelicService", newRelicService, true);
    FieldUtils.writeField(settingsService, "settingValidationService", settingValidationService, true);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testAddParents() {
    secretTextIds.forEach(secretId -> {
      final EncryptedData encryptedData = persistence.get(EncryptedData.class, secretId);
      assertThat(encryptedData.getParents()).isEmpty();
    });

    Map<String, String> secretIdsToFieldNameMap = new HashMap<>();
    for (int i = 0; i < secretTextIds.size(); i++) {
      secretIdsToFieldNameMap.put(secretTextIds.get(i), "secretName");
    }
    APMVerificationConfig apmVerificationConfig = new APMVerificationConfig();
    apmVerificationConfig.setSecretIdsToFieldNameMap(secretIdsToFieldNameMap);

    String settingId = generateUuid();
    apmVerificationService.addParents(
        aSettingAttribute().withUuid(settingId).withAccountId(accountId).withValue(apmVerificationConfig).build());

    secretTextIds.forEach(secretId -> {
      final EncryptedData encryptedData = persistence.get(EncryptedData.class, secretId);
      assertThat(encryptedData.getParents()).isNotEmpty();
      assertThat(encryptedData.getParents().size()).isEqualTo(1);

      encryptedData.getParents().forEach(
          parent -> assertThat(parent).isEqualTo(new EncryptedDataParent(settingId, APM_VERIFICATION, "secretName")));
    });
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testUpdateParents() {
    secretTextIds.forEach(secretId -> {
      final EncryptedData encryptedData = persistence.get(EncryptedData.class, secretId);
      assertThat(encryptedData.getParents()).isEmpty();
    });

    final Map<String, String> existingSecretIdsToFieldNameMap = new HashMap<>();
    for (int i = 0; i < secretTextIds.size(); i = i + 3) {
      existingSecretIdsToFieldNameMap.put(secretTextIds.get(i), "secretName-" + i);
    }
    APMVerificationConfig apmVerificationConfig = new APMVerificationConfig();
    apmVerificationConfig.setSecretIdsToFieldNameMap(existingSecretIdsToFieldNameMap);

    String settingId = generateUuid();
    apmVerificationService.addParents(
        aSettingAttribute().withUuid(settingId).withAccountId(accountId).withValue(apmVerificationConfig).build());

    for (int i = 0; i < secretTextIds.size(); i++) {
      final EncryptedData encryptedData = persistence.get(EncryptedData.class, secretTextIds.get(i));
      if (i % 3 == 0) {
        assertThat(encryptedData.getParents()).isNotEmpty();
        assertThat(encryptedData.getParents().size()).isEqualTo(1);

        assertThat(encryptedData.getParents().iterator().next())
            .isEqualTo(new EncryptedDataParent(settingId, APM_VERIFICATION, "secretName-" + i));
      } else {
        assertThat(encryptedData.getParents()).isEmpty();
      }
    }

    final Map<String, String> newSecretIdsToFieldNameMap = new HashMap<>();
    for (int i = 0; i < secretTextIds.size(); i = i + 2) {
      newSecretIdsToFieldNameMap.put(secretTextIds.get(i), "secretName-" + i);
    }

    apmVerificationConfig.setSecretIdsToFieldNameMap(newSecretIdsToFieldNameMap);
    apmVerificationService.updateParents(
        aSettingAttribute().withUuid(settingId).withAccountId(accountId).withValue(apmVerificationConfig).build(),
        existingSecretIdsToFieldNameMap);

    for (int i = 0; i < secretTextIds.size(); i++) {
      final EncryptedData encryptedData = persistence.get(EncryptedData.class, secretTextIds.get(i));
      if (i % 2 == 0) {
        assertThat(encryptedData.getParents()).isNotEmpty();
        assertThat(encryptedData.getParents().size()).isEqualTo(1);

        assertThat(encryptedData.getParents().iterator().next())
            .isEqualTo(new EncryptedDataParent(settingId, APM_VERIFICATION, "secretName-" + i));
      } else {
        assertThat(encryptedData.getParents()).isEmpty();
      }
    }
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testSaveAndUpdateApmConfigWithSecretReferences() {
    APMVerificationConfig apmVerificationConfig = new APMVerificationConfig();
    apmVerificationConfig.setUrl("https://base-url.com/");
    List<APMVerificationConfig.KeyValues> headers = new ArrayList<>();
    headers.add(
        APMVerificationConfig.KeyValues.builder().key("api_key").value(secretTextIds.get(0)).encrypted(true).build());
    headers.add(APMVerificationConfig.KeyValues.builder().key("api_key_plain").value("123").encrypted(false).build());

    List<APMVerificationConfig.KeyValues> options = new ArrayList<>();
    options.add(APMVerificationConfig.KeyValues.builder()
                    .key("option_key")
                    .value(secretTextIds.get(1))
                    .encrypted(true)
                    .build());
    options.add(
        APMVerificationConfig.KeyValues.builder().key("option_key_plain").value("321").encrypted(false).build());

    apmVerificationConfig.setHeadersList(headers);
    apmVerificationConfig.setOptionsList(options);
    apmVerificationConfig.setAccountId(accountId);
    apmVerificationConfig.setType(APM_VERIFICATION.name());

    String validationBody = "&token=${secretRef:secretBodyName," + secretTextIds.get(2) + "}";
    apmVerificationConfig.setValidationBody(validationBody);

    String validationUrl = "validatiobUrl?&token=${secretRef:secretUrlName," + secretTextIds.get(3) + "}";
    apmVerificationConfig.setValidationUrl(validationUrl);

    final SettingAttribute settingAttribute = settingsService.saveWithPruning(
        aSettingAttribute().withAccountId(accountId).withName(generateUuid()).withValue(apmVerificationConfig).build(),
        appId, accountId);

    assertThat(((APMVerificationConfig) settingAttribute.getValue()).getSecretIdsToFieldNameMap().size()).isEqualTo(4);
    for (int i = 0; i < secretTextIds.size(); i++) {
      final EncryptedData encryptedData = persistence.get(EncryptedData.class, secretTextIds.get(i));
      String filedName;
      switch (i) {
        case 0:
          filedName = "header.api_key";
          break;
        case 1:
          filedName = "option.option_key";
          break;
        case 2:
          filedName = "secretBodyName";
          break;
        case 3:
          filedName = "secretUrlName";
          break;
        default:
          filedName = "secretName-" + i;
      }
      if (i <= 3) {
        assertThat(encryptedData.getParents()).isNotEmpty();
        assertThat(encryptedData.getParents().size()).isEqualTo(1);

        assertThat(encryptedData.getParents().iterator().next())
            .isEqualTo(new EncryptedDataParent(settingAttribute.getUuid(), APM_VERIFICATION, filedName));
        final Set<SecretSetupUsage> secretUsage =
            secretSetupUsageService.getSecretUsage(accountId, secretTextIds.get(i));
        assertThat(secretUsage.size()).isEqualTo(1);
        final SecretSetupUsage secretSetupUsage = secretUsage.iterator().next();
        assertThat(secretSetupUsage.getEntityId()).isEqualTo(settingAttribute.getUuid());
        assertThat(secretSetupUsage.getType()).isEqualTo(APM_VERIFICATION);
        assertThat(secretSetupUsage.getFieldName()).isEqualTo(filedName);
      } else {
        assertThat(encryptedData.getParents()).isEmpty();
      }
    }

    // test update
    headers = new ArrayList<>();
    headers.add(
        APMVerificationConfig.KeyValues.builder().key("api_key").value(secretTextIds.get(4)).encrypted(true).build());
    headers.add(APMVerificationConfig.KeyValues.builder()
                    .key("new_api_key")
                    .value(secretTextIds.get(5))
                    .encrypted(true)
                    .build());

    options = new ArrayList<>();
    options.add(APMVerificationConfig.KeyValues.builder()
                    .key("option_key")
                    .value(secretTextIds.get(1))
                    .encrypted(true)
                    .build());

    apmVerificationConfig.setHeadersList(headers);
    apmVerificationConfig.setOptionsList(options);

    validationBody = "&token=${secretRef:secretBodyName," + secretTextIds.get(6) + "}";
    apmVerificationConfig.setValidationBody(validationBody);

    validationUrl = "validatiobUrl?&token=${secretRef:secretUrlName," + secretTextIds.get(7) + "}";
    apmVerificationConfig.setValidationUrl(validationUrl);

    settingAttribute.setValue(apmVerificationConfig);
    settingsService.updateWithSettingFields(settingAttribute, settingAttribute.getUuid(), appId);

    assertThat(((APMVerificationConfig) settingAttribute.getValue()).getSecretIdsToFieldNameMap().size()).isEqualTo(5);

    for (int i = 0; i < secretTextIds.size(); i++) {
      final EncryptedData encryptedData = persistence.get(EncryptedData.class, secretTextIds.get(i));

      // this should be option
      if (i == 1) {
        assertThat(encryptedData.getParents())
            .isEqualTo(Sets.newHashSet(
                new EncryptedDataParent(settingAttribute.getUuid(), APM_VERIFICATION, "option.option_key")));
        continue;
      }

      // two headers
      if (i == 4) {
        assertThat(encryptedData.getParents())
            .isEqualTo(Sets.newHashSet(
                new EncryptedDataParent(settingAttribute.getUuid(), APM_VERIFICATION, "header.api_key")));
        continue;
      }

      if (i == 5) {
        assertThat(encryptedData.getParents())
            .isEqualTo(Sets.newHashSet(
                new EncryptedDataParent(settingAttribute.getUuid(), APM_VERIFICATION, "header.new_api_key")));
        continue;
      }

      // body
      if (i == 6) {
        assertThat(encryptedData.getParents())
            .isEqualTo(Sets.newHashSet(
                new EncryptedDataParent(settingAttribute.getUuid(), APM_VERIFICATION, "secretBodyName")));
        continue;
      }

      if (i == 7) {
        assertThat(encryptedData.getParents())
            .isEqualTo(Sets.newHashSet(
                new EncryptedDataParent(settingAttribute.getUuid(), APM_VERIFICATION, "secretUrlName")));
        continue;
      }

      assertThat(encryptedData.getParents()).isEmpty();
    }
  }
}
