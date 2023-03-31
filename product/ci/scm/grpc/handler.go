// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package grpc

import (
	"github.com/harness/harness-core/product/ci/scm/file"
	"github.com/harness/harness-core/product/ci/scm/git"
	"github.com/harness/harness-core/product/ci/scm/parser"
	pb "github.com/harness/harness-core/product/ci/scm/proto"
	"github.com/harness/harness-core/product/ci/scm/repo"
	"go.uber.org/zap"
	"golang.org/x/net/context"
)

// handler is used to implement SCMServer
type handler struct {
	stopCh chan bool
	log    *zap.SugaredLogger
}

// NewSCMHandler returns a GRPC handler that implements pb.SCMServer
func NewSCMHandler(stopCh chan bool, log *zap.SugaredLogger) pb.SCMServer {
	return &handler{stopCh, log}
}

// ParseWebhook is used to parse the webhook
func (h *handler) ParseWebhook(ctx context.Context, in *pb.ParseWebhookRequest) (*pb.ParseWebhookResponse, error) {
	return parser.ParseWebhook(ctx, in, h.log)
}

// Createfile is used to create a file
func (h *handler) CreateFile(ctx context.Context, in *pb.FileModifyRequest) (*pb.CreateFileResponse, error) {
	return file.CreateFile(ctx, in, h.log)
}

// DeleteFile is used to delete a file
func (h *handler) DeleteFile(ctx context.Context, in *pb.DeleteFileRequest) (*pb.DeleteFileResponse, error) {
	return file.DeleteFile(ctx, in, h.log)
}

// GetFile is used to return a file
func (h *handler) GetFile(ctx context.Context, in *pb.GetFileRequest) (*pb.FileContent, error) {
	return file.FindFile(ctx, in, h.log)
}

// GetLatestFile is used to return the latest version of a file
func (h *handler) GetLatestFile(ctx context.Context, in *pb.GetLatestFileRequest) (*pb.FileContent, error) {
	log := h.log
	findFileIn := &pb.GetFileRequest{
		Slug: in.Slug,
		Path: in.Path,
		Type: &pb.GetFileRequest_Branch{
			Branch: in.Branch,
		},
		Provider: in.Provider,
	}
	log.Infow("GetLatestFile using FindFile", "slug", in.GetSlug(), "path", in.GetPath(), "branch", in.GetBranch())
	return file.FindFile(ctx, findFileIn, log)
}

// IsLatestFile lets you know if the object_id is from the latest branch/ref.
func (h *handler) IsLatestFile(ctx context.Context, in *pb.IsLatestFileRequest) (*pb.IsLatestFileResponse, error) {
	log := h.log
	findFileIn := &pb.GetFileRequest{
		Slug:     in.GetSlug(),
		Path:     in.GetPath(),
		Provider: in.GetProvider(),
	}
	if in.GetBranch() != "" {
		findFileIn.Type = &pb.GetFileRequest_Branch{
			Branch: in.GetBranch(),
		}
	} else {
		findFileIn.Type = &pb.GetFileRequest_Ref{
			Ref: in.GetRef(),
		}
	}

	log.Infow("IsLatestFile using FindFile", "slug", in.Slug, "path", in.Path)
	response, err := file.FindFile(ctx, findFileIn, log)
	if err != nil {
		log.Errorw("IsLatestFile failure", "slug ", in.GetSlug(), "path", in.GetPath(), "ref", in.GetRef(), "branch", in.GetBranch(), zap.Error(err))
		return nil, err
	}
	var match bool
	// github uses blob id for update check, others use commit id
	switch findFileIn.GetProvider().Hook.(type) {
	case *pb.Provider_Github:
		match = in.GetBlobId() == response.GetBlobId()
	default:
		match = in.GetBlobId() == response.GetCommitId()
	}
	out := &pb.IsLatestFileResponse{
		Latest: match,
	}
	return out, nil
}

// GetBatchFile is used to return multiple files
func (h *handler) GetBatchFile(ctx context.Context, in *pb.GetBatchFileRequest) (*pb.FileBatchContentResponse, error) {
	return file.BatchFindFile(ctx, in, h.log)
}

// UpdateFile is used to update a file
func (h *handler) UpdateFile(ctx context.Context, in *pb.FileModifyRequest) (*pb.UpdateFileResponse, error) {
	return file.UpdateFile(ctx, in, h.log)
}

// PushFile is used to create a file if it doesnt exist, or update the file if it does.
func (h *handler) PushFile(ctx context.Context, in *pb.FileModifyRequest) (*pb.FileContent, error) {
	return file.PushFile(ctx, in, h.log)
}

// FindFilesInBranch is used to return a list of files in a given branch.
func (h *handler) FindFilesInBranch(ctx context.Context, in *pb.FindFilesInBranchRequest) (*pb.FindFilesInBranchResponse, error) {
	return file.FindFilesInBranch(ctx, in, h.log)
}

// FindFilesInCommit is used to return a list of files in a given commit.
func (h *handler) FindFilesInCommit(ctx context.Context, in *pb.FindFilesInCommitRequest) (*pb.FindFilesInCommitResponse, error) {
	return file.FindFilesInCommit(ctx, in, h.log)
}

