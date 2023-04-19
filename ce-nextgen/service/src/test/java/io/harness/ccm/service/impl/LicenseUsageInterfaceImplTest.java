/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.service.impl;

import static com.google.cloud.bigquery.FieldValue.Attribute.PRIMITIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.category.element.UnitTests;
import io.harness.ccm.CENextGenConfiguration;
import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.ccm.clickHouse.ClickHouseService;
import io.harness.ccm.commons.beans.config.GcpConfig;
import io.harness.ccm.commons.beans.usage.CELicenseUsageDTO;
import io.harness.ccm.commons.utils.BigQueryHelper;
import io.harness.licensing.usage.params.UsageRequestParams;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldList;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.TableResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class LicenseUsageInterfaceImplTest extends CategoryTest {
  private static final String COST = "cost";
  private static final String CLOUD_PROVIDER = "cloudProvider";
  private static final String MONTH = "month";
  private static final String ACCOUNT_ID = "accountId";
  private static final String ACCOUNT_1 = "account1";

  @InjectMocks private LicenseUsageInterfaceImpl licenseUsageInterface;
  @Mock private BigQuery bigQuery;
  @Mock private TableResult tableResult;
  @Mock private BigQueryService bigQueryService;
  @Mock private CENextGenConfiguration configuration;
  @Mock private ClickHouseService clickHouseService;
  @Mock private BigQueryHelper bigQueryHelper;

  private List<FieldValue> octoberClusterFieldValues;
  private List<FieldValue> octoberAzureFieldValues;
  private List<FieldValue> octoberGcpFieldValues;
  private List<FieldValue> septemberClusterFieldValues;
  private List<FieldValue> septemberGcpFieldValues;
  private FieldValueList octoberClusterFieldValuesList;
  private FieldValueList octoberAzureFieldValuesList;
  private FieldValueList octoberGcpFieldValuesList;
  private FieldValueList septemberClusterFieldValuesList;
  private FieldValueList septemberGcpFieldValuesList;
  private UsageRequestParams usageRequestParams;

  @Before
  public void setup() throws InterruptedException {
    when(configuration.getGcpConfig()).thenReturn(GcpConfig.builder().gcpProjectId("ccm-play").build());
    when(bigQueryService.get()).thenReturn(bigQuery);
    when(bigQuery.query(any(QueryJobConfiguration.class))).thenReturn(tableResult);

    FieldList fieldList = FieldList.of(Field.newBuilder(ACCOUNT_ID, StandardSQLTypeName.STRING).build(),
        Field.newBuilder(COST, StandardSQLTypeName.FLOAT64).build(),
        Field.newBuilder(CLOUD_PROVIDER, StandardSQLTypeName.STRING).build(),
        Field.newBuilder(MONTH, StandardSQLTypeName.STRING).build());

    octoberAzureFieldValues = new ArrayList<>();
    octoberAzureFieldValues.add(FieldValue.of(PRIMITIVE, ACCOUNT_1));
    octoberAzureFieldValues.add(FieldValue.of(PRIMITIVE, "240"));
    octoberAzureFieldValues.add(FieldValue.of(PRIMITIVE, "AZURE"));
    octoberAzureFieldValues.add(FieldValue.of(PRIMITIVE, "October"));
    octoberAzureFieldValuesList = FieldValueList.of(octoberAzureFieldValues, fieldList);

    octoberClusterFieldValues = new ArrayList<>();
    octoberClusterFieldValues.add(FieldValue.of(PRIMITIVE, ACCOUNT_1));
    octoberClusterFieldValues.add(FieldValue.of(PRIMITIVE, "100"));
    octoberClusterFieldValues.add(FieldValue.of(PRIMITIVE, "K8S_AZURE"));
    octoberClusterFieldValues.add(FieldValue.of(PRIMITIVE, "October"));
    octoberClusterFieldValuesList = FieldValueList.of(octoberClusterFieldValues, fieldList);

    octoberGcpFieldValues = new ArrayList<>();
    octoberGcpFieldValues.add(FieldValue.of(PRIMITIVE, ACCOUNT_1));
    octoberGcpFieldValues.add(FieldValue.of(PRIMITIVE, "600"));
    octoberGcpFieldValues.add(FieldValue.of(PRIMITIVE, "GCP"));
    octoberGcpFieldValues.add(FieldValue.of(PRIMITIVE, "October"));
    octoberGcpFieldValuesList = FieldValueList.of(octoberGcpFieldValues, fieldList);

    septemberGcpFieldValues = new ArrayList<>();
    septemberGcpFieldValues.add(FieldValue.of(PRIMITIVE, ACCOUNT_1));
    septemberGcpFieldValues.add(FieldValue.of(PRIMITIVE, "450"));
    septemberGcpFieldValues.add(FieldValue.of(PRIMITIVE, "GCP"));
    septemberGcpFieldValues.add(FieldValue.of(PRIMITIVE, "September"));
    septemberGcpFieldValuesList = FieldValueList.of(septemberGcpFieldValues, fieldList);

    septemberClusterFieldValues = new ArrayList<>();
    septemberClusterFieldValues.add(FieldValue.of(PRIMITIVE, ACCOUNT_1));
    septemberClusterFieldValues.add(FieldValue.of(PRIMITIVE, "80"));
    septemberClusterFieldValues.add(FieldValue.of(PRIMITIVE, "K8S_GCP"));
    septemberClusterFieldValues.add(FieldValue.of(PRIMITIVE, "September"));
    septemberClusterFieldValuesList = FieldValueList.of(septemberClusterFieldValues, fieldList);

    usageRequestParams = UsageRequestParams.builder().build();
  }

  @Test
  @Owner(developers = OwnerRule.ROHIT)
  @Category(UnitTests.class)
  public void getLicenseUsageClusterOnly() {
    Iterable<FieldValueList> fieldValueListIterator =
        Arrays.asList(septemberClusterFieldValuesList, octoberClusterFieldValuesList);
    when(tableResult.iterateAll()).thenReturn(fieldValueListIterator);

    CELicenseUsageDTO licenseUsage =
        licenseUsageInterface.getLicenseUsage(ACCOUNT_1, ModuleType.CE, 0L, usageRequestParams);
    assertThat(licenseUsage.getActiveSpend().getCount()).isEqualTo(Long.valueOf(180));
  }

  @Test
  @Owner(developers = OwnerRule.ROHIT)
  @Category(UnitTests.class)
  public void getLicenseUsageHybridSameCloud() {
    Iterable<FieldValueList> fieldValueListIterator =
        Arrays.asList(septemberClusterFieldValuesList, octoberClusterFieldValuesList, octoberAzureFieldValuesList);
    when(tableResult.iterateAll()).thenReturn(fieldValueListIterator);

    CELicenseUsageDTO licenseUsage =
        licenseUsageInterface.getLicenseUsage(ACCOUNT_1, ModuleType.CE, 0L, usageRequestParams);
    assertThat(licenseUsage.getActiveSpend().getCount()).isEqualTo(Long.valueOf(320));
  }

  @Test
  @Owner(developers = OwnerRule.ROHIT)
  @Category(UnitTests.class)
  public void getLicenseUsageHybridMultiCloudCloud() {
    Iterable<FieldValueList> fieldValueListIterator = Arrays.asList(septemberClusterFieldValuesList,
        octoberClusterFieldValuesList, octoberAzureFieldValuesList, octoberGcpFieldValuesList);
    when(tableResult.iterateAll()).thenReturn(fieldValueListIterator);

    CELicenseUsageDTO licenseUsage =
        licenseUsageInterface.getLicenseUsage(ACCOUNT_1, ModuleType.CE, 0L, usageRequestParams);
    assertThat(licenseUsage.getActiveSpend().getCount()).isEqualTo(Long.valueOf(920));
  }

  @Test
  @Owner(developers = OwnerRule.ROHIT)
  @Category(UnitTests.class)
  public void getLicenseUsageHybridMultiCloudCloudMultiMonth() {
    Iterable<FieldValueList> fieldValueListIterator =
        Arrays.asList(septemberClusterFieldValuesList, septemberGcpFieldValuesList, octoberClusterFieldValuesList,
            octoberAzureFieldValuesList, octoberGcpFieldValuesList);
    when(tableResult.iterateAll()).thenReturn(fieldValueListIterator);

    CELicenseUsageDTO licenseUsage =
        licenseUsageInterface.getLicenseUsage(ACCOUNT_1, ModuleType.CE, 0L, usageRequestParams);
    assertThat(licenseUsage.getActiveSpend().getCount()).isEqualTo(Long.valueOf(1290));
  }
}
