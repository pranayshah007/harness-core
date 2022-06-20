package io.harness.jira.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.harness.jira.JiraIssueResolution;

import java.io.IOException;

public class JiraIssueResolutionDeserializer extends StdDeserializer<JiraIssueResolution>
{
	public JiraIssueResolutionDeserializer() {
		this(null);
	}

	public JiraIssueResolutionDeserializer(Class<?> vc) {
		super(vc);
	}

	@Override
	public JiraIssueResolution deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException
	{
		JsonNode node = jp.getCodec().readTree(jp);
		return new JiraIssueResolution(node);
	}
}
