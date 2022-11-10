/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.jira;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import net.rcarz.jiraclient.IssueType;
import net.rcarz.jiraclient.Project;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

@OwnedBy(CDC)
@Data
public class JiraProjectData {
  private String id;
  private String key;
  private String name;
  @JsonProperty("issuetypes") private List<JiraIssueType> issueTypes = new ArrayList<>();

  public JiraProjectData(JSONObject obj) {
    this.id = obj.getString("id");
    this.key = obj.getString("key");
    this.name = obj.getString("name");
    JSONArray issueTypeList = obj.getJSONArray("issuetypes");
    for (int i = 0; i < issueTypeList.size(); i++) {
      JSONObject issueTypeObj = issueTypeList.getJSONObject(i);
      this.issueTypes.add(new JiraIssueType(issueTypeObj));
    }
  }

  public JiraProjectData(Project project) {
    this.id = project.getId();
    this.key = project.getKey();
    this.name = project.getName();
    List<IssueType> issueTypes = project.getIssueTypes();
    for (IssueType issueType : issueTypes) {
      this.issueTypes.add(new JiraIssueType(issueType));
    }
  }

  public JiraProjectData(JiraProjectNG jiraProjectNG) {
    this.id = jiraProjectNG.getId();
    this.key = jiraProjectNG.getKey();
    this.name = jiraProjectNG.getName();
    jiraProjectNG.getIssueTypes().values().forEach(issueType -> this.issueTypes.add(new JiraIssueType(issueType)));
  }
}
