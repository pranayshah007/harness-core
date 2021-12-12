package git

import (
	"context"
	"time"

	"github.com/drone/go-scm/scm"
	"github.com/wings-software/portal/commons/go/lib/utils"
	"github.com/wings-software/portal/product/ci/scm/converter"
	"github.com/wings-software/portal/product/ci/scm/gitclient"
	pb "github.com/wings-software/portal/product/ci/scm/proto"
	"go.uber.org/zap"
)

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

	ref, err := gitclient.GetValidRef(*request.Provider, request.GetRef(), request.GetBranch())
	if err != nil {
		log.Errorw("GetLatestCommit failure, bad ref/branch", "provider", gitclient.GetProvider(*request.GetProvider()), "slug", request.GetSlug(), "ref", ref, "elapsed_time_ms", utils.TimeSince(start), zap.Error(err))
		return nil, err
	}
	switch client.Driver {
		case scm.DriverBitbucket,
			scm.DriverStash:
			ref = scm.TrimRef(ref)
			branch, _, err := client.Git.FindBranch(ctx, request.GetSlug(), ref)
			if err != nil {
				return nil, err
			}
			ref = branch.Sha
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
		Commit:   commit,
		Status:   int32(response.Status),
	}
	return out, nil
}

func ListBranches(ctx context.Context, request *pb.ListBranchesRequest, log *zap.SugaredLogger) (out *pb.ListBranchesResponse, err error) {
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
		out = &pb.ListBranchesResponse{
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

	out = &pb.ListBranchesResponse{
		Branches: branches,
		Pagination: &pb.PageResponse{
			Next: int32(response.Page.Next),
		},
	}
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

	commits, response, err := client.Git.ListCommits(ctx, request.GetSlug(), scm.CommitListOptions{Ref: ref, Page: int(request.GetPagination().GetPage())})
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

	repoList, response, err := client.Repositories.List(ctx, scm.ListOptions{Page: int(request.GetPagination().GetPage())})

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

func convertChange(from *scm.Change) *pb.PRFile {
	return &pb.PRFile{
		Path:    from.Path,
		Added:   from.Added,
		Deleted: from.Deleted,
		Renamed: from.Renamed,
	}
}
