/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.helpers;

import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.plan.VariablesCreationBlobResponse;
import io.harness.pms.contracts.plan.YamlUpdates;
import io.harness.pms.variables.VariableCreationBlobResponseUtils;
import io.harness.rule.Owner;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.UserPrincipal;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

public class CurrentUserHelperTest extends CategoryTest {
  @InjectMocks CurrentUserHelper currentUserHelper;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetPrincipalFromSecurityContextNull() {
    assertThatThrownBy(() -> currentUserHelper.getPrincipalFromSecurityContext())
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Unable to fetch current user");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetPrincipalFromSecurityContext() {
    Principal principal = new UserPrincipal("1", "", "", "acc");

    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
    assertThat(currentUserHelper.getPrincipalFromSecurityContext()).isNotNull();
    assertThat(currentUserHelper.getPrincipalFromSecurityContext()).isEqualTo(principal);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testUpdates() {
    VariablesCreationBlobResponse.Builder variable = VariablesCreationBlobResponse.newBuilder();
    Map<String,String> map1 = new HashMap<>();
    map1.put("pipeline/stages","yaml1");
    map1.put("pipeline/stages/[0]/stage/spec/execution/steps","yaml2");
    YamlUpdates yamlUpdates1 = YamlUpdates.newBuilder().putAllFqnToYaml(map1).build();
    VariablesCreationBlobResponse currentResponse = VariablesCreationBlobResponse.newBuilder().setYamlUpdates(yamlUpdates1).build();
    VariablesCreationBlobResponse creationBlobResponse1 = VariableCreationBlobResponseUtils.addYamlUpdates(variable, currentResponse);

    assertThat(creationBlobResponse1.getYamlUpdates().getFqnToYamlMap()).isEqualTo(map1);


    Map<String,String> map2 = new HashMap<>();
    map2.put("pipeline/stages","yaml3");
    map2.put("pipeline/stages/[0]/stage/spec/infrastructure/infrastructureDefinition/provisioner/steps","yaml4");

    YamlUpdates yamlUpdates2 = YamlUpdates.newBuilder().putAllFqnToYaml(map2).build();
    VariablesCreationBlobResponse currentResponse2 = VariablesCreationBlobResponse.newBuilder().setYamlUpdates(yamlUpdates2).build();
    VariablesCreationBlobResponse creationBlobResponse2 = VariableCreationBlobResponseUtils.addYamlUpdates(VariablesCreationBlobResponse.newBuilder(), currentResponse2);

    assertThat(creationBlobResponse2.getYamlUpdates().getFqnToYamlMap()).isEqualTo(map2);
  }
}
