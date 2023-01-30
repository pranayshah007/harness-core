/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.utils;

import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.steps.CIRegistry;
import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.ci.utils.CIStepInfoUtils;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CIStepInfoUtilsTest extends CIExecutionTestBase {
  @Inject private CIStepInfoUtils ciStepInfoUtils;

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testResolveConnectorFromRegistries() {
    List<CIRegistry> registries = null;
    Optional<String> connector = ciStepInfoUtils.resolveConnectorFromRegistries(registries, null);
    assertThat(connector).isEmpty();

    registries = Arrays.asList(CIRegistry.builder().connectorIdentifier("id1").match("^test/").build(),
        CIRegistry.builder().connectorIdentifier("id2").connectorType(ConnectorType.GCP).build(),
        CIRegistry.builder().connectorIdentifier("id3").connectorType(ConnectorType.AWS).build(),
        CIRegistry.builder().connectorIdentifier("id4").connectorType(ConnectorType.AZURE).build(),
        CIRegistry.builder().connectorIdentifier("id5").connectorType(ConnectorType.DOCKER).build());
    connector = ciStepInfoUtils.resolveConnectorFromRegistries(registries, "test/image");
    assertThat(connector).isPresent();
    assertThat(connector.get()).isEqualTo("id1");
    connector = ciStepInfoUtils.resolveConnectorFromRegistries(registries, "test2/image");
    assertThat(connector).isPresent();
    assertThat(connector.get()).isEqualTo("id5");
    connector = ciStepInfoUtils.resolveConnectorFromRegistries(registries, "us.gcr.io/image");
    assertThat(connector).isPresent();
    assertThat(connector.get()).isEqualTo("id2");
    connector = ciStepInfoUtils.resolveConnectorFromRegistries(registries, "account.dkr.ecr.region.amazonaws.com/img");
    assertThat(connector).isPresent();
    assertThat(connector.get()).isEqualTo("id3");
    connector = ciStepInfoUtils.resolveConnectorFromRegistries(registries, "myregistry.azurecr.io/samples/nginx");
    assertThat(connector).isPresent();
    assertThat(connector.get()).isEqualTo("id4");
  }
}
