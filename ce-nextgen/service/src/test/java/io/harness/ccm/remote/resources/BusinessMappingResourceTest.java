/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources;

import static io.harness.rule.OwnerRule.SAHILDEEP;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.audittrails.events.CostCategoryCreateEvent;
import io.harness.ccm.audittrails.events.CostCategoryDeleteEvent;
import io.harness.ccm.audittrails.events.CostCategoryUpdateEvent;
import io.harness.ccm.rbac.CCMRbacHelper;
import io.harness.ccm.views.businessMapping.entities.BusinessMapping;
import io.harness.ccm.views.businessMapping.entities.CostTarget;
import io.harness.ccm.views.businessMapping.entities.SharedCost;
import io.harness.ccm.views.businessMapping.entities.SharedCostSplit;
import io.harness.ccm.views.businessMapping.entities.SharingStrategy;
import io.harness.ccm.views.businessMapping.service.intf.BusinessMappingService;
import io.harness.ccm.views.dto.CostCategoryDeleteDTO;
import io.harness.ccm.views.dto.LinkedPerspectives;
import io.harness.ccm.views.entities.ViewCondition;
import io.harness.ccm.views.entities.ViewIdCondition;
import io.harness.ccm.views.entities.ViewRule;
import io.harness.ccm.views.service.CEViewService;
import io.harness.outbox.api.OutboxService;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@RunWith(MockitoJUnitRunner.class)
public class BusinessMappingResourceTest extends CategoryTest {
  @Mock private BusinessMappingService businessMappingService;
  @Mock private CEViewService ceViewService;
  @Mock private OutboxService outboxService;
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private CCMRbacHelper rbacHelper;
  @InjectMocks private BusinessMappingResource businessMappingResource;
  private BusinessMapping businessMapping;
  private BusinessMapping costCategoryDTO;
  private LinkedPerspectives linkedPerspectives;

  @Captor private ArgumentCaptor<CostCategoryCreateEvent> costCategoryCreateEventArgumentCaptor;
  @Captor private ArgumentCaptor<CostCategoryUpdateEvent> costCategoryUpdateEventArgumentCaptor;
  @Captor private ArgumentCaptor<CostCategoryDeleteEvent> costCategoryDeleteEventArgumentCaptor;

