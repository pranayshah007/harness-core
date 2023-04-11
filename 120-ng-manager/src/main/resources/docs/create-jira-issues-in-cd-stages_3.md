# Limitations

While it's not a strict limitation, some users can forget that when you use a Jira Create step it creates a new, independent Jira issue every time it is run (as opposed to [updating](update-jira-issues-in-cd-stages.md) the same issue).

It is important to remember that you should only add Jira Create to a stage if you want to create a new Jira issue on every run of the stage.
