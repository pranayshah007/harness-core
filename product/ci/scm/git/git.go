// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package git

import (
	"context"
	"fmt"
	"net/url"
	"strings"
	"time"
	"io/ioutil"
	"os"

	"github.com/drone/go-scm/scm"
	"github.com/drone/go-scm/scm/driver/github"
	"github.com/drone/go-scm/scm/transport/oauth2"
	"github.com/harness/harness-core/commons/go/lib/utils"
	"github.com/harness/harness-core/product/ci/scm/converter"
	"github.com/harness/harness-core/product/ci/scm/gitclient"
	pb "github.com/harness/harness-core/product/ci/scm/proto"
	"github.com/wings-software/autogen-go/cloner"
    "github.com/wings-software/autogen-go/builder"
    "github.com/wings-software/autogen-go/chroot"
	"go.uber.org/zap"
)

func RefreshToken(ctx context.Context, request *pb.RefreshTokenRequest, log *zap.SugaredLogger) (out *pb.RefreshTokenResponse, err error) {
	log.Infow("RefreshToken starting:", request.Endpoint, "ClientID:", request.ClientID)

	before := &scm.Token{
		Refresh: request.RefreshToken,
	}

	r := oauth2.Refresher{
		ClientID:     request.ClientID,
		ClientSecret: request.ClientSecret,
		Endpoint:     request.Endpoint,
		Source:       oauth2.StaticTokenSource(before),
	}

	after, err := r.Token(ctx)

	if err != nil {
		log.Errorw("RefreshToken failure:", request.Endpoint, "Error:", err)
		return nil, err
	}

	if after == nil {
		log.Errorw("RefreshToken failure result:", request.Endpoint)
		return nil, err
	}

	out = &pb.RefreshTokenResponse{
		AccessToken:  after.Token,
		RefreshToken: after.Refresh,
	}

	return out, err
}

func GenerateStageYamlForCI(ctx context.Context, request *pb.GenerateYamlRequest, log *zap.SugaredLogger) (out *pb.GenerateYamlResponse, err error) {
    log.Infow("GenerateYaml starting")
    path := request.Url
    temp, err := ioutil.TempDir("", "")
    log.Infow("temp dir" , "dir", temp)
    if err != nil {
        return nil, err
    }

    params := cloner.Params{
        Dir:        temp,
        Repo:       path,
    }

    cloner := cloner.New(1, os.Stdout) // 1 depth, discard git clone logs
    cloner.Clone(context.Background(), params)

    // change the path to the temp directory
    path = temp

    defer os.RemoveAll(temp)

    // create a chroot virtual filesystem that we
    // pass to the builder for isolation purposes.
    chroot, err := chroot.New(path)
    if err != nil {
        return nil, err
    }

    // builds the pipeline configuration based on
    // the contents of the virtual filesystem.
    builder := builder.New("harness", request.GetYamlVersion())
    yml, err := builder.Build(chroot)
    if err != nil {
        return nil, err
    }

	out = &pb.GenerateYamlResponse{
		Yaml: string(yml[:]),
	}
    return out, nil
}

