// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package git

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"testing"
	"time"

	"github.com/harness/harness-core/commons/go/lib/logs"
	pb "github.com/harness/harness-core/product/ci/scm/proto"

	"github.com/drone/go-scm/scm"
	"github.com/stretchr/testify/assert"
	"go.uber.org/zap"
)

func TestCreatePR(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(201)
		content, _ := os.ReadFile("testdata/pr.json")
		fmt.Fprint(w, string(content))
	}))
	defer ts.Close()

	in := &pb.CreatePRRequest{
		Slug:   "tphoney/scm-test",
		Body:   "body text",
		Title:  "pr from scm service",
		Source: "branchy",
		Target: "main",
		Provider: &pb.Provider{
			Hook: &pb.Provider_Github{
				Github: &pb.GithubProvider{
					Provider: &pb.GithubProvider_AccessToken{
						AccessToken: "963408579168567c07ff8bfd2a5455e5307f74d4",
					},
				},
			},
			Endpoint: ts.URL,
		},
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := CreatePR(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.Equal(t, int32(201), got.Status, "Correct http response")
}

func TestFindFilesInPR(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(200)
		content, _ := os.ReadFile("testdata/pr_files.json")
		fmt.Fprint(w, string(content))
	}))
	defer ts.Close()
	in := &pb.FindFilesInPRRequest{
		Slug:   "tphoney/scm-test",
		Number: int32(102),
		Provider: &pb.Provider{
			Hook: &pb.Provider_Github{
				Github: &pb.GithubProvider{
					Provider: &pb.GithubProvider_AccessToken{
						AccessToken: "963408579168567c07ff8bfd2a5455e5307f74d4",
					},
				},
			},
			Endpoint: ts.URL,
		},
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := FindFilesInPR(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.Equal(t, 4, len(got.Files), "4 files")
	assert.True(t, got.Files[1].Added, "file 1 added")
	assert.True(t, got.Files[2].Renamed, "file 2 renamed")
	assert.True(t, got.Files[3].Deleted, "file deleted")
	assert.Equal(t, int32(0), got.Pagination.Next, "No next page")
}

func TestCreateBranch(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(201)
		content, _ := os.ReadFile("testdata/branch.json")
		fmt.Fprint(w, string(content))
	}))
	defer ts.Close()

	in := &pb.CreateBranchRequest{
		Slug:     "tphoney/scm-test",
		Name:     "yooo",
		CommitId: "0efb1bed7c6a4871cb4ddb862ecc2111e11f31ee",
		Provider: &pb.Provider{
			Hook: &pb.Provider_Github{
				Github: &pb.GithubProvider{
					Provider: &pb.GithubProvider_AccessToken{
						AccessToken: "963408579168567c07ff8bfd2a5455e5307f74d4",
					},
				},
			},
			Endpoint: ts.URL,
		},
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := CreateBranch(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.Equal(t, int32(201), got.Status, "Correct http response")
}

func TestGetLatestCommit(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(200)
		content, _ := os.ReadFile("testdata/commit.json")
		fmt.Fprint(w, string(content))
	}))
	defer ts.Close()

	in := &pb.GetLatestCommitRequest{
		Slug: "tphonerry/scm-test",
		Type: &pb.GetLatestCommitRequest_Branch{
			Branch: "main",
		},
		Provider: &pb.Provider{
			Hook: &pb.Provider_Github{
				Github: &pb.GithubProvider{
					Provider: &pb.GithubProvider_AccessToken{
						AccessToken: "963408579168567c07ff8bfd2a5455e5307f74d4",
					},
				},
			},
			Endpoint: ts.URL,
		},
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := GetLatestCommit(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.NotNil(t, got.Commit.Sha, "There is a commit id")
}

func TestGetLatestCommitBitbucketOnprem(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(200)
		content, _ := os.ReadFile("testdata/commit_bb_onprem.json")
		fmt.Fprint(w, string(content))
	}))
	defer ts.Close()

	in := &pb.GetLatestCommitRequest{
		Slug: "har/k8s-manifests",
		Type: &pb.GetLatestCommitRequest_Branch{
			Branch: "master",
		},
		Provider: &pb.Provider{
			Hook: &pb.Provider_BitbucketServer{
				BitbucketServer: &pb.BitbucketServerProvider{
					Username:            "ff",
					PersonalAccessToken: "ODUxWrong8QrWxs",
				},
			},
			Endpoint: ts.URL,
		},
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := GetLatestCommit(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.NotNil(t, got.Commit.Sha, "There is a commit id")
	assert.NotNil(t, got.Commit.Link, "There is a link")
}

func TestListBranches(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(200)
		content, _ := os.ReadFile("testdata/branches.json")
		fmt.Fprint(w, string(content))
	}))
	defer ts.Close()

	in := &pb.ListBranchesRequest{
		Slug: "tphoney/scm-test",
		Provider: &pb.Provider{
			Hook: &pb.Provider_Github{
				Github: &pb.GithubProvider{
					Provider: &pb.GithubProvider_AccessToken{
						AccessToken: "963408579168567c07ff8bfd2a5455e5307f74d4",
					},
				},
			},
			Endpoint: ts.URL,
		},
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := ListBranches(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.Equal(t, 1, len(got.Branches), "one branch")
	assert.Equal(t, int32(0), got.Pagination.Next, "No next page")
}

func TestListBranchesWithDefault(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(200)
		content, _ := os.ReadFile("testdata/branches_with_default.json")
		fmt.Fprint(w, string(content))
	}))
	defer ts.Close()

	in := &pb.ListBranchesWithDefaultRequest{
		Slug: "tphoney/scm-test",
		Provider: &pb.Provider{
			Hook: &pb.Provider_Github{
				Github: &pb.GithubProvider{
					Provider: &pb.GithubProvider_AccessToken{
						AccessToken: "963408579168567c07ff8bfd2a5455e5307f74d4",
					},
				},
			},
			Endpoint: ts.URL,
		},
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := ListBranchesWithDefault(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.Equal(t, 1, len(got.Branches), "one branch")
	assert.Equal(t, int32(0), got.Pagination.Next, "No next page")
}

func TestListCommits(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(200)
		content, _ := os.ReadFile("testdata/commits.json")
		fmt.Fprint(w, string(content))
	}))
	defer ts.Close()

	in := &pb.ListCommitsRequest{
		Slug: "tphoney/scm-test",
		Type: &pb.ListCommitsRequest_Branch{
			Branch: "main",
		},
		Provider: &pb.Provider{
			Hook: &pb.Provider_Github{
				Github: &pb.GithubProvider{
					Provider: &pb.GithubProvider_AccessToken{
						AccessToken: "963408579168567c07ff8bfd2a5455e5307f74d4",
					},
				},
			},
			Endpoint: ts.URL,
		},
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := ListCommits(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.Equal(t, len(got.CommitIds), 1, "1 commit")
	assert.Equal(t, int32(0), got.Pagination.Next, "No next page")
}

func TestListCommitsInPR(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(200)
		content, _ := os.ReadFile("testdata/commits.json")
		fmt.Fprint(w, string(content))
	}))
	defer ts.Close()

	in := &pb.ListCommitsInPRRequest{
		Slug:   "tphoney/scm-test",
		Number: 1234,
		Provider: &pb.Provider{
			Hook: &pb.Provider_Github{
				Github: &pb.GithubProvider{
					Provider: &pb.GithubProvider_AccessToken{
						AccessToken: "963408579168567c07ff8bfd2a5455e5307f74d4",
					},
				},
			},
			Endpoint: ts.URL,
		},
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := ListCommitsInPR(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.Equal(t, len(got.Commits), 1, "1 commit")
	assert.Equal(t, int32(0), got.Pagination.Next, "No next page")
}

func TestCompareCommits(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(200)
		content, _ := os.ReadFile("testdata/compare.json")
		fmt.Fprint(w, string(content))
	}))
	defer ts.Close()

	in := &pb.CompareCommitsRequest{
		Slug:   "tphoney/scm-test",
		Source: "553c2077f0edc3d5dc5d17262f6aa498e69d6f8e",
		Target: "7fd1a60b01f91b314f59955a4e4d4e80d8edf11d",
		Provider: &pb.Provider{
			Hook: &pb.Provider_Github{
				Github: &pb.GithubProvider{
					Provider: &pb.GithubProvider_AccessToken{
						AccessToken: "963408579168567c07ff8bfd2a5455e5307f74d4",
					},
				},
			},
			Endpoint: ts.URL,
		},
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := CompareCommits(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.Equal(t, len(got.Files), 1, "1 file")
	assert.Equal(t, int32(0), got.Pagination.Next, "No next page")
}

func TestGetGetUserRepos(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(200)
		content, _ := os.ReadFile("testdata/repos.json")
		fmt.Fprint(w, string(content))
	}))
	defer ts.Close()

	print(ts.URL)
	time.Sleep(20000)
	in := &pb.GetUserReposRequest{
		Provider: &pb.Provider{
			Hook: &pb.Provider_Github{
				Github: &pb.GithubProvider{
					Provider: &pb.GithubProvider_AccessToken{
						AccessToken: "963408579168567c07ff8bfd2a5455e5307f74d4",
					},
				},
			},
			Endpoint: ts.URL,
		},
		Version: 2,
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := GetUserRepos(context.Background(), in, log.Sugar())

	fmt.Println(got)	
	fmt.Println(err)

	assert.Nil(t, err, "no errors")
	assert.Equal(t, len(got.Repos), 1, "1 repo")
	assert.Equal(t, int32(0), got.Pagination.Next, "No next page")
}

