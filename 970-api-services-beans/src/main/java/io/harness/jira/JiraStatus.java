/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.jira;

import lombok.Data;
import lombok.experimental.FieldNameConstants;
import net.sf.json.JSONObject;

@Data
@FieldNameConstants(innerTypeName = "JiraStatusKeys")
public class JiraStatus {
  private String id;
  private String name;
  private String untranslatedName;
  private String description;
  private JiraStatusCategory statusCategory;

  public JiraStatus(JSONObject jsonObject) {
    this.id = jsonObject.getString(JiraStatusKeys.id);
    this.name = jsonObject.getString(JiraStatusKeys.name);
    this.untranslatedName = jsonObject.getString(JiraStatusKeys.untranslatedName);
    this.description = jsonObject.getString(JiraStatusKeys.description);
    this.statusCategory = new JiraStatusCategory(jsonObject.getJSONObject(JiraStatusKeys.statusCategory));
  }

  public JiraStatus(JiraStatusNG jiraStatusNG) {
    this.id = jiraStatusNG.getId();
    this.name = jiraStatusNG.getName();
    this.statusCategory = new JiraStatusCategory(jiraStatusNG.getStatusCategory());
  }
}
