package io.harness.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.bson.types.ObjectId;

@Data
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraFieldUserPickerNG {
  public static String JIRAUSER_PREFIX = "JIRAUSER";
  private String accountId;
  private String name;

  public JiraFieldUserPickerNG(String identifier) {
    if (ObjectId.isValid(identifier)) {
      this.accountId = identifier;
    } else {
      this.name = identifier;
    }
  }
}