func TestGetGetUserRepo(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(200)
		content, _ := os.ReadFile("testdata/repo.json")
		fmt.Fprint(w, string(content))
	}))
	defer ts.Close()

	in := &pb.GetUserRepoRequest{
		Provider: &pb.Provider{
			Hook: &pb.Provider_Github{
				Github: &pb.GithubProvider{
					Provider: &pb.GithubProvider_AccessToken{
						AccessToken: "963408579168567c07ff8bfd2a5455e5307f74d4",
					},
				},
			},
			Endpoint: ts.URL,
		},
		Slug: "test_project",
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := GetUserRepo(context.Background(), in, log.Sugar())
	
	assert.Nil(t, err, "no errors")
	assert.Equal(t, got.GetRepo().GetId(), "12345", "1 repo")
}

func TestGetAuthenticatedUser(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(200)
		content, _ := os.ReadFile("testdata/user.json")
		fmt.Fprint(w, string(content))
	}))
	defer ts.Close()

	in := &pb.GetAuthenticatedUserRequest{
		Provider: &pb.Provider{
			Hook: &pb.Provider_Github{
				Github: &pb.GithubProvider{
					Provider: &pb.GithubProvider_AccessToken{
						AccessToken: "963408579168567c07ff8bfd2a5455e5307f74d4",
					},
				},
			},
			Endpoint: ts.URL,
		},
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := GetAuthenticatedUser(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.Equal(t, got.Username, "monalisa octocat", "user: octocat")
}

func TestFindPR(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(200)
		content, _ := os.ReadFile("testdata/find_pr.json")
		fmt.Fprint(w, string(content))
	}))
	defer ts.Close()

	in := &pb.FindPRRequest{
		Slug:   "octocat/hello-world",
		Number: 1,
		Provider: &pb.Provider{
			Hook: &pb.Provider_Github{
				Github: &pb.GithubProvider{
					Provider: &pb.GithubProvider_AccessToken{
						AccessToken: "963408579168567c07ff8bfd2a5455e5307f74d4",
					},
				},
			},
			Endpoint: ts.URL,
		},
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := FindPR(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.NotNil(t, got.Pr, "no errors")
	assert.Equal(t, got.Pr.Number, int64(1), "number: 1")
	assert.Equal(t, got.Pr.Sha, "7044a8a032e85b6ab611033b2ac8af7ce85805b2", "sha: 7044a8a032e85b6ab611033b2ac8af7ce85805b2")
}

func TestFindPullRequestWithRetry(t *testing.T) {
	responseCounter := 0
	responses := []func(w http.ResponseWriter, r *http.Request){
		func(w http.ResponseWriter, r *http.Request) {
			w.WriteHeader(500)
		},
		func(w http.ResponseWriter, r *http.Request) {
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(200)
			content, _ := os.ReadFile("testdata/find_pr.json")
			fmt.Fprint(w, string(content))
		},
	}
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		responses[responseCounter](w, r)
		responseCounter++
	}))
	defer ts.Close()

	in := &pb.FindPRRequest{
		Slug:   "octocat/hello-world",
		Number: 1,
		Provider: &pb.Provider{
			Hook: &pb.Provider_Github{
				Github: &pb.GithubProvider{
					Provider: &pb.GithubProvider_AccessToken{
						AccessToken: "token",
					},
				},
			},
			Endpoint: ts.URL,
		},
	}

	config := zap.NewProductionConfig()
	log, _ := config.Build()
	got, err := FindPR(context.Background(), in, log.Sugar())
	assert.Nil(t, err)
	assert.Equal(t, got.Status, int32(200))
	assert.NotNil(t, got.Pr)
	assert.Equal(t, got.Pr.Number, int64(1), "number: 1")
	assert.Equal(t, got.Pr.Sha, "7044a8a032e85b6ab611033b2ac8af7ce85805b2", "sha: 7044a8a032e85b6ab611033b2ac8af7ce85805b2")
}

