/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

// dependencies: lodash3

// The number of approvals required to merge.
let numApprovalsRequired = 1;

let re = /(feat|fix|techdebt).*(:).*(\[CDS|\[PIE|\[PL|\[IDP)/g;

if (review.pullRequest.title.match(re)) {
  numApprovalsRequired = 2;
}

const approvals = review.pullRequest.approvals;
const requestedTeams = _.pluck(review.pullRequest.requestedTeams, 'slug');

let numApprovals = _.where(approvals, 'approved').length;
const numRejections = _.where(approvals, 'changes_requested').length;

const discussionBlockers = _(review.discussions)
  .where({resolved: false})
  .pluck('participants')
  .flatten()
  .reject(participant => participant.username === review.pullRequest.author.username)
  .where({resolved: false})
  .map(user => _.pick(user, 'username'))
  .value();

let pendingReviewers = [];
let required = _.pluck(review.pullRequest.assignees, 'username');

_.pull(required, review.pullRequest.author.username);
if (required.length) {
  numApprovalsRequired =
    _.max([required.length, numApprovalsRequired]);
  numApprovals =
    (_(approvals).pick(required).where('approved').size()) +
    _.min([numApprovals, numApprovalsRequired - required.length]);
  pendingReviewers = _(required)
    .reject(username => approvals[username] === 'approved')
    .reject(username => pendingReviewers.length && approvals[username])
    .map(username=>({username}))
    .concat(pendingReviewers)
    .value();
}

pendingReviewers = _.uniq(pendingReviewers, 'username');

const olderCheck = Math.min(...Object.values(review.pullRequest.checks).map(check => check.timestamp))

let tooOld = olderCheck < Date.now() - 259200000

let numUnreviewedFiles = 0;
let fileBlockers = [];
_.forEach(review.files, function(file) {
  const lastRev = _(file.revisions).reject('obsolete').last();
  if (!lastRev) {
    // When there are reverted files it seems that all revisions on it are obsolete, so break early. 
    return;
  }
  const reviewers = _(lastRev.reviewers)
    .pluck('username')
    .without(review.pullRequest.author.username)
    .value();
  const missingReviewers = _.difference(required, reviewers);
  if (reviewers.length >= numApprovalsRequired &&
      _.isEmpty(missingReviewers)) return;
  numUnreviewedFiles++;
  const lastReviewedRev =
    _(file.revisions).findLast(rev => !_.isEmpty(rev.reviewers));
  fileBlockers = fileBlockers.concat(
    _.map(missingReviewers, username => ({username})),
    lastReviewedRev ? lastReviewedRev.reviewers : []
  );
});

const completed =
      !tooOld &&
      numUnreviewedFiles === 0 &&
      pendingReviewers.length === 0 &&
      numApprovals >= numApprovalsRequired &&
      discussionBlockers.length === 0 &&
      Object.keys(requestedTeams).length === 0;

const description = (completed ? "✓" :
  (tooOld ? `Some of the checks are too old. ` : '') +
  (numUnreviewedFiles > 0 ? `${numUnreviewedFiles} file(s) to review. ` : '') +
  (numRejections ? `${numRejections} change requests. ` : '') +
  (discussionBlockers.length > 0 ? `${discussionBlockers.length}. unresolved discussions. ` : '') +
  (numApprovals < numApprovalsRequired ? `${numApprovals} of ${numApprovalsRequired} approvals obtained. ` : '') +
  (Object.keys(requestedTeams).length == 0 ? "" : `Waiting on team(s): ${requestedTeams}. `));

const shortDescription = (completed ? "✓" : "✗");

return {
  completed, description, shortDescription, pendingReviewers
};// dependencies: lodash3

// The number of approvals required to merge.
let numApprovalsRequired = 1;

let re = /(feat|fix|techdebt).*(:).*(\[CDS|\[PIE|\[PL|\[IDP)/g;

if (review.pullRequest.title.match(re)) {
  numApprovalsRequired = 2;
}

const approvals = review.pullRequest.approvals;
const requestedTeams = _.pluck(review.pullRequest.requestedTeams, 'slug');

let numApprovals = _.where(approvals, 'approved').length;
const numRejections = _.where(approvals, 'changes_requested').length;

const discussionBlockers = _(review.discussions)
  .where({resolved: false})
  .pluck('participants')
  .flatten()
  .reject(participant => participant.username === review.pullRequest.author.username)
  .where({resolved: false})
  .map(user => _.pick(user, 'username'))
  .value();

let pendingReviewers = [];
let required = _.pluck(review.pullRequest.assignees, 'username');

_.pull(required, review.pullRequest.author.username);
if (required.length) {
  numApprovalsRequired =
    _.max([required.length, numApprovalsRequired]);
  numApprovals =
    (_(approvals).pick(required).where('approved').size()) +
    _.min([numApprovals, numApprovalsRequired - required.length]);
  pendingReviewers = _(required)
    .reject(username => approvals[username] === 'approved')
    .reject(username => pendingReviewers.length && approvals[username])
    .map(username=>({username}))
    .concat(pendingReviewers)
    .value();
}

pendingReviewers = _.uniq(pendingReviewers, 'username');

const olderCheck = Math.min(...Object.values(review.pullRequest.checks).map(check => check.timestamp))

let tooOld = olderCheck < Date.now() - 259200000

let numUnreviewedFiles = 0;
let fileBlockers = [];
_.forEach(review.files, function(file) {
  const lastRev = _(file.revisions).reject('obsolete').last();
  if (!lastRev) {
    // When there are reverted files it seems that all revisions on it are obsolete, so break early. 
    return;
  }
  const reviewers = _(lastRev.reviewers)
    .pluck('username')
    .without(review.pullRequest.author.username)
    .value();
  const missingReviewers = _.difference(required, reviewers);
  if (reviewers.length >= numApprovalsRequired &&
      _.isEmpty(missingReviewers)) return;
  numUnreviewedFiles++;
  const lastReviewedRev =
    _(file.revisions).findLast(rev => !_.isEmpty(rev.reviewers));
  fileBlockers = fileBlockers.concat(
    _.map(missingReviewers, username => ({username})),
    lastReviewedRev ? lastReviewedRev.reviewers : []
  );
});

const completed =
      !tooOld &&
      numUnreviewedFiles === 0 &&
      pendingReviewers.length === 0 &&
      numApprovals >= numApprovalsRequired &&
      discussionBlockers.length === 0 &&
      Object.keys(requestedTeams).length === 0;

const description = (completed ? "✓" :
  (tooOld ? `Some of the checks are too old. ` : '') +
  (numUnreviewedFiles > 0 ? `${numUnreviewedFiles} file(s) to review. ` : '') +
  (numRejections ? `${numRejections} change requests. ` : '') +
  (discussionBlockers.length > 0 ? `${discussionBlockers.length}. unresolved discussions. ` : '') +
  (numApprovals < numApprovalsRequired ? `${numApprovals} of ${numApprovalsRequired} approvals obtained. ` : '') +
  (Object.keys(requestedTeams).length == 0 ? "" : `Waiting on team(s): ${requestedTeams}. `));

const shortDescription = (completed ? "✓" : "✗");

return {
  completed, description, shortDescription, pendingReviewers
};