func CreatePR(ctx context.Context, request *pb.CreatePRRequest, log *zap.SugaredLogger) (out *pb.CreatePRResponse, err error) {
	start := time.Now()
	log.Infow("CreatePR starting", "slug", request.GetSlug())

	client, err := gitclient.GetGitClient(*request.GetProvider(), log)
	if err != nil {
		log.Errorw("CreatePR failure", "bad provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}

	inputParams := scm.PullRequestInput{
		Title:  request.Title,
		Body:   request.Body,
		Target: request.Target,
		Source: request.Source,
	}

	pr, response, err := client.PullRequests.Create(ctx, request.GetSlug(), &inputParams)
	if err != nil {
		log.Errorw("CreatePR failure", "provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "source", request.GetSource(), "target", request.GetTarget(),
			"elapsed_time_ms", utils.TimeSince(start), zap.Error(err))

		// hard error from git
		if response == nil {
			return nil, err
		}
		// this is an error from git provider
		out = &pb.CreatePRResponse{
			Status: int32(response.Status),
			Error:  err.Error(),
		}
		return out, nil
	}

	log.Infow("CreatePR success", "slug", request.GetSlug(), "source", request.GetSource(), "target", request.GetTarget(), "elapsed_time_ms", utils.TimeSince(start))

	out = &pb.CreatePRResponse{
		Number: int32(pr.Number),
		Status: int32(response.Status),
	}
	return out, nil
}

func FindPR(ctx context.Context, request *pb.FindPRRequest, log *zap.SugaredLogger) (out *pb.FindPRResponse, err error) {
	start := time.Now()
	log.Infow("FindPR starting", "slug", request.GetSlug())

	client, err := gitclient.GetGitClient(*request.GetProvider(), log)
	if err != nil {
		log.Errorw("FindPR failure", "bad provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}

	pr, response, err := client.PullRequests.Find(ctx, request.GetSlug(), int(request.GetNumber()))
	if err != nil {
		log.Errorw("FindPR failure", "provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "number", request.GetNumber(),
			"elapsed_time_ms", utils.TimeSince(start), zap.Error(err))

		// hard error from git
		if response == nil {
			return nil, err
		}
		// this is an error from git provider
		out = &pb.FindPRResponse{
			Status: int32(response.Status),
			Error:  err.Error(),
		}
	}
	log.Infow("FindPR success", "slug", request.GetSlug(), "number", request.GetNumber(), "elapsed_time_ms", utils.TimeSince(start))

	protoPR, err := converter.ConvertPR(pr)
	if err != nil {
		return nil, err
	}
	out = &pb.FindPRResponse{
		Pr:     protoPR,
		Status: int32(response.Status),
	}
	return out, nil
}

func FindFilesInPR(ctx context.Context, request *pb.FindFilesInPRRequest, log *zap.SugaredLogger) (out *pb.FindFilesInPRResponse, err error) {
	start := time.Now()
	log.Infow("FindFilesInPR starting", "slug", request.GetSlug())

	client, err := gitclient.GetGitClient(*request.GetProvider(), log)
	if err != nil {
		log.Errorw("FindFilesInPR failure", "bad provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}

	changes, response, err := client.PullRequests.ListChanges(ctx, request.GetSlug(), int(request.GetNumber()), scm.ListOptions{Page: int(request.GetPagination().GetPage())})
	if err != nil {
		log.Errorw("FindFilesInPR failure", "provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "number", request.GetNumber(), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}
	log.Infow("FindFilesInPR success", "slug", request.GetSlug(), "number", request.GetNumber(), "elapsed_time_ms", utils.TimeSince(start))

	out = &pb.FindFilesInPRResponse{
		Files: convertChangesList(changes),
		Pagination: &pb.PageResponse{
			Next: int32(response.Page.Next),
		},
	}
	return out, nil
}

func ListCommitsInPR(ctx context.Context, request *pb.ListCommitsInPRRequest, log *zap.SugaredLogger) (out *pb.ListCommitsInPRResponse, err error) {
	start := time.Now()
	log.Infow("ListCommitsInPR starting", "slug", request.GetSlug())

	client, err := gitclient.GetGitClient(*request.GetProvider(), log)
	if err != nil {
		log.Errorw("ListCommitsInPR failure", "bad provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}

	commits, response, err := client.PullRequests.ListCommits(ctx, request.GetSlug(), int(request.GetNumber()), scm.ListOptions{Page: int(request.GetPagination().GetPage())})
	if err != nil {
		log.Errorw("ListCommitsInPR failure", "provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "number", request.GetNumber(), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}
	log.Infow("ListCommitsInPR success", "slug", request.GetSlug(), "number", request.GetNumber(), "elapsed_time_ms", utils.TimeSince(start))

	out = &pb.ListCommitsInPRResponse{
		Commits: convertCommitsList(commits),
		Pagination: &pb.PageResponse{
			Next: int32(response.Page.Next),
		},
	}
	return out, nil
}

func CreateBranch(ctx context.Context, request *pb.CreateBranchRequest, log *zap.SugaredLogger) (out *pb.CreateBranchResponse, err error) {
	start := time.Now()
	log.Infow("CreateBranch starting", "slug", request.GetSlug())

	client, err := gitclient.GetGitClient(*request.GetProvider(), log)
	if err != nil {
		log.Errorw("CreateBranch failure", "bad provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}

	inputParams := scm.CreateBranch{
		Name: request.GetName(),
		Sha:  request.GetCommitId(),
	}

	response, err := client.Git.CreateBranch(ctx, request.GetSlug(), &inputParams)
	if err != nil {
		log.Errorw("CreateBranch failure", "provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "Name", request.GetName(), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))

		// hard error from git
		if response == nil {
			return nil, err
		}
		// this is an error from git provider
		out = &pb.CreateBranchResponse{
			Status: int32(response.Status),
			Error:  err.Error(),
		}
		return out, nil
	}

	log.Infow("CreateBranch success", "slug", request.GetSlug(), "Name", request.GetName(), "elapsed_time_ms", utils.TimeSince(start))

	out = &pb.CreateBranchResponse{
		Status: int32(response.Status),
	}
	return out, nil
}

func GetLatestCommit(ctx context.Context, request *pb.GetLatestCommitRequest, log *zap.SugaredLogger) (out *pb.GetLatestCommitResponse, err error) {
	start := time.Now()
	log.Infow("GetLatestCommit starting", "slug", request.GetSlug())

	client, err := gitclient.GetGitClient(*request.GetProvider(), log)
	if err != nil {
		log.Errorw("GetLatestCommit failure", "bad provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}

	branch := request.GetBranch()
	if client.Driver == scm.DriverGitlab {
		branch = url.QueryEscape(branch)
	}

	ref, err := gitclient.GetValidRef(*request.Provider, request.GetRef(), branch)
	if err != nil {
		log.Errorw("GetLatestCommit failure, bad ref/branch", "provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "ref", ref, "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}

	switch client.Driver {
	case scm.DriverBitbucket,
		scm.DriverStash:
		if branch != "" && strings.Contains(ref, "/") {
			ref = scm.TrimRef(ref)
			branch, _, err := client.Git.FindBranch(ctx, request.GetSlug(), ref)
			if err == nil {
				ref = branch.Sha
			}
		}
	case scm.DriverAzure, scm.DriverHarness:
		// Azure doesnt support getting a commit by ref/branch name. So we get the latest commit from the branch using the root folder.
		// Harness only supports a ref
		contents, _, err := client.Contents.List(ctx, request.GetSlug(), "", ref, scm.ListOptions{})
		if err == nil {
			ref = contents[0].Sha
		}
	}

	refResponse, response, err := client.Git.FindCommit(ctx, request.GetSlug(), ref)
	if err != nil {
		log.Errorw("GetLatestCommit failure", "provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "ref", ref, "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		// this is a hard error with no response
		if response == nil {
			return nil, err
		}
		// this is an error from the git provider
		out = &pb.GetLatestCommitResponse{
			Error:  err.Error(),
			Status: int32(response.Status),
		}
		return out, nil
	}

	// bitbucket onprem API doesn't return commit link, hence populating it manually.
	if refResponse.Link == "" && request.GetProvider().GetBitbucketServer() != nil {
		namespace, name := scm.Split(request.GetSlug())
		refResponse.Link = fmt.Sprintf("%sprojects/%s/repos/%s/commits/%s", client.BaseURL, namespace, name, refResponse.Sha)
	}

	commit, err := converter.ConvertCommit(refResponse)
	if err != nil {
		log.Errorw("GetLatestCommit convert commit failure", "provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "ref", ref, "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}
	log.Infow("GetLatestCommit success", "slug", request.GetSlug(), "ref", ref, "elapsed_time_ms", utils.TimeSince(start))

	out = &pb.GetLatestCommitResponse{
		CommitId: refResponse.Sha,
		Commit:   commit,
		Status:   int32(response.Status),
	}
	return out, nil
}

func FindCommit(ctx context.Context, request *pb.FindCommitRequest, log *zap.SugaredLogger) (out *pb.FindCommitResponse, err error) {
	start := time.Now()
	log.Infow("FindCommit starting", "slug", request.GetSlug())

	client, err := gitclient.GetGitClient(*request.GetProvider(), log)
	if err != nil {
		log.Errorw("FindCommit failure", "bad provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}

	refResponse, response, err := client.Git.FindCommit(ctx, request.GetSlug(), request.GetRef())
	if err != nil {
		log.Errorw("FindCommit failure", "provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "ref", request.GetRef(), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		// this is a hard error with no response
		if response == nil {
			return nil, err
		}
		// this is an error from the git provider
		out = &pb.FindCommitResponse{
			Error:  err.Error(),
			Status: int32(response.Status),
		}
		return out, nil
	}

	commit, err := converter.ConvertCommit(refResponse)
	if err != nil {
		log.Errorw("FindCommit convert commit failure", "provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "ref", request.GetRef(), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}
	log.Infow("FindCommit success", "slug", request.GetSlug(), "commitId", request.GetRef(), "elapsed_time_ms", utils.TimeSince(start))

	out = &pb.FindCommitResponse{
		Commit: commit,
		Status: int32(response.Status),
	}
	return out, nil
}

func ListBranches(ctx context.Context, request *pb.ListBranchesRequest, log *zap.SugaredLogger) (*pb.ListBranchesResponse, error) {
	start := time.Now()
	log.Infow("ListBranches starting", "slug", request.GetSlug())

	client, err := gitclient.GetGitClient(*request.GetProvider(), log)
	if err != nil {
		log.Errorw("ListBranches failure", "bad provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}

	branchesContent, response, err := client.Git.ListBranches(ctx, request.GetSlug(), scm.ListOptions{Page: int(request.GetPagination().GetPage())})
	if err != nil {
		log.Errorw("ListBranches failure", "provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		if response == nil {
			return nil, err
		}
		// this is an error from the git provider, e.g. authentication.
		out := &pb.ListBranchesResponse{
			Status: int32(response.Status),
			Error:  err.Error(),
		}
		return out, nil
	}

	log.Infow("ListBranches success", "slug", request.GetSlug(), "elapsed_time_ms", utils.TimeSince(start))
	var branches []string
	for _, v := range branchesContent {
		branches = append(branches, v.Name)
	}

	out := &pb.ListBranchesResponse{
		Branches: branches,
		Pagination: &pb.PageResponse{
			Next: int32(response.Page.Next),
		},
	}
	return out, nil
}

func ListBranchesWithDefault(ctx context.Context, request *pb.ListBranchesWithDefaultRequest, log *zap.SugaredLogger) (*pb.ListBranchesWithDefaultResponse, error) {
	start := time.Now()
	log.Infow("ListBranchesWithDefault starting", "slug", request.GetSlug())

	client, err := gitclient.GetGitClient(*request.GetProvider(), log)
	if err != nil {
		log.Errorw("ListBranchesWithDefault failure", "bad provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}

	branchesContent, response, err := client.Git.ListBranches(ctx, request.GetSlug(), scm.ListOptions{Page: int(request.GetPagination().GetPage())})
	if err != nil {
		// this is an error from the git provider, e.g. authentication.
		log.Errorw("ListBranchesWithDefault failure", "provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		if response == nil {
			return nil, err
		}
		out := &pb.ListBranchesWithDefaultResponse{
			Status: int32(response.Status),
			Error:  err.Error(),
		}
		return out, nil
	}

	getUserRepoRequest := &pb.GetUserRepoRequest{
		Slug:     request.GetSlug(),
		Provider: request.GetProvider(),
	}

	var branches []string
	for _, v := range branchesContent {
		branches = append(branches, v.Name)
	}

	if len(branches) == 0 && int(request.GetPagination().GetPage()) == 1 {
		out := &pb.ListBranchesWithDefaultResponse{
			Branches: branches,
			Pagination: &pb.PageResponse{
				Next: int32(response.Page.Next),
			},
		}
		return out, nil
	}

	log.Infow("ListBranches API ran successfully", "slug", request.GetSlug(), "elapsed_time_ms", utils.TimeSince(start))
	userRepoResponse, err := GetUserRepo(ctx, getUserRepoRequest, log)

	if err != nil {
		// this is an error from the git provider, e.g. authentication.
		log.Errorw("List Default Branch V2 failure", "provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		if response == nil {
			return nil, err
		}
		// this is an error from the git provider, e.g. authentication.
		out := &pb.ListBranchesWithDefaultResponse{
			Status: userRepoResponse.GetStatus(),
			Error:  userRepoResponse.GetError(),
		}
		return out, nil
	}

	log.Infow("ListRepo success", "slug", request.GetSlug(), "elapsed_time_ms", utils.TimeSince(start))
	out := &pb.ListBranchesWithDefaultResponse{
		Branches:      branches,
		DefaultBranch: userRepoResponse.GetRepo().GetBranch(),
		Pagination: &pb.PageResponse{
			Next: int32(response.Page.Next),
		},
	}
	log.Infow("ListBranchesWithDefault success", "slug", request.GetSlug(), "elapsed_time_ms", utils.TimeSince(start))
	return out, nil
}

func ListCommits(ctx context.Context, request *pb.ListCommitsRequest, log *zap.SugaredLogger) (out *pb.ListCommitsResponse, err error) {
	start := time.Now()
	log.Infow("ListCommits starting", "slug", request.GetSlug())

	client, err := gitclient.GetGitClient(*request.GetProvider(), log)
	if err != nil {
		log.Errorw("ListCommits failure", "bad provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}

	ref, err := gitclient.GetValidRef(*request.Provider, request.GetRef(), request.GetBranch())
	if err != nil {
		log.Errorw("ListCommits failure, bad ref/branch", "provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "ref", ref, "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}

	commits, response, err := client.Git.ListCommits(ctx, request.GetSlug(), scm.CommitListOptions{Ref: ref, Page: int(request.GetPagination().GetPage()), Path: request.FilePath})

	if err != nil {
		log.Errorw("ListCommits failure", "provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "ref", ref, "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}

	log.Infow("ListCommits success", "slug", request.GetSlug(), "ref", ref, "elapsed_time_ms", utils.TimeSince(start))
	var commitIDs []string
	for _, v := range commits {
		commitIDs = append(commitIDs, v.Sha)
	}

	out = &pb.ListCommitsResponse{
		CommitIds: commitIDs,
		Pagination: &pb.PageResponse{
			Next: int32(response.Page.Next),
		},
	}
	return out, nil
}

func CompareCommits(ctx context.Context, request *pb.CompareCommitsRequest, log *zap.SugaredLogger) (out *pb.CompareCommitsResponse, err error) {
	start := time.Now()
	log.Infow("CompareCommits starting", "slug", request.GetSlug())

	client, err := gitclient.GetGitClient(*request.GetProvider(), log)
	if err != nil {
		log.Errorw("CompareCommits failure", "bad provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}

	changes, response, err := client.Git.CompareChanges(ctx, request.GetSlug(), request.GetSource(), request.GetTarget(), scm.ListOptions{Page: int(request.GetPagination().GetPage())})
	if err != nil {
		log.Errorw("CompareCommits failure", "provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "source", request.GetSource(), "target", request.GetTarget(),
			"elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}
	log.Infow("CompareCommits success", "slug", request.GetSlug(), "source", request.GetSource(), "target", request.GetTarget(), "elapsed_time_ms", utils.TimeSince(start))

	out = &pb.CompareCommitsResponse{
		Files: convertChangesList(changes),
		Pagination: &pb.PageResponse{
			Next: int32(response.Page.Next),
		},
	}
	return out, nil
}

func GetAuthenticatedUser(ctx context.Context, request *pb.GetAuthenticatedUserRequest, log *zap.SugaredLogger) (out *pb.GetAuthenticatedUserResponse, err error) {
	start := time.Now()
	log.Infow("GetAuthenticatedUser starting")

	client, err := gitclient.GetGitClient(*request.GetProvider(), log)
	if err != nil {
		log.Errorw("GetAuthenticatedUser failure", "bad provider", gitclient.GetProvider(*request.GetProvider()), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}

	response, _, err := client.Users.Find(ctx)
	if err != nil {
		log.Errorw("GetAuthenticatedUser failure", "provider", gitclient.GetProvider(*request.GetProvider()), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}
	log.Infow("GetAuthenticatedUser success", "elapsed_time_ms", utils.TimeSince(start))

	out = &pb.GetAuthenticatedUserResponse{
		Username: response.Name,
	}
	return out, nil
}

func GetUserRepos(ctx context.Context, request *pb.GetUserReposRequest, log *zap.SugaredLogger) (out *pb.GetUserReposResponse, err error) {
	start := time.Now()
	log.Infow("GetUserRepos starting")

	client, err := gitclient.GetGitClient(*request.GetProvider(), log)

	if err != nil {
		log.Errorw("GetUserRepos failure", "bad provider", gitclient.GetProvider(*request.GetProvider()), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}
	isGithubApp := isGithubApp(*&request.Provider)
	paginatedCall := !request.GetFetchAllRepos()

	if paginatedCall {
		var repoList []*scm.Repository
		var response *scm.Response

		if isGithubApp {
			repoList, response, err = client.Repositories.(*github.RepositoryService).ListByInstallation(ctx, scm.ListOptions{Page: int(request.GetPagination().GetPage())})
		} else {
			repoList, response, err = client.Repositories.List(ctx, scm.ListOptions{Page: int(request.GetPagination().GetPage())})
		}
		if err != nil {
			log.Errorw("GetUserRepos failure", "provider", gitclient.GetProvider(*request.GetProvider()), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
			// this is a hard error with no response
			if response == nil {
				return nil, err
			}

			out = &pb.GetUserReposResponse{
				Status: int32(response.Status),
				Error:  err.Error(),
			}
			return out, nil
		}
		log.Infow("GetUserRepos success", "elapsed_time_ms", utils.TimeSince(start))

		out = &pb.GetUserReposResponse{
			Status: int32(response.Status),
			Repos:  convertRepoList(repoList),
			Pagination: &pb.PageResponse{
				Next: int32(response.Page.Next),
			},
		}
		return out, nil
	} else {
		// TODO as part of error plan improvement change the
		// Repos function to return the status code as well
		listAllReposRequest := &ListAllReposRequest{
			IsGithubApp: isGithubApp,
		}
		repoList, err := Repos(ctx, client, log, listAllReposRequest)
		if err != nil {
			log.Infow("GetAllUserRepos failed", err)
			return nil, err
		}

		log.Infow("GetAllUserRepos success", "elapsed_time_ms", utils.TimeSince(start), "response", len(repoList))
		out = &pb.GetUserReposResponse{
			Status:     int32(200),
			Repos:      convertAndMiniseRepoList(repoList),
			Pagination: &pb.PageResponse{},
		}
		return out, nil
	}
}

func GetUserRepo(ctx context.Context, request *pb.GetUserRepoRequest, log *zap.SugaredLogger) (out *pb.GetUserRepoResponse, err error) {
	start := time.Now()
	log.Infow("GetUserRepos starting")

	client, err := gitclient.GetGitClient(*request.GetProvider(), log)
	if err != nil {
		log.Errorw("GetUserRepo failure", "bad provider", gitclient.GetProvider(*request.GetProvider()), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}

	repo, response, err := client.Repositories.Find(ctx, request.GetSlug())
	if err != nil {
		log.Errorw("GetUserRepo failure", "provider", gitclient.GetProvider(*request.GetProvider()), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		// this is a hard error with no response
		if response == nil {
			return nil, err
		}

		out = &pb.GetUserRepoResponse{
			Status: int32(response.Status),
			Error:  err.Error(),
		}
		return out, nil
	}

	repository, err := converter.ConvertRepo(repo)
	if err != nil {
		log.Errorw("GetUserRepo convert repo failure", "provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}
	log.Infow("GetUserRepo success", "elapsed_time_ms", utils.TimeSince(start))
	out = &pb.GetUserRepoResponse{
		Status: int32(response.Status),
		Repo:   repository,
	}
	return out, nil
}

func GetLatestCommitOnFile(ctx context.Context, request *pb.GetLatestCommitOnFileRequest, log *zap.SugaredLogger) (out *pb.GetLatestCommitOnFileResponse, err error) {
	// For Bitbucket, we also get commits for a non-existent file if it had been created before (deleted now)
	response, err := ListCommits(ctx, &pb.ListCommitsRequest{Provider: request.Provider, Slug: request.Slug, Type: &pb.ListCommitsRequest_Branch{Branch: request.Branch}, FilePath: request.FilePath}, log)
	log.Infow("GetLatestCommitOnFile", "response", response, "error", err)
	if err != nil {
		return &pb.GetLatestCommitOnFileResponse{
			CommitId: "",
			Error:    err.Error(),
		}, nil
	}

	if response.CommitIds != nil && len(response.CommitIds) != 0 {
		return &pb.GetLatestCommitOnFileResponse{
			CommitId: response.CommitIds[0],
		}, nil
	}
	// TODO Return an error saying no commit found for the given file
	return &pb.GetLatestCommitOnFileResponse{
		CommitId: "",
	}, nil
}

func convertChangesList(from []*scm.Change) (to []*pb.PRFile) {
	for _, v := range from {
		to = append(to, convertChange(v))
	}
	return to
}

func convertCommitsList(from []*scm.Commit) (to []*pb.Commit) {
	for _, v := range from {
		convertedCommit, _ := converter.ConvertCommit(v)
		to = append(to, convertedCommit)
	}
	return to
}

func convertRepoList(from []*scm.Repository) (to []*pb.Repository) {
	for _, v := range from {
		convertedRepository, _ := converter.ConvertRepo(v)
		to = append(to, convertedRepository)
	}
	return to
}

func convertAndMiniseRepoList(from []*scm.Repository) (to []*pb.Repository) {
	for _, v := range from {
		convertedRepository, _ := converter.ConvertsAndMinimiseRepo(v)
		to = append(to, convertedRepository)
	}
	return to
}

func convertChange(from *scm.Change) *pb.PRFile {
	return &pb.PRFile{
		Path:         from.Path,
		Added:        from.Added,
		Deleted:      from.Deleted,
		Renamed:      from.Renamed,
		PrevFilePath: from.PrevFilePath,
	}
}

// Repos returns the full repository list, traversing and
// combining paginated responses if necessary.
func Repos(ctx context.Context, client *scm.Client, log *zap.SugaredLogger, request *ListAllReposRequest) ([]*scm.Repository, error) {
	list := []*scm.Repository{}
	opts := scm.ListOptions{Size: 100}
	for {
		result := []*scm.Repository{}
		var meta *scm.Response
		var err error
		if request.IsGithubApp {
			result, meta, err = client.Repositories.(*github.RepositoryService).ListByInstallation(ctx, opts)
		} else {
			result, meta, err = client.Repositories.List(ctx, opts)
		}
		if err != nil {
			return nil, err
		}
		if result != nil {
			list = append(list, result...)
		}
		opts.Page = meta.Page.Next
		opts.URL = meta.Page.NextURL

		if opts.Page == 0 && opts.URL == "" {
			break
		}
	}
	return list, nil
}

type ListAllReposRequest struct {
	IsGithubApp bool
}

// Finds out if provider is github app
func isGithubApp(p *pb.Provider) (out bool) {
	gitProvider := gitclient.GetProvider(*p)
	if gitProvider == "github" && p.GetGithub().GetIsGithubApp() {
		return true
	} else {
		return false
	}
}
