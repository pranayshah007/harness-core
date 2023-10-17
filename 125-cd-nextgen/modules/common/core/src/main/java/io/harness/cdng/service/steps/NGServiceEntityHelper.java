/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.cdng.service.beans.ServicesYaml;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.yaml.ParameterField;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(CDC)
@Singleton
@Slf4j
public class NGServiceEntityHelper {
  @Inject private ServiceEntityService serviceEntityService;
  public Map<String, List<NGTag>> getServiceTags(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, ServicesYaml services, ServiceYamlV2 serviceYamlV2) {
    List<String> serviceRefs = new ArrayList<>();
    if (services != null && ParameterField.isNotNull(services.getValues())
        && isNotEmpty(services.getValues().getValue())) {
      List<ServiceYamlV2> serviceYamlV2List = services.getValues().getValue();
      serviceRefs = serviceYamlV2List.stream().map(s -> s.getServiceRef().getValue()).collect(Collectors.toList());
    }
    if (isEmpty(serviceRefs) && serviceYamlV2 != null && ParameterField.isNotNull(serviceYamlV2.getServiceRef())
        && isNotEmpty(serviceYamlV2.getServiceRef().getValue())) {
      serviceRefs = Collections.singletonList(serviceYamlV2.getServiceRef().getValue());
    }
    if (isEmpty(serviceRefs)) {
      return Collections.emptyMap();
    }

    List<ServiceEntity> serviceEntityList =
        serviceEntityService.getMetadata(accountIdentifier, orgIdentifier, projectIdentifier, serviceRefs);

    Map<String, List<NGTag>> serviceToTagsMap = new LinkedHashMap<>();
    for (ServiceEntity serviceEntity : serviceEntityList) {
      serviceToTagsMap.put(serviceEntity.getIdentifier(), serviceEntity.getTags());
    }
    return serviceToTagsMap;
  }

  public List<NGTag> getServiceTags(PlanCreationContext ctx, ServiceYamlV2 service) {
    if (service == null || ParameterField.isNull(service.getServiceRef())
        || isEmpty(service.getServiceRef().getValue())) {
      return Collections.emptyList();
    }
    String serviceRef = service.getServiceRef().getValue();
    Optional<ServiceEntity> serviceEntity = serviceEntityService.get(
        ctx.getAccountIdentifier(), ctx.getOrgIdentifier(), ctx.getProjectIdentifier(), serviceRef, false);
    if (serviceEntity.isPresent()) {
      return serviceEntity.get().getTags();
    }
    return Collections.emptyList();
  }
}
