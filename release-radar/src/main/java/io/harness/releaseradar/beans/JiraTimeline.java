package io.harness.releaseradar.beans;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Builder
@Data
public class JiraTimeline {
    Map<String, List<JiraEventDetails>> jiraTimelineDetails;
}
