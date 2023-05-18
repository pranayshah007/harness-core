/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.billing;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.rule.OwnerRule.ROHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.ccm.billing.graphql.CloudBillingFilter;
import io.harness.ccm.billing.graphql.CloudBillingGroupBy;
import io.harness.ccm.billing.graphql.CloudBillingIdFilter;
import io.harness.ccm.billing.graphql.CloudBillingSortCriteria;
import io.harness.ccm.billing.graphql.CloudEntityGroupBy;
import io.harness.ccm.billing.preaggregated.PreAggregateBillingServiceImpl;
import io.harness.ccm.billing.preaggregated.PreAggregateConstants;
import io.harness.ccm.billing.preaggregated.PreAggregateFilterValuesDTO;
import io.harness.ccm.billing.preaggregated.PreAggregatedFilterValuesDataPoint;
import io.harness.rule.Owner;

import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.schema.type.aggregation.QLData;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.service.intfc.ce.CeAccountExpirationChecker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._375_CE_GRAPHQL)
@OwnedBy(CE)
public class CloudFilterValuesDataFetcherTest extends AbstractDataFetcherTestBase {
  @Mock CloudBillingHelper cloudBillingHelper;
  @Mock PreAggregateBillingServiceImpl preAggregateBillingService;
  @Mock CeAccountExpirationChecker accountChecker;
  @InjectMocks CloudFilterValuesDataFetcher cloudFilterValuesDataFetcher;

  private List<CloudBillingFilter> filters = new ArrayList<>();
  private List<CloudBillingGroupBy> groupBy = new ArrayList<>();
  private List<CloudBillingSortCriteria> sort = new ArrayList<>();
  private static QLEntityData entityData;
  private static final String INSTANCE_TYPE = "instanceType";
  private static final String CLOUD_PROVIDER = "AWS";
  private static final String ID = "id";
  private static final String NAME = "name";
  private static final String TYPE = "type";

