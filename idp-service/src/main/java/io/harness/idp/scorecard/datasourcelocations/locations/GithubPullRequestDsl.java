/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.locations;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.http.HttpHeaderConfig;
import io.harness.idp.onboarding.beans.BackstageCatalogEntity;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datapoints.parser.DataPointParser;
import io.harness.idp.scorecard.datapoints.parser.DataPointParserFactory;
import io.harness.idp.scorecard.datasourcelocations.client.DslClient;
import io.harness.idp.scorecard.datasourcelocations.client.DslClientFactory;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.IDP)
public class GithubPullRequestDsl implements DataSourceLocation {
  DataPointParserFactory dataPointParserFactory;
  DslClientFactory dslClientFactory;

  @Override
  public Map<String, Object> fetchData(String accountIdentifier, BackstageCatalogEntity entity,
      DataSourceLocationEntity dataSourceLocationEntity, List<DataPointEntity> dataPointsToFetch) {
    for (DataPointEntity dataPoint : dataPointsToFetch) { // isBranchProtected(main), isBranchProtected(develop)
      DataPointParser dataPointParser = dataPointParserFactory.getParser(dataPoint.getIdentifier());
      if (dataPoint.isConditional()) {
        String key = dataPointParser.getReplaceKey(dataPoint); // {branch}
        String value = dataPointParser.extractInputValue(dataPoint.getExpression()); // main/develop
        // replace in the API
      }
    }

    // API
    // headers
    // catalog entity -> https://github.com/harness/harness-idp -> repo details
    // backstage env variables -> GITHUB_TOKEN, APP -> Authorization Header

    // --------------------------------
    // Testing code. To be removed when we have actual code here later.
    String host = "github.com"; // get this from catalog yaml
    String url = "https://api.github.com/repos/vikyathharekal/order-service/contents/README.md";
    String body = "";
    String method = "GET";
    List<HttpHeaderConfig> headers = new ArrayList<>();
    headers.add(HttpHeaderConfig.builder().key("Authorization").value("Bearer xxx").build());
    headers.add(HttpHeaderConfig.builder().key("X-GitHub-Api-Version").value("2022-11-28").build());
    headers.add(HttpHeaderConfig.builder().key("Accept").value("application/vnd.github.v3.raw").build());
    // --------------------------------

    DslClient client = dslClientFactory.getClient(accountIdentifier, host);
    Response response = client.call(accountIdentifier, url, headers, body, method);

    // convert response to Map
    return null;
  }
}
