package io.harness.artifacts.gar.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.network.Http.getOkHttpClientBuilder;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.comparator.BuildDetailsInternalComparatorAscending;
import io.harness.artifacts.gar.GarRestClient;
import io.harness.artifacts.gar.beans.GarInternalConfig;
import io.harness.artifacts.gar.beans.GarPackageVersionResponse;
import io.harness.network.Http;

import com.google.inject.Singleton;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class GARApiServiceImpl implements GarApiService {
  private static final int CONNECT_TIMEOUT = 5; // TODO:: read from config
  private GarRestClient getGarRestClient() {
    String url = getUrl();
    OkHttpClient okHttpClient = getOkHttpClientBuilder()
                                    .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                                    .proxy(Http.checkAndGetNonProxyIfApplicable(url))
                                    .build();
    Retrofit retrofit = new Retrofit.Builder()
                            .client(okHttpClient)
                            .baseUrl(url)
                            .addConverterFactory(JacksonConverterFactory.create())
                            .build();
    return retrofit.create(GarRestClient.class);
  }
  public String getUrl() {
    return "https://"
        + "artifactregistry.googleapis.com";
  }

  @Override
  public List<BuildDetailsInternal> getBuilds(GarInternalConfig garinternalConfig, int maxNumberOfBuilds) {
    String project = garinternalConfig.getProject();
    String region = garinternalConfig.getRegion();
    String repositories = garinternalConfig.getRepositoryName();
    String pkg = garinternalConfig.getPkg();
    try {
      Response<GarPackageVersionResponse> response =
          getGarRestClient()
              .listImageTags(garinternalConfig.getBearerToken(), project, region, repositories, pkg)
              .execute();
      return processBuildResponse(project, region, repositories, pkg, response.body());
    } catch (IOException ie) {
      return null;
    }
  }
  private List<BuildDetailsInternal> processBuildResponse(
      String project, String region, String repositories, String pkg, GarPackageVersionResponse response) {
    if (response != null && response.getTags() != null) {
      int index = response.getTags().get(0).getName().lastIndexOf("/");

      List<BuildDetailsInternal> buildDetails =
          response.getTags()
              .stream()
              .map(tag -> {
                Map<String, String> metadata = new HashMap();
                metadata.put(ArtifactMetadataKeys.PACKAGE, tag.getName().substring(index + 1));
                metadata.put(ArtifactMetadataKeys.TAG, tag.getName().substring(index + 1));
                return BuildDetailsInternal.builder()
                    .uiDisplayName("Tag# " + tag.getName().substring(index + 1))
                    .number(tag.getName().substring(index + 1))
                    .metadata(metadata)
                    .build();
              })
              .collect(toList());
      // Sorting at build tag for docker artifacts.
      return buildDetails.stream().sorted(new BuildDetailsInternalComparatorAscending()).collect(toList());
    }
    return emptyList();
  }
}
