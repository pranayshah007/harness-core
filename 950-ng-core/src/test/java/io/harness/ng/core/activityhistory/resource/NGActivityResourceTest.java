/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.activityhistory.resource;

import static io.harness.EntityType.CONNECTORS;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.activityhistory.ActivityHistoryTestHelper;
import io.harness.ng.core.activityhistory.NGActivityStatus;
import io.harness.ng.core.activityhistory.NGActivityType;
import io.harness.ng.core.activityhistory.dto.NGActivityDTO;
import io.harness.ng.core.activityhistory.service.NGActivityService;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class NGActivityResourceTest extends CategoryTest {
  @Mock NGActivityService activityService;
  @InjectMocks NGActivityResource ngActivityResource;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void listTest() {
    String accountIdentifier = "accountIdentifier";
    String orgIdentifier = "orgIdentifier";
    String projectIdentifier = "projectIdentifier";
    String identifier = "identifier";
    ngActivityResource.list(100, 100, accountIdentifier, orgIdentifier, projectIdentifier, identifier, 0L, 100L,
        NGActivityStatus.SUCCESS, CONNECTORS, null, null);

    Set<NGActivityType> ngActivityTypes = new HashSet<>(List.of(NGActivityType.values()));
    ngActivityTypes.remove(NGActivityType.CONNECTIVITY_CHECK);

    Mockito.verify(activityService, times(1))
        .list(eq(100), eq(100), eq(accountIdentifier), eq(orgIdentifier), eq(projectIdentifier), eq(identifier), eq(0L),
            eq(100L), eq(NGActivityStatus.SUCCESS), eq(CONNECTORS), eq(null), eq(ngActivityTypes));
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void saveTest() {
    String accountIdentifier = "accountIdentifier";
    String orgIdentifier = "orgIdentifier";
    String projectIdentifier = "projectIdentifier";
    String identifier = "identifier";
    NGActivityDTO activityHistoryDTO =
        ActivityHistoryTestHelper.createActivityHistoryDTO(accountIdentifier, orgIdentifier, projectIdentifier,
            identifier, NGActivityStatus.SUCCESS, System.currentTimeMillis(), NGActivityType.ENTITY_USAGE);
    ngActivityResource.save(activityHistoryDTO);
    Mockito.verify(activityService, times(1)).save(eq(activityHistoryDTO));
  }
}
