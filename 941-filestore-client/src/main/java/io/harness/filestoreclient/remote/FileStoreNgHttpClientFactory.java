/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestoreclient.remote;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.remote.client.AbstractHttpClientFactory;
import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

@OwnedBy(CDP)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Singleton
public class FileStoreNgHttpClientFactory extends AbstractHttpClientFactory implements Provider<FileStoreClient> {
  public FileStoreNgHttpClientFactory(ServiceHttpClientConfig fileStoreClientConfig, String serviceSecret,
      ServiceTokenGenerator tokenGenerator, KryoConverterFactory kryoConverterFactory, String clientId,
      ClientMode clientMode) {
    super(fileStoreClientConfig, serviceSecret, tokenGenerator, kryoConverterFactory, clientId, false, clientMode);
  }

  @Override
  public FileStoreClient get() {
    return getUnsafeOkRetrofit().create(FileStoreClient.class);
  }
}
