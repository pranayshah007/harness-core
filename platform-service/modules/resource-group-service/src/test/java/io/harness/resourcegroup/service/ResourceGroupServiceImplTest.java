/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resourcegroup.service;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.REETIKA;
import static io.harness.utils.PageTestUtils.getPage;

import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.beans.PageRequest;
import io.harness.resourcegroup.framework.v1.repositories.spring.ResourceGroupRepository;
import io.harness.resourcegroup.framework.v1.service.impl.ResourceGroupServiceImpl;
import io.harness.resourcegroup.v1.remote.dto.ResourceGroupFilterDTO;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PL)
public class ResourceGroupServiceImplTest {
  @Mock private ResourceGroupRepository resourceGroupRepository;

  @Inject @InjectMocks private ResourceGroupServiceImpl resourceGroupService;

  private static final String ACCOUNT_IDENTIFIER = "A1";
  private static final String ORG_IDENTIFIER = "O1";
  private static final String PROJECT_IDENTIFIER = "P1";

  @Before
  public void setup() {
    initMocks(this);
  }

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void testListResourceGroups() throws IOException {
    String searchTerm = randomAlphabetic(5);
    final ArgumentCaptor<Criteria> resourceGroupCriteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    doReturn(getPage(emptyList(), 0)).when(resourceGroupRepository).findAll(any(Criteria.class), any(Pageable.class));
    resourceGroupService.list(ResourceGroupFilterDTO.builder()
                                  .accountIdentifier(ACCOUNT_IDENTIFIER)
                                  .orgIdentifier(ORG_IDENTIFIER)
                                  .projectIdentifier(PROJECT_IDENTIFIER)
                                  .searchTerm(searchTerm)
                                  .identifierFilter(new HashSet<>(Arrays.asList("RG1", "RG2")))
                                  .build(),
        PageRequest.builder().pageSize(10).pageIndex(0).build());
    verify(resourceGroupRepository, times(1)).findAll(resourceGroupCriteriaArgumentCaptor.capture(), any());
  }
}
