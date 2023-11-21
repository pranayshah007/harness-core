/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.onboarding.mappers;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.NGCommonEntityConstants.ORG_KEY;
import static io.harness.NGCommonEntityConstants.PROJECT_KEY;
import static io.harness.NGCommonEntityConstants.SERVICE_IDENTIFIER_KEY;
import static io.harness.idp.backstagebeans.Constants.ENTITY_UNKNOWN_OWNER;
import static io.harness.idp.backstagebeans.Constants.SERVICE;
import static io.harness.idp.common.CommonUtils.truncateEntityName;
import static io.harness.idp.onboarding.utils.Constants.BACKSTAGE_HARNESS_ANNOTATION_CD_SERVICE_ID;
import static io.harness.idp.onboarding.utils.Constants.BACKSTAGE_HARNESS_ANNOTATION_PROJECT_URL;
import static io.harness.idp.onboarding.utils.Constants.BACKSTAGE_HARNESS_ANNOTATION_SERVICES;
import static io.harness.idp.onboarding.utils.Constants.ENTITY_UNKNOWN_LIFECYCLE;
import static io.harness.idp.onboarding.utils.Constants.ENTITY_UNKNOWN_REF;
import static io.harness.idp.onboarding.utils.Constants.PROJECT_URL;
import static io.harness.idp.onboarding.utils.Constants.SERVICE_URL;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstagebeans.BackstageCatalogComponentEntity;
import io.harness.idp.backstagebeans.BackstageCatalogEntity;
import io.harness.idp.onboarding.config.OnboardingModuleConfig;
import io.harness.ng.core.service.dto.ServiceResponseDTO;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.IDP)
public class HarnessServiceToBackstageComponent
    implements HarnessEntityToBackstageEntity<ServiceResponseDTO, BackstageCatalogComponentEntity> {
  private final OnboardingModuleConfig onboardingModuleConfig;
  private final String env;
  public final List<String> entityNamesSeenSoFar = new ArrayList<>();
  private final List<String> envOverrideForHarnessCiCdAnnotation = List.of("stress", "qa", "stage");

  @Inject
  public HarnessServiceToBackstageComponent(
      @Named("onboardingModuleConfig") OnboardingModuleConfig onboardingModuleConfig, @Named("env") String env) {
    this.onboardingModuleConfig = onboardingModuleConfig;
    this.env = env;
  }

  @Override
  public BackstageCatalogComponentEntity map(ServiceResponseDTO serviceResponseDTO) {
    String orgIdentifier =
        serviceResponseDTO.getOrgIdentifier() == null ? ENTITY_UNKNOWN_REF : serviceResponseDTO.getOrgIdentifier();
    String projectIdentifier = serviceResponseDTO.getProjectIdentifier() == null
        ? ENTITY_UNKNOWN_REF
        : serviceResponseDTO.getProjectIdentifier();

    BackstageCatalogComponentEntity backstageCatalogComponentEntity = new BackstageCatalogComponentEntity();

    BackstageCatalogEntity.Metadata metadata = new BackstageCatalogEntity.Metadata();
    metadata.setMetadata(serviceResponseDTO.getIdentifier(),
        orgIdentifier + "-" + projectIdentifier + "-" + serviceResponseDTO.getIdentifier(),
        truncateEntityName(serviceResponseDTO.getIdentifier()), serviceResponseDTO.getName(),
        serviceResponseDTO.getDescription(), getTags(serviceResponseDTO.getTags()),
        getHarnessCiCdAnnotations(serviceResponseDTO));
    backstageCatalogComponentEntity.setMetadata(metadata);

    BackstageCatalogComponentEntity.Spec spec = new BackstageCatalogComponentEntity.Spec();
    spec.setType(SERVICE);
    spec.setLifecycle(ENTITY_UNKNOWN_LIFECYCLE);
    spec.setOwner(ENTITY_UNKNOWN_OWNER);
    spec.setDomain(truncateEntityName(orgIdentifier));
    spec.setSystem(truncateEntityName(projectIdentifier));
    spec.setHarnessSystem(projectIdentifier);
    backstageCatalogComponentEntity.setSpec(spec);

    if (entityNamesSeenSoFar.contains(serviceResponseDTO.getIdentifier())) {
      backstageCatalogComponentEntity.getMetadata().setName(
          truncateEntityName(backstageCatalogComponentEntity.getMetadata().getAbsoluteIdentifier()));
    }

    entityNamesSeenSoFar.add(serviceResponseDTO.getIdentifier());

    return backstageCatalogComponentEntity;
  }

  private Map<String, String> getHarnessCiCdAnnotations(ServiceResponseDTO serviceResponseDTO) {
    if (serviceResponseDTO.getOrgIdentifier() != null && serviceResponseDTO.getProjectIdentifier() != null) {
      Map<String, String> harnessCiCdAnnotations = new LinkedHashMap<>();
      harnessCiCdAnnotations.put(
          getBackstageHarnessAnnotationProjectUrlByEnv(), getProjectUrlForHarnessCiCdAnnotation(serviceResponseDTO));
      harnessCiCdAnnotations.put(BACKSTAGE_HARNESS_ANNOTATION_CD_SERVICE_ID, serviceResponseDTO.getIdentifier());
      harnessCiCdAnnotations.put(BACKSTAGE_HARNESS_ANNOTATION_SERVICES,
          getHarnessCiCdAnnotationServices(Collections.singletonList(serviceResponseDTO)));
      return harnessCiCdAnnotations;
    }
    return Map.of();
  }

  private String getBackstageHarnessAnnotationProjectUrlByEnv() {
    if (envOverrideForHarnessCiCdAnnotation.contains(env))
      return BACKSTAGE_HARNESS_ANNOTATION_PROJECT_URL + "-" + env;
    return BACKSTAGE_HARNESS_ANNOTATION_PROJECT_URL;
  }

  private String getProjectUrlForHarnessCiCdAnnotation(ServiceResponseDTO serviceResponseDTO) {
    return onboardingModuleConfig.getHarnessCiCdAnnotations()
        .get(PROJECT_URL)
        .replace(ACCOUNT_KEY, serviceResponseDTO.getAccountId())
        .replace(ORG_KEY, serviceResponseDTO.getOrgIdentifier())
        .replace(PROJECT_KEY, serviceResponseDTO.getProjectIdentifier());
  }

  private String getHarnessCiCdAnnotationServices(List<ServiceResponseDTO> serviceResponseDTOs) {
    StringBuilder harnessCiCdAnnotationServices = new StringBuilder();
    serviceResponseDTOs.forEach(serviceResponseDTO
        -> harnessCiCdAnnotationServices.append(getServiceUrlForHarnessCiCdAnnotation(serviceResponseDTO)));
    return harnessCiCdAnnotationServices.toString();
  }

  private String getServiceUrlForHarnessCiCdAnnotation(ServiceResponseDTO serviceResponseDTO) {
    return serviceResponseDTO.getIdentifier() + ": "
        + onboardingModuleConfig.getHarnessCiCdAnnotations()
              .get(SERVICE_URL)
              .replace(ACCOUNT_KEY, serviceResponseDTO.getAccountId())
              .replace(ORG_KEY, serviceResponseDTO.getOrgIdentifier())
              .replace(PROJECT_KEY, serviceResponseDTO.getProjectIdentifier())
              .replace(SERVICE_IDENTIFIER_KEY, serviceResponseDTO.getIdentifier())
        + "\n";
  }
}
