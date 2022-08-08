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

var azureToken = os.Getenv("AZURE_TOKEN")

const (
	organization = "tphoney"
	project      = "test_project"
	repoID       = "fde2d21f-13b9-4864-a995-83329045289a"
)

func TestListCommitsAzure(t *testing.T) {
	if azureToken == "" {
		t.Skip("Skipping, Acceptance test")
	}
	in := &pb.ListCommitsRequest{
		Slug: repoID,
		Type: &pb.ListCommitsRequest_Branch{
			Branch: "main",
		},
		Provider: &pb.Provider{
			Hook: &pb.Provider_Azure{
				Azure: &pb.AzureProvider{
					PersonalAccessToken: azureToken,
					Organization:        organization,
					Project:             project,
				},
			},
			Debug: true,
		},
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := ListCommits(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.Greater(t, len(got.CommitIds), 1, "more than 1 commit")
}

func TestFindCommitAzure(t *testing.T) {
	if azureToken == "" {
		t.Skip("Skipping, Acceptance test")
	}
	in := &pb.ListCommitsRequest{
		Slug: repoID,
		Type: &pb.ListCommitsRequest_Branch{
			Branch: "main",
		},
		Provider: &pb.Provider{
			Hook: &pb.Provider_Azure{
				Azure: &pb.AzureProvider{
					PersonalAccessToken: azureToken,
					Organization:        organization,
					Project:             project,
				},
			},
			Debug: true,
		},
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	commitsList, err := ListCommits(context.Background(), in, log.Sugar())
	assert.Nil(t, err, "no errors")
	// lets grab the second commit from the list
	in2 := &pb.FindCommitRequest{
		Slug: repoID,
		Ref:  commitsList.CommitIds[1],
		Provider: &pb.Provider{
			Hook: &pb.Provider_Azure{
				Azure: &pb.AzureProvider{
					PersonalAccessToken: azureToken,
					Organization:        organization,
					Project:             project,
				},
			},
			Debug: true,
		},
	}
	got, err2 := FindCommit(context.Background(), in2, log.Sugar())
	assert.Nil(t, err2, "no errors")
	assert.NotEqual(t, "", got.Commit.Sha, "there is a commit sha")
}

func TestGetLatestCommitOnFileAzure(t *testing.T) {
	if azureToken == "" {
		t.Skip("Skipping, Acceptance test")
	}
	in := &pb.GetLatestCommitOnFileRequest{
		Slug:     repoID,
		FilePath: "README.md",
		Branch:   "main",
		Provider: &pb.Provider{
			Hook: &pb.Provider_Azure{
				Azure: &pb.AzureProvider{
					PersonalAccessToken: azureToken,
					Organization:        organization,
					Project:             project,
				},
			},
			Debug: true,
		},
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := GetLatestCommitOnFile(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.NotEqual(t, "", got.CommitId, "there is a commit id")
}
func TestListBranchesAzure(t *testing.T) {
	if azureToken == "" {
		t.Skip("Skipping, Acceptance test")
	}
	in := &pb.ListBranchesRequest{
		Slug: repoID,
		Provider: &pb.Provider{
			Hook: &pb.Provider_Azure{
				Azure: &pb.AzureProvider{
					PersonalAccessToken: azureToken,
					Organization:        organization,
					Project:             project,
				},
			},
			Debug: true,
		},
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := ListBranches(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.GreaterOrEqual(t, len(got.Branches), 1, "status matches")
}

// NB make sure there is no existing PR for this branch, or the test will fail.
func TestCreatePRAzure(t *testing.T) {
	if azureToken == "" {
		t.Skip("Skipping, Acceptance test")
	}
	in := &pb.CreatePRRequest{
		Slug:   repoID,
		Body:   "body text",
		Title:  "pr from scm service",
		Source: "pr_branch",
		Target: "main",
		Provider: &pb.Provider{
			Hook: &pb.Provider_Azure{
				Azure: &pb.AzureProvider{
					PersonalAccessToken: azureToken,
					Organization:        organization,
					Project:             project,
				},
			},
			Debug: true,
		},
	}
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := CreatePR(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.Equal(t, int32(201), got.Status, "Correct http response")
}

// NB make sure there is no existing Branch by this name
func TestCreateBranchAzure(t *testing.T) {
	if azureToken == "" {
		t.Skip("Skipping, Acceptance test")
	}
	in := &pb.CreateBranchRequest{
		Slug:     repoID,
		Name:     "test_branch",
		CommitId: "e0aee6aa543294d62520fb906689da6710af149c",
		Provider: &pb.Provider{
			Hook: &pb.Provider_Azure{
				Azure: &pb.AzureProvider{
					PersonalAccessToken: azureToken,
					Organization:        organization,
					Project:             project,
				},
			},
			Debug: true,
		},
	}
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := CreateBranch(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.Equal(t, int32(200), got.Status, "Correct http response")
}

func TestGetLatestCommitAzure(t *testing.T) {
	if azureToken == "" {
		t.Skip("Skipping, Acceptance test")
	}
	in := &pb.GetLatestCommitRequest{
		Slug: repoID,
		Type: &pb.GetLatestCommitRequest_Branch{
			Branch: "main",
		},
		Provider: &pb.Provider{
			Hook: &pb.Provider_Azure{
				Azure: &pb.AzureProvider{
					PersonalAccessToken: azureToken,
					Organization:        organization,
					Project:             project,
				},
			},
			Debug: true,
		},
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := GetLatestCommit(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.NotNil(t, got.Commit.Sha, "There is a commit id")
}

func TestCompareCommitsAzure(t *testing.T) {
	if azureToken == "" {
		t.Skip("Skipping, Acceptance test")
	}

	in := &pb.ListCommitsRequest{
		Slug: repoID,
		Type: &pb.ListCommitsRequest_Branch{
			Branch: "main",
		},
		Provider: &pb.Provider{
			Hook: &pb.Provider_Azure{
				Azure: &pb.AzureProvider{
					PersonalAccessToken: azureToken,
					Organization:        organization,
					Project:             project,
				},
			},
			Debug: true,
		},
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	returnedCommits, err := ListCommits(context.Background(), in, log.Sugar())
	assert.Nil(t, err, "no errors")

	in2 := &pb.CompareCommitsRequest{
		Slug:   repoID,
		Target: returnedCommits.CommitIds[0],
		Source: returnedCommits.CommitIds[10],
		Provider: &pb.Provider{
			Hook: &pb.Provider_Azure{
				Azure: &pb.AzureProvider{
					PersonalAccessToken: azureToken,
					Organization:        organization,
					Project:             project,
				},
			},
			Debug: true,
		},
	}

	log, _ = logs.GetObservedLogger(zap.InfoLevel)
	got2, err2 := CompareCommits(context.Background(), in2, log.Sugar())

	assert.Nil(t, err2, "no errors")
	assert.GreaterOrEqual(t, len(got2.Files), 1, "At least 1 file is different")
}

func TestRepoListAzure(t *testing.T) {
	if azureToken == "" {
		t.Skip("Skipping, Acceptance test")
	}
	in := &pb.GetUserReposRequest{
		Provider: &pb.Provider{
			Hook: &pb.Provider_Azure{
				Azure: &pb.AzureProvider{
					PersonalAccessToken: azureToken,
					Organization:        organization,
					Project:             project,
				},
			},
			Debug: true,
		},
	}

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	got, err := GetUserRepos(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.GreaterOrEqual(t, len(got.Repos), 1, "There is at least one repo")
}
