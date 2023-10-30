/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.releasedetails.resources;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.dtos.InstanceDTO;
import io.harness.entities.ReleaseDetailsMapping;
import io.harness.repositories.releasedetailsmapping.ReleaseDetailsMappingRepository;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.service.instance.InstanceService;
import io.harness.spec.server.ng.v1.K8sReleaseServiceMappingApi;
import io.harness.spec.server.ng.v1.model.BatchReleaseDetailsResponse;
import io.harness.spec.server.ng.v1.model.ReleaseDetailsRequest;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@Slf4j
public class K8sReleaseServiceMappingApiImpl implements K8sReleaseServiceMappingApi {
  private final ReleaseDetailsMappingRepository releaseDetailsMappingRepository;
  private final K8sReleaseServiceMappingApiUtils k8sReleaseServiceMappingApiUtils;

  private final InstanceService instanceService;

  @Override
  public Response getV1ReleaseDetails(
      @Valid List<ReleaseDetailsRequest> releaseDetailsRequest, String accountIdentifier) {
    List<BatchReleaseDetailsResponse> batchReleaseDetailsResponses = new ArrayList<>();
    for (ReleaseDetailsRequest releaseDetails : releaseDetailsRequest) {
      String releaseKey = String.format("%s_%s", releaseDetails.getReleaseName(), releaseDetails.getNamespace());
      List<ReleaseDetailsMapping> releaseDetailsMappingList =
          releaseDetailsMappingRepository.findByAccountIdentifierAndReleaseKey(accountIdentifier, releaseKey);
      if (releaseDetailsMappingList.isEmpty()) {
        List<InstanceDTO> instanceList = instanceService.getActiveInstancesByInstanceInfoAndReleaseName(
            accountIdentifier, releaseDetails.getNamespace(), releaseDetails.getReleaseName());
        batchReleaseDetailsResponses.add(
            k8sReleaseServiceMappingApiUtils.mapInstancesToBatchReleaseDetailsResponse(instanceList, releaseKey));
      } else {
        batchReleaseDetailsResponses.add(
            k8sReleaseServiceMappingApiUtils.mapToBatchReleaseDetailsResponse(releaseDetailsMappingList));
      }
    }
    return Response.ok().entity(batchReleaseDetailsResponses).build();
  }
}
