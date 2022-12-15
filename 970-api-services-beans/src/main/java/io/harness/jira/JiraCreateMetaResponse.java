/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.jira;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.codehaus.jackson.annotate.JsonTypeName;

@OwnedBy(CDC)
@Data
@JsonTypeName("jiraCreateMeta")
public class JiraCreateMetaResponse {
  private String expand;
  private List<JiraProjectData> projects = new ArrayList<>();

  public JiraCreateMetaResponse(JSONObject response) {
    this.expand = response.getString("expand");
    JSONArray projectList = response.getJSONArray("projects");
    for (int i = 0; i < projectList.size(); i++) {
      JSONObject proj = projectList.getJSONObject(i);
      this.projects.add(new JiraProjectData(proj));
    }
  }

  public JiraCreateMetaResponse(JiraIssueCreateMetadataNG jiraIssueCreateMetadataNG) {
    this.expand = "projects";
    jiraIssueCreateMetadataNG.getProjects().values().forEach(proj -> this.projects.add(new JiraProjectData(proj)));
  }
}
