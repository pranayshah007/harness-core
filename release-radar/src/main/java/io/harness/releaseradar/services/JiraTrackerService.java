package io.harness.releaseradar.services;

import io.harness.releaseradar.beans.JiraStatusDetails;

public interface JiraTrackerService {
    JiraStatusDetails getJiraStatusDetails(String jiraId);
    void getJiraTimeline(String jiraId);
}
