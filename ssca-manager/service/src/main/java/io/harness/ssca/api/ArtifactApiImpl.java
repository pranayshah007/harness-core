/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.api;

import io.harness.spec.server.ssca.v1.ArtifactApi;
import io.harness.spec.server.ssca.v1.model.ArtifactComponentViewRequestBody;
import io.harness.spec.server.ssca.v1.model.ArtifactComponentViewResponse;
import io.harness.spec.server.ssca.v1.model.ArtifactDeploymentViewRequestBody;
import io.harness.spec.server.ssca.v1.model.ArtifactDeploymentViewResponse;
import io.harness.spec.server.ssca.v1.model.ArtifactListingRequestBody;
import io.harness.spec.server.ssca.v1.model.ArtifactListingResponse;
import io.harness.ssca.services.ArtifactService;
import io.harness.ssca.utils.PageResponseUtils;

import com.google.inject.Inject;
import java.util.Collections;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.ws.rs.core.Response;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public class ArtifactApiImpl implements ArtifactApi {
  @Inject ArtifactService artifactService;

  @Override
  public Response getArtifactDetailComponentView(String org, String project, String artifact, String tag,
      @Valid ArtifactComponentViewRequestBody body, String harnessAccount, @Min(1L) @Max(1000L) Integer limit,
      String order, @Min(0L) Integer page, String sort) {
    Pageable pageable = PageResponseUtils.getPageable(page, limit, sort, order);
    Page<ArtifactComponentViewResponse> artifactComponentViewResponses =
        artifactService.getArtifactComponentView(harnessAccount, org, project, artifact, tag, body, pageable);
    return PageResponseUtils.getPagedResponse(artifactComponentViewResponses, page, limit);
  }

  @Override
  public Response getArtifactDetailDeploymentView(String org, String project, String artifact, String tag,
      @Valid ArtifactDeploymentViewRequestBody body, String harnessAccount, @Min(1L) @Max(1000L) Integer limit,
      String order, @Min(0L) Integer page, String sort) {
    // TODO: Populate artifact deployment responses from CDInstanceSummary collection.
    Pageable pageable = PageResponseUtils.getPageable(page, limit, sort, order);
    Page<ArtifactDeploymentViewResponse> artifactDeploymentViewResponses =
        new PageImpl<>(Collections.singletonList(new ArtifactDeploymentViewResponse()));
    return PageResponseUtils.getPagedResponse(artifactDeploymentViewResponses, page, limit);
  }

  @Override
  public Response listArtifacts(String org, String project, @Valid ArtifactListingRequestBody body,
      String harnessAccount, @Min(1L) @Max(1000L) Integer limit, String order, @Min(0L) Integer page, String sort) {
    Pageable pageable = PageResponseUtils.getPageable(page, limit, sort, order);
    Page<ArtifactListingResponse> artifactEntities =
        artifactService.listArtifacts(harnessAccount, org, project, null, pageable);
    return PageResponseUtils.getPagedResponse(artifactEntities, page, limit);
  }

  @Override
  public Response listLatestArtifacts(String org, String project, String harnessAccount,
      @Min(1L) @Max(1000L) Integer limit, String order, @Min(0L) Integer page, String sort) {
    Pageable pageable = PageResponseUtils.getPageable(page, limit, sort, order);
    Page<ArtifactListingResponse> artifactEntities =
        artifactService.listLatestArtifacts(harnessAccount, org, project, pageable);
    return PageResponseUtils.getPagedResponse(artifactEntities, page, limit);
  }
}
