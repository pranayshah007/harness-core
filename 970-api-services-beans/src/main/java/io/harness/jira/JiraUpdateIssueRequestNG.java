/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.jira;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.jira.JiraInstanceData.JiraDeploymentType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.HashMap;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@OwnedBy(CDC)
@Data
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraUpdateIssueRequestNG {
  JiraIssueTransitionRequestNG transition;
  @NotNull Map<String, Object> fields = new HashMap<>();

  public JiraUpdateIssueRequestNG(JiraIssueUpdateMetadataNG updateMetadata, String transitionId,
      Map<String, String> fields, JiraDeploymentType jiraDeploymentType) {
    if (EmptyPredicate.isNotEmpty(transitionId)) {
      this.transition = new JiraIssueTransitionRequestNG(transitionId);
    }

    if (EmptyPredicate.isEmpty(fields)) {
      return;
    }

    fields = new HashMap<>(fields);
    JiraIssueUtilsNG.updateFieldValues(this.fields, updateMetadata.getFields(), fields, false, jiraDeploymentType);
  }
}