  @Before
  public void setUp() {
    businessMapping = BusinessMappingHelperTest.getBusinessMapping(UUID.randomUUID().toString());
    linkedPerspectives = LinkedPerspectives.builder().build();
    costCategoryDTO = businessMapping.toDTO();
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testSave() {
    when(businessMappingService.save(any(BusinessMapping.class))).thenReturn(businessMapping);
    when(businessMappingService.isNamePresent(any(), any())).thenReturn(true);
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    final RestResponse<BusinessMapping> response =
        businessMappingResource.save(BusinessMappingHelperTest.TEST_ACCOUNT_ID, businessMapping);
    verify(businessMappingService).save(businessMapping);
    verify(transactionTemplate, times(1)).execute(any());
    verify(outboxService, times(1)).save(costCategoryCreateEventArgumentCaptor.capture());
    CostCategoryCreateEvent capturedCostCategoryCreateEvent = costCategoryCreateEventArgumentCaptor.getValue();
    assertThat(costCategoryDTO).isEqualTo(capturedCostCategoryCreateEvent.getCostCategoryDTO());
    assertThat(response.getResource()).isEqualTo(businessMapping);
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testList() {
    final List<BusinessMapping> businessMappings = Collections.singletonList(businessMapping);
    when(businessMappingService.list(BusinessMappingHelperTest.TEST_ACCOUNT_ID)).thenReturn(businessMappings);
    final RestResponse<List<BusinessMapping>> response =
        businessMappingResource.list(BusinessMappingHelperTest.TEST_ACCOUNT_ID);
    verify(businessMappingService).list(BusinessMappingHelperTest.TEST_ACCOUNT_ID);
    assertThat(response.getResource()).isEqualTo(businessMappings);
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testGet() {
    when(businessMappingService.get(BusinessMappingHelperTest.TEST_ID, BusinessMappingHelperTest.TEST_ACCOUNT_ID))
        .thenReturn(businessMapping);
    final RestResponse<BusinessMapping> response =
        businessMappingResource.get(BusinessMappingHelperTest.TEST_ACCOUNT_ID, BusinessMappingHelperTest.TEST_ID);
    verify(businessMappingService).get(BusinessMappingHelperTest.TEST_ID, BusinessMappingHelperTest.TEST_ACCOUNT_ID);
    assertThat(response.getResource()).isEqualTo(businessMapping);
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testUpdate() {
    when(businessMappingService.update(businessMapping)).thenReturn(businessMapping);
    when(businessMappingService.get(businessMapping.getUuid(), BusinessMappingHelperTest.TEST_ACCOUNT_ID))
        .thenReturn(businessMapping);
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    final RestResponse<String> response =
        businessMappingResource.update(BusinessMappingHelperTest.TEST_ACCOUNT_ID, businessMapping);
    verify(businessMappingService).update(businessMapping);
    verify(transactionTemplate, times(1)).execute(any());
    verify(outboxService, times(1)).save(costCategoryUpdateEventArgumentCaptor.capture());
    CostCategoryUpdateEvent costCategoryUpdateEvent = costCategoryUpdateEventArgumentCaptor.getValue();
    assertThat(costCategoryDTO).isEqualTo(costCategoryUpdateEvent.getCostCategoryDTO());
    assertThat(response.getResource()).isEqualTo("Successfully updated the Business Mapping");
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testDelete() {
    when(businessMappingService.get(BusinessMappingHelperTest.TEST_ID, BusinessMappingHelperTest.TEST_ACCOUNT_ID))
        .thenReturn(businessMapping);
    when(businessMappingService.delete(BusinessMappingHelperTest.TEST_ID, BusinessMappingHelperTest.TEST_ACCOUNT_ID))
        .thenReturn(true);
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    when(ceViewService.getViewsByBusinessMapping(
             BusinessMappingHelperTest.TEST_ACCOUNT_ID, Collections.singletonList(BusinessMappingHelperTest.TEST_ID)))
        .thenReturn(Collections.singletonList(linkedPerspectives));
    final RestResponse<CostCategoryDeleteDTO> response =
        businessMappingResource.delete(BusinessMappingHelperTest.TEST_ACCOUNT_ID, BusinessMappingHelperTest.TEST_ID);
    verify(businessMappingService).delete(BusinessMappingHelperTest.TEST_ID, BusinessMappingHelperTest.TEST_ACCOUNT_ID);
    verify(transactionTemplate, times(1)).execute(any());
    verify(outboxService, times(1)).save(costCategoryDeleteEventArgumentCaptor.capture());
    CostCategoryDeleteEvent costCategoryDeleteEvent = costCategoryDeleteEventArgumentCaptor.getValue();
    assertThat(costCategoryDTO).isEqualTo(costCategoryDeleteEvent.getCostCategoryDTO());
    assertThat(response.getResource().isDeleted()).isEqualTo(true);
  }

  static final class BusinessMappingHelperTest {
    public static final String TEST_ID = UUID.randomUUID().toString();
    public static final String TEST_ACCOUNT_ID = "TEST_ACCOUNT_ID";
    public static final String TEST_NAME_1 = "TEST_NAME_1";
    public static final String TEST_NAME_2 = "TEST_NAME_2";

    private BusinessMappingHelperTest() {}

    public static BusinessMapping getBusinessMapping(final String uuid) {
      return BusinessMapping.builder()
          .uuid(uuid)
          .accountId(TEST_ACCOUNT_ID)
          .name(TEST_NAME_2)
          .costTargets(getCostTargets())
          .sharedCosts(getSharedCosts())
          .build();
    }

    private static CostTarget getCostTarget(final String name, final List<ViewRule> rules) {
      return CostTarget.builder().name(name).rules(rules).build();
    }

    @NotNull
    private static List<CostTarget> getCostTargets() {
      final List<ViewRule> rules = getRules();
      return Stream.of(getCostTarget(TEST_NAME_1, rules), getCostTarget(TEST_NAME_2, rules))
          .collect(Collectors.toList());
    }

    private static SharedCost getSharedCost(final String name, final List<ViewRule> rules,
        final SharingStrategy strategy, final List<SharedCostSplit> splits) {
      return SharedCost.builder().name(name).rules(rules).strategy(strategy).splits(splits).build();
    }

    @NotNull
    private static List<SharedCost> getSharedCosts() {
      final List<ViewRule> rules = getRules();
      final List<SharedCostSplit> splits = getSharedCostSplits();
      return Stream
          .of(getSharedCost(TEST_NAME_1, rules, SharingStrategy.EQUAL, splits),
              getSharedCost(TEST_NAME_2, rules, SharingStrategy.PROPORTIONAL, splits))
          .collect(Collectors.toList());
    }

    private static SharedCostSplit getSharedCostSplit(
        final String costTargetName, final double percentageContribution) {
      return SharedCostSplit.builder()
          .costTargetName(costTargetName)
          .percentageContribution(percentageContribution)
          .build();
    }

    @NotNull
    private static List<SharedCostSplit> getSharedCostSplits() {
      return Stream.of(getSharedCostSplit(TEST_NAME_1, 10.0), getSharedCostSplit(TEST_NAME_2, 90.0))
          .collect(Collectors.toList());
    }

    @NotNull
    private static List<ViewRule> getRules() {
      final List<ViewCondition> viewConditions = Collections.singletonList(ViewIdCondition.builder().build());
      return Collections.singletonList(ViewRule.builder().viewConditions(viewConditions).build());
    }
  }
}
