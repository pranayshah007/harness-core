package io.harness.ccm.views.service.impl;

import com.google.inject.Inject;
import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.dao.anomaly.AnomalyDao;
import io.harness.ccm.commons.entities.anomaly.AnomalyData;
import io.harness.ccm.commons.utils.AnomalyQueryBuilder;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.ViewField;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.entities.ViewIdCondition;
import io.harness.ccm.views.entities.ViewIdOperator;
import io.harness.ccm.views.entities.ViewRule;
import io.harness.ccm.views.graphql.QLCEViewField;
import io.harness.ccm.views.graphql.ViewsQueryBuilder;
import io.harness.ccm.views.helper.PerspectiveToAnomalyQueryHelper;
import io.harness.ccm.views.service.CEViewService;
import io.harness.ccm.views.utils.ViewFieldUtils;
import io.harness.rule.Owner;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Table;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.harness.rule.OwnerRule.ROHIT;
import static io.harness.rule.OwnerRule.TRUNAPUSHPA;
import static java.util.Collections.emptyList;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;

public class PerspectiveAnomalyServiceImplTest extends CategoryTest {
  @InjectMocks @Inject private PerspectiveAnomalyServiceImpl perspectiveAnomalyService;
  @Mock CEViewService viewService;
  @Mock PerspectiveToAnomalyQueryHelper perspectiveToAnomalyQueryHelper;
  @Mock AnomalyQueryBuilder anomalyQueryBuilder;
  @Mock AnomalyDao anomalyDao;
  @Mock DSLContext dslContext;

  private static final String ACCOUNT_ID = "account_id";
  private static final String VIEW_NAME = "view_name";
  private static final String UUID = "uuid";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = TRUNAPUSHPA)
  @Category(UnitTests.class)
//  public void testListAnomaliesForDate() {
//    doReturn(ceView()).when(viewService).get(any());
//    doCallRealMethod().when(perspectiveToAnomalyQueryHelper).getConvertedRulesForPerspective(any());
//    doCallRealMethod().when(perspectiveToAnomalyQueryHelper).mapConditionToFilter(any());
//    doCallRealMethod().when(perspectiveToAnomalyQueryHelper).getViewFieldInput(any());
//    doCallRealMethod().when(perspectiveToAnomalyQueryHelper).mapViewIdOperatorToQLCEViewFilterOperator(any());
//    doCallRealMethod().when(perspectiveToAnomalyQueryHelper).getStringArray(any());
//    doCallRealMethod().when(perspectiveToAnomalyQueryHelper).convertFilters(any());
//    doCallRealMethod().when(perspectiveToAnomalyQueryHelper).buildStringFilter(any(), any(), any());
//    doCallRealMethod().when(anomalyQueryBuilder).applyPerspectiveRuleFilters(any());
//    doCallRealMethod().when(anomalyQueryBuilder).applyStringFilters(any(), any());
//    doCallRealMethod().when(anomalyDao).fetchAnomaliesForNotification(any(), any(), any(), any(), any(), any());
//    List<AnomalyData> list = perspectiveAnomalyService.listPerspectiveAnomaliesForDate(ACCOUNT_ID, UUID, Instant.now());
//  }

  private CEView ceView() {
    List<QLCEViewField> awsFields = ViewFieldUtils.getAwsFields();
    final QLCEViewField awsAccount = awsFields.get(1);
    return CEView.builder()
        .uuid(UUID)
        .name(VIEW_NAME)
        .accountId(ACCOUNT_ID)
        .viewRules(Arrays.asList(ViewRule.builder()
            .viewConditions(Arrays.asList(
                ViewIdCondition.builder()
                    .viewField(ViewField.builder()
                        .fieldName(awsAccount.getFieldName())
                        .fieldId(awsAccount.getFieldId())
                        .identifier(ViewFieldIdentifier.AWS)
                        .build())
                    .viewOperator(ViewIdOperator.NOT_NULL)
                    .values(Collections.singletonList(""))
                    .build()))
            .build()))
        .build();
  }
}