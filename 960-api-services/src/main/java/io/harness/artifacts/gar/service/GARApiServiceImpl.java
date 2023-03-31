/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

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
import io.harness.beans.ArtifactMetaInfo;
import io.harness.beans.ArtifactMetaInfo.ArtifactMetaInfoBuilder;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.HintException;
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
  // For now google api is supporting 500 page size, but in future they may decrease api response page limit.
  private static final int PAGESIZE = 500;
  private static final String COULD_NOT_FETCH_IMAGE_MANIFEST = "Could not fetch image manifest";

  private GarRestClient getGarRestClient(GarInternalConfig garinternalConfig) {
    String url = getUrl();
    return getGarRestClientHelper(garinternalConfig, url);
  }

  private GarRestClient getGarRestClientDockerRegistryAPI(GarInternalConfig garinternalConfig) {
    String url = getGarRestClientDockerRegistryAPIUrl(garinternalConfig.getRegion());
    return getGarRestClientHelper(garinternalConfig, url);
  }

  private GarRestClient getGarRestClientHelper(GarInternalConfig garinternalConfig, String url) {
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

  public String getGarRestClientDockerRegistryAPIUrl(String region) {
    return String.format("https://%s-docker.pkg.dev", region);
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
          "Please check if the package exists and if the permissions are scoped for the authenticated user",
          new ArtifactServerException(ExceptionUtils.getMessage(e), e, WingsException.USER));
    }
  }

  @Override
  public BuildDetailsInternal getLastSuccessfulBuildFromRegex(
      GarInternalConfig garinternalConfig, String versionRegex) {
    List<BuildDetailsInternal> builds = getBuilds(garinternalConfig, versionRegex, garinternalConfig.getMaxBuilds());
    if (builds.isEmpty()) {
      throw NestedExceptionUtils.hintWithExplanationException("Could not fetch versions for the package",
          "Please check versionRegex Provided",
          new InvalidArtifactServerException("No versions found with versionRegex provided for the given package"));
    }
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
      if (response == null) {
        throw NestedExceptionUtils.hintWithExplanationException("Response Is Null",
            "Please Check Whether Artifact exists or not",
            new InvalidArtifactServerException(response.errorBody().toString(), USER));
      }
      if (!response.isSuccessful()) {
        log.error("Request not successful. Reason: {}", response);
        if (!isSuccessful(response.code(), response.errorBody().toString())) {
          throw NestedExceptionUtils.hintWithExplanationException("Unable to fetch the versions for the package",
              "Please check region field", new InvalidArtifactServerException(response.message(), USER));
        }
      }
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

      if (response == null) {
        throw NestedExceptionUtils.hintWithExplanationException("Response Is Null",
            "Please Check Whether Artifact exists or not",
            new InvalidArtifactServerException(response.errorBody().toString(), USER));
      }
      if (!response.isSuccessful()) {
        log.error("Request not successful. Reason: {}", response);
        if (!isSuccessful(response.code(), response.errorBody().toString())) {
          throw NestedExceptionUtils.hintWithExplanationException("Unable to fetch the versions for the package",
              "Please check region field", new InvalidArtifactServerException(response.message(), USER));
        }
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

  private boolean isSuccessful(int code, String errormessage) {
    switch (code) {
      case 404:
        throw new HintException(
            "Please provide valid values for region, project, repository, package and version fields.");
      case 400:
        return false;
      case 401:
        throw NestedExceptionUtils.hintWithExplanationException(
            "The connector provided does not have sufficient privileges to access Google artifact registry",
            "Please check connector's permission and credentials",
            new InvalidArtifactServerException(errormessage, USER));
      case 403:
        throw new HintException("Connector provided does not have access to project. Please check the project field.");
      default:
        throw NestedExceptionUtils.hintWithExplanationException(
            "The server could have failed authenticate ,Please check your credentials",
            " Server responded with the following error code",
            new InvalidArtifactServerException(StringUtils.isNotBlank(errormessage)
                    ? errormessage
                    : String.format("Server responded with the following error code - %d", code),
                USER));
    }
  }
  public List<BuildDetailsInternal> processPage(
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
                metadata.put(ArtifactMetadataKeys.IMAGE, getImageName(garinternalConfig, tagFinal));
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

  @Override
  public ArtifactMetaInfo getArtifactMetaInfo(GarInternalConfig garInternalConfig, String version) {
    String imageName = garInternalConfig.getPkg();
    ArtifactMetaInfoBuilder artifactMetaInfoBuilder = ArtifactMetaInfo.builder();
    try {
      GarRestClient garRestClient = getGarRestClientDockerRegistryAPI(garInternalConfig);
      Response<GarPackageVersionResponse> response =
          garRestClient
              .getImageManifest(garInternalConfig.getBearerToken(), garInternalConfig.getProject(),
                  garInternalConfig.getRepositoryName(), imageName, version)
              .execute();
      if (!GARUtils.checkIfResponseNull(response) && response.isSuccessful()) {
        String sha = response.headers().get("Docker-Content-Digest");
        artifactMetaInfoBuilder.shaV2(sha);
      } else if (!GARUtils.checkIfResponseNull(response)) {
        isSuccessful(response.code(), response.errorBody().toString());
      }
    } catch (Exception e) {
      log.error(COULD_NOT_FETCH_IMAGE_MANIFEST, e);
    }
    return artifactMetaInfoBuilder.build();
  }

  private String getImageName(GarInternalConfig garinternalConfig, String tag) {
    return garinternalConfig.getRegion() + '-' + "docker.pkg.dev/" + garinternalConfig.getProject() + "/"
        + garinternalConfig.getRepositoryName() + "/" + garinternalConfig.getPkg() + ":" + tag;
  }
}
