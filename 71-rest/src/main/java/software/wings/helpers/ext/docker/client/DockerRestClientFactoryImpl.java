package software.wings.helpers.ext.docker.client;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import com.google.inject.Singleton;

import io.harness.annotations.dev.OwnedBy;
import io.harness.network.Http;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import software.wings.beans.DockerConfig;
import software.wings.helpers.ext.docker.DockerRegistryRestClient;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class DockerRestClientFactoryImpl implements DockerRestClientFactory {
  @Override
  public DockerRegistryRestClient getDockerRegistryRestClient(DockerConfig dockerConfig) {
    OkHttpClient okHttpClient = Http.getUnsafeOkHttpClient(dockerConfig.getDockerRegistryUrl());
    Retrofit retrofit = new Retrofit.Builder()
                            .client(okHttpClient)
                            .baseUrl(dockerConfig.getDockerRegistryUrl())
                            .addConverterFactory(JacksonConverterFactory.create())
                            .build();
    return retrofit.create(DockerRegistryRestClient.class);
  }
}