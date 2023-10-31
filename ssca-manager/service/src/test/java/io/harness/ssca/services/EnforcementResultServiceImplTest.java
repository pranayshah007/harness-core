/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import static io.harness.rule.OwnerRule.ARPITJ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.BuilderFactory;
import io.harness.SSCAManagerTestBase;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.ssca.beans.AllowLicense;
import io.harness.ssca.beans.AllowList.AllowListItem;
import io.harness.ssca.beans.AllowList.AllowListRuleType;
import io.harness.ssca.beans.DenyList.DenyListItem;
import io.harness.ssca.beans.Supplier;
import io.harness.ssca.entities.EnforcementResultEntity;

import com.google.inject.Inject;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

public class EnforcementResultServiceImplTest extends SSCAManagerTestBase {
  @Inject EnforcementResultService enforcementResultService;

  private BuilderFactory builderFactory;

  @Before
  public void setup() throws IllegalAccessException {
    MockitoAnnotations.initMocks(this);
    builderFactory = BuilderFactory.getDefault();
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetEnforcementResults() {
    EnforcementResultEntity entity =
        enforcementResultService.getEnforcementResults(builderFactory.getNormalizedSBOMComponentBuilder().build(),
            "Deny_List", "zlib is denied", builderFactory.getArtifactEntityBuilder().build(), "enforcementId");
    assertThat(entity.getEnforcementID()).isEqualTo("enforcementId");
    assertThat(entity.getArtifactId()).isEqualTo("artifactId");
    assertThat(entity.getTag()).isEqualTo("tag");
    assertThat(entity.getImageName()).isEqualTo("test/image");
    assertThat(entity.getAccountId()).isEqualTo(builderFactory.getContext().getAccountId());
    assertThat(entity.getOrgIdentifier()).isEqualTo(builderFactory.getContext().getOrgIdentifier());
    assertThat(entity.getProjectIdentifier()).isEqualTo(builderFactory.getContext().getProjectIdentifier());
    assertThat(entity.getOrchestrationID()).isEqualTo("stepExecutionId");
    assertThat(entity.getViolationType()).isEqualTo("Deny_List");
    assertThat(entity.getViolationDetails()).isEqualTo("zlib is denied");
    assertThat(entity.getName()).isEqualTo("packageName");
    assertThat(entity.getVersion()).isEqualTo("packageVersion");
    assertThat(entity.getSupplier()).isEqualTo("packageOriginatorName");
    assertThat(entity.getSupplierType()).isEqualTo("originatorType");
    assertThat(entity.getPackageManager()).isEqualTo("packageManager");
    assertThat(entity.getLicense()).isEqualTo(Arrays.asList("license1", "license2"));
    assertThat(entity.getPurl()).isEqualTo("purl");
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetViolationDetails_denyList() {
    String violation = enforcementResultService.getViolationDetails(
        builderFactory.getNormalizedSBOMComponentBuilder().packageName("nginx").build(),
        DenyListItem.builder().license("GPL-2.0").supplier("Busybox").build());
    assertThat(violation).isEqualTo("nginx is denied: supplier is Busybox, license is GPL-2.0");
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetViolationDetails_allowListLicense() {
    AllowListItem allowListItem =
        AllowListItem.builder()
            .licenses(Arrays.asList(AllowLicense.builder().license("GPL-2.0").name("zlib").build(),
                AllowLicense.builder().license("MIT").build(), AllowLicense.builder().license("Apache").build()))
            .build();
    String violation = enforcementResultService.getViolationDetails(
        builderFactory.getNormalizedSBOMComponentBuilder().packageName("nginx").build(), allowListItem,
        AllowListRuleType.ALLOW_LICENSE_ITEM);
    assertThat(violation).isEqualTo("License for nginx needs to be: MIT or Apache, but got: [license1, license2]");

    allowListItem =
        AllowListItem.builder()
            .licenses(Arrays.asList(AllowLicense.builder().license("GPL-2.0").name("nginx").build(),
                AllowLicense.builder().license("MIT").build(), AllowLicense.builder().license("Apache").build()))
            .build();
    violation = enforcementResultService.getViolationDetails(
        builderFactory.getNormalizedSBOMComponentBuilder().packageName("nginx").build(), allowListItem,
        AllowListRuleType.ALLOW_LICENSE_ITEM);
    assertThat(violation).isEqualTo("License for nginx needs to be: GPL-2.0, but got: [license1, license2]");

    allowListItem =
        AllowListItem.builder()
            .licenses(Arrays.asList(AllowLicense.builder().license("!GPL-2.0").name("nginx").build(),
                AllowLicense.builder().license("MIT").build(), AllowLicense.builder().license("Apache").build()))
            .build();
    violation = enforcementResultService.getViolationDetails(
        builderFactory.getNormalizedSBOMComponentBuilder().packageName("nginx").build(), allowListItem,
        AllowListRuleType.ALLOW_LICENSE_ITEM);
    assertThat(violation).isEqualTo("License for nginx should not be GPL-2.0");
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetViolationDetails_allowListSupplier() {
    AllowListItem allowListItem =
        AllowListItem.builder()
            .suppliers(Arrays.asList(Supplier.builder().supplier("GPL-2.0").name("zlib").build(),
                Supplier.builder().supplier("MIT").build(), Supplier.builder().supplier("Apache").build()))
            .build();
    String violation = enforcementResultService.getViolationDetails(
        builderFactory.getNormalizedSBOMComponentBuilder().packageName("nginx").build(), allowListItem,
        AllowListRuleType.ALLOW_SUPPLIER_ITEM);
    assertThat(violation).isEqualTo("Supplier for nginx needs to be: MIT or Apache, but got: packageOriginatorName");

    allowListItem = AllowListItem.builder()
                        .suppliers(Arrays.asList(Supplier.builder().supplier("GPL-2.0").name("nginx").build(),
                            Supplier.builder().supplier("MIT").build(), Supplier.builder().supplier("Apache").build()))
                        .build();
    violation = enforcementResultService.getViolationDetails(
        builderFactory.getNormalizedSBOMComponentBuilder().packageName("nginx").build(), allowListItem,
        AllowListRuleType.ALLOW_SUPPLIER_ITEM);
    assertThat(violation).isEqualTo("Supplier for nginx needs to be: GPL-2.0, but got: packageOriginatorName");

    allowListItem = AllowListItem.builder()
                        .suppliers(Arrays.asList(Supplier.builder().supplier("!GPL-2.0").name("nginx").build(),
                            Supplier.builder().supplier("MIT").build(), Supplier.builder().supplier("Apache").build()))
                        .build();
    violation = enforcementResultService.getViolationDetails(
        builderFactory.getNormalizedSBOMComponentBuilder().packageName("nginx").build(), allowListItem,
        AllowListRuleType.ALLOW_SUPPLIER_ITEM);
    assertThat(violation).isEqualTo("Supplier for nginx should not be GPL-2.0");
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetViolationDetails_allowListPurl() {
    AllowListItem allowListItem =
        AllowListItem.builder().purls(Arrays.asList("purl/nginx/tag", "purl/google/v2")).build();
    String violation = enforcementResultService.getViolationDetails(
        builderFactory.getNormalizedSBOMComponentBuilder().packageName("nginx").purl("purl/nginx/v1").build(),
        allowListItem, AllowListRuleType.ALLOW_PURL_ITEM);
    assertThat(violation).isEqualTo("Purl for nginx needs to be of the format purl/nginx/tag, but got: purl/nginx/v1");
  }
}
