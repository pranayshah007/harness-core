/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator;

import static io.harness.rule.OwnerRule.ARPITJ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.services.api.UpdatableEntity;
import io.harness.cvng.servicelevelobjective.beans.SLIExecutionType;
import io.harness.cvng.servicelevelobjective.beans.SLIMetricType;
import io.harness.cvng.servicelevelobjective.entities.RatioServiceLevelIndicator.RatioServiceLevelIndicatorUpdatableEntity;
import io.harness.cvng.servicelevelobjective.entities.RequestServiceLevelIndicator.RequestServiceLevelIndicatorUpdatableEntity;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.ThresholdServiceLevelIndicator.ThresholdServiceLevelIndicatorUpdatableEntity;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ServiceLevelIndicatorEntityAndDTOTransformerTest extends CvNextGenTestBase {
  BuilderFactory builderFactory;
  @Inject private ServiceLevelIndicatorEntityAndDTOTransformer serviceLevelIndicatorEntityAndDTOTransformer;
  @Inject private Map<String, ServiceLevelIndicatorTransformer> serviceLevelIndicatorFQDITransformerMapBinder;

  @Before
  @SneakyThrows
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testFQDIMapping() {
    assertThat(serviceLevelIndicatorFQDITransformerMapBinder.get(SLIExecutionType.REQUEST.name()).getClass())
        .hasSameClassAs(RequestServiceLevelIndicatorTransformer.class);
    assertThat(serviceLevelIndicatorFQDITransformerMapBinder
                   .get(SLIExecutionType.WINDOW.name() + "_" + SLIMetricType.RATIO.name())
                   .getClass())
        .hasSameClassAs(RatioServiceLevelIndicatorTransformer.class);
    assertThat(serviceLevelIndicatorFQDITransformerMapBinder
                   .get(SLIExecutionType.WINDOW.name() + "_" + SLIMetricType.THRESHOLD.name())
                   .getClass())
        .hasSameClassAs(ThresholdServiceLevelIndicatorTransformer.class);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetFullyQualifiedIdentifier_forDTO() {
    String fullyQualifiedType =
        builderFactory.getRequestServiceLevelIndicatorDTOBuilder().build().getEvaluationAndMetricType();
    assertThat(fullyQualifiedType).isEqualTo(SLIExecutionType.REQUEST.name());
    fullyQualifiedType = builderFactory.getRatioServiceLevelIndicatorDTOBuilder().build().getEvaluationAndMetricType();
    assertThat(fullyQualifiedType).isEqualTo(SLIExecutionType.WINDOW.name() + "_" + SLIMetricType.RATIO.name());
    fullyQualifiedType =
        builderFactory.getThresholdServiceLevelIndicatorDTOBuilder().build().getEvaluationAndMetricType();
    assertThat(fullyQualifiedType).isEqualTo(SLIExecutionType.WINDOW.name() + "_" + SLIMetricType.THRESHOLD.name());
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetUpdatableEntity() {
    UpdatableEntity<ServiceLevelIndicator, ServiceLevelIndicator> updatableEntity =
        serviceLevelIndicatorEntityAndDTOTransformer.getUpdatableEntity(
            builderFactory.getRequestServiceLevelIndicatorDTOBuilder().build());
    assertThat(updatableEntity.getClass()).hasSameClassAs(RequestServiceLevelIndicatorUpdatableEntity.class);
    updatableEntity = serviceLevelIndicatorEntityAndDTOTransformer.getUpdatableEntity(
        builderFactory.getRatioServiceLevelIndicatorDTOBuilder().build());
    assertThat(updatableEntity.getClass()).hasSameClassAs(RatioServiceLevelIndicatorUpdatableEntity.class);
    updatableEntity = serviceLevelIndicatorEntityAndDTOTransformer.getUpdatableEntity(
        builderFactory.getThresholdServiceLevelIndicatorDTOBuilder().build());
    assertThat(updatableEntity.getClass()).hasSameClassAs(ThresholdServiceLevelIndicatorUpdatableEntity.class);
  }
}
