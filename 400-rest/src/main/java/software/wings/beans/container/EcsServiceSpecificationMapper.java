/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.container;

import lombok.experimental.UtilityClass;

@UtilityClass
public class EcsServiceSpecificationMapper {
  public software.wings.beans.dto.EcsServiceSpecification toEcsServiceSpecificationDTO(
      EcsServiceSpecification ecsServiceSpecification) {
    if (ecsServiceSpecification == null) {
      return null;
    }

    return software.wings.beans.dto.EcsServiceSpecification.builder()
        .serviceId(ecsServiceSpecification.getServiceId())
        .serviceSpecJson(ecsServiceSpecification.getServiceSpecJson())
        .build();
  }
}
