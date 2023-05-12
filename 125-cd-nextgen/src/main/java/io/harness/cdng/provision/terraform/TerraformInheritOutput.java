/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.FileStorageConfigDTO;
import io.harness.cdng.manifest.yaml.FileStorageStoreConfig;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Data
@Builder
@TypeAlias("terraformInheritOutput")
@JsonTypeName("terraformInheritOutput")
@RecasterAlias("io.harness.cdng.provision.terraform.TerraformInheritOutput")
public class TerraformInheritOutput implements ExecutionSweepingOutput {
  String workspace;
  GitStoreConfig configFiles;
  FileStorageStoreConfig fileStoreConfig; // should be removed
  FileStorageConfigDTO fileStorageConfigDTO;
  List<TerraformVarFileConfig> varFileConfigs;
  TerraformBackendConfigFileConfig backendConfigurationFileConfig;
  String backendConfig;
  List<String> targets;
  Map<String, String> environmentVariables;
  boolean useConnectorCredentials;
  EncryptionConfig encryptionConfig;
  EncryptedRecordData encryptedTfPlan;
  String planName;
}