func TestFindFilesInPRWithRetry(t *testing.T) {
	responseCounter := 0
	responses := []func(w http.ResponseWriter, r *http.Request){
		func(w http.ResponseWriter, r *http.Request) {
			w.WriteHeader(500)
		},
		func(w http.ResponseWriter, r *http.Request) {
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(200)
			content, _ := os.ReadFile("testdata/pr_files.json")
			fmt.Fprint(w, string(content))
		},
	}
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		responses[responseCounter](w, r)
		responseCounter++
	}))
	in := &pb.FindFilesInPRRequest{
		Slug:   "tphoney/scm-test",
		Number: int32(102),
		Provider: &pb.Provider{
			Hook: &pb.Provider_Github{
				Github: &pb.GithubProvider{
					Provider: &pb.GithubProvider_AccessToken{
						AccessToken: "token",
					},
				},
			},
			Endpoint: ts.URL,
		},
	}

	config := zap.NewProductionConfig()
	log, _ := config.Build()
	got, err := FindFilesInPR(context.Background(), in, log.Sugar())
	assert.Nil(t, err)
	assert.NotNil(t, got)
	assert.Equal(t, 4, len(got.Files), "4 files")
	assert.True(t, got.Files[1].Added, "file 1 added")
	assert.True(t, got.Files[2].Renamed, "file 2 renamed")
	assert.True(t, got.Files[3].Deleted, "file deleted")
	assert.Equal(t, int32(0), got.Pagination.Next, "No next page")
}

