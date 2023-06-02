// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package git

import (
	"context"
	"os"
	"testing"

	"github.com/harness/harness-core/commons/go/lib/logs"
	pb "github.com/harness/harness-core/product/ci/scm/proto"
	"github.com/stretchr/testify/assert"
	"go.uber.org/zap"
)

var (
	harnessToken = os.Getenv("HARNESS_TOKEN")
	// harnessEndpoint = "http://localhost:3000" // local
	// harnessSlug     = "1"                     // local
	harnessEndpoint     = "https://qa.harness.io/gateway/code"
	harnessAccount      = "px7xd_BFRCi-pfWPYXVjvw"
	harnessOrganization = "default"
	harnessProject      = "codeciintegration"
	harnessSlug         = "thomas"
)

func TestRepoListHarness(t *testing.T) {
	if harnessToken == "" {
		t.Skip("Skipping, Acceptance test")
	}
	in := &pb.GetUserReposRequest{
		Provider: &pb.Provider{
			Hook: &pb.Provider_Harness{
				Harness: &pb.HarnessProvider{
					Provider: &pb.HarnessProvider_HarnessAccessToken{
						HarnessAccessToken: &pb.HarnessAccessToken{
							AccessToken:  harnessToken,
							Account:      harnessAccount,
							Organization: harnessOrganization,
							Project:      harnessProject,
						},
					},
				},
			},
			Debug:    true,
			Endpoint: harnessEndpoint,
		},
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := GetUserRepos(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.GreaterOrEqual(t, len(got.Repos), 1, "There is at least one repo")
}

func TestGetUserRepoHarness(t *testing.T) {
	if harnessToken == "" {
		t.Skip("Skipping, Acceptance test")
	}
	in := &pb.GetUserRepoRequest{
		Slug: harnessSlug,
		Provider: &pb.Provider{
			Hook: &pb.Provider_Harness{
				Harness: &pb.HarnessProvider{
					Provider: &pb.HarnessProvider_HarnessAccessToken{
						HarnessAccessToken: &pb.HarnessAccessToken{
							AccessToken:  harnessToken,
							Account:      harnessAccount,
							Organization: harnessOrganization,
							Project:      harnessProject,
						},
					},
				},
			},
			Debug:    true,
			Endpoint: harnessEndpoint,
		},
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := GetUserRepo(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.Equal(t, got.Repo.Name, harnessSlug, "The repo slug is correct")
}

func TestGetLatestCommitHarness(t *testing.T) {
	if harnessToken == "" {
		t.Skip("Skipping, Acceptance test")
	}
	in := &pb.GetLatestCommitRequest{
		Slug: harnessSlug,
		Type: &pb.GetLatestCommitRequest_Ref{
			Ref: "a2cc94eff4add6551ce3bb23eb8db83ba3ad79cb",
		},
		Provider: &pb.Provider{
			Hook: &pb.Provider_Harness{
				Harness: &pb.HarnessProvider{
					Provider: &pb.HarnessProvider_HarnessAccessToken{
						HarnessAccessToken: &pb.HarnessAccessToken{
							AccessToken:  harnessToken,
							Account:      harnessAccount,
							Organization: harnessOrganization,
							Project:      harnessProject,
						},
					},
				},
			},
			Debug:    true,
			Endpoint: harnessEndpoint,
		},
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := GetLatestCommit(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.NotNil(t, got.Commit.Sha, "There is a commit id")
}

func TestListCommitsHarness(t *testing.T) {
	if harnessToken == "" {
		t.Skip("Skipping, Acceptance test")
	}
	in := &pb.ListCommitsRequest{
		Slug: harnessSlug,
		Type: &pb.ListCommitsRequest_Branch{
			Branch: "main",
		},
		Provider: &pb.Provider{
			Hook: &pb.Provider_Harness{
				Harness: &pb.HarnessProvider{
					Provider: &pb.HarnessProvider_HarnessAccessToken{
						HarnessAccessToken: &pb.HarnessAccessToken{
							AccessToken:  harnessToken,
							Account:      harnessAccount,
							Organization: harnessOrganization,
							Project:      harnessProject,
						},
					},
				},
			},
			Debug:    true,
			Endpoint: harnessEndpoint,
		},
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := ListCommits(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.Greater(t, len(got.CommitIds), 1, "more than 1 commit")
	assert.Equal(t, int32(2), got.Pagination.Next, "there is a next page page")
}

// func TestCreateBranchHarness(t *testing.T) {
// 	if harnessToken == "" {
// 		t.Skip("Skipping, Acceptance test")
// 	}
// 	in := &pb.CreateBranchRequest{
// 		Slug:     harnessSlug,
// 		Name:     "test_branch",
// 		CommitId: "a2cc94eff4add6551ce3bb23eb8db83ba3ad79cb",
// 		Provider: &pb.Provider{
// 			Hook: &pb.Provider_Harness{
// 				Harness: &pb.HarnessProvider{
// 					Provider: &pb.HarnessProvider_HarnessAccessToken{
// 						HarnessAccessToken: &pb.HarnessAccessToken{
// 							AccessToken:  harnessToken,
// 							Account:      harnessAccount,
// 							Organization: harnessOrganization,
// 							Project:      harnessProject,
// 						},
// 					},
// 				},
// 			},
// 			Debug:    true,
// 			Endpoint: harnessEndpoint,
// 		},
// 	}
// 	log, _ := logs.GetObservedLogger(zap.InfoLevel)
// 	got, err := CreateBranch(context.Background(), in, log.Sugar())

// 	assert.Nil(t, err, "no errors")
// 	assert.Equal(t, int32(201), got.Status, "Correct http response")
// }

func TestListBranchesHarness(t *testing.T) {
	if harnessToken == "" {
		t.Skip("Skipping, Acceptance test")
	}
	in := &pb.ListBranchesRequest{
		Slug: harnessSlug,
		Provider: &pb.Provider{
			Hook: &pb.Provider_Harness{
				Harness: &pb.HarnessProvider{
					Provider: &pb.HarnessProvider_HarnessAccessToken{
						HarnessAccessToken: &pb.HarnessAccessToken{
							AccessToken:  harnessToken,
							Account:      harnessAccount,
							Organization: harnessOrganization,
							Project:      harnessProject,
						},
					},
				},
			},
			Debug:    true,
			Endpoint: harnessEndpoint,
		},
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := ListBranches(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.GreaterOrEqual(t, len(got.Branches), 1, "there is more than 1 branch")
	assert.Equal(t, int32(0), got.Pagination.Next, "there is no next page")
}

func TestFindPRHarness(t *testing.T) {
	if harnessToken == "" {
		t.Skip("Skipping, Acceptance test")
	}
	in := &pb.FindPRRequest{
		Number: 1,
		Slug:   harnessSlug,
		Provider: &pb.Provider{
			Hook: &pb.Provider_Harness{
				Harness: &pb.HarnessProvider{
					Provider: &pb.HarnessProvider_HarnessAccessToken{
						HarnessAccessToken: &pb.HarnessAccessToken{
							AccessToken:  harnessToken,
							Account:      harnessAccount,
							Organization: harnessOrganization,
							Project:      harnessProject,
						},
					},
				},
			},
			Debug:    true,
			Endpoint: harnessEndpoint,
		},
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := FindPR(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.NotEqual(t, len(got.Pr.Title), "", "there is a pr title")
}

func TestListCommitsInPRsHarness(t *testing.T) {
	if harnessToken == "" {
		t.Skip("Skipping, Acceptance test")
	}
	in := &pb.ListCommitsInPRRequest{
		Slug:   harnessSlug,
		Number: 1,
		Provider: &pb.Provider{
			Hook: &pb.Provider_Harness{
				Harness: &pb.HarnessProvider{
					Provider: &pb.HarnessProvider_HarnessAccessToken{
						HarnessAccessToken: &pb.HarnessAccessToken{
							AccessToken:  harnessToken,
							Account:      harnessAccount,
							Organization: harnessOrganization,
							Project:      harnessProject,
						},
					},
				},
			},
			Debug:    true,
			Endpoint: harnessEndpoint,
		},
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := ListCommitsInPR(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.GreaterOrEqual(t, len(got.Commits), 1, "more than 1 commit")
}

// this test will fail if there is already a PR on the same branch, delete the PR "scm test pr" and try again
func TestCreatePRHarness(t *testing.T) {
	if harnessToken == "" {
		t.Skip("Skipping, Acceptance test")
	}
	in := &pb.CreatePRRequest{
		Slug:   harnessSlug,
		Title:  "scm test pr",
		Body:   "scm test pr",
		Source: "branch3",
		Target: "main",
		Provider: &pb.Provider{
			Hook: &pb.Provider_Harness{
				Harness: &pb.HarnessProvider{
					Provider: &pb.HarnessProvider_HarnessAccessToken{
						HarnessAccessToken: &pb.HarnessAccessToken{
							AccessToken:  harnessToken,
							Account:      harnessAccount,
							Organization: harnessOrganization,
							Project:      harnessProject,
						},
					},
				},
			},
			Debug:    true,
			Endpoint: harnessEndpoint,
		},
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := CreatePR(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.Equal(t, int32(200), got.Status, "Correct http response")
}
