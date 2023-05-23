/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.helper;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.commons.constants.ViewFieldConstants.AWS_ACCOUNT_FIELD;
import static io.harness.ccm.views.entities.ViewFieldIdentifier.AWS;
import static io.harness.ccm.views.entities.ViewIdOperator.IN;
import static io.harness.rule.OwnerRule.SAHILDEEP;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.service.intf.EntityMetadataService;
import io.harness.ccm.views.entities.ViewCondition;
import io.harness.ccm.views.entities.ViewField;
import io.harness.ccm.views.entities.ViewIdCondition;
import io.harness.ccm.views.entities.ViewRule;
import io.harness.ccm.views.graphql.QLCEViewFieldInput;
import io.harness.ccm.views.graphql.QLCEViewFilter;
import io.harness.ccm.views.graphql.QLCEViewFilterOperator;
import io.harness.ccm.views.graphql.QLCEViewRule;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CE)
@RunWith(MockitoJUnitRunner.class)
public class AwsAccountFieldHelperTest extends CategoryTest {
  @Mock private EntityMetadataService entityMetadataService;
  @InjectMocks private AwsAccountFieldHelper awsAccountFieldHelper;

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testMergeAwsAccountIdAndName_Valid() {
    final String accountId = "1234567890";
    final String accountName = "testAccount";
    final String result = AwsAccountFieldHelper.mergeAwsAccountIdAndName(accountId, accountName);
    final String expected = "testAccount (1234567890)";
    assertThat(result).isEqualTo(expected);
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testMergeAwsAccountIdAndName_Invalid() {
    final String accountId = "1234567890";
    final String accountName = "testAccount";
    final String result = AwsAccountFieldHelper.mergeAwsAccountIdAndName(accountId, accountName);
    final String expected = "testAccount - 1234567890";
    assertThat(result).isNotEqualTo(expected);
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testMergeAwsAccountIdAndName_EmptyAccountName() {
    final String accountId = "1234567890";
    final String accountName = "";
    final String result = AwsAccountFieldHelper.mergeAwsAccountIdAndName(accountId, accountName);
    final String expected = "1234567890";
    assertThat(result).isEqualTo(expected);
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testMergeAwsAccountIdAndName_NullAccountName() {
    final String accountId = "1234567890";
    final String result = AwsAccountFieldHelper.mergeAwsAccountIdAndName(accountId, null);
    final String expected = "1234567890";
    assertThat(result).isEqualTo(expected);
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testRemoveAwsAccountNameFromValue_WithAccountName() {
    final String value = "testAccount (1234567890)";
    final String result = AwsAccountFieldHelper.removeAwsAccountNameFromValue(value);
    final String expected = "1234567890";
    assertThat(result).isEqualTo(expected);
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testRemoveAwsAccountNameFromValue_WithoutAccountName() {
    final String value = "1234567890";
    final String result = AwsAccountFieldHelper.removeAwsAccountNameFromValue(value);
    final String expected = "1234567890";
    assertThat(result).isEqualTo(expected);
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testRemoveAwsAccountNameFromValue_NullValue() {
    final String result = AwsAccountFieldHelper.removeAwsAccountNameFromValue(null);
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testRemoveAwsAccountNameFromValues() {
    final List<String> values = List.of("testAccount (1234567890)", "1234567891");
    final List<String> result = AwsAccountFieldHelper.removeAwsAccountNameFromValues(values);
    final List<String> expected = List.of("1234567890", "1234567891");
    assertThat(result.size()).isEqualTo(expected.size());
    assertThat(result.get(0)).isEqualTo(expected.get(0));
    assertThat(result.get(1)).isEqualTo(expected.get(1));
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testRemoveAwsAccountNameFromAccountRules() {
    final List<String> values = List.of("testAccount (1234567890)", "1234567891");
    final List<ViewRule> viewRules = getTestViewAccountRules(values);
    awsAccountFieldHelper.removeAwsAccountNameFromAccountRules(viewRules);
    final List<String> expected = List.of("1234567890", "1234567891");
    final ViewIdCondition viewIdCondition = (ViewIdCondition) viewRules.get(0).getViewConditions().get(0);
    assertThat(viewIdCondition.getValues().size()).isEqualTo(expected.size());
    assertThat(viewIdCondition.getValues().get(0)).isEqualTo(expected.get(0));
    assertThat(viewIdCondition.getValues().get(1)).isEqualTo(expected.get(1));
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testMergeAwsAccountNameWithValues() {
    when(entityMetadataService.getEntityIdToNameMapping(anyList(), anyString(), eq(AWS_ACCOUNT_FIELD)))
        .thenReturn(Collections.singletonMap("1234567890", "testAccount"));
    final List<String> values = List.of("1234567890", "1234567891");
    final List<String> result = awsAccountFieldHelper.mergeAwsAccountNameWithValues(values, "testAccountId");
    final List<String> expected = List.of("testAccount (1234567890)", "1234567891");
    assertThat(result.size()).isEqualTo(expected.size());
    assertThat(result.get(0)).isEqualTo(expected.get(0));
    assertThat(result.get(1)).isEqualTo(expected.get(1));
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testMergeAwsAccountNameWithValues_EmptyList() {
    when(entityMetadataService.getEntityIdToNameMapping(anyList(), anyString(), eq(AWS_ACCOUNT_FIELD)))
        .thenReturn(Collections.emptyMap());
    final List<String> result =
        awsAccountFieldHelper.mergeAwsAccountNameWithValues(Collections.emptyList(), "testAccountId");
    assertThat(result.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testMergeAwsAccountNameWithValues_NullEntityIdToNameMapping() {
    when(entityMetadataService.getEntityIdToNameMapping(anyList(), anyString(), eq(AWS_ACCOUNT_FIELD)))
        .thenReturn(null);
    final List<String> values = List.of("1234567890", "1234567891");
    final List<String> result = awsAccountFieldHelper.mergeAwsAccountNameWithValues(values, "testAccountId");
    final List<String> expected = List.of("1234567890", "1234567891");
    assertThat(result.size()).isEqualTo(expected.size());
    assertThat(result.get(0)).isEqualTo(expected.get(0));
    assertThat(result.get(1)).isEqualTo(expected.get(1));
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testMergeAwsAccountNameInAccountRules() {
    when(entityMetadataService.getEntityIdToNameMapping(anyList(), anyString(), eq(AWS_ACCOUNT_FIELD)))
        .thenReturn(Collections.singletonMap("1234567890", "testAccount"));
    final List<String> values = List.of("1234567890", "1234567891");
    final List<ViewRule> viewRules = getTestViewAccountRules(values);
    awsAccountFieldHelper.mergeAwsAccountNameInAccountRules(viewRules, "testAccountId");
    final List<String> expected = List.of("testAccount (1234567890)", "1234567891");
    final ViewIdCondition viewIdCondition = (ViewIdCondition) viewRules.get(0).getViewConditions().get(0);
    assertThat(viewIdCondition.getValues().size()).isEqualTo(expected.size());
    assertThat(viewIdCondition.getValues().get(0)).isEqualTo(expected.get(0));
    assertThat(viewIdCondition.getValues().get(1)).isEqualTo(expected.get(1));
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testAddAccountIdsByAwsAccountNameFilter() {
    when(entityMetadataService.getAccountIdAndNameByAccountNameFilter(anyString(), anyString()))
        .thenReturn(Collections.singletonMap("1234567890", "testAccount"));
    final String[] values = new String[] {"testAccount"};
    final List<QLCEViewFilter> filters = getQLCEViewAccountFilters(values);
    final List<QLCEViewFilter> updatedFilters =
        awsAccountFieldHelper.addAccountIdsByAwsAccountNameFilter(filters, "testAccountId");
    final List<String> expected = List.of("testAccount", "1234567890");
    final String[] result = updatedFilters.get(0).getValues();
    assertThat(result.length).isEqualTo(expected.size());
    assertThat(result[0]).isEqualTo(expected.get(0));
    assertThat(result[1]).isEqualTo(expected.get(1));
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testRemoveAccountNameFromAWSAccountIdFilter() {
    final String[] values = new String[] {"testAccount (1234567890)", "1234567891"};
    final List<QLCEViewFilter> filters = getQLCEViewAccountFilters(values);
    final List<QLCEViewFilter> updatedFilters = AwsAccountFieldHelper.removeAccountNameFromAWSAccountIdFilter(filters);
    final List<String> expected = List.of("1234567890", "1234567891");
    final String[] result = updatedFilters.get(0).getValues();
    assertThat(result.length).isEqualTo(expected.size());
    assertThat(result[0]).isEqualTo(expected.get(0));
    assertThat(result[1]).isEqualTo(expected.get(1));
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testRemoveAccountNameFromAWSAccountRuleFilter() {
    final String[] values = new String[] {"testAccount (1234567890)", "1234567891"};
    final List<QLCEViewRule> rules = getQLCEViewAccountRules(values);
    final List<QLCEViewRule> updatedRules = AwsAccountFieldHelper.removeAccountNameFromAWSAccountRuleFilter(rules);
    final List<String> expected = List.of("1234567890", "1234567891");
    final String[] result = updatedRules.get(0).getConditions().get(0).getValues();
    assertThat(result.length).isEqualTo(expected.size());
    assertThat(result[0]).isEqualTo(expected.get(0));
    assertThat(result[1]).isEqualTo(expected.get(1));
  }

  @Test
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testSpiltAndSortAWSAccountIdListBasedOnAccountName() {
    final List<String> values = List.of("testAccount (1234567890)", "1234567891", "dummyAccount (1234567892)");
    final List<String> result = awsAccountFieldHelper.spiltAndSortAWSAccountIdListBasedOnAccountName(values);
    final List<String> expected = List.of("dummyAccount (1234567892)", "testAccount (1234567890)", "1234567891");
    assertThat(result.size()).isEqualTo(expected.size());
    assertThat(result.get(0)).isEqualTo(expected.get(0));
    assertThat(result.get(1)).isEqualTo(expected.get(1));
    assertThat(result.get(2)).isEqualTo(expected.get(2));
  }

  private List<QLCEViewRule> getQLCEViewAccountRules(final String[] values) {
    return Collections.singletonList(QLCEViewRule.builder().conditions(getQLCEViewAccountFilters(values)).build());
  }

  private List<QLCEViewFilter> getQLCEViewAccountFilters(final String[] values) {
    return Collections.singletonList(QLCEViewFilter.builder()
                                         .field(QLCEViewFieldInput.builder()
                                                    .fieldId("awsUsageAccountId")
                                                    .fieldName("Account")
                                                    .identifier(AWS)
                                                    .identifierName(AWS.getDisplayName())
                                                    .build())
                                         .operator(QLCEViewFilterOperator.IN)
                                         .values(values)
                                         .build());
  }

  private List<ViewRule> getTestViewAccountRules(final List<String> values) {
    final List<ViewCondition> viewConditions = getViewAccountConditions(values);
    return Collections.singletonList(ViewRule.builder().viewConditions(viewConditions).build());
  }

  @NotNull
  private List<ViewCondition> getViewAccountConditions(final List<String> values) {
    final ViewField viewField = ViewField.builder()
                                    .fieldId("awsUsageAccountId")
                                    .fieldName("Account")
                                    .identifier(AWS)
                                    .identifierName(AWS.getDisplayName())
                                    .build();
    return Collections.singletonList(
        ViewIdCondition.builder().viewField(viewField).viewOperator(IN).values(values).build());
  }
}
