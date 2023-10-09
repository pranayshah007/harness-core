/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.connector;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.secrets.SecretSpecDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.secretmanagerclient.SecretType;

import software.wings.beans.SettingAttribute;
import software.wings.ngmigration.CgEntityId;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@OwnedBy(HarnessTeam.CDC)
public interface BaseConnector {
  default boolean isConnectorSupported(SettingAttribute settingAttribute) {
    return true;
  }

  default SupportStatus getSupportStatus() {
    return SupportStatus.SUPPORTED;
  }

  default List<String> getConnectorIds(SettingAttribute settingAttribute) {
    return Collections.emptyList();
  }

  List<String> getSecretIds(SettingAttribute settingAttribute);

  ConnectorType getConnectorType(SettingAttribute settingAttribute);

  default SecretType getSecretType() {
    return null;
  }

  default SecretSpecDTO getSecretSpecDTO(
      SettingAttribute settingAttribute, Map<CgEntityId, NGYamlFile> migratedEntities) {
    throw new InvalidRequestException("Invalid access");
  }

  ConnectorConfigDTO getConfigDTO(
      SettingAttribute settingAttribute, Set<CgEntityId> childEntities, Map<CgEntityId, NGYamlFile> migratedEntities);

  default Set<String> toSet(List<String> list) {
    if (EmptyPredicate.isEmpty(list)) {
      return new HashSet<>();
    }
    return new HashSet<>(list);
  }
}
