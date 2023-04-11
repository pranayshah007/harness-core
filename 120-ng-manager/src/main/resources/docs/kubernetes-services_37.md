### Permissions

Ensure the connected user account has the following permissions in the Nexus Server.

* Repo: All repositories (Read)
* Nexus UI: Repository Browser

![Nexus repo perms for Harness](static/c98a49842e9d8bc5f3d2bef35aeff23c39932602a28d311eec5288cbf0fb22a9.png)

See [Nexus Managing Security](https://help.sonatype.com/display/NXRM2/Managing+Security).

For Nexus 3, when used as a **Docker** repo, the user needs:

- A role with the `nx-repository-view-*_*_*` privilege.

</details>
