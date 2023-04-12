package io.harness.releaseradar.clients;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Data;
import lombok.ToString;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.Date;
import java.util.List;

public class GitHubApiClient {
    private static final String BASE_URL = "https://api.github.com";

    private final OkHttpClient client;
    private final Gson gson;

    public GitHubApiClient() {
        this.client = new OkHttpClient();
        this.gson = new GsonBuilder().create();
    }

    public List<Commit> listCommits(String owner, String repo, String apiToken, int pageCount) throws IOException {
        String url = String.format("%s/repos/%s/%s/commits?page=%d&per_page=1", BASE_URL, owner, repo, pageCount);
        Request request = new Request.Builder().addHeader("Authorization", "Token " + apiToken).url(url).build();
        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();
            Commit[] commits = gson.fromJson(responseBody, Commit[].class);
            return List.of(commits);
        }
    }

    @Data
    @ToString
    public static class Commit {
        private String sha;
        private CommitInfo commit;
        // getters and setters omitted for brevity
    }

    @ToString
    @Data
    public static class CommitInfo {
        private String message;
        private CommitterInfo committer;
        // getters and setters omitted for brevity
    }

    @ToString
    @Data
    public static class CommitterInfo {
        private Date date;
    }
}