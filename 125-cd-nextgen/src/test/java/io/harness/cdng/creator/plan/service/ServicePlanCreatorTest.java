/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.service;

import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.licenserestriction.EnforcementValidator;
import io.harness.cdng.service.beans.ServiceConfig;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDC)
public class ServicePlanCreatorTest extends CDNGTestBase {
  @Mock private EnforcementValidator enforcementValidator;
  @Inject @InjectMocks ServicePlanCreator servicePlanCreator;

  @Before
  public void before() throws IllegalAccessException {
    FieldUtils.writeField(servicePlanCreator, "enforcementValidator", enforcementValidator, true);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(servicePlanCreator.getFieldClass()).isEqualTo(ServiceConfig.class);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    Map<String, Set<String>> supportedTypes = servicePlanCreator.getSupportedTypes();
    assertThat(supportedTypes.containsKey(YamlTypes.SERVICE_CONFIG)).isEqualTo(true);
    assertThat(supportedTypes.get(YamlTypes.SERVICE_CONFIG).size()).isEqualTo(1);
    assertThat(supportedTypes.get(YamlTypes.SERVICE_CONFIG).contains(PlanCreatorUtils.ANY_TYPE)).isEqualTo(true);
  }
}