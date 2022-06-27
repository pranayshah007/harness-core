package io.harness.filestoreclient.module;

import io.harness.filestoreclient.remote.FileStoreClientFactoryForCg;
import io.harness.filestoreclient.remote.FileStoreClient;
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
