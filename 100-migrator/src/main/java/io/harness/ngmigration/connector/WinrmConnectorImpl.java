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
import io.harness.delegate.task.winrm.AuthenticationScheme;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.secrets.BaseWinRmSpecDTO;
import io.harness.ng.core.dto.secrets.KerberosWinRmConfigDTO;
import io.harness.ng.core.dto.secrets.NTLMConfigDTO;
import io.harness.ng.core.dto.secrets.SecretSpecDTO;
import io.harness.ng.core.dto.secrets.TGTGenerationSpecDTO;
import io.harness.ng.core.dto.secrets.TGTKeyTabFilePathSpecDTO;
import io.harness.ng.core.dto.secrets.TGTPasswordSpecDTO;
import io.harness.ng.core.dto.secrets.WinRmAuthDTO;
import io.harness.ng.core.dto.secrets.WinRmCredentialsSpecDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.WinRmAuthScheme;

import software.wings.beans.SettingAttribute;
import software.wings.beans.WinRmConnectionAttributes;
import software.wings.ngmigration.CgEntityId;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDC)
public class WinrmConnectorImpl implements BaseConnector {
  @Override
  public List<String> getSecretIds(SettingAttribute settingAttribute) {
    WinRmConnectionAttributes hostConnectionAttributes = (WinRmConnectionAttributes) settingAttribute.getValue();
    return Lists.newArrayList(hostConnectionAttributes.getEncryptedPassword());
  }

  @Override
  public ConnectorType getConnectorType(SettingAttribute settingAttribute) {
    return null;
  }

  @Override
  public SecretType getSecretType() {
    return SecretType.WinRmCredentials;
  }

  @Override
  public SecretSpecDTO getSecretSpecDTO(
      SettingAttribute settingAttribute, Map<CgEntityId, NGYamlFile> migratedEntities) {
    WinRmConnectionAttributes connectionAttributes = (WinRmConnectionAttributes) settingAttribute.getValue();
    WinRmAuthScheme authScheme = connectionAttributes.getAuthenticationScheme().equals(AuthenticationScheme.KERBEROS)
        ? WinRmAuthScheme.Kerberos
        : WinRmAuthScheme.NTLM;
    BaseWinRmSpecDTO spec;
    if (authScheme.equals(WinRmAuthScheme.Kerberos)) {
      TGTGenerationSpecDTO tgtGenerationSpecDTO;
      if (StringUtils.isNotBlank(connectionAttributes.getKeyTabFilePath())) {
        tgtGenerationSpecDTO =
            TGTKeyTabFilePathSpecDTO.builder().keyPath(connectionAttributes.getKeyTabFilePath()).build();
      } else {
        tgtGenerationSpecDTO =
            TGTPasswordSpecDTO.builder()
                .password(MigratorUtility.getSecretRef(migratedEntities, connectionAttributes.getEncryptedPassword()))
                .build();
      }
      spec = KerberosWinRmConfigDTO.builder()
                 .principal(connectionAttributes.getDomain())
                 .realm(connectionAttributes.getUsername())
                 .spec(tgtGenerationSpecDTO)
                 .useSSL(connectionAttributes.isUseSSL())
                 .skipCertChecks(connectionAttributes.isSkipCertChecks())
                 .useNoProfile(connectionAttributes.isUseNoProfile())
                 .build();
    } else {
      spec = NTLMConfigDTO.builder()
                 .username(connectionAttributes.getUsername())
                 .domain(connectionAttributes.getDomain())
                 .password(MigratorUtility.getSecretRef(migratedEntities, connectionAttributes.getEncryptedPassword()))
                 .useSSL(connectionAttributes.isUseSSL())
                 .skipCertChecks(connectionAttributes.isSkipCertChecks())
                 .useNoProfile(connectionAttributes.isUseNoProfile())
                 .build();
    }
    return WinRmCredentialsSpecDTO.builder()
        .auth(WinRmAuthDTO.builder().type(authScheme).spec(spec).build())
        .port(connectionAttributes.getPort())
        .parameters(connectionAttributes.getParameters())
        .build();
  }

  @Override
  public ConnectorConfigDTO getConfigDTO(
      SettingAttribute settingAttribute, Set<CgEntityId> childEntities, Map<CgEntityId, NGYamlFile> migratedEntities) {
    throw new InvalidRequestException("Unsupported method");
  }
}