func TestListCommitsInPRWithRetry(t *testing.T) {
	responseCounter := 0
	responses := []func(w http.ResponseWriter, r *http.Request){
		func(w http.ResponseWriter, r *http.Request) {
			w.WriteHeader(500)
		},
		func(w http.ResponseWriter, r *http.Request) {
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(200)
			content, _ := os.ReadFile("testdata/commits.json")
			fmt.Fprint(w, string(content))
		},
	}
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		responses[responseCounter](w, r)
		responseCounter++
	}))
	in := &pb.ListCommitsInPRRequest{
		Slug:   "tphoney/scm-test",
		Number: 1234,
		Provider: &pb.Provider{
			Hook: &pb.Provider_Github{
				Github: &pb.GithubProvider{
					Provider: &pb.GithubProvider_AccessToken{
						AccessToken: "token",
					},
				},
			},
			Endpoint: ts.URL,
		},
	}

	config := zap.NewProductionConfig()
	log, _ := config.Build()
	got, err := ListCommitsInPR(context.Background(), in, log.Sugar())
	assert.Nil(t, err)
	assert.Equal(t, len(got.Commits), 1)
	assert.Equal(t, int32(0), got.Pagination.Next)
}

func TestGetLatestCommitBitbucketOnpremWithRetry(t *testing.T) {
	responseCounter := 0
	responses := []func(w http.ResponseWriter, r *http.Request){
		func(w http.ResponseWriter, r *http.Request) {
			w.WriteHeader(500)
		},
		func(w http.ResponseWriter, r *http.Request) {
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(200)
			enc := json.NewEncoder(w)
			enc.Encode([]scm.Commit{{Sha: "master"}})
		},
		func(w http.ResponseWriter, r *http.Request) {
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(200)
			content, _ := os.ReadFile("testdata/commit_bb_onprem.json")
			fmt.Fprint(w, string(content))
		},
	}
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		responses[responseCounter](w, r)
		fmt.Println(responseCounter)
		responseCounter++
	}))

	in := &pb.GetLatestCommitRequest{
		Slug: "har/k8s-manifests",
		Type: &pb.GetLatestCommitRequest_Branch{
			Branch: "refs/head/master",
		},
		Provider: &pb.Provider{
			Hook: &pb.Provider_BitbucketServer{
				BitbucketServer: &pb.BitbucketServerProvider{
					Username:            "username",
					PersonalAccessToken: "token",
				},
			},
			Endpoint: ts.URL,
		},
	}

	config := zap.NewProductionConfig()
	log, _ := config.Build()
	got, err := GetLatestCommit(context.Background(), in, log.Sugar())
	assert.Nil(t, err, "no errors")
	assert.NotNil(t, got.Commit.Sha, "There is a commit id")
	assert.NotNil(t, got.Commit.Link, "There is a link")
}

