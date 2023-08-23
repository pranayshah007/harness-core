/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import io.harness.repositories.SBOMComponentRepo;
import io.harness.spec.server.ssca.v1.model.NormalizedSbomComponentDTO;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity;
import io.harness.ssca.utils.transformers.NormalizeSbomComponentTransformer;
import io.harness.utils.ApiUtils;

import com.google.inject.Inject;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

public class NormalizeSbomServiceImpl implements NormalizeSbomService {
  @Inject SBOMComponentRepo sbomComponentRepo;

  @Override
  public Response listNormalizedSbomComponent(String orgIdentifier, String projectIdentifier, Integer page,
      Integer limit, String orchestrationId, String accountId) {
    Pageable pageRequest = PageRequest.of(page, limit);
    Page<NormalizedSBOMComponentEntity> entities =
        sbomComponentRepo.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndOrchestrationId(
            accountId, orgIdentifier, projectIdentifier, orchestrationId, pageRequest);
    Page<NormalizedSbomComponentDTO> result = entities.map(entity -> NormalizeSbomComponentTransformer.toDTO(entity));
    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks =
        ApiUtils.addLinksHeader(responseBuilder, entities.getTotalElements(), page, limit);
    return responseBuilderWithLinks.entity(result.getContent()).build();
  }
}
