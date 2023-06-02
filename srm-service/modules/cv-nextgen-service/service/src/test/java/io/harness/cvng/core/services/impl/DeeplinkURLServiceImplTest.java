/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.rule.OwnerRule.ANSUMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.client.NextGenService;
import io.harness.cvng.core.entities.AppDynamicsCVConfig;
import io.harness.cvng.core.entities.PrometheusCVConfig;
import io.harness.cvng.core.services.DeeplinkURLService;
import io.harness.cvng.models.VerificationType;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.prometheusconnector.PrometheusConnectorDTO;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import com.google.inject.Injector;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.http.client.utils.URIBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DeeplinkURLServiceImplTest extends CvNextGenTestBase {
  private static final String BASE_PROMETHEUS_URL = "http://35.226.185.156:8080/";
  @Inject private DeeplinkURLService deeplinkURLService;

  private BuilderFactory builderFactory;

  private Instant startTimeInstant;
  private Instant endTimeInstant;

  @Inject private Injector injector;
  @Before
  public void setup() {
    injector.injectMembers(deeplinkURLService);
    builderFactory = BuilderFactory.getDefault();
    startTimeInstant = Instant.parse("2023-01-24T09:57:00Z");
    endTimeInstant = Instant.parse("2023-01-24T10:02:00Z");
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void generateDeeplinkURLForPrometheus() throws IllegalAccessException, URISyntaxException {
    String query = "process_cpu_seconds_total\t{\n"
        + "\n"
        + "\t\tjob=\"kubernetes-nodes\",\n"
        + "\t\tjob=\"kubernetes-nodes\"\n"
        + "\n"
        + "}";
    PrometheusCVConfig prometheusCVConfig = (PrometheusCVConfig) builderFactory.prometheusCVConfigBuilder()
                                                .groupName("g1")
                                                .uuid("XNwW8B7WSI2a_EZ0m6N7WQ")
                                                .verificationType(VerificationType.TIME_SERIES)
                                                .accountId("kmpySmUISimoRrJL6NL73w")
                                                .enabled(false)
                                                .identifier("ELK_NonProd/prom_hs")
                                                .monitoringSourceName("prom hs")
                                                .build();
    prometheusCVConfig.setMetricInfoList(List.of(PrometheusCVConfig.MetricInfo.builder()
                                                     .identifier("prometheus_metric")
                                                     .metricName("Prometheus Metric")
                                                     .serviceInstanceFieldName("host")
                                                     .metricType(TimeSeriesMetricType.ERROR)
                                                     .isManualQuery(true)
                                                     .query(query)
                                                     .build()));
    Optional<ConnectorInfoDTO> connectorInfoDTO =
        Optional.of(ConnectorInfoDTO.builder()
                        .name("prom conn")
                        .identifier("prom_conn")
                        .orgIdentifier(prometheusCVConfig.getOrgIdentifier())
                        .projectIdentifier(prometheusCVConfig.getProjectIdentifier())
                        .connectorType(ConnectorType.PROMETHEUS)
                        .connectorConfig(PrometheusConnectorDTO.builder().url(BASE_PROMETHEUS_URL).build())
                        .build());
    NextGenService mockedNextGenService = mock(NextGenService.class);
    when(
        mockedNextGenService.get(eq(prometheusCVConfig.getAccountId()), eq(prometheusCVConfig.getConnectorIdentifier()),
            eq(prometheusCVConfig.getOrgIdentifier()), eq(prometheusCVConfig.getProjectIdentifier())))
        .thenReturn(connectorInfoDTO);
    FieldUtils.writeField(deeplinkURLService, "nextGenService", mockedNextGenService, true);
    Optional<String> prometheusDeeplinkURL = deeplinkURLService.buildDeeplinkURLFromCVConfig(
        prometheusCVConfig, "prometheus_metric", startTimeInstant, endTimeInstant);

    URIBuilder uriBuilder = new URIBuilder(BASE_PROMETHEUS_URL + "graph");
    uriBuilder.addParameter("g0.step_input", "60");
    uriBuilder.addParameter("g0.expr", query);
    uriBuilder.addParameter("g0.range_input", "5m");
    uriBuilder.addParameter("g0.end_input", "2023-01-24 10:02");
    uriBuilder.addParameter("g0.tab", "0");
    assertThat(prometheusDeeplinkURL.get()).isEqualTo(uriBuilder.build().toString());
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void deepLinkURLNotImplemented() {
    AppDynamicsCVConfig appDynamicsCVConfig = builderFactory.appDynamicsCVConfigBuilder().build();
    Optional<String> appDynamicsDeeplinkURL = deeplinkURLService.buildDeeplinkURLFromCVConfig(
        appDynamicsCVConfig, "identifier", startTimeInstant, endTimeInstant);
    assertThat(appDynamicsDeeplinkURL.isEmpty()).isEqualTo(true);
  }
}
