/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resourcegroup.framework.v2.remote.mapper;

import static io.harness.rule.OwnerRule.REETIKA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.resourcegroup.ResourceGroupTestBase;
import io.harness.resourcegroup.beans.ScopeFilterType;
import io.harness.resourcegroup.model.ResourceSelector;
import io.harness.resourcegroup.model.ResourceSelectorByScope;
import io.harness.resourcegroup.v1.model.ResourceGroup;
import io.harness.resourcegroup.v1.remote.dto.ResourceGroupDTO;
import io.harness.resourcegroup.v2.model.ScopeSelector;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.serializer.HObjectMapper;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
public class ResourceGroupMapperTest extends ResourceGroupTestBase {
  private ObjectMapper objectMapper;

  @Before
  public void setup() {
    objectMapper = new ObjectMapper();
    HObjectMapper.configureObjectMapperForNG(objectMapper);
  }

  public static String readFileAsString(String file) {
    try {
      return new String(Files.readAllBytes(Paths.get(file)));
    } catch (Exception ex) {
      Assert.fail("Failed reading the json from " + file + " with error " + ex.getMessage());
      return "";
    }
  }

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void testManagedRGV1ToV2() {
    String accountLevelResourceGroup = readFileAsString(
        "platform-service/modules/resource-group-service/src/test/resources/resourcegroups/v1/AllResourcesIncludingChildScopes.json");
    ResourceGroup resourceGroupV1 = null;
    try {
      ResourceGroupDTO resourceGroupV1DTO = objectMapper.readValue(accountLevelResourceGroup, ResourceGroupDTO.class);
      resourceGroupV1 =
          io.harness.resourcegroup.framework.v1.remote.mapper.ResourceGroupMapper.fromDTO(resourceGroupV1DTO);
      resourceGroupV1.setHarnessManaged(true);

    } catch (Exception ex) {
      Assert.fail("Encountered exception while deserializing resource group " + ex.getMessage());
    }
    try {
      ResourceGroupMapper.fromV1(resourceGroupV1);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while converting resource group  V1 to V2" + ex.getMessage());
    }
  }

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void testCustomRGV1ToV2() {
    String customResourceGroupV1 = readFileAsString(
        "platform-service/modules/resource-group-service/src/main/resources/io/harness/resourcegroup/v1/customResourceGroup.json");
    String customResourceGroupV2 = readFileAsString(
        "platform-service/modules/resource-group-service/src/main/resources/io/harness/resourcegroup/v2/customResourceGroup.json");
    ResourceGroup resourceGroupV1 = null;
    io.harness.resourcegroup.v2.remote.dto.ResourceGroupDTO resourceGroupV2DTO = null;
    try {
      ResourceGroupDTO resourceGroupV1DTO = objectMapper.readValue(customResourceGroupV1, ResourceGroupDTO.class);
      resourceGroupV2DTO =
          objectMapper.readValue(customResourceGroupV2, io.harness.resourcegroup.v2.remote.dto.ResourceGroupDTO.class);
      resourceGroupV1 =
          io.harness.resourcegroup.framework.v1.remote.mapper.ResourceGroupMapper.fromDTO(resourceGroupV1DTO);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while deserializing resource group " + ex.getMessage());
    }
    try {
      io.harness.resourcegroup.v2.remote.dto.ResourceGroupDTO convertedResourceGroupDTO =
          ResourceGroupMapper.toDTO(ResourceGroupMapper.fromV1(resourceGroupV1));
      assertThat(convertedResourceGroupDTO).isEqualTo(resourceGroupV2DTO);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while converting resource group  V1 to V2" + ex.getMessage());
    }
  }

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void testCustomRGV2ToV1() {
    String customResourceGroupV1 = readFileAsString(
        "platform-service/modules/resource-group-service/src/main/resources/io/harness/resourcegroup/v1/newResourceGroup.json");
    String customResourceGroupV2 = readFileAsString(
        "platform-service/modules/resource-group-service/src/main/resources/io/harness/resourcegroup/v2/newResourceGroup.json");
    ResourceGroupDTO resourceGroupV1DTO = null;
    io.harness.resourcegroup.v2.remote.dto.ResourceGroupDTO resourceGroupV2DTO = null;
    try {
      resourceGroupV1DTO = objectMapper.readValue(customResourceGroupV1, ResourceGroupDTO.class);
      resourceGroupV2DTO =
          objectMapper.readValue(customResourceGroupV2, io.harness.resourcegroup.v2.remote.dto.ResourceGroupDTO.class);
      io.harness.resourcegroup.framework.v1.remote.mapper.ResourceGroupMapper.fromDTO(resourceGroupV1DTO);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while deserializing resource group " + ex.getMessage());
    }
    try {
      ResourceGroupDTO convertedResourceGroupDTO =
          ResourceGroupMapper.toV1DTO(ResourceGroupMapper.fromDTO(resourceGroupV2DTO), false);
      assertThat(convertedResourceGroupDTO).isEqualTo(resourceGroupV1DTO);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while converting resource group  V1 to V2" + ex.getMessage());
    }
  }

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void testCustomRGV2ToV1ScopeCheck() {
    String customResourceGroupV1 = readFileAsString(
        "platform-service/modules/resource-group-service/src/main/resources/io/harness/resourcegroup/v1/selectorByScope.json");
    String customResourceGroupV2 = readFileAsString(
        "platform-service/modules/resource-group-service/src/main/resources/io/harness/resourcegroup/v2/selectorByScope.json");
    ResourceGroupDTO resourceGroupV1DTO = null;
    io.harness.resourcegroup.v2.remote.dto.ResourceGroupDTO resourceGroupV2DTO = null;
    try {
      resourceGroupV1DTO = objectMapper.readValue(customResourceGroupV1, ResourceGroupDTO.class);
      resourceGroupV2DTO =
          objectMapper.readValue(customResourceGroupV2, io.harness.resourcegroup.v2.remote.dto.ResourceGroupDTO.class);
      io.harness.resourcegroup.framework.v1.remote.mapper.ResourceGroupMapper.fromDTO(resourceGroupV1DTO);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while deserializing resource group " + ex.getMessage());
    }
    try {
      ResourceGroupDTO convertedResourceGroupDTO =
          ResourceGroupMapper.toV1DTO(ResourceGroupMapper.fromDTO(resourceGroupV2DTO), false);
      assertThat(convertedResourceGroupDTO).isEqualTo(resourceGroupV1DTO);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while converting resource group  V1 to V2" + ex.getMessage());
    }
    try {
      resourceGroupV2DTO.setAccountIdentifier(null);
      resourceGroupV2DTO.setIncludedScopes(
          Arrays.asList(ScopeSelector.builder().filter(ScopeFilterType.EXCLUDING_CHILD_SCOPES).build()));
      ResourceGroupDTO convertedResourceGroupDTO =
          ResourceGroupMapper.toV1DTO(ResourceGroupMapper.fromDTO(resourceGroupV2DTO), true);
      assertThat(convertedResourceGroupDTO.getResourceSelectors().size()).isEqualTo(1);
      ResourceSelector resourceSelector = convertedResourceGroupDTO.getResourceSelectors().get(0);
      assertThat(((ResourceSelectorByScope) resourceSelector).getScope()).isEqualTo(null);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while converting resource group  V1 to V2" + ex.getMessage());
    }
  }
}