// CreatePR creates a PR given a source branch and target branch.
func (h *handler) CreatePR(ctx context.Context, in *pb.CreatePRRequest) (*pb.CreatePRResponse, error) {
	return git.CreatePR(ctx, in, h.log)
}

func (h *handler) RefreshToken(ctx context.Context, in *pb.RefreshTokenRequest) (*pb.RefreshTokenResponse, error) {
	return git.RefreshToken(ctx, in, h.log)
}

func (h *handler) GenerateStageYamlForCI(ctx context.Context, in *pb.GenerateYamlRequest) (*pb.GenerateYamlResponse, error) {
	return git.GenerateStageYamlForCI(ctx, in, h.log)
}

// FindFilesInPR lists the files in a PR.
func (h *handler) FindFilesInPR(ctx context.Context, in *pb.FindFilesInPRRequest) (*pb.FindFilesInPRResponse, error) {
	return git.FindFilesInPR(ctx, in, h.log)
}

// CreateBranch creates a Branch given a branch name and commit_id.
func (h *handler) CreateBranch(ctx context.Context, in *pb.CreateBranchRequest) (*pb.CreateBranchResponse, error) {
	return git.CreateBranch(ctx, in, h.log)
}

// GetLatestCommit returns the latest commit_id for a branch.
func (h *handler) GetLatestCommit(ctx context.Context, in *pb.GetLatestCommitRequest) (*pb.GetLatestCommitResponse, error) {
	return git.GetLatestCommit(ctx, in, h.log)
}

// ListBranches is used to return a list of commit ids given a ref or branch.
func (h *handler) ListBranches(ctx context.Context, in *pb.ListBranchesRequest) (*pb.ListBranchesResponse, error) {
	return git.ListBranches(ctx, in, h.log)
}

// ListBranches is used to return a list of branches, along with the default branch
func (h *handler) ListBranchesWithDefault(ctx context.Context, in *pb.ListBranchesWithDefaultRequest) (*pb.ListBranchesWithDefaultResponse, error) {
	return git.ListBranchesWithDefault(ctx, in, h.log)
}

// ListCommits is used to return a list of commit ids given a ref or branch.
func (h *handler) ListCommits(ctx context.Context, in *pb.ListCommitsRequest) (*pb.ListCommitsResponse, error) {
	return git.ListCommits(ctx, in, h.log)
}

// GetLatestCommitOnFile is used to return latest commit id of a file given a branch.
func (h *handler) GetLatestCommitOnFile(ctx context.Context, in *pb.GetLatestCommitOnFileRequest) (*pb.GetLatestCommitOnFileResponse, error) {
	return git.GetLatestCommitOnFile(ctx, in, h.log)
}

// ListCommitsInPR is used to return a list of commit details given pr number.
func (h *handler) ListCommitsInPR(ctx context.Context, in *pb.ListCommitsInPRRequest) (*pb.ListCommitsInPRResponse, error) {
	return git.ListCommitsInPR(ctx, in, h.log)
}

// ListCommits is used to return a list of commit ids given a ref or branch.
func (h *handler) CompareCommits(ctx context.Context, in *pb.CompareCommitsRequest) (*pb.CompareCommitsResponse, error) {
	return git.CompareCommits(ctx, in, h.log)
}

// CreateWebhook is used to add a webhook to a repo.
func (h *handler) CreateWebhook(ctx context.Context, in *pb.CreateWebhookRequest) (*pb.CreateWebhookResponse, error) {
	return repo.CreateWebhook(ctx, in, h.log)
}

// DeleteWebhook is used to add a webhook to a repo.
func (h *handler) DeleteWebhook(ctx context.Context, in *pb.DeleteWebhookRequest) (*pb.DeleteWebhookResponse, error) {
	return repo.DeleteWebhook(ctx, in, h.log)
}

// ListWebhooks is used to list all webhooks associated with a repo.
func (h *handler) ListWebhooks(ctx context.Context, in *pb.ListWebhooksRequest) (*pb.ListWebhooksResponse, error) {
	return repo.ListWebhooks(ctx, in, h.log)
}

// GetAuthenticatedUser is used to get authenticated user.
func (h *handler) GetAuthenticatedUser(ctx context.Context, in *pb.GetAuthenticatedUserRequest) (*pb.GetAuthenticatedUserResponse, error) {
	return git.GetAuthenticatedUser(ctx, in, h.log)
}

func (h *handler) GetUserRepos(ctx context.Context, in *pb.GetUserReposRequest) (*pb.GetUserReposResponse, error) {
	return git.GetUserRepos(ctx, in, h.log)
}

func (h *handler) GetUserRepo(ctx context.Context, in *pb.GetUserRepoRequest) (*pb.GetUserRepoResponse, error) {
	return git.GetUserRepo(ctx, in, h.log)
}

func (h *handler) FindPR(ctx context.Context, in *pb.FindPRRequest) (*pb.FindPRResponse, error) {
	return git.FindPR(ctx, in, h.log)
}

func (h *handler) FindCommit(ctx context.Context, in *pb.FindCommitRequest) (*pb.FindCommitResponse, error) {
	return git.FindCommit(ctx, in, h.log)
}
