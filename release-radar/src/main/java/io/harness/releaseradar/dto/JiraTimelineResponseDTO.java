package io.harness.releaseradar.dto;

import io.harness.releaseradar.beans.JiraEventDetails;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class JiraTimelineResponseDTO {
    Map<String, List<JiraEventDetails>> jiraTimelineDetails;
}
