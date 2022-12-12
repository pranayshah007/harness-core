/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.multiartifact;

import io.harness.beans.PageResponse;
import io.harness.rest.RestResponse;
import io.harness.testframework.framework.Setup;

import software.wings.persistence.artifact.Artifact;

import io.restassured.http.ContentType;
import javax.ws.rs.core.GenericType;

public class MultiArtifactTestUtils {
  public static Artifact collectArtifact(String bearerToken, String accountId, String artifactStreamId) {
    GenericType<RestResponse<PageResponse<Artifact>>> workflowType =
        new GenericType<RestResponse<PageResponse<Artifact>>>() {};
    RestResponse<PageResponse<Artifact>> savedArtifactResponse = Setup.portal()
                                                                     .auth()
                                                                     .oauth2(bearerToken)
                                                                     .queryParam("accountId", accountId)
                                                                     .queryParam("artifactStreamId", artifactStreamId)
                                                                     .queryParam("search[0][field]", "status")
                                                                     .queryParam("search[0][op]", "IN")
                                                                     .queryParam("search[0][value]", "READY")
                                                                     .queryParam("search[0][value]", "APPROVED")
                                                                     .contentType(ContentType.JSON)
                                                                     .get("/artifacts/v2")
                                                                     .as(workflowType.getType());

    return (savedArtifactResponse != null && savedArtifactResponse.getResource() != null
               && savedArtifactResponse.getResource().getResponse() != null
               && savedArtifactResponse.getResource().getResponse().size() > 0)
        ? savedArtifactResponse.getResource().getResponse().get(0)
        : null;
  }
}
