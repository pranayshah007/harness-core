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
    baseUrl = "https://app.harness.io";
    anomalyData = AnomalyDataStub.getAnomalyData();
  }

  @Test
  @Owner(developers = TRUNAPUSHPA)
  @Category(UnitTests.class)
  public void checkAnomalyNgUrl() throws URISyntaxException {
    assertThat(HarnessNgUrl.getPerspectiveAnomalyUrl(
                   "wFHXHD0RRQWoO8tIZT5YVw", "CvFIuPsqQGKeZ_xHHiQ5UA", "GCP", anomalyData, baseUrl))
        .isEqualTo(
            "https://app.harness.io/ng/account/wFHXHD0RRQWoO8tIZT5YVw/ce/perspectives/CvFIuPsqQGKeZ_xHHiQ5UA/name/GCP?filters=%5B%7B%22field%22%3A%7B%22fieldId%22%3A%22gcpProduct%22%2C%22fieldName%22%3A%22Product%22%2C%22identifier%22%3A%22GCP%22%2C%22identifierName%22%3A%22GCP%22%7D%2C%22operator%22%3A%22IN%22%2C%22type%22%3A%22VIEW_ID_CONDITION%22%2C%22values%22%3A%5B%22Cloud+Storage%22%5D%7D%2C%7B%22field%22%3A%7B%22fieldId%22%3A%22gcpProjectId%22%2C%22fieldName%22%3A%22Project%22%2C%22identifier%22%3A%22GCP%22%2C%22identifierName%22%3A%22GCP%22%7D%2C%22operator%22%3A%22IN%22%2C%22type%22%3A%22VIEW_ID_CONDITION%22%2C%22values%22%3A%5B%22platform-205701%22%5D%7D%2C%7B%22field%22%3A%7B%22fieldId%22%3A%22gcpSKUDescription%22%2C%22fieldName%22%3A%22SKUs%22%2C%22identifier%22%3A%22GCP%22%2C%22identifierName%22%3A%22GCP%22%7D%2C%22operator%22%3A%22IN%22%2C%22type%22%3A%22VIEW_ID_CONDITION%22%2C%22values%22%3A%5B%22Download+Worldwide+Destinations+%28excluding+Asia+%26+Australia%29%22%5D%7D%5D&groupBy=%7B%22fieldId%22%3A%22gcpSkuDescription%22%2C%22fieldName%22%3A%22SKUs%22%2C%22identifier%22%3A%22GCP%22%2C%22identifierName%22%3A%22GCP%22%7D&timeRange=%7B%22from%22%3A%222023-06-26%22%2C%22to%22%3A%222023-07-03%22%7D&aggregation=%22DAY%22&chartType=%22column%22");
  }
}
