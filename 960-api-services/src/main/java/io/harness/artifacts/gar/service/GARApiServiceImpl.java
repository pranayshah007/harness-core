package io.harness.artifacts.gar.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.exception.WingsException.USER;

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
import io.harness.exception.ArtifactServerException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.expression.RegexFunctor;
import io.harness.network.Http;

import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
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
  private static final int PAGESIZE = 30;
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
    return "https://artifactregistry.googleapis.com";
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
    } catch (IOException e) {
      throw NestedExceptionUtils.hintWithExplanationException("Could not fetch versions for the package",
          "Check if the package exists and if the permissions are scoped for the authenticated user",
          new ArtifactServerException(ExceptionUtils.getMessage(e), e, WingsException.USER));
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
      int index = garTags.getName().lastIndexOf("/");
      String tagFinal = garTags.getName().substring(index + 1);
      Map<String, String> metadata = new HashMap();
      String registryHostname = String.format("%s-docker.pkg.dev", region);
      String image = String.format("%s-docker.pkg.dev/%s/%s/%s:%s", region, project, repositories, pkg, tagFinal);
      metadata.put(ArtifactMetadataKeys.IMAGE, image);
      metadata.put("registryHostname", registryHostname);
      return BuildDetailsInternal.builder()
          .uiDisplayName("Tag# " + tagFinal)
          .number(tagFinal)
          .metadata(metadata)
          .build();
    } catch (IOException e) {
      throw NestedExceptionUtils.hintWithExplanationException("Unable to fetch the given tag for the image",
          "The tag provided for the image may be incorrect.",
          new ArtifactServerException(ExceptionUtils.getMessage(e), e, USER));
    }
  }

  private List<BuildDetailsInternal> paginate(GarInternalConfig garinternalConfig, GarRestClient garRestClient,
      String versionRegex, int maxNumberOfBuilds) throws WingsException, IOException {
    List<BuildDetailsInternal> details = new ArrayList<>();

    String nextPage = "";
    String project = garinternalConfig.getProject();
    String region = garinternalConfig.getRegion();
    String repositoryName = garinternalConfig.getRepositoryName();
    String pkg = garinternalConfig.getPkg();
    // process rest of pages
    do {
      Response<GarPackageVersionResponse> response = garRestClient
                                                         .listImageTags(garinternalConfig.getBearerToken(), project,
                                                             region, repositoryName, pkg, PAGESIZE, nextPage)
                                                         .execute();

      if (!isSuccessful(response)) {
        throw NestedExceptionUtils.hintWithExplanationException(
            "GOOGLE ARTIFACT REGISTRY : Unable to fetch the versions for the package",
            "Check if the package exists and if the permissions are scoped for the authenticated user",
            new InvalidArtifactServerException(response.message(), USER));
      }

      GarPackageVersionResponse page = response.body();
      List<BuildDetailsInternal> pageDetails = processPage(page, versionRegex, garinternalConfig);
      details.addAll(pageDetails);

      if (details.size() >= maxNumberOfBuilds || page == null || StringUtils.isBlank(page.getNextPageToken())) {
        break;
      }

      nextPage = StringUtils.isBlank(page.getNextPageToken()) ? null : page.getNextPageToken();
    } while (StringUtils.isNotBlank(nextPage));

    return details.stream().limit(maxNumberOfBuilds).collect(Collectors.toList());
  }

  private boolean isSuccessful(Response<GarPackageVersionResponse> response) {
    if (response == null) {
      throw NestedExceptionUtils.hintWithExplanationException("GOOGLE ARTIFACT REGISTRY : Response Is Null",
          "Check Whether Artifact exists or not",
          new InvalidArtifactServerException(response.errorBody().toString(), USER));
    }

    if (response.isSuccessful()) {
      return true;
    }

    log.error("Request not successful. Reason: {}", response);
    int code = response.code();
    switch (code) {
      case 404:
        throw NestedExceptionUtils.hintWithExplanationException(
            "Please Check Project,RepositoryName,Package,Region fields",
            "Google Artifact Registry: Check Values of Project,RepositoryName,Package,Region Field",
            new InvalidArtifactServerException(response.errorBody().toString(), USER));
      case 400:
        return false;
      case 401:
        throw NestedExceptionUtils.hintWithExplanationException(
            "Google Artifact Registry: Connector provided Is not Having Artifact Registry Reader permission",
            "Check connector's permission and credentials",
            new InvalidArtifactServerException(response.errorBody().toString(), USER));
      default:
        throw NestedExceptionUtils.hintWithExplanationException("Google Artifact Registry", "Google Artifact Registry",
            new InvalidArtifactServerException(StringUtils.isNotBlank(response.errorBody().toString())
                    ? response.errorBody().toString()
                    : String.format("Server responded with the following error code - %d", code),
                USER));
    }
  }

  private List<BuildDetailsInternal> processPage(
      GarPackageVersionResponse tagsPage, String versionRegex, GarInternalConfig garinternalConfig) {
    if (tagsPage != null && EmptyPredicate.isNotEmpty(tagsPage.getTags())) {
      int index = tagsPage.getTags().get(0).getName().lastIndexOf("/");
      List<BuildDetailsInternal> buildDetails =
          tagsPage.getTags()
              .stream()
              .map(tag -> {
                String tagFinal = tag.getName().substring(index + 1);
                Map<String, String> metadata = new HashMap();
                metadata.put(ArtifactMetadataKeys.artifactPackage, tagFinal);
                metadata.put(ArtifactMetadataKeys.artifactPackage, garinternalConfig.getPkg());
                metadata.put(ArtifactMetadataKeys.artifactProject, garinternalConfig.getProject());
                metadata.put(ArtifactMetadataKeys.artifactRepositoryName, garinternalConfig.getRepositoryName());
                metadata.put(ArtifactMetadataKeys.artifactRegion, garinternalConfig.getRegion());
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
    }
  }
}
