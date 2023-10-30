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
import io.harness.spec.server.ng.v1.model.BatchReleaseDetailsResponse;
import io.harness.spec.server.ng.v1.model.ReleaseDetailsResponse;
import io.harness.spec.server.ng.v1.model.ReleaseEnvDetails;
import io.harness.spec.server.ng.v1.model.ReleaseServiceDetails;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.dropwizard.jersey.validation.JerseyViolationException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@Singleton
public class K8sReleaseServiceMappingApiUtils {
  private final Validator validator;

  @Inject
  public K8sReleaseServiceMappingApiUtils(Validator validator) {
    this.validator = validator;
  }

  public BatchReleaseDetailsResponse mapToBatchReleaseDetailsResponse(
      List<ReleaseDetailsMapping> releaseDetailsMappingList) {
    BatchReleaseDetailsResponse batchReleaseDetailsResponse = new BatchReleaseDetailsResponse();
    batchReleaseDetailsResponse.addAll(releaseDetailsMappingList.stream()
                                           .distinct()
                                           .map(this::mapToReleaseDetailsResponse)
                                           .collect(Collectors.toCollection(LinkedHashSet::new)));
    Set<ConstraintViolation<BatchReleaseDetailsResponse>> violations = validator.validate(batchReleaseDetailsResponse);
    if (!violations.isEmpty()) {
      throw new JerseyViolationException(violations, null);
    }
    return batchReleaseDetailsResponse;
  }

  public ReleaseDetailsResponse mapToReleaseDetailsResponse(ReleaseDetailsMapping releaseDetailsMapping) {
    ReleaseDetailsResponse releaseDetailsResponse = new ReleaseDetailsResponse();
    ReleaseServiceDetails releaseServiceDetailsResponse = new ReleaseServiceDetails();
    io.harness.entities.releasedetailsinfo.ReleaseServiceDetails releaseServiceDetails =
        releaseDetailsMapping.getReleaseDetails().getServiceDetails();
    io.harness.entities.releasedetailsinfo.ReleaseEnvDetails releaseEnvDetails =
        releaseDetailsMapping.getReleaseDetails().getEnvDetails();
    ReleaseEnvDetails releaseEnvDetailsResponse = new ReleaseEnvDetails();
    releaseServiceDetailsResponse.setServiceId(releaseServiceDetails.getServiceId());
    releaseServiceDetailsResponse.setServiceName(releaseServiceDetails.getServiceName());
    releaseServiceDetailsResponse.setOrg(releaseServiceDetails.getOrgIdentifier());
    releaseServiceDetailsResponse.setProject(releaseServiceDetails.getProjectIdentifier());

    releaseEnvDetailsResponse.setEnvId(releaseEnvDetails.getEnvId());
    releaseEnvDetailsResponse.setEnvName(releaseEnvDetails.getEnvName());
    releaseEnvDetailsResponse.setOrg(releaseEnvDetails.getOrgIdentifier());
    releaseEnvDetailsResponse.setProject(releaseEnvDetails.getProjectIdentifier());
    releaseEnvDetailsResponse.setConnectorRef(releaseEnvDetails.getConnectorRef());
    releaseEnvDetailsResponse.setInfraId(releaseEnvDetails.getInfraIdentifier());
    releaseEnvDetailsResponse.setInfraName(releaseEnvDetails.getInfraName());
    releaseEnvDetailsResponse.setInfrastructureKind(releaseEnvDetails.getInfrastructureKind());

    releaseDetailsResponse.setEnvironmentDetails(releaseEnvDetailsResponse);
    releaseDetailsResponse.setServiceDetails(releaseServiceDetailsResponse);
    releaseDetailsResponse.setReleaseKey(releaseDetailsMapping.getReleaseKey());
    releaseDetailsResponse.setProject(releaseDetailsMapping.getProjectIdentifier());
    releaseDetailsResponse.setOrg(releaseDetailsMapping.getOrgIdentifier());
    releaseDetailsResponse.setAccount(releaseDetailsMapping.getAccountIdentifier());
    return releaseDetailsResponse;
  }

  public BatchReleaseDetailsResponse mapInstancesToBatchReleaseDetailsResponse(
      List<InstanceDTO> instanceDTOList, String releaseKey) {
    BatchReleaseDetailsResponse batchReleaseDetailsResponse = new BatchReleaseDetailsResponse();
    batchReleaseDetailsResponse.addAll(instanceDTOList.stream()
                                           .distinct()
                                           .map(dto -> mapInstanceToReleaseDetailsResponse(dto, releaseKey))
                                           .collect(Collectors.toCollection(LinkedHashSet::new)));
    Set<ConstraintViolation<BatchReleaseDetailsResponse>> violations = validator.validate(batchReleaseDetailsResponse);
    if (!violations.isEmpty()) {
      throw new JerseyViolationException(violations, null);
    }
    return batchReleaseDetailsResponse;
  }

  public ReleaseDetailsResponse mapInstanceToReleaseDetailsResponse(InstanceDTO instanceDTO, String releaseKey) {
    ReleaseDetailsResponse releaseDetailsResponse = new ReleaseDetailsResponse();
    ReleaseServiceDetails releaseServiceDetailsResponse = new ReleaseServiceDetails();
    ReleaseEnvDetails releaseEnvDetailsResponse = new ReleaseEnvDetails();
    releaseServiceDetailsResponse.setServiceId(instanceDTO.getServiceIdentifier());
    releaseServiceDetailsResponse.setServiceName(instanceDTO.getServiceName());

    releaseEnvDetailsResponse.setEnvId(instanceDTO.getEnvIdentifier());
    releaseEnvDetailsResponse.setEnvName(instanceDTO.getEnvName());
    releaseEnvDetailsResponse.setConnectorRef(instanceDTO.getConnectorRef());
    releaseEnvDetailsResponse.setInfraId(instanceDTO.getInfraIdentifier());
    releaseEnvDetailsResponse.setInfraName(instanceDTO.getInfraName());
    releaseEnvDetailsResponse.setInfrastructureKind(instanceDTO.getInfrastructureKind());

    releaseDetailsResponse.setEnvironmentDetails(releaseEnvDetailsResponse);
    releaseDetailsResponse.setServiceDetails(releaseServiceDetailsResponse);
    releaseDetailsResponse.setProject(instanceDTO.getProjectIdentifier());
    releaseDetailsResponse.setOrg(instanceDTO.getOrgIdentifier());
    releaseDetailsResponse.setReleaseKey(releaseKey);
    releaseDetailsResponse.setAccount(instanceDTO.getAccountIdentifier());
    return releaseDetailsResponse;
  }
}
