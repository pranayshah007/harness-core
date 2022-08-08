/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.filestore;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.filestore.remote.FileStoreClient;
import io.harness.filestore.remote.FileStoreHttpClientFactory;
import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

@OwnedBy(CDP)
public class FileStoreClientModule extends AbstractModule {
  private static final Object LOCK = new Object();
  private static FileStoreClientModule instance;

  private final ServiceHttpClientConfig fileStoreClientConfig;
  private final String serviceSecret;
  private final String clientId;

  public FileStoreClientModule(ServiceHttpClientConfig fileStoreClientConfig, String serviceSecret, String clientId) {
    this.fileStoreClientConfig = fileStoreClientConfig;
    this.serviceSecret = serviceSecret;
    this.clientId = clientId;
  }

  public static FileStoreClientModule getInstance(
      ServiceHttpClientConfig fileStoreClientConfig, String serviceSecret, String clientId) {
    synchronized (LOCK) {
      if (instance == null) {
        instance = new FileStoreClientModule(fileStoreClientConfig, serviceSecret, clientId);
      }
    }

    return instance;
  }

  @Provides
  @Named("PRIVILEGED")
  private FileStoreHttpClientFactory privilegedFileStoreHttpClientFactory(KryoConverterFactory kryoConverterFactory) {
    return new FileStoreHttpClientFactory(fileStoreClientConfig, serviceSecret, new ServiceTokenGenerator(),
        kryoConverterFactory, clientId, ClientMode.PRIVILEGED);
  }

  @Provides
  @Named("NON_PRIVILEGED")
  private FileStoreHttpClientFactory nonPrivilegedFileStoreHttpClientFactory(
      KryoConverterFactory kryoConverterFactory) {
    return new FileStoreHttpClientFactory(fileStoreClientConfig, serviceSecret, new ServiceTokenGenerator(),
        kryoConverterFactory, clientId, ClientMode.NON_PRIVILEGED);
  }

  @Override
  protected void configure() {
    bind(FileStoreClient.class)
        .annotatedWith(Names.named(ClientMode.PRIVILEGED.name()))
        .toProvider(Key.get(FileStoreHttpClientFactory.class, Names.named(ClientMode.PRIVILEGED.name())))
        .in(Scopes.SINGLETON);
    bind(FileStoreClient.class)
        .toProvider(Key.get(FileStoreHttpClientFactory.class, Names.named(ClientMode.NON_PRIVILEGED.name())))
        .in(Scopes.SINGLETON);
  }
}