  @Before
  public void setup() {
    doCallRealMethod().when(cloudBillingHelper).getGroupByMapper(anyBoolean(), anyBoolean());
    doCallRealMethod().when(cloudBillingHelper).getFiltersMapper(anyBoolean(), anyBoolean());
    filters.addAll(Arrays.asList(getCloudProviderFilter(new String[] {CLOUD_PROVIDER}),
        getAwsFilter(new String[] {PreAggregateConstants.nullStringValueConstant}),
        getInstanceTypeAwsFilter(new String[] {INSTANCE_TYPE, PreAggregateConstants.nullStringValueConstant})));

    groupBy.addAll(Arrays.asList(getServiceGroupBy()));

    Set<QLEntityData> awsRegionSet = new HashSet<>();
    entityData = QLEntityData.builder().id(ID).name(NAME).type(TYPE).build();
    awsRegionSet.add(entityData);

    when(preAggregateBillingService.getPreAggregateFilterValueStats(
             anyString(), anyList(), anyList(), anyString(), any(), anyInt(), anyInt()))
        .thenReturn(PreAggregateFilterValuesDTO.builder()
                        .data(Arrays.asList(PreAggregatedFilterValuesDataPoint.builder()
                                                .region(awsRegionSet)
                                                .awsInstanceType(null)
                                                .awsService(null)
                                                .awsUsageType(null)
                                                .awsLinkedAccount(null)
                                                .build()))
                        .build());
    when(cloudBillingHelper.getCloudProviderTableName(anyString())).thenReturn("CLOUD_PROVIDER_TABLE_NAME");
    doNothing().when(accountChecker).checkIsCeEnabled(anyString());
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void fetch() {
    doCallRealMethod().when(cloudBillingHelper).getCloudProvider(anyList());
    PreAggregateFilterValuesDTO data = (PreAggregateFilterValuesDTO) cloudFilterValuesDataFetcher.fetch(
        ACCOUNT1_ID, null, filters, groupBy, sort, 10, 0);
    assertThat(data.getData()).isNotNull();
    assertThat(data.getData().get(0).getRegion()).contains(entityData);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void fetchWithLabels() {
    doCallRealMethod().when(cloudBillingHelper).fetchIfRawTableQueryRequired(anyList(), anyList());
    doCallRealMethod().when(cloudBillingHelper).getCloudProvider(anyList());
    doCallRealMethod().when(cloudBillingHelper).removeAndReturnCloudProviderFilter(anyList());
    doCallRealMethod().when(cloudBillingHelper).removeAndReturnCloudProviderGroupBy(anyList());

    when(preAggregateBillingService.getPreAggregateFilterValueStats(
             anyString(), anyList(), anyList(), anyString(), any(), anyInt(), anyInt()))
        .thenReturn(null);
    PreAggregateFilterValuesDTO data = (PreAggregateFilterValuesDTO) cloudFilterValuesDataFetcher.fetch(ACCOUNT1_ID,
        null, Arrays.asList(getCloudProviderFilter(new String[] {"GCP"})),
        Arrays.asList(getLabelsKeyGroupBy(), getLabelsValueGroupBy()), null, 10, 0);
    assertThat(data).isNull();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void fetchWithTags() {
    doCallRealMethod().when(cloudBillingHelper).fetchIfRawTableQueryRequired(anyList(), anyList());
    doCallRealMethod().when(cloudBillingHelper).getCloudProvider(anyList());
    doCallRealMethod().when(cloudBillingHelper).removeAndReturnCloudProviderFilter(anyList());
    doCallRealMethod().when(cloudBillingHelper).removeAndReturnCloudProviderGroupBy(anyList());

    when(preAggregateBillingService.getPreAggregateFilterValueStats(
             anyString(), anyList(), anyList(), anyString(), any(), anyInt(), anyInt()))
        .thenReturn(null);

    PreAggregateFilterValuesDTO data = (PreAggregateFilterValuesDTO) cloudFilterValuesDataFetcher.fetch(ACCOUNT1_ID,
        null, Arrays.asList(getCloudProviderFilter(new String[] {CLOUD_PROVIDER})),
        Arrays.asList(getTagsKeyGroupBy(), getTagsValueGroupBy()), null, 10, 0);
    assertThat(data).isNull();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void postFetch() {
    QLData postFetchData = cloudFilterValuesDataFetcher.postFetch(ACCOUNT1_ID, groupBy, null, null, null, 10, true);
    assertThat(postFetchData).isNull();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void getEntityType() {
    String entityType = cloudFilterValuesDataFetcher.getEntityType();
    assertThat(entityType).isNull();
  }

  private CloudBillingGroupBy getServiceGroupBy() {
    CloudBillingGroupBy cloudBillingGroupBy = new CloudBillingGroupBy();
    cloudBillingGroupBy.setEntityGroupBy(CloudEntityGroupBy.awsService);
    return cloudBillingGroupBy;
  }

  private CloudBillingFilter getAwsFilter(String[] region) {
    CloudBillingFilter cloudBillingFilter = new CloudBillingFilter();
    cloudBillingFilter.setRegion(CloudBillingIdFilter.builder().operator(QLIdOperator.EQUALS).values(region).build());
    return cloudBillingFilter;
  }

  private CloudBillingFilter getInstanceTypeAwsFilter(String[] instanceType) {
    CloudBillingFilter cloudBillingFilter = new CloudBillingFilter();
    cloudBillingFilter.setAwsInstanceType(
        CloudBillingIdFilter.builder().operator(QLIdOperator.IN).values(instanceType).build());
    return cloudBillingFilter;
  }

  private CloudBillingFilter getCloudProviderFilter(String[] cloudProvider) {
    CloudBillingFilter cloudBillingFilter = new CloudBillingFilter();
    cloudBillingFilter.setCloudProvider(
        CloudBillingIdFilter.builder().operator(QLIdOperator.IN).values(cloudProvider).build());
    return cloudBillingFilter;
  }

  private CloudBillingGroupBy getLabelsKeyGroupBy() {
    CloudBillingGroupBy cloudBillingGroupBy = new CloudBillingGroupBy();
    cloudBillingGroupBy.setEntityGroupBy(CloudEntityGroupBy.labelsKey);
    return cloudBillingGroupBy;
  }

  private CloudBillingGroupBy getLabelsValueGroupBy() {
    CloudBillingGroupBy cloudBillingGroupBy = new CloudBillingGroupBy();
    cloudBillingGroupBy.setEntityGroupBy(CloudEntityGroupBy.labelsValue);
    return cloudBillingGroupBy;
  }

  private CloudBillingGroupBy getTagsKeyGroupBy() {
    CloudBillingGroupBy cloudBillingGroupBy = new CloudBillingGroupBy();
    cloudBillingGroupBy.setEntityGroupBy(CloudEntityGroupBy.tagsKey);
    return cloudBillingGroupBy;
  }

  private CloudBillingGroupBy getTagsValueGroupBy() {
    CloudBillingGroupBy cloudBillingGroupBy = new CloudBillingGroupBy();
    cloudBillingGroupBy.setEntityGroupBy(CloudEntityGroupBy.tagsValue);
    return cloudBillingGroupBy;
  }
}
