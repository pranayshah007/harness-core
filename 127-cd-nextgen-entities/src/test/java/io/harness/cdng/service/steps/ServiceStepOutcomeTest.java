/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.service.steps;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.mappers.NGServiceEntityMapper;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ServiceStepOutcomeTest {
  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testFromServiceEntity() {
    ServiceEntity entity = ServiceEntity.builder()
                               .identifier("id")
                               .name("name")
                               .type(ServiceDefinitionType.KUBERNETES)
                               .tag(NGTag.builder().key("k").value("v").build())
                               .description("desc")
                               .build();
    ServiceStepOutcome outcome = ServiceStepOutcome.fromServiceEntity("k8s", entity);
    assertThat(outcome.getIdentifier()).isEqualTo("id");
    assertThat(outcome.getName()).isEqualTo("name");
    assertThat(outcome.getDescription()).isEqualTo("desc");
    assertThat(outcome.getType()).isEqualTo("k8s");
    assertThat(outcome.getTags()).hasSize(1).containsOnlyKeys("k");
    assertThat(outcome.isGitOpsEnabled()).isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testFromServiceEntity_null() {
    assertThat(ServiceStepOutcome.fromServiceEntity("k8s", null)).isNull();
  }
  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testFromServiceStepV2_1() {
    ServiceStepOutcome outcome =
        ServiceStepOutcome.fromServiceStepV2("id", "name", "k8s", "desc", ImmutableMap.of("k", "v"), null);
    assertThat(outcome.getIdentifier()).isEqualTo("id");
    assertThat(outcome.getName()).isEqualTo("name");
    assertThat(outcome.getDescription()).isEqualTo("desc");
    assertThat(outcome.getType()).isEqualTo("k8s");
    assertThat(outcome.getTags()).hasSize(1).containsOnlyKeys("k");
    assertThat(outcome.isGitOpsEnabled()).isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testFromServiceStepV2_2() {
    ServiceStepOutcome outcome =
        ServiceStepOutcome.fromServiceStepV2("id", "name", "k8s", "desc", ImmutableMap.of("k", "v"), Boolean.FALSE);
    assertThat(outcome.getIdentifier()).isEqualTo("id");
    assertThat(outcome.getName()).isEqualTo("name");
    assertThat(outcome.getDescription()).isEqualTo("desc");
    assertThat(outcome.getType()).isEqualTo("k8s");
    assertThat(outcome.getTags()).hasSize(1).containsOnlyKeys("k");
    assertThat(outcome.isGitOpsEnabled()).isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testFromServiceStepV2_3() {
    ServiceStepOutcome outcome =
        ServiceStepOutcome.fromServiceStepV2("id", "name", "k8s", "desc", ImmutableMap.of("k", "v"), Boolean.TRUE);
    assertThat(outcome.getIdentifier()).isEqualTo("id");
    assertThat(outcome.getName()).isEqualTo("name");
    assertThat(outcome.getDescription()).isEqualTo("desc");
    assertThat(outcome.getType()).isEqualTo("k8s");
    assertThat(outcome.getTags()).hasSize(1).containsOnlyKeys("k");
    assertThat(outcome.isGitOpsEnabled()).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.HINGER)
  @Category(UnitTests.class)
  public void testToOutcomeWithServiceRef() {
    ServiceEntity entity = ServiceEntity.builder()
                               .identifier("serviceId")
                               .accountId("accountId")
                               .name("name")
                               .type(ServiceDefinitionType.KUBERNETES)
                               .tag(NGTag.builder().key("k").value("v").build())
                               .gitOpsEnabled(true)
                               .yaml("service:\n"
                                   + "  identifier: \"serviceId\"\n"
                                   + "  gitOpsEnabled: true\n"
                                   + "  serviceDefinition:\n"
                                   + "    type: \"Kubernetes\"\n"
                                   + "    spec:\n"
                                   + "      variables:\n"
                                   + "      - name: \"variable1\"\n"
                                   + "        type: \"String\"\n"
                                   + "        value: \"<+input>\"")
                               .description("desc")
                               .build();

    ServiceStepOutcome outcome = ServiceStepOutcome.fromServiceStepV2(
        entity, NGServiceEntityMapper.toNGServiceConfig(entity).getNgServiceV2InfoConfig());
    assertThat(outcome.getIdentifier()).isEqualTo("account.serviceId");
    assertThat(outcome.getName()).isEqualTo("name");
    assertThat(outcome.getDescription()).isEqualTo("desc");
    assertThat(outcome.getType()).isEqualTo("Kubernetes");
    assertThat(outcome.getTags()).hasSize(1).containsOnlyKeys("k");
    assertThat(outcome.isGitOpsEnabled()).isTrue();
  }
}
