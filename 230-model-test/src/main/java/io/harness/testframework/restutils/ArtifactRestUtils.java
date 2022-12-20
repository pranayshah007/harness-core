/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.testframework.restutils;

import io.harness.beans.PageResponse;
import io.harness.rest.RestResponse;
import io.harness.testframework.framework.Retry;
import io.harness.testframework.framework.Setup;
import io.harness.testframework.framework.matchers.ArtifactMatcher;

import software.wings.persistence.artifact.Artifact;
import software.wings.persistence.artifact.Artifact.ArtifactKeys;

import io.restassured.http.ContentType;
import java.util.List;
import javax.ws.rs.core.GenericType;

public class ArtifactRestUtils {
  public static List<Artifact> fetchArtifactByArtifactStream(
      String bearerToken, String appId, String artifactStreamId) {
    GenericType<RestResponse<PageResponse<Artifact>>> artifactType =
        new GenericType<RestResponse<PageResponse<Artifact>>>() {};

    RestResponse<PageResponse<Artifact>> savedArtifactResponse =
        Setup.portal()
            .auth()
            .oauth2(bearerToken)
            .queryParam("appId", appId)
            .queryParam("search[0][field]", ArtifactKeys.artifactStreamId)
            .queryParam("search[0][op]", "EQ")
            .queryParam("search[0][value]", artifactStreamId)
            .queryParam("search[1][field]", "status")
            .queryParam("search[1][op]", "IN")
            .queryParam("search[1][value]", "READY")
            .queryParam("search[1][value]", "APPROVED")
            .contentType(ContentType.JSON)
            .get("/artifacts")
            .as(artifactType.getType());

    return savedArtifactResponse.getResource();
  }

  public static Artifact waitAndFetchArtifactByArtfactStream(
      String bearerToken, String appId, String artifactStreamId, int artifactIndex) {
    Retry retry = new Retry(80, 10000);
    List<Artifact> artifacts = (List<Artifact>) retry.executeWithRetry(
        () -> fetchArtifactByArtifactStream(bearerToken, appId, artifactStreamId), new ArtifactMatcher(), null);
    return artifacts.get(artifactIndex);
  }

  public static List<Artifact> waitAndFetchArtifactListByArtfactStream(
      String bearerToken, String appId, String artifactStreamId) {
    Retry retry = new Retry(80, 10000);
    return (List<Artifact>) retry.executeWithRetry(
        () -> fetchArtifactByArtifactStream(bearerToken, appId, artifactStreamId), new ArtifactMatcher(), null);
  }
}
