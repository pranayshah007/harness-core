/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.githubpackages.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.exception.WingsException.USER;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifacts.comparator.BuildDetailsComparatorDescending;
import io.harness.artifacts.githubpackages.beans.GithubPackagesInternalConfig;
import io.harness.artifacts.githubpackages.beans.GithubPackagesVersion;
import io.harness.artifacts.githubpackages.beans.GithubPackagesVersionsResponse;
import io.harness.artifacts.githubpackages.client.GithubPackagesRestClient;
import io.harness.artifacts.githubpackages.client.GithubPackagesRestClientFactory;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.exception.runtime.GithubPackagesServerRuntimeException;

import software.wings.common.BuildDetailsComparatorAscending;
import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Credentials;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Response;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class GithubPackagesRegistryServiceImpl implements GithubPackagesRegistryService {
  @Inject private GithubPackagesRestClientFactory githubPackagesRestClientFactory;

  private final String USERNAME_PASSWORD = "UsernamePassword";
  private final String USERNAME_TOKEN = "UsernameToken";
  private final String OAUTH = "OAuth";

  @Override
  public List<BuildDetails> getBuilds(GithubPackagesInternalConfig githubPackagesInternalConfig, String packageName,
      String packageType, int maxNoOfVersionsPerPackage) {
    List<BuildDetails> buildDetails;

    try {
      buildDetails = getBuildDetails(githubPackagesInternalConfig, packageName, packageType);
    } catch (GithubPackagesServerRuntimeException ex) {
      throw ex;
    } catch (Exception e) {
      throw NestedExceptionUtils.hintWithExplanationException("Could not fetch versions for the package",
          "Check if the package exists and if the permissions are scoped for the authenticated user",
          new ArtifactServerException(ExceptionUtils.getMessage(e), e, USER));
    }

    // Version Regex Filtering - TODO

    return buildDetails.stream().sorted(new BuildDetailsComparatorAscending()).collect(toList());
  }

  @Override
  public BuildDetails getLastSuccessfulBuildFromRegex(GithubPackagesInternalConfig githubPackagesInternalConfig,
      String packageName, String packageType, String versionRegex) {
    List<BuildDetails> buildDetails;

    try {
      buildDetails = getBuildDetails(githubPackagesInternalConfig, packageName, packageType);
    } catch (GithubPackagesServerRuntimeException ex) {
      throw ex;
    } catch (Exception e) {
      throw NestedExceptionUtils.hintWithExplanationException("Could not fetch the version for the package",
          "Check if the package and the version exists and if the permissions are scoped for the authenticated user",
          new ArtifactServerException(ExceptionUtils.getMessage(e), e, USER));
    }

    return buildDetails.get(0);
  }

  @Override
  public BuildDetails getBuild(GithubPackagesInternalConfig githubPackagesInternalConfig, String packageName,
      String packageType, String version) {
    BuildDetails build = null;

    try {
      build = getBuildForAVersion(githubPackagesInternalConfig, packageName, packageType, version);
    } catch (GithubPackagesServerRuntimeException ex) {
      throw ex;
    } catch (Exception e) {
      throw NestedExceptionUtils.hintWithExplanationException("Could not fetch the version for the package",
          "Check if the package and the version exists and if the permissions are scoped for the authenticated user",
          new ArtifactServerException(ExceptionUtils.getMessage(e), e, USER));
    }

    return build;
  }

  private BuildDetails getBuildForAVersion(GithubPackagesInternalConfig githubPackagesInternalConfig,
      String packageName, String packageType, String version) throws IOException {
    GithubPackagesRestClient githubPackagesRestClient =
        githubPackagesRestClientFactory.getGithubPackagesRestClient(githubPackagesInternalConfig);

    String authType = githubPackagesInternalConfig.getAuthMechanism();

    String basicAuthHeader = "";

    if (authType == USERNAME_PASSWORD) {
      basicAuthHeader =
          Credentials.basic(githubPackagesInternalConfig.getUsername(), githubPackagesInternalConfig.getPassword());
    } else if (authType == USERNAME_TOKEN) {
      basicAuthHeader = "token " + githubPackagesInternalConfig.getToken();
    } else if (authType == OAUTH) {
      basicAuthHeader =
          Credentials.basic(githubPackagesInternalConfig.getUsername(), githubPackagesInternalConfig.getToken());
    }

    Integer versionId = Integer.parseInt(version);

    Response<GithubPackagesVersion> response =
        githubPackagesRestClient.getVersion(basicAuthHeader, packageName, packageType, versionId).execute();

    GithubPackagesVersion githubPackagesVersion = response.body();

    BuildDetails build = new BuildDetails();

    build.setUiDisplayName("Tag# " + githubPackagesVersion.getVersion());
    build.setNumber(githubPackagesVersion.getVersion());
    build.setBuildDisplayName(packageName);

    return build;
  }

  private List<BuildDetails> getBuildDetails(GithubPackagesInternalConfig githubPackagesInternalConfig,
      String packageName, String packageType) throws IOException {
    GithubPackagesRestClient githubPackagesRestClient =
        githubPackagesRestClientFactory.getGithubPackagesRestClient(githubPackagesInternalConfig);

    String authType = githubPackagesInternalConfig.getAuthMechanism();

    String basicAuthHeader = "";

    if (authType == USERNAME_PASSWORD) {
      basicAuthHeader =
          Credentials.basic(githubPackagesInternalConfig.getUsername(), githubPackagesInternalConfig.getPassword());
    } else if (authType == USERNAME_TOKEN) {
      basicAuthHeader = "token " + githubPackagesInternalConfig.getToken();
    } else if (authType == OAUTH) {
      basicAuthHeader =
          Credentials.basic(githubPackagesInternalConfig.getUsername(), githubPackagesInternalConfig.getToken());
    }

    List<BuildDetails> buildDetails = new ArrayList<>();

    Response<GithubPackagesVersionsResponse> response =
        githubPackagesRestClient.listVersionsForPackages(basicAuthHeader, packageName, packageType).execute();

    if (!isSuccessful(response)) {
      throw NestedExceptionUtils.hintWithExplanationException("Unable to fetch the versions for the package",
          "Check if the package exists and if the permissions are scoped for the authenticated user",
          new InvalidArtifactServerException(response.message(), USER));
    }

    buildDetails = processPage(response.body(), packageName);

    return buildDetails;
  }

  private List<BuildDetails> processPage(GithubPackagesVersionsResponse response, String packageName) {
    if (response != null && EmptyPredicate.isNotEmpty(response.getVersions())) {
      int index = response.getVersions().get(0).getName().lastIndexOf("/");

      List<BuildDetails> buildDetails = response.getVersions()
                                            .stream()
                                            .map(version -> {
                                              String finalVersion = version.getName().substring(index + 1);
                                              Map<String, String> metadata = new HashMap();
                                              metadata.put(ArtifactMetadataKeys.IMAGE, packageName);
                                              metadata.put(ArtifactMetadataKeys.TAG, finalVersion);

                                              BuildDetails build = new BuildDetails();
                                              build.setUiDisplayName("Tag# " + finalVersion);
                                              build.setNumber(finalVersion);
                                              build.setBuildDisplayName(packageName);

                                              return build;
                                            })
                                            .collect(toList());

      return buildDetails.stream().sorted(new BuildDetailsComparatorDescending()).collect(toList());

    } else {
      if (response == null) {
        log.warn("Github Packages Version response was null.");
      } else {
        log.warn("Github Packages Version response was empty.");
      }
      return null;
    }
  }

  public static boolean isSuccessful(Response<?> response) {
    if (response == null) {
      throw new InvalidArtifactServerException("Null response found", USER);
    }

    if (response.isSuccessful()) {
      return true;
    }

    log.error("Request not successful. Reason: {}", response);
    int code = response.code();
    switch (code) {
      case 404:
      case 400:
        return false;
      case 401:
        throw unauthorizedException();
      default:
        throw new InvalidArtifactServerException(StringUtils.isNotBlank(response.message())
                ? response.message()
                : String.format("Server responded with the following error code - %d", code),
            USER);
    }
  }

  public static WingsException unauthorizedException() {
    return NestedExceptionUtils.hintWithExplanationException("Update the credentials",
        "Check if the provided credentials are correct",
        new InvalidArtifactServerException("Invalid Github Packages Registry credentials", USER));
  }
}
