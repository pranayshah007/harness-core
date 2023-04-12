package io.harness.releaseradar.services;

import io.harness.releaseradar.beans.JiraStatusDetails;

public interface JiraTrackerService {
    JiraStatusDetails getJiraStatusDetails(String jiraId);
}
