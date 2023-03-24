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
import io.harness.cvng.servicelevelobjective.beans.SLIEvaluationType;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.RatioSLIMetricEventType;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.RequestBasedServiceLevelIndicatorSpec;
import io.harness.cvng.servicelevelobjective.entities.RequestServiceLevelIndicator;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class RequestServiceLevelIndicatorTransformerTest extends CvNextGenTestBase {
  BuilderFactory builderFactory;
  RequestServiceLevelIndicatorTransformer requestServiceLevelIndicatorTransformer;
  @Inject private Map<String, ServiceLevelIndicatorTransformer> serviceLevelIndicatorFQDITransformerMapBinder;

  @Before
  @SneakyThrows
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    requestServiceLevelIndicatorTransformer =
        (RequestServiceLevelIndicatorTransformer) serviceLevelIndicatorFQDITransformerMapBinder.get(
            SLIEvaluationType.REQUEST.name());
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetEntity() {
    RequestServiceLevelIndicator requestServiceLevelIndicator = requestServiceLevelIndicatorTransformer.getEntity(
        builderFactory.getProjectParams(), builderFactory.getRequestServiceLevelIndicatorDTOBuilder().build(),
        "monitoredServiceIdentifier", "healthSourceIdentifier", true);
    assertThat(requestServiceLevelIndicator.getEventType()).isEqualTo(RatioSLIMetricEventType.GOOD);
    assertThat(requestServiceLevelIndicator.getMetric1()).isEqualTo("Errors per Minute");
    assertThat(requestServiceLevelIndicator.getMetric2()).isEqualTo("Calls per Minute");
    assertThat(requestServiceLevelIndicator.getSLIEvaluationType()).isEqualTo(SLIEvaluationType.REQUEST);
    assertThat(requestServiceLevelIndicator.getSLIMetricType()).isEqualTo(null);
    assertThat(requestServiceLevelIndicator.getHealthSourceIdentifier()).isEqualTo("healthSourceIdentifier");
    assertThat(requestServiceLevelIndicator.getMonitoredServiceIdentifier()).isEqualTo("monitoredServiceIdentifier");
    assertThat(requestServiceLevelIndicator.getAccountId())
        .isEqualTo(builderFactory.getProjectParams().getAccountIdentifier());
    assertThat(requestServiceLevelIndicator.getProjectIdentifier())
        .isEqualTo(builderFactory.getProjectParams().getProjectIdentifier());
    assertThat(requestServiceLevelIndicator.getOrgIdentifier())
        .isEqualTo(builderFactory.getProjectParams().getOrgIdentifier());
    assertThat(requestServiceLevelIndicator.getIdentifier()).isEqualTo("identifier");
    assertThat(requestServiceLevelIndicator.getName()).isEqualTo("name");
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetSpec() {
    RequestBasedServiceLevelIndicatorSpec requestBasedServiceLevelIndicatorSpec =
        requestServiceLevelIndicatorTransformer.getSpec(builderFactory.requestServiceLevelIndicatorBuilder().build());
    assertThat(requestBasedServiceLevelIndicatorSpec.getEventType()).isEqualTo(RatioSLIMetricEventType.GOOD);
    assertThat(requestBasedServiceLevelIndicatorSpec.getMetric1()).isEqualTo("metric1");
    assertThat(requestBasedServiceLevelIndicatorSpec.getMetric2()).isEqualTo("metric2");
  }
}
