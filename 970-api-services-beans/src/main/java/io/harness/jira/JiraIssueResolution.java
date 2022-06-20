package io.harness.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.harness.jackson.JsonNodeUtils;
import io.harness.jira.deserializer.JiraIssueResolutionDeserializer;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = JiraIssueResolutionDeserializer.class)
public class JiraIssueResolution {
	private String id;
	private String name;

	public JiraIssueResolution(JsonNode jsonNode) {
		this.id = JsonNodeUtils.mustGetString(jsonNode, "id");
		this.name = JsonNodeUtils.mustGetString(jsonNode, "name");
	}
}
