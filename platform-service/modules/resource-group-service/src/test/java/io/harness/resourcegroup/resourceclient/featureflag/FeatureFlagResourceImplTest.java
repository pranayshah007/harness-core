/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resourcegroup.resourceclient.featureflag;

import static io.harness.rule.OwnerRule.DMACK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.beans.Scope;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.ArrayList;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class FeatureFlagResourceImplTest {
  @Inject @InjectMocks FeatureFlagResourceImplTest featureFlagResource;
  @Inject @InjectMocks FeatureFlagResourceImpl featureFlagResourceImpl;
  private static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";

  @Before
  public void setup() {
    initMocks(this);
  }

  @Test
  @Owner(developers = DMACK)
  @Category(UnitTests.class)
  public void getType() {
    assertThat(featureFlagResourceImpl.getType()).isEqualTo("FEATUREFLAG");
  }

  @Test
  @Owner(developers = DMACK)
  @Category(UnitTests.class)
  public void getValidScopeLevels() {
    assertThat(featureFlagResourceImpl.getValidScopeLevels())
        .containsExactlyInAnyOrder(ScopeLevel.PROJECT, ScopeLevel.ORGANIZATION, ScopeLevel.ACCOUNT);
  }

  @Test
  @Owner(developers = DMACK)
  @Category(UnitTests.class)
  public void getEventFrameworkEntityType() {
    assertThat(featureFlagResourceImpl.getEventFrameworkEntityType().get()).isEqualTo("featureflag");
  }

  @Test
  @Owner(developers = DMACK)
  @Category(UnitTests.class)
  public void testValidateEmptyResourceList() {
    assertThat(featureFlagResourceImpl.validate(
                   new ArrayList<>(), Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER)))
        .isEmpty();
  }
}