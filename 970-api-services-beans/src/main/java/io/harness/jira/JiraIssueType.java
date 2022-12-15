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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Data;
import net.rcarz.jiraclient.IssueType;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

@OwnedBy(CDC)
@Data
public class JiraIssueType {
  @NotNull private String id;
  @NotNull private String name;
  @NotNull private boolean isSubTask;
  @NotNull private String description;
  @JsonProperty("fields") private Map<String, JiraField> jiraFields = new HashMap<>();
  private List<JiraStatus> jiraStatusList = new ArrayList<>();

  public JiraIssueType(JSONObject data) {
    this.id = data.getString("id");
    this.name = data.getString("name");
    this.description = data.containsKey("description") ? data.getString("description") : "";
    this.isSubTask = data.getBoolean("subtask");
    JSONObject fields = data.getJSONObject("fields");
    fields.keySet().forEach(keyStr -> {
      String kk = (String) keyStr;
      JSONObject fieldData = fields.getJSONObject(kk);
      this.jiraFields.put(kk, new JiraField(fieldData, kk));
    });

    JSONArray statuses = data.containsKey("statuses") ? data.getJSONArray("statuses") : new JSONArray();
    statuses.forEach(status -> jiraStatusList.add(new JiraStatus((JSONObject) status)));
  }

  JiraIssueType(IssueType issueType) {
    this.id = issueType.getId();
    this.name = issueType.getName();
    this.description = issueType.getDescription();
    this.isSubTask = issueType.isSubtask();
    JSONObject fields = issueType.getFields();
    fields.keySet().forEach(keyStr -> {
      String kk = (String) keyStr;
      JSONObject fieldData = fields.getJSONObject(kk);
      this.jiraFields.put(kk, new JiraField(fieldData, kk));
    });
  }

  public JiraIssueType(JiraIssueTypeNG jiraIssueTypeNG) {
    this.id = jiraIssueTypeNG.getId();
    this.name = jiraIssueTypeNG.getName();
    this.description = jiraIssueTypeNG.getDescription();
    this.isSubTask = jiraIssueTypeNG.isSubTask();
    jiraIssueTypeNG.getStatuses().forEach(status -> this.jiraStatusList.add(new JiraStatus(status)));
    jiraIssueTypeNG.getFields().forEach((key, val) -> this.jiraFields.put(val.getKey(), new JiraField(val)));
  }
}
