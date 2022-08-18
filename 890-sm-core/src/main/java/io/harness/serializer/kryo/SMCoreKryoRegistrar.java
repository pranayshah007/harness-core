/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.kryo;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegatetasks.DeleteSecretTaskParameters;
import io.harness.delegatetasks.DeleteSecretTaskResponse;
import io.harness.delegatetasks.EncryptSecretTaskParameters;
import io.harness.delegatetasks.EncryptSecretTaskResponse;
import io.harness.delegatetasks.FetchSecretTaskParameters;
import io.harness.delegatetasks.FetchSecretTaskResponse;
import io.harness.delegatetasks.NGAzureKeyVaultFetchEngineResponse;
import io.harness.delegatetasks.NGAzureKeyVaultFetchEngineTaskParameters;
import io.harness.delegatetasks.NGVaultFetchEngineTaskResponse;
import io.harness.delegatetasks.NGVaultRenewalAppRoleTaskResponse;
import io.harness.delegatetasks.NGVaultRenewalTaskParameters;
import io.harness.delegatetasks.NGVaultRenewalTaskResponse;
import io.harness.delegatetasks.UpsertSecretTaskParameters;
import io.harness.delegatetasks.UpsertSecretTaskResponse;
import io.harness.delegatetasks.UpsertSecretTaskType;
import io.harness.delegatetasks.ValidateSecretManagerConfigurationTaskParameters;
import io.harness.delegatetasks.ValidateSecretManagerConfigurationTaskResponse;
import io.harness.delegatetasks.ValidateSecretReferenceTaskParameters;
import io.harness.delegatetasks.ValidateSecretReferenceTaskResponse;
import io.harness.ng.core.entities.NGEncryptedData;
import io.harness.serializer.KryoRegistrar;

import com.esotericsoftware.kryo.Kryo;

@OwnedBy(PL)
public class SMCoreKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    kryo.register(ValidateSecretReferenceTaskParameters.class, 150001);
    kryo.register(UpsertSecretTaskParameters.class, 150002);
    kryo.register(FetchSecretTaskParameters.class, 150003);
    kryo.register(EncryptSecretTaskParameters.class, 150004);
    kryo.register(DeleteSecretTaskParameters.class, 150005);
    kryo.register(UpsertSecretTaskResponse.class, 150006);
    kryo.register(FetchSecretTaskResponse.class, 150007);
    kryo.register(EncryptSecretTaskResponse.class, 150008);
    kryo.register(DeleteSecretTaskResponse.class, 150009);
    kryo.register(ValidateSecretReferenceTaskResponse.class, 150010);
    kryo.register(UpsertSecretTaskType.class, 15011);
    kryo.register(ValidateSecretManagerConfigurationTaskParameters.class, 15015);
    kryo.register(ValidateSecretManagerConfigurationTaskResponse.class, 15016);
    kryo.register(NGEncryptedData.class, 15017);
    kryo.register(NGVaultRenewalTaskParameters.class, 150018);
    kryo.register(NGVaultRenewalTaskResponse.class, 150019);
    kryo.register(NGVaultFetchEngineTaskResponse.class, 150020);
    kryo.register(NGVaultRenewalAppRoleTaskResponse.class, 150021);
    kryo.register(NGAzureKeyVaultFetchEngineTaskParameters.class, 150022);
    kryo.register(NGAzureKeyVaultFetchEngineResponse.class, 150023);
  }
}
