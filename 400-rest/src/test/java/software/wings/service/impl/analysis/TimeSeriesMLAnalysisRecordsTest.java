/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.analysis;

import static io.harness.rule.OwnerRule.GEORGE;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import software.wings.WingsBaseTest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Created by sriram_parthasarathy on 10/14/17.
 */
@RunWith(MockitoJUnitRunner.class)
public class TimeSeriesMLAnalysisRecordsTest extends WingsBaseTest {
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testJsonParsing() throws IOException {
    InputStream is = getClass().getClassLoader().getResourceAsStream("verification/TimeSeriesNRAnalysisRecords.json");
    String jsonTxt = IOUtils.toString(is, Charset.defaultCharset());
    TimeSeriesMLAnalysisRecord records = JsonUtils.asObject(jsonTxt, TimeSeriesMLAnalysisRecord.class);
    assert records.getTransactions().size() == 1;
    assert records.getTransactions().get("0").getMetrics().size() == 1;
    assert !records.getTransactions().get("0").getMetrics().get("2").getControl().getData().isEmpty();
    assert !records.getTransactions().get("0").getMetrics().get("2").getTest().getData().isEmpty();
    assert !records.getTransactions().get("0").getMetrics().get("2").getControl().getWeights().isEmpty();
    assert !records.getTransactions().get("0").getMetrics().get("2").getTest().getWeights().isEmpty();
    assert records.getTransactions().get("0").getMetrics().get("2").getResults().size() == 1;
    TimeSeriesMLHostSummary data =
        records.getTransactions().get("0").getMetrics().get("2").getResults().get("ip-172-31-0-38.harness.io");
    assert !data.getControl_cuts().isEmpty();
    assert !data.getTest_cuts().isEmpty();
    assert !data.getDistance().isEmpty();
    assert data.getRisk() == 2;
    assert Double.compare(data.getScore(), 3.75) == 0;
  }
}
