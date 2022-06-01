package io.harness.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraFieldUserPickerNG {
  private String accountId;

  public JiraFieldUserPickerNG(String accountId) {
    this.accountId = accountId;
  }
}
