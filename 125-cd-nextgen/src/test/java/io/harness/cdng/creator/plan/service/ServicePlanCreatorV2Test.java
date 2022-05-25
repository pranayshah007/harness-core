package io.harness.cdng.creator.plan.service;

import static io.harness.rule.OwnerRule.ARCHIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.licenserestriction.EnforcementValidator;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
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
public class ServicePlanCreatorV2Test extends CDNGTestBase {
  @Mock private EnforcementValidator enforcementValidator;
  @Inject @InjectMocks ServicePlanCreatorV2 servicePlanCreatorV2;

  @Before
  public void before() throws IllegalAccessException {
    FieldUtils.writeField(servicePlanCreatorV2, "enforcementValidator", enforcementValidator, true);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(servicePlanCreatorV2.getFieldClass()).isEqualTo(NGServiceV2InfoConfig.class);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    Map<String, Set<String>> supportedTypes = servicePlanCreatorV2.getSupportedTypes();
    assertThat(supportedTypes.containsKey(YamlTypes.SERVICE_ENTITY)).isEqualTo(true);
    assertThat(supportedTypes.get(YamlTypes.SERVICE_ENTITY).size()).isEqualTo(1);
    assertThat(supportedTypes.get(YamlTypes.SERVICE_ENTITY).contains(PlanCreatorUtils.ANY_TYPE)).isEqualTo(true);
  }
}