func TestGetLatestCommitWithRetry(t *testing.T) {
	responseCounter := 0
	responses := []func(w http.ResponseWriter, r *http.Request){
		func(w http.ResponseWriter, r *http.Request) {
			w.WriteHeader(500)
		},
		func(w http.ResponseWriter, r *http.Request) {
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(200)
			content, _ := os.ReadFile("testdata/commit.json")
			fmt.Fprint(w, string(content))
		},
	}
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		responses[responseCounter](w, r)
		fmt.Println(responseCounter)
		responseCounter++
	}))
	defer ts.Close()

	in := &pb.GetLatestCommitRequest{
		Slug: "tphonerry/scm-test",
		Type: &pb.GetLatestCommitRequest_Branch{
			Branch: "main",
		},
		Provider: &pb.Provider{
			Hook: &pb.Provider_Github{
				Github: &pb.GithubProvider{
					Provider: &pb.GithubProvider_AccessToken{
						AccessToken: "token",
					},
				},
			},
			Endpoint: ts.URL,
		},
	}

	config := zap.NewProductionConfig()
	log, _ := config.Build()
	got, err := GetLatestCommit(context.Background(), in, log.Sugar())
	assert.Nil(t, err, "no errors")
	assert.NotNil(t, got.Commit.Sha, "There is a commit id")
}

// func TestGetLatestCommitOnFileOnNonExistingFile(t *testing.T) {
// 	provider :=	&pb.Provider{
// 			Hook: &pb.Provider_BitbucketCloud{
// 				BitbucketCloud: &pb.BitbucketCloudProvider{
// 					Username:    "mohitgargharness",
// 					AppPassword: "d58ztzmwJksybeatmP4e",
// 				},
// 			},
// 		}
// 	in := &pb.GetLatestCommitOnFileRequest {
// 			Slug: "mohitgargharness/test-repository",
// 			Branch: "main",
// 			Provider: provider,
// 			FilePath: "DUMMYPATH",
// 		}

// 	log, _ := logs.GetObservedLogger(zap.InfoLevel)
// 	latestCommitIdResponse, err := GetLatestCommitOnFile(context.Background(), in, log.Sugar())

