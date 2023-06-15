/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.decryption.delegate.serializer.kryo;

import io.harness.azure.AzureEnvironmentType;
import io.harness.security.encryption.AdditionalMetadata;
import io.harness.security.encryption.EncryptedDataParams;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionType;
import io.harness.serializer.KryoRegistrar;

import com.esotericsoftware.kryo.Kryo;

/**
 * This registrar adds bindings for classes which should probably belong to SecretConfigKryoRegistrar, but are more
 * widely used than just secret providers and would be risky moving them.
 *
 * @see io.harness.serializer.kryo.SecretConfigKryoRegistrar
 */
public class SecretProviderKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(final Kryo kryo) {
    //    kryo.register(LinkedHashSet.class, 100030); // Defined in ConnectorBeans as well.
    kryo.register(EncryptionType.class, 5123);
    kryo.register(EncryptedRecordData.class, 1401);
    kryo.register(EncryptedDataParams.class, 1413);
    kryo.register(AdditionalMetadata.class, 72101);

    kryo.register(AzureEnvironmentType.class, 1436);
  }
}
