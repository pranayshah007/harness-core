/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.anomalydetection;

import static io.harness.rule.OwnerRule.TRUNAPUSHPA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.anomaly.AnomalyDataStub;
import io.harness.ccm.anomaly.url.HarnessNgUrl;
import io.harness.ccm.commons.entities.anomaly.AnomalyData;
import io.harness.rule.Owner;

import java.net.URISyntaxException;
import java.sql.SQLException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class HarnessNgUrlTest extends CategoryTest {
  AnomalyData anomalyData;
  String baseUrl;

  @Before
  public void setUp() throws SQLException {
    baseUrl = "https://qa.harness.io";
    anomalyData = AnomalyDataStub.getAnomalyData();
  }

  @Test
  @Owner(developers = TRUNAPUSHPA)
  @Category(UnitTests.class)
  @Ignore("Ignoring for now as its flaky")
  public void checkAnomalyNgUrl() throws URISyntaxException {
    assertThat(HarnessNgUrl.getPerspectiveAnomalyUrl(
                   "zEaak-FLS425IEO7OLzMUg", "Shza0IsVQwm_ZMPiCM45pQ", "Gcp", anomalyData, baseUrl))
        .isEqualTo(
            "https://qa.harness.io/ng/#/account/zEaak-FLS425IEO7OLzMUg/ce/perspectives/Shza0IsVQwm_ZMPiCM45pQ/name/Gcp?filters=[%7B%22field%22:%7B%22fieldId%22:%22gcpProduct%22,%22fieldName%22:%22Product%22,%22identifier%22:%22GCP%22,%22identifierName%22:%22GCP%22%7D,%22operator%22:%22IN%22,%22type%22:%22VIEW_ID_CONDITION%22,%22values%22:[%22MongoDB%20Inc.%20MongoDB%20Atlas%22]%7D,%7B%22field%22:%7B%22fieldId%22:%22gcpProjectId%22,%22fieldName%22:%22Project%22,%22identifier%22:%22GCP%22,%22identifierName%22:%22GCP%22%7D,%22operator%22:%22IN%22,%22type%22:%22VIEW_ID_CONDITION%22,%22values%22:[%22pr10406a87045145c3%22]%7D,%7B%22field%22:%7B%22fieldId%22:%22gcpSKUDescription%22,%22fieldName%22:%22SKUs%22,%22identifier%22:%22GCP%22,%22identifierName%22:%22GCP%22%7D,%22operator%22:%22IN%22,%22type%22:%22VIEW_ID_CONDITION%22,%22values%22:[%22MongoDB%20Atlas%20-%20Elastic%20Billing%20Subscription%20Overage%20Atlas%20Credits%22]%7D]&groupBy=%7B%22fieldId%22:%22gcpSkuDescription%22,%22fieldName%22:%22SKUs%22,%22identifier%22:%22GCP%22,%22identifierName%22:%22GCP%22%7D&timeRange=%7B%22to%22:%222022-08-21%22,%22from%22:%222022-08-03%22%7D&aggregation=%22DAY%22&chartType=%22column%22");
  }
}
