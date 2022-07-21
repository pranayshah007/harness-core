# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

import sys
from os import environ
from github import Github

GITHUB_PULL = sys.argv[1]
GITHUB_REPO = environ.get('GITHUB_REPO')
GITHUB_TOKEN = environ.get('GITHUB_TOKEN')

GITHUB_ORG = 'harness'
DELEGATE_TEAM = 'del-team'

class codebaseHash(object):

    @staticmethod
    def runCodeBaseHash():

        if GITHUB_REPO is None or GITHUB_TOKEN is None:
            print("No Github credentials are provided. Exiting...")
            exit(13)

        github = Github(GITHUB_TOKEN)
        pr_reviews = github.get_repo(GITHUB_REPO).get_pull(int(GITHUB_PULL)).get_reviews()
        org = github.get_organization(GITHUB_ORG)
        members = org.get_team_by_slug(DELEGATE_TEAM).get_members()

        delegate_team = []
        for member in members:
            if member.email is not None:
                delegate_team.append(member.email)

        print(delegate_team)

        f = open("STATUS.txt", "a")
        if pr_reviews.totalCount > 0:
            for review in pr_reviews:
                if review.user.email is not None:
                    if review.user.email in delegate_team and review.state == 'APPROVED':
                        print("Changes have been approved by DELEGATE Team member:", review.user.email +
                              ". Re-Running CodeBaseHashCheck is not required.")
                        f.write("STATUS=0\n")
                        break
                    else:
                        print("CodeBaseHashCheck logic will execute.")
                else:
                    print("User Email is private, CodeBaseHashCheck logic will execute.")
        f.close()

if __name__ == '__main__':
    codebaseHash.runCodeBaseHash()
