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
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.secrets.KerberosConfigDTO;
import io.harness.ng.core.dto.secrets.SSHAuthDTO;
import io.harness.ng.core.dto.secrets.SSHConfigDTO;
import io.harness.ng.core.dto.secrets.SSHCredentialType;
import io.harness.ng.core.dto.secrets.SSHKeyPathCredentialDTO;
import io.harness.ng.core.dto.secrets.SSHKeyPathCredentialDTO.SSHKeyPathCredentialDTOBuilder;
import io.harness.ng.core.dto.secrets.SSHKeyReferenceCredentialDTO;
import io.harness.ng.core.dto.secrets.SSHKeyReferenceCredentialDTO.SSHKeyReferenceCredentialDTOBuilder;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.ng.core.dto.secrets.SSHPasswordCredentialDTO;
import io.harness.ng.core.dto.secrets.SecretSpecDTO;
import io.harness.ng.core.dto.secrets.TGTGenerationMethod;
import io.harness.ng.core.dto.secrets.TGTGenerationSpecDTO;
import io.harness.ng.core.dto.secrets.TGTKeyTabFilePathSpecDTO;
import io.harness.ng.core.dto.secrets.TGTPasswordSpecDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.secretmanagerclient.SSHAuthScheme;
import io.harness.secretmanagerclient.SecretType;
import io.harness.shell.AccessType;

import software.wings.beans.HostConnectionAttributes;
import software.wings.beans.HostConnectionAttributes.ConnectionType;
import software.wings.beans.SettingAttribute;
import software.wings.ngmigration.CgEntityId;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@OwnedBy(HarnessTeam.CDC)
public class SshConnectorImpl implements BaseConnector {
  @Override
  public List<String> getSecretIds(SettingAttribute settingAttribute) {
    HostConnectionAttributes hostConnectionAttributes = (HostConnectionAttributes) settingAttribute.getValue();
    return Lists.newArrayList(hostConnectionAttributes.getEncryptedPassphrase(),
        hostConnectionAttributes.getEncryptedKerberosPassword(), hostConnectionAttributes.getEncryptedKey(),
        hostConnectionAttributes.getEncryptedSshPassword());
  }

  @Override
  public ConnectorType getConnectorType(SettingAttribute settingAttribute) {
    return null;
  }

  @Override
  public SecretType getSecretType() {
    return SecretType.SSHKey;
  }

  @Override
  public SecretSpecDTO getSecretSpecDTO(
      SettingAttribute settingAttribute, Map<CgEntityId, NGYamlFile> migratedEntities) {
    HostConnectionAttributes hostConnectionAttributes = (HostConnectionAttributes) settingAttribute.getValue();
    SSHAuthScheme authScheme = hostConnectionAttributes.getConnectionType().equals(ConnectionType.SSH)
        ? SSHAuthScheme.SSH
        : SSHAuthScheme.Kerberos;
    return SSHKeySpecDTO.builder()
        .port(hostConnectionAttributes.getSshPort())
        .auth(SSHAuthDTO.builder()
                  .spec(authScheme == SSHAuthScheme.SSH ? getSSHConfig(hostConnectionAttributes, migratedEntities)
                                                        : getKerberosConfig(hostConnectionAttributes, migratedEntities))
                  .type(authScheme)
                  .build())
        .build();
  }

  private SSHConfigDTO getSSHConfig(
      HostConnectionAttributes connectionAttributes, Map<CgEntityId, NGYamlFile> migratedEntities) {
    if (connectionAttributes.getAccessType().equals(AccessType.KEY) && !connectionAttributes.isKeyless()) {
      SSHKeyReferenceCredentialDTOBuilder builder =
          SSHKeyReferenceCredentialDTO.builder()
              .userName(connectionAttributes.getUserName())
              .key(MigratorUtility.getSecretRef(migratedEntities, connectionAttributes.getEncryptedKey()));
      if (StringUtils.isNotBlank(connectionAttributes.getEncryptedPassphrase())) {
        builder.encryptedPassphrase(
            MigratorUtility.getSecretRef(migratedEntities, connectionAttributes.getEncryptedPassphrase()));
      }
      return SSHConfigDTO.builder().credentialType(SSHCredentialType.KeyReference).spec(builder.build()).build();
    }

    if (connectionAttributes.getAccessType().equals(AccessType.KEY) && connectionAttributes.isKeyless()) {
      SSHKeyPathCredentialDTOBuilder builder = SSHKeyPathCredentialDTO.builder()
                                                   .userName(connectionAttributes.getUserName())
                                                   .keyPath(connectionAttributes.getKeyPath());
      if (StringUtils.isNotBlank(connectionAttributes.getEncryptedPassphrase())) {
        builder.encryptedPassphrase(
            MigratorUtility.getSecretRef(migratedEntities, connectionAttributes.getEncryptedPassphrase()));
      }
      return SSHConfigDTO.builder().credentialType(SSHCredentialType.KeyPath).spec(builder.build()).build();
    }

    return SSHConfigDTO.builder()
        .credentialType(SSHCredentialType.Password)
        .spec(SSHPasswordCredentialDTO.builder()
                  .userName(connectionAttributes.getUserName())
                  .password(
                      MigratorUtility.getSecretRef(migratedEntities, connectionAttributes.getEncryptedSshPassword()))
                  .build())
        .build();
  }

  private KerberosConfigDTO getKerberosConfig(
      HostConnectionAttributes connectionAttributes, Map<CgEntityId, NGYamlFile> migratedEntities) {
    TGTGenerationMethod method = null;
    TGTGenerationSpecDTO specDTO = null;
    if (connectionAttributes.getKerberosConfig().isGenerateTGT()) {
      if (StringUtils.isNotBlank(connectionAttributes.getKerberosConfig().getKeyTabFilePath())) {
        method = TGTGenerationMethod.KeyTabFilePath;
        specDTO = TGTKeyTabFilePathSpecDTO.builder()
                      .keyPath(connectionAttributes.getKerberosConfig().getKeyTabFilePath())
                      .build();
      } else {
        method = TGTGenerationMethod.Password;
        specDTO = TGTPasswordSpecDTO.builder()
                      .password(MigratorUtility.getSecretRef(
                          migratedEntities, connectionAttributes.getEncryptedKerberosPassword()))
                      .build();
      }
    }
    return KerberosConfigDTO.builder()
        .principal(connectionAttributes.getKerberosConfig().getPrincipal())
        .tgtGenerationMethod(method)
        .spec(specDTO)
        .realm(connectionAttributes.getKerberosConfig().getRealm())
        .build();
  }

  @Override
  public ConnectorConfigDTO getConfigDTO(
      SettingAttribute settingAttribute, Set<CgEntityId> childEntities, Map<CgEntityId, NGYamlFile> migratedEntities) {
    throw new InvalidRequestException("Unsupported method");
  }
}
