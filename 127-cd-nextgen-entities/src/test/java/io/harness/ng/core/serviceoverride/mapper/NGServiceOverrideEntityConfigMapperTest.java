/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.serviceoverride.mapper;

import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.NGCoreTestBase;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideConfig;
import io.harness.rule.Owner;

import java.io.InputStream;
import java.util.Scanner;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class NGServiceOverrideEntityConfigMapperTest extends NGCoreTestBase {
  private static final String environmentRef = "environmentRef";
  private static final String serviceRef = "serviceRef";

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void toNGServiceOverrideConfig() {
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile = classLoader.getResourceAsStream("serviceOverrides-with-manifests-and-configs.yaml");

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    NGServiceOverridesEntity serviceOverridesEntity =
        NGServiceOverridesEntity.builder().environmentRef(environmentRef).serviceRef(serviceRef).yaml(yaml).build();

    NGServiceOverrideConfig result =
        NGServiceOverrideEntityConfigMapper.toNGServiceOverrideConfig(serviceOverridesEntity);

    assertThat(result.getServiceOverrideInfoConfig().getEnvironmentRef()).isEqualTo(environmentRef);
    assertThat(result.getServiceOverrideInfoConfig().getServiceRef()).isEqualTo(serviceRef);
    assertThat(result.getServiceOverrideInfoConfig().getConfigFiles().size()).isEqualTo(1);
    assertThat(result.getServiceOverrideInfoConfig().getManifests().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void toNGServiceOverrideConfigWithInvalidManifest() {
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile = classLoader.getResourceAsStream("serviceOverrides-with-invalid-manifest.yaml");

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    NGServiceOverridesEntity serviceOverridesEntity =
        NGServiceOverridesEntity.builder().environmentRef(environmentRef).serviceRef(serviceRef).yaml(yaml).build();

    assertThatThrownBy(() -> NGServiceOverrideEntityConfigMapper.toNGServiceOverrideConfig(serviceOverridesEntity))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Invalid manifest structure provided");
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void toNGServiceOverrideConfigWithInvalidConfig() {
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile = classLoader.getResourceAsStream("serviceOverrides-with-invalid-config.yaml");

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    NGServiceOverridesEntity serviceOverridesEntity =
        NGServiceOverridesEntity.builder().environmentRef(environmentRef).serviceRef(serviceRef).yaml(yaml).build();

    assertThatThrownBy(() -> NGServiceOverrideEntityConfigMapper.toNGServiceOverrideConfig(serviceOverridesEntity))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Invalid config file structure provided");
  }
}
