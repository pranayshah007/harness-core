/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.servicelevelobjective.transformer;

import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDetailsDTO;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective.ServiceLevelObjectivesDetail;

public class ServiceLevelObjectiveDetailsTransformer {
  public ServiceLevelObjectivesDetail getServiceLevelObjectiveDetails(
      ServiceLevelObjectiveDetailsDTO serviceLevelObjectiveDetailsDTO) {
    return ServiceLevelObjectivesDetail.builder()
        .serviceLevelObjectiveRef(serviceLevelObjectiveDetailsDTO.getServiceLevelObjectiveRef())
        .weightagePercentage(serviceLevelObjectiveDetailsDTO.getWeightagePercentage())
        .orgIdentifier(serviceLevelObjectiveDetailsDTO.getOrgIdentifier())
        .accountId(serviceLevelObjectiveDetailsDTO.getAccountId())
        .projectIdentifier(serviceLevelObjectiveDetailsDTO.getProjectIdentifier())
        .build();
  }

  public ServiceLevelObjectiveDetailsDTO getServiceLevelObjectiveDetailsDTO(
      ServiceLevelObjectivesDetail serviceLevelObjectivesDetail) {
    return ServiceLevelObjectiveDetailsDTO.builder()
        .serviceLevelObjectiveRef(serviceLevelObjectivesDetail.getServiceLevelObjectiveRef())
        .weightagePercentage(serviceLevelObjectivesDetail.getWeightagePercentage())
        .accountId(serviceLevelObjectivesDetail.getAccountId())
        .orgIdentifier(serviceLevelObjectivesDetail.getOrgIdentifier())
        .projectIdentifier(serviceLevelObjectivesDetail.getProjectIdentifier())
        .build();
  }
}
