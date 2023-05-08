/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.serviceoverride.mapper;

import static io.harness.rule.OwnerRule.LOVISH_BANSAL;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.NGCoreTestBase;
import io.harness.ng.core.serviceoverride.beans.ServiceOverrideRequestDTO;
import io.harness.rule.Owner;
import io.harness.yaml.core.variables.NGServiceOverrides;

import java.io.InputStream;
import java.util.Scanner;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDC)
public class ServiceOverrideMapperTest extends NGCoreTestBase {
  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void toServiceOverrides() {
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile = classLoader.getResourceAsStream("serviceOverride.yml");
    assertThat(yamlFile).isNotNull();

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    NGServiceOverrides ngServiceOverrides = ServiceOverridesMapper.toServiceOverrides(yaml);

    assertThat(ngServiceOverrides.getServiceRef()).isNotNull();
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void toServiceOverridesThrowsExceptionIfEnvIDIsNotProjectLevel() {
    ServiceOverrideRequestDTO serviceOverrideRequestDTO = ServiceOverrideRequestDTO.builder()
                                                              .environmentIdentifier("org.env")
                                                              .projectIdentifier("projectIdentifier")
                                                              .orgIdentifier("orgIdentifier")
                                                              .build();
    assertThatThrownBy(() -> ServiceOverridesMapper.toServiceOverridesEntity("accountId", serviceOverrideRequestDTO))
        .hasMessageContaining(
            "Project Identifier should not be passed when environment used in service override is at organisation or account scope");
  }
}
