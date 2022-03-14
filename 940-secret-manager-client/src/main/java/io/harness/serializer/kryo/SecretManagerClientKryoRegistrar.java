/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.kryo;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.SSHTaskParams;
import io.harness.delegate.beans.WinRmTaskParams;
import io.harness.ng.core.DecryptableEntityWithEncryptionConsumers;
import io.harness.ng.core.NGAccessWithEncryptionConsumer;
import io.harness.ng.core.entities.SampleEncryptableSettingImplementation;
import io.harness.secretmanagerclient.NGEncryptedDataMetadata;
import io.harness.secretmanagerclient.NGSecretManagerMetadata;
import io.harness.secretmanagerclient.dto.EncryptedDataDTO;
import io.harness.secretmanagerclient.dto.EncryptedDataMigrationDTO;
import io.harness.secretmanagerclient.dto.GcpKmsConfigDTO;
import io.harness.secretmanagerclient.dto.GcpKmsConfigUpdateDTO;
import io.harness.secretmanagerclient.dto.LocalConfigDTO;
import io.harness.secretmanagerclient.dto.SecretTextDTO;
import io.harness.secretmanagerclient.dto.SecretTextUpdateDTO;
import io.harness.secretmanagerclient.dto.VaultConfigDTO;
import io.harness.secretmanagerclient.dto.VaultConfigUpdateDTO;
import io.harness.secretmanagerclient.dto.awskms.AwsKmsConfigDTO;
import io.harness.secretmanagerclient.dto.awskms.AwsKmsConfigUpdateDTO;
import io.harness.secretmanagerclient.dto.awskms.AwsKmsCredentialSpecConfig;
import io.harness.secretmanagerclient.dto.awskms.AwsKmsIamCredentialConfig;
import io.harness.secretmanagerclient.dto.awskms.AwsKmsManualCredentialConfig;
import io.harness.secretmanagerclient.dto.awskms.AwsKmsStsCredentialConfig;
import io.harness.secretmanagerclient.dto.awskms.BaseAwsKmsConfigDTO;
import io.harness.secretmanagerclient.dto.awssecretmanager.AwsSMConfigDTO;
import io.harness.secretmanagerclient.dto.awssecretmanager.AwsSMConfigUpdateDTO;
import io.harness.secretmanagerclient.dto.awssecretmanager.AwsSMCredentialSpecConfig;
import io.harness.secretmanagerclient.dto.awssecretmanager.AwsSMIamRoleCredentialConfig;
import io.harness.secretmanagerclient.dto.awssecretmanager.AwsSMManualCredentialConfig;
import io.harness.secretmanagerclient.dto.awssecretmanager.AwsSMStsCredentialConfig;
import io.harness.secretmanagerclient.dto.awssecretmanager.BaseAwsSMConfigDTO;
import io.harness.secretmanagerclient.dto.azureblob.AzureBlobConfigDTO;
import io.harness.secretmanagerclient.dto.azureblob.AzureBlobConfigUpdateDTO;
import io.harness.secretmanagerclient.dto.azurekeyvault.AzureKeyVaultConfigDTO;
import io.harness.secretmanagerclient.dto.azurekeyvault.AzureKeyVaultConfigUpdateDTO;
import io.harness.serializer.KryoRegistrar;

import com.esotericsoftware.kryo.Kryo;

@OwnedBy(PL)
public class SecretManagerClientKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    kryo.register(NGSecretManagerMetadata.class, 543210);
    kryo.register(NGEncryptedDataMetadata.class, 543211);
    kryo.register(SecretTextDTO.class, 543212);
    kryo.register(SecretTextUpdateDTO.class, 543213);

    kryo.register(EncryptedDataDTO.class, 543216);
    kryo.register(VaultConfigDTO.class, 543217);
    kryo.register(LocalConfigDTO.class, 543218);
    kryo.register(VaultConfigUpdateDTO.class, 543219);
    kryo.register(GcpKmsConfigDTO.class, 543220);
    kryo.register(GcpKmsConfigUpdateDTO.class, 543221);

    kryo.register(SSHTaskParams.class, 543225);
    kryo.register(WinRmTaskParams.class, 543526);

    kryo.register(SampleEncryptableSettingImplementation.class, 54322);
    kryo.register(NGAccessWithEncryptionConsumer.class, 54323);
    kryo.register(DecryptableEntityWithEncryptionConsumers.class, 54326);
    kryo.register(AwsKmsConfigDTO.class, 643283);
    kryo.register(AwsKmsConfigUpdateDTO.class, 643284);
    kryo.register(BaseAwsKmsConfigDTO.class, 543287);

    kryo.register(AwsKmsCredentialSpecConfig.class, 543295);
    kryo.register(AwsKmsIamCredentialConfig.class, 543296);
    kryo.register(AwsKmsManualCredentialConfig.class, 543297);
    kryo.register(AwsKmsStsCredentialConfig.class, 543298);

    kryo.register(EncryptedDataMigrationDTO.class, 549966);
    kryo.register(AzureKeyVaultConfigDTO.class, 543299);
    kryo.register(AzureKeyVaultConfigUpdateDTO.class, 543300);

    kryo.register(AwsSMConfigDTO.class, 643301);
    kryo.register(AwsSMConfigUpdateDTO.class, 643302);
    kryo.register(BaseAwsSMConfigDTO.class, 643303);

    kryo.register(AwsSMCredentialSpecConfig.class, 643304);
    kryo.register(AwsSMManualCredentialConfig.class, 643305);
    kryo.register(AwsSMIamRoleCredentialConfig.class, 643306);
    kryo.register(AwsSMStsCredentialConfig.class, 643307);

    kryo.register(AzureBlobConfigDTO.class, 643300);
    kryo.register(AzureBlobConfigUpdateDTO.class, 743300);
  }
}