// 	assert.NotNil(t, err, "found errors")
// 	assert.Equal(t, latestCommitIdResponse.CommitId, "", "status matches")
// }

// func TestGetLatestCommitOnExistingFile(t *testing.T) {
// 	provider :=	&pb.Provider{
// 			Hook: &pb.Provider_BitbucketCloud{
// 				BitbucketCloud: &pb.BitbucketCloudProvider{
// 					Username:    "mohitgargharness",
// 					AppPassword: "d58ztzmwJksybeatmP4e",
// 				},
// 			},
// 		}
// 	in := &pb.GetLatestCommitOnFileRequest {
// 		Slug: "mohitgargharness/test-repository",
// 		Branch: "master",
// 		Provider: provider,
// 		FilePath: "DO-NOT-DELETE.txt",
// 	}

// 	log, _ := logs.GetObservedLogger(zap.InfoLevel)
// 	latestCommitIdResponse, err := GetLatestCommitOnFile(context.Background(), in, log.Sugar())

// 	assert.Nil(t, err, "no errors")
// 	assert.NotNil(t, latestCommitIdResponse.CommitId, "commit exists")
// }

// func TestListCommitsBitbucket(t *testing.T) {
// 	in := &pb.ListCommitsRequest{
// 		Slug: "mohitgargharness/test-repository",
// 		Type: &pb.ListCommitsRequest_Branch{
// 			Branch: "master",
// 		},
// 		Provider: &pb.Provider{
// 			Hook: &pb.Provider_BitbucketCloud{
// 				BitbucketCloud: &pb.BitbucketCloudProvider{
// 					Username:    "mohitgargharness",
// 					AppPassword: "d58ztzmwJksybeatmP4e",
// 				},
// 			},
// 		},
// 	}

// 	log, _ := logs.GetObservedLogger(zap.InfoLevel)
// 	got, err := ListCommits(context.Background(), in, log.Sugar())

// 	assert.Nil(t, err, "no errors")
// 	assert.NotNil(t, len(got.CommitIds), "has commit")
// }

// func TestListCommitsBitbucketOnFile(t *testing.T) {
// 	in := &pb.ListCommitsRequest{
// 		Slug: "mohitgargharness/test-repository",
// 		Type: &pb.ListCommitsRequest_Branch{
// 			Branch: "master",
// 		},
// 		FilePath: "DO-NOT-DELETE.txt",
// 		Provider: &pb.Provider{
// 			Hook: &pb.Provider_BitbucketCloud{
// 				BitbucketCloud: &pb.BitbucketCloudProvider{
// 					Username:    "mohitgargharness",
// 					AppPassword: "d58ztzmwJksybeatmP4e",
// 				},
// 			},
// 		},
// 	}

// 	log, _ := logs.GetObservedLogger(zap.InfoLevel)
// 	got, err := ListCommits(context.Background(), in, log.Sugar())

// 	assert.Nil(t, err, "no errors")
// 	assert.NotNil(t, len(got.CommitIds), "has commit")
// }

// func TestListCommitsBitbucketOnNewFileThatDoesntExist(t *testing.T) {
// 	in := &pb.ListCommitsRequest{
// 		Slug: "mohitgargharness/test-repository",
// 		Type: &pb.ListCommitsRequest_Branch{
// 			Branch: "master",
// 		},
// 		FilePath: "DUMMY-FILE-THAT-NEVER-EXISTED",
// 		Provider: &pb.Provider{
// 			Hook: &pb.Provider_BitbucketCloud{
// 				BitbucketCloud: &pb.BitbucketCloudProvider{
// 					Username:    "mohitgargharness",
// 					AppPassword: "d58ztzmwJksybeatmP4e",
// 				},
// 			},
// 		},
// 	}

// 	log, _ := logs.GetObservedLogger(zap.InfoLevel)
// 	got, err := ListCommits(context.Background(), in, log.Sugar())

// 	assert.Nil(t, err, "no errors")
// 	assert.Equal(t, len(got.CommitIds), 0, "0 commits found")
// }
