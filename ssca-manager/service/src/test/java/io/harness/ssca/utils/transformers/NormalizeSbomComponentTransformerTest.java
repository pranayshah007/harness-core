/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.utils.transformers;

import static io.harness.rule.OwnerRule.ARPITJ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.SSCAManagerTestBase;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.spec.server.ssca.v1.model.NormalizedSbomComponentDTO;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity;

import com.google.inject.Inject;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;
import org.modelmapper.ModelMapper;

public class NormalizeSbomComponentTransformerTest extends SSCAManagerTestBase {
  private NormalizedSBOMComponentEntity entity;
  private NormalizedSbomComponentDTO dto;

  @Inject ModelMapper modelMapper;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);

    entity = NormalizedSBOMComponentEntity.builder()
                 .packageManager("packageManager")
                 .packageNamespace("packageNamespace")
                 .purl("purl")
                 .patchVersion(1)
                 .minorVersion(2)
                 .majorVersion(3)
                 .artifactURL("artifactUrl")
                 .accountId("accountId")
                 .orgIdentifier("orgIdentifier")
                 .projectIdentifier("projectIdentifier")
                 .artifactId("artifactId")
                 .artifactName("artifactName")
                 .createdOn(Instant.ofEpochMilli(1000l))
                 .orchestrationId("orchestrationId")
                 .originatorType("originatorType")
                 .packageCPE("packageCPE")
                 .packageDescription("packageDescription")
                 .packageId("packageId")
                 .packageLicense(Arrays.asList("license1", "license2"))
                 .packageName("packageName")
                 .packageOriginatorName("packageOriginatorName")
                 .packageProperties("packageProperties")
                 .packageSourceInfo("packageSourceInfo")
                 .packageSupplierName("packageSupplierName")
                 .packageType("packageType")
                 .packageVersion("packageVersion")
                 .sbomVersion("sbomVersion")
                 .pipelineIdentifier("pipelineidentifier")
                 .sequenceId("sequenceId")
                 .tags(Arrays.asList("tag1", "tag2"))
                 .toolName("toolName")
                 .toolVendor("toolVendor")
                 .toolVersion("toolVersion")
                 .build();

    dto = new NormalizedSbomComponentDTO()
              .packageManager("packageManager")
              .packageNamespace("packageNamespace")
              .purl("purl")
              .patchVersion(new BigDecimal(1))
              .minorVersion(new BigDecimal(2))
              .majorVersion(new BigDecimal(3))
              .artifactUrl("artifactUrl")
              .accountId("accountId")
              .orgIdentifier("orgIdentifier")
              .projectIdentifier("projectIdentifier")
              .artifactId("artifactId")
              .artifactName("artifactName")
              .created(new BigDecimal(1000l))
              .orchestrationId("orchestrationId")
              .originatorType("originatorType")
              .packageCpe("packageCPE")
              .packageDescription("packageDescription")
              .packageId("packageId")
              .packageLicense(Arrays.asList("license1", "license2"))
              .packageName("packageName")
              .packageOriginatorName("packageOriginatorName")
              .packageProperties("packageProperties")
              .packageSourceInfo("packageSourceInfo")
              .packageSupplierName("packageSupplierName")
              .packageType("packageType")
              .packageVersion("packageVersion")
              .sbomVersion("sbomVersion")
              .pipelineIdentifier("pipelineidentifier")
              .sequenceId("sequenceId")
              .tags(Arrays.asList("tag1", "tag2"))
              .toolName("toolName")
              .toolVendor("toolVendor")
              .toolVersion("toolVersion");
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testToEntity() {
    NormalizedSBOMComponentEntity normalizedSBOMComponentEntity =
        modelMapper.map(dto, NormalizedSBOMComponentEntity.class);

    assertThat(normalizedSBOMComponentEntity.equals(entity)).isEqualTo(true);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testToDTO() {
    NormalizedSbomComponentDTO normalizedSbomComponentDTO = modelMapper.map(entity, NormalizedSbomComponentDTO.class);
    assertThat(normalizedSbomComponentDTO.equals(dto)).isEqualTo(true);
  }
}
