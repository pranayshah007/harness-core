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

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class GithubPullRequestDsl implements DataSourceLocation {
  DataPointParserFactory dataPointParserFactory;
  DslClientFactory dslClientFactory;

  @Override
  public Map<String, Object> fetchData(String accountIdentifier, BackstageCatalogEntity entity,
      String dataSourceLocationEntity, Map<String, Set<String>> dataPointsAndInputValues) {
    // ---------------------------------------------------------------
    // In case of idp-service generic API, this needs to go in idp-service generic API
    for (Map.Entry<String, Set<String>> entry : dataPointsAndInputValues.entrySet()) {
      // isBranchProtected(main), isBranchProtected(develop)
      String dataPointIdentifier = entry.getKey();
      Set<String> inputValues = entry.getValue();
      DataPointParser dataPointParser = dataPointParserFactory.getParser(dataPointIdentifier);
      if (!inputValues.isEmpty()) {
        String key = dataPointParser.getReplaceKey(); // {branch}
        log.info("replace key : {}, value: [{}]", key, inputValues);
        // replace in the API url or params
      }
    }
    // ---------------------------------------------------------------

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
