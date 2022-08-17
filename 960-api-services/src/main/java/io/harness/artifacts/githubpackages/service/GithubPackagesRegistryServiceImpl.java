/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.githubpackages.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.githubpackages.beans.GithubPackagesInternalConfig;
import io.harness.artifacts.githubpackages.beans.GithubPackagesVersion;
import io.harness.artifacts.githubpackages.beans.GithubPackagesVersionsResponse;
import io.harness.artifacts.githubpackages.client.GithubPackagesRestClient;
import io.harness.artifacts.githubpackages.client.GithubPackagesRestClientFactory;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.*;
import io.harness.exception.runtime.GithubPackagesServerRuntimeException;

import software.wings.helpers.ext.jenkins.BuildDetails;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
      String packageType, String org, String versionRegex, int maxNoOfVersionsPerPackage) {
    List<BuildDetails> buildDetails;

    try {
      buildDetails = getBuildDetails(githubPackagesInternalConfig, packageName, packageType, org);
    } catch (GithubPackagesServerRuntimeException ex) {
      throw ex;
    } catch (Exception e) {
      throw NestedExceptionUtils.hintWithExplanationException("Could not fetch versions for the package",
          "Check if the package exists and if the permissions are scoped for the authenticated user",
          new ArtifactServerException(ExceptionUtils.getMessage(e), e, USER));
    }

    Pattern pattern = Pattern.compile(versionRegex.replace(".", "\\.").replace("?", ".?").replace("*", ".*?"));

    buildDetails = buildDetails.stream()
                       .filter(build -> !build.getNumber().endsWith("/") && pattern.matcher(build.getNumber()).find())
                       .collect(Collectors.toList());

    return buildDetails;
  }

  @Override
  public BuildDetails getLastSuccessfulBuildFromRegex(GithubPackagesInternalConfig githubPackagesInternalConfig,
      String packageName, String packageType, String versionRegex, String org) {
    List<BuildDetails> buildDetails;

    try {
      buildDetails = getBuildDetails(githubPackagesInternalConfig, packageName, packageType, org);
    } catch (GithubPackagesServerRuntimeException ex) {
      throw ex;
    } catch (Exception e) {
      throw NestedExceptionUtils.hintWithExplanationException("Could not fetch the version for the package",
          "Check if the package and the version exists and if the permissions are scoped for the authenticated user",
          new ArtifactServerException(ExceptionUtils.getMessage(e), e, USER));
    }

    buildDetails = regexFilteringForGetBuilds(versionRegex, buildDetails, packageName);

    if (buildDetails.isEmpty()) {
      throw new InvalidRequestException("No version with matching regex is present");
    }

    return buildDetails.get(0);
  }

  @Override
  public BuildDetails getBuild(GithubPackagesInternalConfig githubPackagesInternalConfig, String packageName,
      String packageType, String version, String org) {
    List<BuildDetails> builds = new ArrayList<>();

    try {
      builds = getBuildDetails(githubPackagesInternalConfig, packageName, packageType, org);
    } catch (GithubPackagesServerRuntimeException ex) {
      throw ex;
    } catch (Exception e) {
      throw NestedExceptionUtils.hintWithExplanationException("Could not fetch the version for the package",
          "Check if the package and the version exists and if the permissions are scoped for the authenticated user",
          new ArtifactServerException(ExceptionUtils.getMessage(e), e, USER));
    }

    return versionFiltering(version, builds, packageName);
  }

  private List<BuildDetails> regexFilteringForGetBuilds(
      String versionRegex, List<BuildDetails> builds, String packageName) {
    Pattern pattern = Pattern.compile(versionRegex.replace(".", "\\.").replace("?", ".?").replace("*", ".*?"));

    for (BuildDetails build : builds) {
      Map<String, String> map = build.getMetadata();

      List<String> keys = map.keySet().stream().collect(Collectors.toList());

      keys =
          keys.stream().filter(key -> !key.endsWith("/") && pattern.matcher(key).find()).collect(Collectors.toList());

      if (!keys.isEmpty()) {
        build.setNumber(keys.get(0));
        build.setBuildDisplayName(packageName + ": " + keys.get(0));
        build.setUiDisplayName("Tag# " + keys.get(0));

        Map<String, String> finalMap = new HashMap<>();

        for (String key : keys) {
          finalMap.put(key, map.get(key));
        }
        build.setMetadata(finalMap);
      } else {
        builds.remove(build);
        return regexFilteringForGetBuilds(versionRegex, builds, packageName);
      }
    }
    return builds;
  }

  private BuildDetails versionFiltering(String version, List<BuildDetails> builds, String packageName) {
    for (BuildDetails build : builds) {
      Map<String, String> map = build.getMetadata();

      if (map.containsKey(version)) {
        build.setBuildDisplayName(packageName + ": " + version);
        build.setUiDisplayName("Tag# " + version);
        build.setNumber(version);

        return build;
      }
    }

    throw new InvalidRequestException("Could not find version " + version + " in the package " + packageName);
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

    return null;
  }

  private List<BuildDetails> getBuildDetails(GithubPackagesInternalConfig githubPackagesInternalConfig,
      String packageName, String packageType, String org) throws IOException {
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

    GithubPackagesVersionsResponse githubPackagesVersionsResponse = GithubPackagesVersionsResponse.builder().build();

    Response<List<JsonNode>> response;

    if (EmptyPredicate.isEmpty(org)) {
      response = githubPackagesRestClient.listVersionsForPackages(basicAuthHeader, packageName, packageType).execute();
    } else {
      response = githubPackagesRestClient.listVersionsForPackagesInOrg(basicAuthHeader, org, packageName, packageType)
                     .execute();
    }

    if (!isSuccessful(response)) {
      throw NestedExceptionUtils.hintWithExplanationException("Unable to fetch the versions for the package",
          "Check if the package exists and if the permissions are scoped for the authenticated user",
          new InvalidArtifactServerException(response.message(), USER));
    }

    githubPackagesVersionsResponse = processResponse(response.body());

    return processBuildDetails(githubPackagesVersionsResponse, packageName);
  }

  private List<BuildDetails> processBuildDetails(
      GithubPackagesVersionsResponse githubPackagesVersionsResponse, String packageName) {
    List<GithubPackagesVersion> versions = githubPackagesVersionsResponse.getVersionDetails();

    List<BuildDetails> buildDetails = new ArrayList<>();

    for (GithubPackagesVersion v : versions) {
      BuildDetails build = new BuildDetails();

      List<String> tags = v.getTags();

      Map<String, String> metadata = new HashMap<>();

      for (String t : tags) {
        metadata.put(t, packageName);
      }

      String tag = tags.get(0);

      build.setBuildDisplayName(packageName + ": " + tag);
      build.setUiDisplayName("Tag# " + tag);
      build.setNumber(tag);
      build.setBuildUrl(v.getVersionUrl());
      build.setStatus(BuildDetails.BuildStatus.SUCCESS);
      build.setBuildFullDisplayName(v.getVersionName());
      build.setMetadata(metadata);
      build.setArtifactPath(v.getVersionHtmlUrl());

      buildDetails.add(build);
    }

    return buildDetails;
  }

  private GithubPackagesVersionsResponse processResponse(List<JsonNode> versionDetails) {
    List<GithubPackagesVersion> versions = new ArrayList<>();

    if (versionDetails != null) {
      for (JsonNode node : versionDetails) {
        JsonNode metadata = node.get("metadata");

        JsonNode container = metadata.get("container");

        ArrayNode tags = (ArrayNode) container.get("tags");

        List<String> tagList = new ArrayList<>();

        for (JsonNode jsonNode : tags) {
          String tag = jsonNode.asText();

          tagList.add(tag);
        }

        GithubPackagesVersion version = GithubPackagesVersion.builder()
                                            .versionId(node.get("id").asText())
                                            .versionName(node.get("name").asText())
                                            .versionUrl(node.get("url").asText())
                                            .versionHtmlUrl(node.get("html_url").asText())
                                            .packageUrl(node.get("package_html_url").asText())
                                            .createdAt(node.get("created_at").asText())
                                            .lastUpdatedAt(node.get("updated_at").asText())
                                            .packageType(metadata.get("package_type").asText())
                                            .tags(tagList)
                                            .build();

        versions.add(version);
      }
    } else {
      if (versionDetails == null) {
        log.warn("Github Packages Version response was null.");
      } else {
        log.warn("Github Packages Version response was empty.");
      }
      return null;
    }

    return GithubPackagesVersionsResponse.builder().versionDetails(versions).build();
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
