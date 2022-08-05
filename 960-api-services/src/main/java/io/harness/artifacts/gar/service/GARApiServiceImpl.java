package io.harness.artifacts.gar.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.artifacts.docker.service.DockerRegistryServiceImpl.isSuccessful;
import static io.harness.exception.WingsException.USER;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.comparator.BuildDetailsInternalComparatorDescending;
import io.harness.artifacts.gar.GarRestClient;
import io.harness.artifacts.gar.beans.GarInternalConfig;
import io.harness.artifacts.gar.beans.GarPackageVersionResponse;
import io.harness.artifacts.gar.beans.GarTags;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.expression.RegexFunctor;
import io.harness.network.Http;

import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class GARApiServiceImpl implements GarApiService {
  private static final int Page_Size = 50;
  private GarRestClient getGarRestClient(GarInternalConfig garinternalConfig) {
    String url = getUrl();
    OkHttpClient okHttpClient = Http.getOkHttpClient(url, garinternalConfig.isCertValidationRequired());
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
  public List<BuildDetailsInternal> getBuilds(
      GarInternalConfig garinternalConfig, String versionRegex, int maxNumberOfBuilds) {
    String project = garinternalConfig.getProject();
    String region = garinternalConfig.getRegion();
    String repositories = garinternalConfig.getRepositoryName();
    String pkg = garinternalConfig.getPkg();
    try {
      GarRestClient garRestClient = getGarRestClient(garinternalConfig);
      return paginate(garinternalConfig, garRestClient, versionRegex, maxNumberOfBuilds);
    } catch (IOException ie) {
      return emptyList(); // todo @vivek
    }
  }

  @Override
  public BuildDetailsInternal getLastSuccessfulBuildFromRegex(
      GarInternalConfig garinternalConfig, String versionRegex) {
    List<BuildDetailsInternal> builds = getBuilds(garinternalConfig, versionRegex, garinternalConfig.getMaxBuilds());
    return builds.get(0);
  }

  @Override
  public BuildDetailsInternal verifyBuildNumber(GarInternalConfig garinternalConfig, String version) {
    String project = garinternalConfig.getProject();
    String region = garinternalConfig.getRegion();
    String repositories = garinternalConfig.getRepositoryName();
    String pkg = garinternalConfig.getPkg();
    try {
      GarRestClient garRestClient = getGarRestClient(garinternalConfig);
      Response<GarTags> response =
          garRestClient.getversioninfo(garinternalConfig.getBearerToken(), project, region, repositories, pkg, version)
              .execute();
      GarTags garTags = response.body();
      return BuildDetailsInternal.builder()
          .uiDisplayName("Tag# " + garTags.getVersion())
          .number(garTags.getVersion())
          .build();
    } catch (IOException ie) {
      // todo @vivek
    }

    return null;
  }

  private List<BuildDetailsInternal> paginate(GarInternalConfig garinternalConfig, GarRestClient garRestClient,
      String versionRegex, int maxNumberOfBuilds) throws IOException {
    List<BuildDetailsInternal> details = Collections.<BuildDetailsInternal>emptyList(); // TODO

    String nextPage = "";
    String project = garinternalConfig.getProject();
    String region = garinternalConfig.getRegion();
    String repositories = garinternalConfig.getRepositoryName(); // TODO
    String pkg = garinternalConfig.getPkg();
    // process rest of pages
    do {
      Response<GarPackageVersionResponse> response = garRestClient
                                                         .listImageTags(garinternalConfig.getBearerToken(), project,
                                                             region, repositories, pkg, Page_Size, nextPage)
                                                         .execute();

      if (!isSuccessful(response)) {
        throw NestedExceptionUtils.hintWithExplanationException("Unable to fetch the versions for the package",
            "Check if the package exists and if the permissions are scoped for the authenticated user",
            new InvalidArtifactServerException(response.message(), USER));
      }

      GarPackageVersionResponse page = response.body();
      List<BuildDetailsInternal> pageDetails = processPage(page, versionRegex);
      if (details.isEmpty()) {
        details = pageDetails;
      } else {
        details.addAll(pageDetails);
      }

      if (details.size() >= maxNumberOfBuilds || page == null || StringUtils.isBlank(page.getNextPageToken())) {
        break;
      } // TODO -1

      nextPage = StringUtils.isBlank(page.getNextPageToken()) ? null : page.getNextPageToken();
    } while (StringUtils.isNotBlank(nextPage));

    return details.stream().limit(maxNumberOfBuilds).collect(Collectors.toList());
  }
  private List<BuildDetailsInternal> processPage(GarPackageVersionResponse tagsPage, String versionRegex) {
    if (tagsPage != null && EmptyPredicate.isNotEmpty(tagsPage.getTags())) {
      int index = tagsPage.getTags().get(0).getName().lastIndexOf("/");
      List<BuildDetailsInternal> buildDetails =
          tagsPage.getTags()
              .stream()
              .map(tag -> {
                String tagFinal = tag.getName().substring(index + 1);
                Map<String, String> metadata = new HashMap();
                metadata.put(ArtifactMetadataKeys.PACKAGE, tagFinal);
                metadata.put(ArtifactMetadataKeys.TAG, tagFinal);
                return BuildDetailsInternal.builder()
                    .uiDisplayName("Tag# " + tagFinal)
                    .number(tagFinal)
                    .metadata(metadata)
                    .build();
              })
              .filter(build
                  -> StringUtils.isBlank(versionRegex) || new RegexFunctor().match(versionRegex, build.getNumber()))
              .collect(toList());

      return buildDetails.stream().sorted(new BuildDetailsInternalComparatorDescending()).collect(toList());

    } else {
      if (tagsPage == null) {
        log.warn("Google Artifact Registry Package version response was null.");
      } else {
        log.warn("Google Artifact Registry Package version response had an empty or missing tag list.");
      }
      return Collections.emptyList();
    } // TODO
  }
}
