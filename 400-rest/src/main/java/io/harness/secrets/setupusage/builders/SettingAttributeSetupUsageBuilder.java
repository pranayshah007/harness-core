/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secrets.setupusage.builders;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;

import static software.wings.settings.SettingVariableTypes.APM_VERIFICATION;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EncryptedDataParent;
import io.harness.beans.SearchFilter;
import io.harness.beans.EncryptionReflectUtils;
import io.harness.secrets.setupusage.EncryptionDetail;
import io.harness.secrets.setupusage.SecretSetupUsage;
import io.harness.secrets.setupusage.SecretSetupUsageBuilder;

import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
import software.wings.service.intfc.SettingsService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.NonNull;

@OwnedBy(PL)
@Singleton
public class SettingAttributeSetupUsageBuilder implements SecretSetupUsageBuilder {
  @Inject private SettingsService settingsService;

  public Set<SecretSetupUsage> buildSecretSetupUsages(@NonNull String accountId, String secretId,
      @NonNull Map<String, Set<EncryptedDataParent>> parentsByParentIds, @NonNull EncryptionDetail encryptionDetail) {
    Set<String> parentIds = parentsByParentIds.keySet();
    List<SettingAttribute> settingAttributeList =
        settingsService
            .list(aPageRequest()
                      .addFilter(ID_KEY, SearchFilter.Operator.IN, parentIds.toArray())
                      .addFilter(ACCOUNT_ID_KEY, SearchFilter.Operator.EQ, accountId)
                      .build(),
                null, null, false)
            .getResponse();

    Set<SecretSetupUsage> secretSetupUsages = new HashSet<>();
    for (SettingAttribute settingAttribute : settingAttributeList) {
      settingAttribute.setEncryptionType(encryptionDetail.getEncryptionType());
      settingAttribute.setEncryptedBy(encryptionDetail.getSecretManagerName());
      Set<EncryptedDataParent> parents = parentsByParentIds.get(settingAttribute.getUuid());
      if (settingAttribute.getValue().getSettingType() == APM_VERIFICATION) {
        parents.forEach(parent
            -> secretSetupUsages.add(SecretSetupUsage.builder()
                                         .entityId(parent.getId())
                                         .type(parent.getType())
                                         .fieldName(parent.getFieldName())
                                         .entity(settingAttribute)
                                         .build()));
      } else {
        parents.forEach(parent -> {
          List<Field> encryptedFields = settingAttribute.getValue().getEncryptedFields();
          EncryptionReflectUtils.getFieldHavingFieldName(encryptedFields, parent.getFieldName()).ifPresent(field -> {
            secretSetupUsages.add(SecretSetupUsage.builder()
                                      .entityId(parent.getId())
                                      .type(parent.getType())
                                      .fieldName(SettingAttributeKeys.value.concat(".").concat(field.getName()))
                                      .entity(settingAttribute)
                                      .build());
          });
        });
      }
    }
    return secretSetupUsages;
  }

  @Override
  public Map<String, Set<String>> buildAppEnvMap(
      String accountId, String secretId, Map<String, Set<EncryptedDataParent>> parentsByParentIds) {
    return Collections.emptyMap();
  }
}
