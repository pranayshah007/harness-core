/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.connector;

import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthConnectorDTO;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthKeyAndValue;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthMethod;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.utils.MigratorUtility;

import software.wings.beans.APMVerificationConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.apm.Method;
import software.wings.ngmigration.CgEntityId;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.collections4.CollectionUtils;

public class APMVerificationConnectorImpl implements BaseConnector {
  @Override
  public List<String> getSecretIds(SettingAttribute settingAttribute) {
    APMVerificationConfig config = (APMVerificationConfig) settingAttribute.getValue();
    return config.fetchRelevantEncryptedSecrets();
  }

  @Override
  public ConnectorType getConnectorType(SettingAttribute settingAttribute) {
    return ConnectorType.CUSTOM_HEALTH;
  }

  @Override
  public ConnectorConfigDTO getConfigDTO(
      SettingAttribute settingAttribute, Set<CgEntityId> childEntities, Map<CgEntityId, NGYamlFile> migratedEntities) {
    APMVerificationConfig config = (APMVerificationConfig) settingAttribute.getValue();
    return CustomHealthConnectorDTO.builder()
        .baseURL(config.getUrl())
        .method(config.getValidationMethod() == Method.POST ? CustomHealthMethod.POST : CustomHealthMethod.GET)
        .validationPath(config.getValidationUrl())
        .validationBody(config.getValidationBody())
        .headers(createCustomHealthKeyAndValueFromCG(config.getHeadersList(), migratedEntities))
        .params(createCustomHealthKeyAndValueFromCG(config.getOptionsList(), migratedEntities))
        .build();
  }

  private List<CustomHealthKeyAndValue> createCustomHealthKeyAndValueFromCG(
      List<APMVerificationConfig.KeyValues> keyValues, Map<CgEntityId, NGYamlFile> migratedEntities) {
    return Stream
        .concat(CollectionUtils.emptyIfNull(keyValues)
                    .stream()
                    .filter(keyValue -> !keyValue.isEncrypted())
                    .map(keyValue
                        -> CustomHealthKeyAndValue.builder()
                               .key(keyValue.getKey())
                               .value(keyValue.getValue())
                               .isValueEncrypted(false)
                               .build()),
            CollectionUtils.emptyIfNull(keyValues)
                .stream()
                .filter(keyValue -> keyValue.isEncrypted())
                .map(keyValue
                    -> CustomHealthKeyAndValue.builder()
                           .key(keyValue.getKey())
                           .value(keyValue.getValue())
                           .isValueEncrypted(true)
                           .encryptedValueRef(
                               MigratorUtility.getSecretRef(migratedEntities, keyValue.getEncryptedValue()))
                           .build()))
        .collect(Collectors.toList());
  }
}
