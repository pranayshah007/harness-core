/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.connector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.exception.UnsupportedOperationException;
import io.harness.ngmigration.beans.NgEntityDetail;

import software.wings.beans.SettingAttribute;
import software.wings.ngmigration.CgEntityId;

import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.CDC)
public class UnsupportedConnectorImpl implements BaseConnector {
  @Override
  public boolean isConnectorSupported(SettingAttribute settingAttribute) {
    return false;
  }

  @Override
  public String getSecretId(SettingAttribute settingAttribute) {
    return null;
  }

  @Override
  public ConnectorType getConnectorType(SettingAttribute settingAttribute) {
    return null;
  }

  @Override
  public ConnectorConfigDTO getConfigDTO(SettingAttribute settingAttribute, Set<CgEntityId> childEntities,
      Map<CgEntityId, NgEntityDetail> migratedEntities) {
    throw new UnsupportedOperationException(settingAttribute.getName() + "is not supported for migration");
  }
}
