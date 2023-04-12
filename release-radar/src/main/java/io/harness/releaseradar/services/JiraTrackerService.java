package io.harness.releaseradar.services;

import io.harness.releaseradar.beans.JiraStatusDetails;
import io.harness.releaseradar.beans.JiraTimeline;

public interface JiraTrackerService {
    JiraStatusDetails getJiraStatusDetails(String jiraId);
    JiraTimeline getJiraTimeline(String jiraId);
}
