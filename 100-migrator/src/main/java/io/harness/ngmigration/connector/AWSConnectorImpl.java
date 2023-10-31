/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.connector;
import static io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO.builder;
import static io.harness.delegate.beans.connector.awsconnector.AwsCredentialType.INHERIT_FROM_DELEGATE;
import static io.harness.delegate.beans.connector.awsconnector.AwsCredentialType.IRSA;
import static io.harness.delegate.beans.connector.awsconnector.AwsCredentialType.MANUAL_CREDENTIALS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO.AwsConnectorDTOBuilder;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.CrossAccountAccessDTO;
import io.harness.encryption.SecretRefData;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.utils.MigratorUtility;

import software.wings.beans.AwsConfig;
import software.wings.beans.AwsCrossAccountAttributes;
import software.wings.beans.SettingAttribute;
import software.wings.ngmigration.CgEntityId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@OwnedBy(HarnessTeam.CDP)
public class AWSConnectorImpl implements BaseConnector {
  @Override
  public List<String> getSecretIds(SettingAttribute settingAttribute) {
    List<String> secrets = new ArrayList<>();
    secrets.add(((AwsConfig) settingAttribute.getValue()).getEncryptedSecretKey());
    secrets.add(((AwsConfig) settingAttribute.getValue()).getEncryptedAccessKey());
    return secrets;
  }

  @Override
  public ConnectorType getConnectorType(SettingAttribute settingAttribute) {
    return ConnectorType.AWS;
  }

  @Override
  public ConnectorConfigDTO getConfigDTO(
      SettingAttribute settingAttribute, Set<CgEntityId> childEntities, Map<CgEntityId, NGYamlFile> migratedEntities) {
    AwsConfig clusterConfig = (AwsConfig) settingAttribute.getValue();
    AwsConnectorDTOBuilder builder = builder();
    AwsCredentialDTO awsCredentialDTO;

    if (clusterConfig.isUseEc2IamCredentials()) {
      awsCredentialDTO = getEc2IamCredentials(clusterConfig);
    } else if (clusterConfig.isUseIRSA()) {
      awsCredentialDTO = getIrsaCredentials(clusterConfig);
    } else {
      awsCredentialDTO = getManualCredentials(clusterConfig, migratedEntities);
    }

    if (StringUtils.isNotBlank(clusterConfig.getTag())) {
      builder.delegateSelectors(Collections.singleton(clusterConfig.getTag()));
    }

    return builder.executeOnDelegate(true).credential(awsCredentialDTO).build();
  }

  private AwsCredentialDTO getEc2IamCredentials(AwsConfig clusterConfig) {
    return getAwsCredentialDTO(INHERIT_FROM_DELEGATE, null, clusterConfig.getDefaultRegion(),
        clusterConfig.getCrossAccountAttributes(), clusterConfig.isAssumeCrossAccountRole());
  }

  private AwsCredentialDTO getIrsaCredentials(AwsConfig clusterConfig) {
    return getAwsCredentialDTO(IRSA, null, clusterConfig.getDefaultRegion(), clusterConfig.getCrossAccountAttributes(),
        clusterConfig.isAssumeCrossAccountRole());
  }

  private AwsCredentialDTO getManualCredentials(AwsConfig clusterConfig, Map<CgEntityId, NGYamlFile> migratedEntities) {
    SecretRefData secretRefData = MigratorUtility.getSecretRef(migratedEntities, clusterConfig.getEncryptedSecretKey());
    SecretRefData accessRefData = MigratorUtility.getSecretRef(migratedEntities, clusterConfig.getEncryptedAccessKey());
    AwsManualConfigSpecDTO awsManualConfigSpecDTO;
    if (clusterConfig.isUseEncryptedAccessKey()) {
      awsManualConfigSpecDTO =
          AwsManualConfigSpecDTO.builder().accessKeyRef(accessRefData).secretKeyRef(secretRefData).build();
    } else {
      awsManualConfigSpecDTO = AwsManualConfigSpecDTO.builder()
                                   .accessKey(String.valueOf(clusterConfig.getAccessKey()))
                                   .secretKeyRef(secretRefData)
                                   .build();
    }
    return getAwsCredentialDTO(MANUAL_CREDENTIALS, awsManualConfigSpecDTO, clusterConfig.getDefaultRegion(),
        clusterConfig.getCrossAccountAttributes(), clusterConfig.isAssumeCrossAccountRole());
  }

  private AwsCredentialDTO getAwsCredentialDTO(AwsCredentialType awsCredentialType,
      AwsCredentialSpecDTO awsCredentialSpecDTO, String testRegion, AwsCrossAccountAttributes awsCrossAccountAttributes,
      boolean isAssumeCrossAccountRole) {
    return AwsCredentialDTO.builder()
        .awsCredentialType(awsCredentialType)
        .crossAccountAccess(getAwsCrossAccountAccessDTO(awsCrossAccountAttributes, isAssumeCrossAccountRole))
        .config(awsCredentialSpecDTO)
        .testRegion(testRegion)
        .build();
  }

  private CrossAccountAccessDTO getAwsCrossAccountAccessDTO(
      AwsCrossAccountAttributes crossAccountAttributes, boolean isAssumeCrossAccountRole) {
    if (isAssumeCrossAccountRole && crossAccountAttributes != null) {
      return CrossAccountAccessDTO.builder()
          .crossAccountRoleArn(crossAccountAttributes.getCrossAccountRoleArn())
          .externalId(crossAccountAttributes.getExternalId())
          .build();
    }
    return null;
  }
}
