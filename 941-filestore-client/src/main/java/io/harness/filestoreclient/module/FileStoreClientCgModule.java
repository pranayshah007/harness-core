/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestoreclient.module;

import io.harness.filestoreclient.remote.FileStoreClient;
import io.harness.filestoreclient.remote.FileStoreClientFactoryForCg;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;

public class FileStoreClientCgModule extends AbstractModule {
  private String baseUrl;
  private String serviceSecret;
  private ServiceTokenGenerator serviceTokenGenerator;

  public FileStoreClientCgModule(String baseUrl, String serviceSecret, ServiceTokenGenerator serviceTokenGenerator) {
    this.baseUrl = baseUrl;
    this.serviceSecret = serviceSecret;
    this.serviceTokenGenerator = serviceTokenGenerator;
  }

  @Provides
  public FileStoreClientFactoryForCg secretManagerHttpClientFactory(KryoConverterFactory kryoConverterFactory) {
    return new FileStoreClientFactoryForCg(baseUrl, serviceSecret, serviceTokenGenerator);
  }

  @Override
  protected void configure() {
    bind(FileStoreClient.class).toProvider(FileStoreClientFactoryForCg.class).in(Scopes.SINGLETON);
  }
}
