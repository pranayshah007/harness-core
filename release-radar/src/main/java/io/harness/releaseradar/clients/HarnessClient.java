package io.harness.releaseradar.clients;

import com.google.gson.Gson;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.Scanner;

@Slf4j
public class HarnessClient {
    @ToString
    static class VersionResponse {
        VersionData resource;
        // Add any other fields as needed
    }
    @ToString
    static class VersionData {
        VersionInfo versionInfo;
        // Add any other fields as needed
    }
    @ToString
    @Data
    public static class VersionInfo {
        String version;
        String buildNo;
        String gitCommit;
        String gitBranch;
        String timestamp;
        String patch;
        // Add any other fields as needed
    }


    public VersionInfo getCurrentVersionStatus(String inputUrl) {
        try {
            URL url = new URL(inputUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            if (conn.getResponseCode() != 200) {
                throw new RuntimeException("HTTP error code : " + conn.getResponseCode());
            }

            Scanner scanner = new Scanner(conn.getInputStream());
            String responseBody = scanner.useDelimiter("\\A").next();
            scanner.close();

            // Parse the JSON response
            VersionResponse versionResponse = new Gson().fromJson(responseBody, VersionResponse.class);
            VersionInfo versionInfo = versionResponse.resource.versionInfo;

            System.out.println("Version: " + versionInfo.version);
            System.out.println("Build Number: " + versionInfo.buildNo);
            System.out.println("Git Commit: " + versionInfo.gitCommit);
            System.out.println("Git Branch: " + versionInfo.gitBranch);
            System.out.println("Timestamp: " + versionInfo.timestamp);
            System.out.println("Patch: " + versionInfo.patch);

            conn.disconnect();
            return versionInfo;
        } catch (ProtocolException e) {
            e.printStackTrace();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}