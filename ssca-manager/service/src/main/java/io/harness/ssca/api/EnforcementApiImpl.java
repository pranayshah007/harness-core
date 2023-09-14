/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.api;

import io.harness.annotations.SSCAServiceAuth;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SortOrder;
import io.harness.beans.SortOrder.OrderType;
import io.harness.ng.beans.PageRequest;
import io.harness.spec.server.ssca.v1.EnforcementApi;
import io.harness.spec.server.ssca.v1.model.Artifact;
import io.harness.spec.server.ssca.v1.model.EnforcementResultDTO;
import io.harness.spec.server.ssca.v1.model.EnforcementSummaryDTO;
import io.harness.spec.server.ssca.v1.model.EnforcementSummaryResponse;
import io.harness.spec.server.ssca.v1.model.PolicyViolation;
import io.harness.ssca.services.EnforcementResultService;
import io.harness.ssca.services.EnforcementStepService;
import io.harness.ssca.services.EnforcementSummaryService;
import io.harness.ssca.services.NormalisedSbomComponentService;
import io.harness.utils.ApiUtils;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import java.util.Arrays;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@OwnedBy(HarnessTeam.SSCA)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@SSCAServiceAuth
public class EnforcementApiImpl implements EnforcementApi {
  @Inject EnforcementStepService enforcementStepService;

  @Inject EnforcementResultService enforcementResultService;

  @Inject EnforcementSummaryService enforcementSummaryService;

  @Inject NormalisedSbomComponentService normalisedSbomComponentService;

  @Override
  public Response getEnforcementSummary(String org, String project, String enforcementId, String harnessAccount) {
    EnforcementSummaryResponse response =
        enforcementStepService.getEnforcementSummary(harnessAccount, org, project, enforcementId);
    return Response.ok().entity(response).build();
  }

  @Override
  public Response getNormalisedSbomComponent(String org, String project, @Valid Artifact body, String harnessAccount,
      @Max(1000L) Integer limit, Integer page) {
    return normalisedSbomComponentService.listNormalizedSbomComponent(org, project, page, limit, body, harnessAccount);
  }

  @Override
  public Response getPolicyViolations(String org, String project, String enforcementId, String harnessAccount,
      @Max(1000L) Integer limit, String order, Integer page, String sort, String searchText) {
    SortOrder sortOrder = new SortOrder();
    sortOrder.setFieldName(sort);
    sortOrder.setOrderType(OrderType.valueOf(order));
    Pageable pageable = PageUtils.getPageRequest(new PageRequest(page, limit, Arrays.asList(sortOrder)));

    Page<PolicyViolation> policyViolations =
        enforcementStepService.getPolicyViolations(harnessAccount, org, project, enforcementId, searchText, pageable);
    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks =
        ApiUtils.addLinksHeader(responseBuilder, policyViolations.getTotalElements(), page, limit);

    return responseBuilderWithLinks.entity(policyViolations.getContent()).build();
  }

  @Override
  public Response saveEnforcementResult(String org, String project, @Valid EnforcementResultDTO body) {
    enforcementResultService.create(body);
    return Response.ok().status(201).build();
  }

  @Override
  public Response saveEnforcementSummary(String org, String project, @Valid EnforcementSummaryDTO body) {
    enforcementSummaryService.create(body);
    return Response.ok().status(201).build();
  }
}
