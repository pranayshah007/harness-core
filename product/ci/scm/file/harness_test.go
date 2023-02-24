// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package file

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

func TestFindFileHarness(t *testing.T) {
	if harnessToken == "" {
		t.Skip("Skipping, Acceptance test")
	}
	in := &pb.GetFileRequest{
		Slug: harnessSlug,
		Path: "README.md",
		Type: &pb.GetFileRequest_Branch{
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
	got, err := FindFile(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.Equal(t, "0e035642f7733eabd18e6734ec2638469927c981", got.CommitId, "there is a commit_id")
	assert.NotNil(t, got.BlobId, "there is a blob_id")
}

func TestFindFilesInBranchHarness(t *testing.T) {
	if harnessToken == "" {
		t.Skip("Skipping, Acceptance test")
	}
	in := &pb.FindFilesInBranchRequest{
		Slug: harnessSlug,
		Type: &pb.FindFilesInBranchRequest_Branch{
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
	got, err := FindFilesInBranch(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.LessOrEqual(t, 1, len(got.File), "More than 1 files in branch")
}

func TestCreateUpdateDeleteFileHarness(t *testing.T) {
	if harnessToken == "" {
		t.Skip("Skipping, Acceptance test")
	}
	in := &pb.FileModifyRequest{
		Slug:    harnessSlug,
		Path:    "readme.2",
		Content: "hello",
		Branch:  "main",
		Message: "create CRUD",
		Signature: &pb.Signature{
			Name:  "tp honey",
			Email: "tp@harness.io",
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
	create, err := CreateFile(context.Background(), in, log.Sugar())

	assert.Nil(t, err, "no errors")
	assert.NotEqual(t, "", create.CommitId, "there is a commit_id")

	// update
	in2 := &pb.FileModifyRequest{
		Slug:    harnessSlug,
		Path:    "readme.2",
		Content: "bye",
		Branch:  "main",
		Message: "update CRUD",
		BlobId:  "b6fc4c620b67d95f953a5c1c1230aaab5db5a1b0",
		Signature: &pb.Signature{
			Name:  "tp honey",
			Email: "tp@harness.io",
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

	log, _ = logs.GetObservedLogger(zap.InfoLevel)
	update, err := UpdateFile(context.Background(), in2, log.Sugar())
	assert.Nil(t, err, "no errors")
	assert.NotEqual(t, "", update.CommitId, "there is a commit_id")
	assert.NotEqual(t, create.CommitId, update.CommitId, "commitid is different from create")

	// delete
	in3 := &pb.DeleteFileRequest{
		Slug:    harnessSlug,
		Path:    "readme.2",
		Branch:  "main",
		Message: "delete CRUD",
		Signature: &pb.Signature{
			Name:  "tp honey",
			Email: "tp@harness.io",
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
	log, _ = logs.GetObservedLogger(zap.InfoLevel)
	delete, err := DeleteFile(context.Background(), in3, log.Sugar())
	assert.Nil(t, err, "no errors")
	assert.NotNil(t, delete.CommitId, "there is a commit_id")
}
