/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.githubpackages.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifacts.comparator.BuildDetailsComparatorDescending;
import io.harness.artifacts.githubpackages.beans.GithubPackagesInternalConfig;
import io.harness.artifacts.githubpackages.beans.GithubPackagesVersionsResponse;
import io.harness.artifacts.githubpackages.client.GithubPackagesRestClient;
import io.harness.artifacts.githubpackages.client.GithubPackagesRestClientFactory;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.ExceptionUtils;
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
import retrofit2.Response;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class GithubPackagesRegistryServiceImpl implements GithubPackagesRegistryService {
  @Inject private GithubPackagesRestClientFactory githubPackagesRestClientFactory;

  @Override
  public List<BuildDetails> getBuilds(
      GithubPackagesInternalConfig githubPackagesInternalConfig, String packageName, int maxNoOfVersionsPerPackage) {
    List<BuildDetails> buildDetails;

    try {
      buildDetails = getBuildDetails(githubPackagesInternalConfig, packageName);
    } catch (GithubPackagesServerRuntimeException ex) {
      throw ex;
    } catch (Exception e) {
      throw NestedExceptionUtils.hintWithExplanationException("Could not fetch versions for the package",
          "Check if the package exists and if the permissions are scoped for the authenticated user",
          new ArtifactServerException(ExceptionUtils.getMessage(e), e, WingsException.USER));
    }

    return buildDetails.stream().sorted(new BuildDetailsComparatorAscending()).collect(toList());
  }

  private List<BuildDetails> getBuildDetails(
      GithubPackagesInternalConfig githubPackagesInternalConfig, String packageName) throws IOException {
    GithubPackagesRestClient githubPackagesRestClient =
        githubPackagesRestClientFactory.getGithubPackagesRestClient(githubPackagesInternalConfig);

    String basicAuthHeader = Credentials.basic(
        githubPackagesInternalConfig.getUsername(), githubPackagesInternalConfig.getPassword().toString());

    List<BuildDetails> buildDetails = new ArrayList<>();

    String token = null;

    Response<GithubPackagesVersionsResponse> response =
        githubPackagesRestClient.listVersionsForPackages(basicAuthHeader, packageName).execute();

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
                                              return BuildDetails.builder()
                                                  .uiDisplayName("Tag# " + finalVersion)
                                                  .number(finalVersion)
                                                  .buildDisplayName(packageName)
                                                  .build();
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
}
