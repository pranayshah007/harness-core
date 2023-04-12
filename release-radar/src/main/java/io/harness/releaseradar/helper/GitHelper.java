package io.harness.releaseradar.helper;

public class GitHelper {
    public static String getJiraId(String commitMessage) {
        String[] splitMessages = commitMessage.split(":");
        if (splitMessages.length < 2) {
            return null;
        }
        return splitMessages[1].trim();
    }
}
