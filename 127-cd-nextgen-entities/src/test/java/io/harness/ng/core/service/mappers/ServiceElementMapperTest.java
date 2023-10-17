/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.mappers;

import static io.harness.rule.OwnerRule.ABHINAV2;
import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;
import static io.harness.rule.OwnerRule.YOGESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.service.dto.ServiceRequestDTO;
import io.harness.ng.core.service.dto.ServiceResponseDTO;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class ServiceElementMapperTest extends CategoryTest {
  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  @Parameters(method = "getDataForTestToServiceEntity")
  public void testToServiceEntity(ServiceRequestDTO input, ServiceEntity output) {
    ServiceEntity mappedService = ServiceElementMapper.toServiceEntity("ACCOUNT_ID", input);
    assertThat(mappedService).isNotNull();
    assertThat(mappedService).isEqualTo(output);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testToServiceEntityCheckDescriptionAndTags() {
    Map<String, String> tags = new HashMap<>();
    tags.put("k1", "v1");
    tags.put("k2", "v2");
    ServiceRequestDTO serviceRequestDTO = ServiceRequestDTO.builder()
                                              .identifier("IDENTIFIER")
                                              .orgIdentifier("ORG_ID")
                                              .projectIdentifier("PROJECT_ID")
                                              .description("svc description")
                                              .tags(new HashMap<>())
                                              .name("Service")
                                              .tags(ImmutableMap.of("k1", "v1", "k2", "v2"))
                                              .yaml("service:\n"
                                                  + " name: Service\n"
                                                  + " identifier: IDENTIFIER\n"
                                                  + " orgIdentifier: ORG_ID\n"
                                                  + " projectIdentifier: PROJECT_ID\n"
                                                  + " gitOpsEnabled: false\n"
                                                  + " serviceDefinition:\n"
                                                  + "    type: \"Kubernetes\"\n"
                                                  + "    spec:\n"
                                                  + "        variables: []\n"
                                                  + "        artifacts:\n"
                                                  + "            primary:\n"
                                                  + "                spec:\n"
                                                  + "                    connectorRef: \"account.harnessImage\"\n"
                                                  + "                    imagePath: \"nginx\"\n"
                                                  + "                    tag: \"latest\"\n"
                                                  + "                type: \"DockerRegistry\"\n")
                                              .build();
    ServiceEntity mappedService = ServiceElementMapper.toServiceEntity("ACCOUNT_ID", serviceRequestDTO);
    assertThat(mappedService).isNotNull();
    assertThat(mappedService.getDescription()).isEqualTo("svc description");
    assertThat(mappedService.getTags().size()).isEqualTo(2);
    assertThat(mappedService.getTags().get(0).getKey()).isEqualTo("k1");
    assertThat(mappedService.getTags().get(0).getValue()).isEqualTo("v1");
    assertThat(mappedService.getTags().get(1).getKey()).isEqualTo("k2");
    assertThat(mappedService.getTags().get(1).getValue()).isEqualTo("v2");
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  @Parameters(method = "getDataForMultipleManifestsFailureTest")
  public void testToServiceEntityFailureCheck(ServiceRequestDTO input) {
    ServiceRequestDTO serviceRequestDTO = ServiceRequestDTO.builder()
                                              .identifier("IDENTIFIER")
                                              .orgIdentifier("ORG_ID")
                                              .projectIdentifier("PROJECT_ID")
                                              .description("svc description")
                                              .tags(new HashMap<>())
                                              .name("Service")
                                              .tags(ImmutableMap.of("k1", "v1", "k2", "v2"))
                                              .yaml("service:\n"
                                                  + " name: Service\n"
                                                  + " identifier: DIFF_IDENTIFIER\n"
                                                  + " orgIdentifier: ORG_ID\n"
                                                  + " projectIdentifier: PROJECT_ID\n"
                                                  + " gitOpsEnabled: false\n"
                                                  + " serviceDefinition:\n"
                                                  + "    type: \"Kubernetes\"\n"
                                                  + "    spec\n"
                                                  + "        variables: []\n"
                                                  + "        arti\nnectorRef: \"account.harnessImage\"\n"
                                                  + "                    imagePath: \"nginx\"\n"
                                                  + "                    tag: \"latest\"\n"
                                                  + "                type: \"DockerRegistry\"\n")
                                              .build();
    assertThatThrownBy(() -> ServiceElementMapper.toServiceEntity("ACCOUNT_ID", serviceRequestDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Cannot create service ng with Identifier : IDENTIFIER service config due to");
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  @Parameters(method = "getDataForMultipleManifestsFailureTest")
  public void testToServiceEntity(ServiceRequestDTO input) {
    assertThatThrownBy(() -> ServiceElementMapper.toServiceEntity("ACCOUNT_ID", input))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Multiple manifests found")
        .hasMessageContaining("deployment support only one manifest of one of types");
  }

  @Test
  @Owner(developers = YOGESH)
  @Parameters(method = "getDataForTestWriteDTO")
  @Category(UnitTests.class)
  public void testWriteDTO(ServiceEntity input, ServiceResponseDTO output) {
    ServiceResponseDTO serviceResponseDTO = ServiceElementMapper.writeDTO(input);
    assertThat(serviceResponseDTO).isNotNull();
    assertThat(serviceResponseDTO).isEqualTo(output);
  }

  // Method to provide parameters to test
  private Object[] getDataForMultipleManifestsFailureTest() {
    ServiceRequestDTO requestDTO1 = ServiceRequestDTO.builder()
                                        .identifier("k1")
                                        .orgIdentifier("ORG_ID")
                                        .projectIdentifier("PROJECT_ID")
                                        .name("k1")
                                        .tags(ImmutableMap.of("k1", "v1", "k2", "v2"))
                                        .yaml("service:\n"
                                            + "  name: k1\n"
                                            + "  identifier: k1\n"
                                            + "  tags: {}\n"
                                            + "  serviceDefinition:\n"
                                            + "    spec:\n"
                                            + "      manifests:\n"
                                            + "        - manifest:\n"
                                            + "            identifier: m1\n"
                                            + "            type: K8sManifest\n"
                                            + "            spec:\n"
                                            + "              store:\n"
                                            + "                type: Harness\n"
                                            + "                spec:\n"
                                            + "                  files:\n"
                                            + "                    - /helm-chart1/templates\n"
                                            + "              valuesPaths: []\n"
                                            + "              skipResourceVersioning: false\n"
                                            + "        - manifest:\n"
                                            + "            identifier: m2\n"
                                            + "            type: K8sManifest\n"
                                            + "            spec:\n"
                                            + "              store:\n"
                                            + "                type: Harness\n"
                                            + "                spec:\n"
                                            + "                  files:\n"
                                            + "                    - /k8s-render/templates\n"
                                            + "              valuesPaths: []\n"
                                            + "              skipResourceVersioning: false\n"
                                            + "    type: Kubernetes\n")
                                        .build();

    return new Object[] {requestDTO1};
  }

  private Object[][] getDataForTestToServiceEntity() {
    List<NGTag> tags_1 =
        Arrays.asList(NGTag.builder().key("k1").value("v1").build(), NGTag.builder().key("k2").value("v2").build());
    ServiceRequestDTO serviceRequestDTO_1 =
        ServiceRequestDTO.builder()
            .identifier("IDENTIFIER")
            .orgIdentifier("ORG_ID")
            .projectIdentifier("PROJECT_ID")
            .name("Service")
            .tags(ImmutableMap.of("k1", "v1", "k2", "v2"))
            .yaml("service:\n  name: \"Service\"\n  identifier: \"IDENTIFIER\"\n  "
                + "orgIdentifier: \"ORG_ID\"\n  projectIdentifier: \"PROJECT_ID\"\n  tags:\n    "
                + "k1: \"v1\"\n    k2: \"v2\"\n")
            .build();
    ServiceEntity responseServiceEntity_1 = ServiceEntity.builder()
                                                .accountId("ACCOUNT_ID")
                                                .identifier("IDENTIFIER")
                                                .orgIdentifier("ORG_ID")
                                                .projectIdentifier("PROJECT_ID")
                                                .name("Service")
                                                .deleted(false)
                                                .tags(tags_1)
                                                .yaml("service:\n"
                                                    + "  name: \"Service\"\n"
                                                    + "  identifier: \"IDENTIFIER\"\n"
                                                    + "  orgIdentifier: \"ORG_ID\"\n"
                                                    + "  projectIdentifier: \"PROJECT_ID\"\n"
                                                    + "  tags:\n"
                                                    + "    k1: \"v1\"\n"
                                                    + "    k2: \"v2\"\n")
                                                .build();

    ServiceRequestDTO serviceRequestDTO_2 = ServiceRequestDTO.builder()
                                                .identifier("IDENTIFIER")
                                                .orgIdentifier("ORG_ID")
                                                .projectIdentifier("PROJECT_ID")
                                                .name("Service")
                                                .tags(ImmutableMap.of("k1", "v1", "k2", "v2"))
                                                .yaml("service:\n"
                                                    + " name: Service\n"
                                                    + " identifier: IDENTIFIER\n"
                                                    + " orgIdentifier: ORG_ID\n"
                                                    + " projectIdentifier: PROJECT_ID\n"
                                                    + " gitOpsEnabled: false\n"
                                                    + " tags:\n"
                                                    + "   k1: v1\n"
                                                    + "   k2: v2\n"
                                                    + " serviceDefinition:\n"
                                                    + "    type: \"Kubernetes\"\n"
                                                    + "    spec:\n"
                                                    + "        variables: []\n"
                                                    + "        artifacts:\n"
                                                    + "            primary:\n"
                                                    + "                spec:\n"
                                                    + "                    connectorRef: \"account.harnessImage\"\n"
                                                    + "                    imagePath: \"nginx\"\n"
                                                    + "                    tag: \"latest\"\n"
                                                    + "                type: \"DockerRegistry\"\n")
                                                .build();
    ServiceEntity responseServiceEntity_2 = ServiceEntity.builder()
                                                .accountId("ACCOUNT_ID")
                                                .identifier("IDENTIFIER")
                                                .orgIdentifier("ORG_ID")
                                                .projectIdentifier("PROJECT_ID")
                                                .name("Service")
                                                .deleted(false)
                                                .tags(tags_1)
                                                .type(ServiceDefinitionType.KUBERNETES)
                                                .gitOpsEnabled(false)
                                                .yaml("service:\n"
                                                    + " name: Service\n"
                                                    + " identifier: IDENTIFIER\n"
                                                    + " orgIdentifier: ORG_ID\n"
                                                    + " projectIdentifier: PROJECT_ID\n"
                                                    + " gitOpsEnabled: false\n"
                                                    + " tags:\n"
                                                    + "   k1: v1\n"
                                                    + "   k2: v2\n"
                                                    + " serviceDefinition:\n"
                                                    + "    type: \"Kubernetes\"\n"
                                                    + "    spec:\n"
                                                    + "        variables: []\n"
                                                    + "        artifacts:\n"
                                                    + "            primary:\n"
                                                    + "                spec:\n"
                                                    + "                    connectorRef: \"account.harnessImage\"\n"
                                                    + "                    imagePath: \"nginx\"\n"
                                                    + "                    tag: \"latest\"\n"
                                                    + "                type: \"DockerRegistry\"\n")
                                                .build();

    ServiceRequestDTO serviceRequestDTO_3 = ServiceRequestDTO.builder()
                                                .identifier("IDENTIFIER")
                                                .orgIdentifier("ORG_ID")
                                                .projectIdentifier("PROJECT_ID")
                                                .name("Service")
                                                .tags(ImmutableMap.of("k1", "v1", "k2", "v2"))
                                                .yaml("service:\n"
                                                    + " name: Service\n"
                                                    + " identifier: IDENTIFIER\n"
                                                    + " orgIdentifier: ORG_ID\n"
                                                    + " projectIdentifier: PROJECT_ID\n"
                                                    + " gitOpsEnabled: true\n"
                                                    + " tags:\n"
                                                    + "   k1: v1\n"
                                                    + "   k2: v2\n"
                                                    + " serviceDefinition:\n"
                                                    + "    type: \"Kubernetes\"\n"
                                                    + "    spec:\n"
                                                    + "        variables: []\n"
                                                    + "        artifacts:\n"
                                                    + "            primary:\n"
                                                    + "                spec:\n"
                                                    + "                    connectorRef: \"account.harnessImage\"\n"
                                                    + "                    imagePath: \"nginx\"\n"
                                                    + "                    tag: \"latest\"\n"
                                                    + "                type: \"DockerRegistry\"\n")
                                                .build();
    ServiceEntity responseServiceEntity_3 = ServiceEntity.builder()
                                                .accountId("ACCOUNT_ID")
                                                .identifier("IDENTIFIER")
                                                .orgIdentifier("ORG_ID")
                                                .projectIdentifier("PROJECT_ID")
                                                .name("Service")
                                                .deleted(false)
                                                .tags(tags_1)
                                                .type(ServiceDefinitionType.KUBERNETES)
                                                .gitOpsEnabled(true)
                                                .yaml("service:\n"
                                                    + " name: Service\n"
                                                    + " identifier: IDENTIFIER\n"
                                                    + " orgIdentifier: ORG_ID\n"
                                                    + " projectIdentifier: PROJECT_ID\n"
                                                    + " gitOpsEnabled: true\n"
                                                    + " tags:\n"
                                                    + "   k1: v1\n"
                                                    + "   k2: v2\n"
                                                    + " serviceDefinition:\n"
                                                    + "    type: \"Kubernetes\"\n"
                                                    + "    spec:\n"
                                                    + "        variables: []\n"
                                                    + "        artifacts:\n"
                                                    + "            primary:\n"
                                                    + "                spec:\n"
                                                    + "                    connectorRef: \"account.harnessImage\"\n"
                                                    + "                    imagePath: \"nginx\"\n"
                                                    + "                    tag: \"latest\"\n"
                                                    + "                type: \"DockerRegistry\"\n")
                                                .build();

    return new Object[][] {{serviceRequestDTO_1, responseServiceEntity_1},
        {serviceRequestDTO_2, responseServiceEntity_2}, {serviceRequestDTO_3, responseServiceEntity_3}};
  }

  // Method to provide parameters to test
  private Object[][] getDataForTestWriteDTO() {
    List<NGTag> tags_1 =
        Arrays.asList(NGTag.builder().key("k1").value("v1").build(), NGTag.builder().key("k2").value("v2").build());
    ServiceResponseDTO ServiceResponseDTO_1 = io.harness.ng.core.service.dto.ServiceResponseDTO.builder()
                                                  .accountId("ACCOUNT_ID")
                                                  .identifier("IDENTIFIER")
                                                  .orgIdentifier("ORG_ID")
                                                  .projectIdentifier("PROJECT_ID")
                                                  .name("Service")
                                                  .deleted(false)
                                                  .tags(ImmutableMap.of("k1", "v1", "k2", "v2"))
                                                  .yaml("service:\n"
                                                      + "  name: \"Service\"\n"
                                                      + "  identifier: \"IDENTIFIER\"\n"
                                                      + "  orgIdentifier: \"ORG_ID\"\n"
                                                      + "  projectIdentifier: \"PROJECT_ID\"\n"
                                                      + "  tags:\n"
                                                      + "    k1: \"v1\"\n"
                                                      + "    k2: \"v2\"\n")
                                                  .build();

    ServiceEntity requestServiceEntity_1 =
        ServiceEntity.builder()
            .accountId("ACCOUNT_ID")
            .identifier("IDENTIFIER")
            .orgIdentifier("ORG_ID")
            .projectIdentifier("PROJECT_ID")
            .name("Service")
            .deleted(false)
            .tags(tags_1)
            .yaml("service:\n  name: \"Service\"\n  identifier: \"IDENTIFIER\"\n  "
                + "orgIdentifier: \"ORG_ID\"\n  projectIdentifier: \"PROJECT_ID\"\n  tags:\n    "
                + "k1: \"v1\"\n    k2: \"v2\"\n")
            .build();

    ServiceResponseDTO ServiceResponseDTO_2 = ServiceResponseDTO.builder()
                                                  .accountId("ACCOUNT_ID")
                                                  .identifier("IDENTIFIER")
                                                  .orgIdentifier("ORG_ID")
                                                  .projectIdentifier("PROJECT_ID")
                                                  .name("Service")
                                                  .deleted(false)
                                                  .tags(ImmutableMap.of("k1", "v1", "k2", "v2"))
                                                  .yaml("service:\n"
                                                      + " name: Service\n"
                                                      + " identifier: IDENTIFIER\n"
                                                      + " orgIdentifier: ORG_ID\n"
                                                      + " projectIdentifier: PROJECT_ID\n"
                                                      + " tags:\n"
                                                      + "   k1: v1\n"
                                                      + "   k2: v2\n"
                                                      + " gitOpsEnabled: true\n"
                                                      + " serviceDefinition:\n"
                                                      + "    type: \"Kubernetes\"\n"
                                                      + "    spec:\n"
                                                      + "        variables: []\n"
                                                      + "        artifacts:\n"
                                                      + "            primary:\n"
                                                      + "                spec:\n"
                                                      + "                    connectorRef: \"account.harnessImage\"\n"
                                                      + "                    imagePath: \"nginx\"\n"
                                                      + "                    tag: \"latest\"\n"
                                                      + "                type: \"DockerRegistry\"\n")
                                                  .build();

    ServiceEntity requestServiceEntity_2 = ServiceEntity.builder()
                                               .accountId("ACCOUNT_ID")
                                               .identifier("IDENTIFIER")
                                               .orgIdentifier("ORG_ID")
                                               .projectIdentifier("PROJECT_ID")
                                               .name("Service")
                                               .deleted(false)
                                               .tags(tags_1)
                                               .gitOpsEnabled(true)
                                               .yaml("service:\n"
                                                   + " name: Service\n"
                                                   + " identifier: IDENTIFIER\n"
                                                   + " orgIdentifier: ORG_ID\n"
                                                   + " projectIdentifier: PROJECT_ID\n"
                                                   + " tags:\n"
                                                   + "   k1: v1\n"
                                                   + "   k2: v2\n"
                                                   + " gitOpsEnabled: true\n"
                                                   + " serviceDefinition:\n"
                                                   + "    type: \"Kubernetes\"\n"
                                                   + "    spec:\n"
                                                   + "        variables: []\n"
                                                   + "        artifacts:\n"
                                                   + "            primary:\n"
                                                   + "                spec:\n"
                                                   + "                    connectorRef: \"account.harnessImage\"\n"
                                                   + "                    imagePath: \"nginx\"\n"
                                                   + "                    tag: \"latest\"\n"
                                                   + "                type: \"DockerRegistry\"\n")
                                               .build();
    return new Object[][] {
        {requestServiceEntity_1, ServiceResponseDTO_1}, {requestServiceEntity_2, ServiceResponseDTO_2}};
  }
}
