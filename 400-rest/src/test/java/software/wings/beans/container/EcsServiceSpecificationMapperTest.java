/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.container;

import static io.harness.rule.OwnerRule.JOHANNES;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class EcsServiceSpecificationMapperTest extends CategoryTest {
  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void toEcsServiceSpecificationDTOSmokeTest() {
    EcsServiceSpecification ecsServiceSpecification =
        EcsServiceSpecification.builder().serviceId("someServiceId").serviceSpecJson("{\"IsThisJson\": true}").build();

    software.wings.beans.dto.EcsServiceSpecification ecsServiceSpecificationDTO =
        EcsServiceSpecificationMapper.toEcsServiceSpecificationDTO(ecsServiceSpecification);

    assertThat(ecsServiceSpecificationDTO).isNotNull();
    assertThat(ecsServiceSpecificationDTO.getServiceId()).isEqualTo(ecsServiceSpecification.getServiceId());
    assertThat(ecsServiceSpecificationDTO.getServiceSpecJson()).isEqualTo(ecsServiceSpecification.getServiceSpecJson());
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void toEcsServiceSpecificationDTOForNull() {
    assertThat(EcsServiceSpecificationMapper.toEcsServiceSpecificationDTO(null)).isNull();
  }
}
