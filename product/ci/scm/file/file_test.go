// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package file

import (
	"context"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"

	"github.com/harness/harness-core/commons/go/lib/logs"
	pb "github.com/harness/harness-core/product/ci/scm/proto"
	"github.com/stretchr/testify/assert"
	"go.uber.org/zap"
)

func TestFindFilePositivePath(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(200)
		if strings.Contains(r.URL.Path, "contents") {
			content, _ := os.ReadFile("testdata/FileFindSource.json")
			fmt.Fprint(w, string(content))
		} else {
			content, _ := os.ReadFile("testdata/CommitsOfFile.json")
			fmt.Fprint(w, string(content))
		}
	}))
	defer ts.Close()

	in := &pb.GetFileRequest{
		Slug: "tphoney/scm-test",
		Path: "jello",
		Type: &pb.GetFileRequest_Branch{
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
	got, err := FindFile(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.Contains(t, got.Content, "test repo for source control operations")
}

func TestFindFileNegativePath(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(404)
		content, _ := os.ReadFile("testdata/FileErrorSource.json")
		fmt.Fprint(w, string(content))
	}))
	defer ts.Close()

	in := &pb.GetFileRequest{
		Slug: "tphoney/scm-test",
		Path: "jello",
		Type: &pb.GetFileRequest_Branch{
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
	got, err := FindFile(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.Equal(t, int32(404), got.Status, "Nothing returned")
	assert.Equal(t, "Not Found", got.Error, "Not found")
}
func TestCreateFile(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(201)
		content, _ := os.ReadFile("testdata/FileCreateSource.json")
		fmt.Fprint(w, string(content))
	}))
	defer ts.Close()

	in := &pb.FileModifyRequest{
		Slug:    "tphoney/scm-test",
		Path:    "jello",
		Message: "message",
		Branch:  "main",
		Content: "data",
		Signature: &pb.Signature{
			Name:  "tp honey",
			Email: "tp@harness.io",
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
	got, err := CreateFile(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.Equal(t, got.Status, int32(201), "status matches")
	assert.NotNil(t, got.CommitId)
}

func TestCreateFileConflict(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(409)
		content, _ := os.ReadFile("testdata/FileCreateNoMatch.json")
		fmt.Fprint(w, string(content))
	}))
	defer ts.Close()

	in := &pb.FileModifyRequest{
		Slug:    "tphoney/scm-test",
		Path:    "jello",
		Message: "message",
		Branch:  "main",
		Content: "data",
		BlobId:  "4ea5e4dd2666245c95ea7d4cd353182ea19934b3",
		Signature: &pb.Signature{
			Name:  "tp honey",
			Email: "tp@harness.io",
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
	got, err := CreateFile(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.Equal(t, got.Status, int32(409), "status matches")
	assert.Equal(t, got.Error, "newfile does not match ", "error matches")
}

func TestUpdateFile(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(200)
		content, _ := os.ReadFile("testdata/FileUpdateSource.json")
		fmt.Fprint(w, string(content))
	}))
	defer ts.Close()

	in := &pb.FileModifyRequest{
		Slug:    "tphoney/scm-test",
		Path:    "jello",
		Message: "message",
		Branch:  "main",
		Content: "data",
		BlobId:  "4ea5e4dd2666245c95ea7d4cd353182ea19934b3",
		Signature: &pb.Signature{
			Name:  "tp honey",
			Email: "tp@harness.io",
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
	got, err := UpdateFile(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.Equal(t, got.Status, int32(200), "status matches")
	assert.NotNil(t, got.CommitId)
}

func TestUpdateFileNoMatchGithub(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(409)
		content, _ := os.ReadFile("testdata/FileUpdateNoMatch.json")
		fmt.Fprint(w, string(content))
	}))
	defer ts.Close()

	in := &pb.FileModifyRequest{
		Slug:    "tphoney/scm-test",
		Path:    "jello",
		Message: "message",
		Branch:  "main",
		Content: "data",
		BlobId:  "4ea5e4dd2666245c95ea7d4cd353182ea19934b3",
		Signature: &pb.Signature{
			Name:  "tp honey",
			Email: "tp@harness.io",
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
	got, err := UpdateFile(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.Equal(t, got.Status, int32(409), "status matches")
	assert.Equal(t, got.Error, "newfile does not match ff9b1a04-7828-4288-8135-b331a38e9fac", "error matches")
}

// func TestUpdateFilCommtConflictBitbucket(t *testing.T) {
// 	in := &pb.FileModifyRequest{
// 		Slug:     "mohitgargharness/test-repository",
// 		Path:     "test-file.txt",
// 		Message:  "message",
// 		Branch:   "main",
// 		Content:  "data",
// 		CommitId: "DUMMY",
// 		Signature: &pb.Signature{
// 			Name:  "mohitgargharness",
// 			Email: "mohit.garg@harness.io",
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
// 	got, err := UpdateFile(context.Background(), in, log.Sugar())

// 	assert.Nil(t, err, "no errors")
// 	assert.Equal(t, got.Status, int32(400), "status matches")
// }

func TestDeleteFile(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(200)
		content, _ := os.ReadFile("testdata/FileUpdateSource.json")
		fmt.Fprint(w, string(content))
	}))
	defer ts.Close()

	in := &pb.DeleteFileRequest{
		Slug:    "tphoney/scm-test",
		Path:    "jello",
		Message: "message",
		Branch:  "main",
		BlobId:  "4ea5e4dd2666245c95ea7d4cd353182ea19934b3",
		Signature: &pb.Signature{
			Name:  "tp honey",
			Email: "tp@harness.io",
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
	got, err := DeleteFile(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.Equal(t, got.Status, int32(200), "status matches")
}

func TestPushNewFile(t *testing.T) {
	serveActualFile := false
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(200)
		if r.Method == http.MethodGet {
			if serveActualFile {
				// 3. file find
				content, _ := os.ReadFile("testdata/FileFindSource.json")
				fmt.Fprint(w, string(content))
			} else {
				// 1. file does not exist yet
				content, _ := os.ReadFile("testdata/FileError.json")
				fmt.Fprint(w, string(content))
			}
		} else {
			// 2. file is created
			content, _ := os.ReadFile("testdata/FileCreateSource.json")
			serveActualFile = true
			fmt.Fprint(w, string(content))
		}
	}))
	defer ts.Close()
	in := &pb.FileModifyRequest{
		Slug:    "tphoney/scm-test",
		Path:    "jello",
		Message: "message",
		Branch:  "main",
		Content: "data",
		Signature: &pb.Signature{
			Name:  "tp honey",
			Email: "tp@harness.io",
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
	got, err := PushFile(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.Equal(t, got.Status, int32(200), "status matches")
}

func TestFindFilesInBranch(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(200)
		content, _ := os.ReadFile("testdata/FileList.json")
		fmt.Fprint(w, string(content))
	}))
	defer ts.Close()
	in := &pb.FindFilesInBranchRequest{
		Slug: "tphoney/scm-test",
		Type: &pb.FindFilesInBranchRequest_Branch{
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
	got, err := FindFilesInBranch(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.Equal(t, 26, len(got.File), "26 files")
	assert.Equal(t, int32(0), got.Pagination.Next, "No next page")
}

// func TestFindFilesInBranchBitbucket(t *testing.T) {
// 	in := &pb.FindFilesInBranchRequest{
// 		Slug: "mohitgargharness/test-repository",
// 		Type: &pb.FindFilesInBranchRequest_Branch{
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
// 	got, err := FindFilesInBranch(context.Background(), in, log.Sugar())

// 	assert.Nil(t, err, "no errors")
// 	assert.NotNil(t, len(got.File), "Non-Null file count")
// 	assert.NotNil(t, got.Pagination.NextUrl, "Next page found")

// 	in = &pb.FindFilesInBranchRequest{
// 		Slug: "mohitgargharness/test-repository",
// 		Type: &pb.FindFilesInBranchRequest_Branch{
// 			Branch: "master",
// 		},
// 		Pagination: &pb.PageRequest{Url: got.GetPagination().GetNextUrl()},
// 		Provider: &pb.Provider{
// 			Hook: &pb.Provider_BitbucketCloud{
// 				BitbucketCloud: &pb.BitbucketCloudProvider{
// 					Username:    "mohitgargharness",
// 					AppPassword: "d58ztzmwJksybeatmP4e",
// 				},
// 			},
// 		},
// 	}

// 	assert.Nil(t, err, "no errors")
// 	assert.NotNil(t, len(got.File), "Non-Null file count")
// }

func TestFindFilesInCommit(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(200)
		content, _ := os.ReadFile("testdata/FileList.json")
		fmt.Fprint(w, string(content))
	}))
	defer ts.Close()
	in := &pb.FindFilesInCommitRequest{
		Slug: "tphoney/scm-test",
		Ref:  "9a9b31a127e7ed3ee781b6268ae3f9fb7e4525bb",
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
	got, err := FindFilesInCommit(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.Equal(t, 26, len(got.File), "26 files")
	assert.Equal(t, int32(0), got.Pagination.Next, "No next page")
}

func TestBatchFindFile(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/repos/tphoney/scm-test/contents/README.md" {
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(200)
			content, _ := os.ReadFile("testdata/FileFindSource.json")
			fmt.Fprint(w, string(content))
		} else if strings.Contains(r.URL.Path, "") {
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(200)
			content, _ := os.ReadFile("testdata/CommitsOfFile.json")
			fmt.Fprint(w, string(content))
		} else {
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(404)
		}
	}))
	defer ts.Close()

	in1 := &pb.GetFileRequest{
		Slug: "tphoney/scm-test",
		Path: "README.md",
		Type: &pb.GetFileRequest_Branch{
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

	in2 := &pb.GetFileRequest{
		Slug: "tphoney/scm-test",
		Path: "NOTHING",
		Type: &pb.GetFileRequest_Branch{
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

	in := &pb.GetBatchFileRequest{
		FindRequest: []*pb.GetFileRequest{in1, in2},
	}
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := BatchFindFile(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.Contains(t, got.FileContents[0].Content, "test repo for source control operations")
	assert.Equal(t, "", got.FileContents[1].Content, "missing file has no content")
}

func TestGetCommitIdByProvider(t *testing.T) {
	if azureToken == "" {
		t.Skip("Skipping, Acceptance test")
	}
	provider := &pb.Provider{
		Hook: &pb.Provider_Azure{
			Azure: &pb.AzureProvider{
				PersonalAccessToken: azureToken,
				Organization:        organization,
				Project:             project,
			},
		},
		Debug: true,
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := getCommitIdIfEmptyInRequest(context.Background(), "", repoID, "main", provider, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.NotNil(t, got, "There is a commit id")
}
