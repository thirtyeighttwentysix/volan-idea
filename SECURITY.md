# Security policy

Security fixes target the latest released plugin version. The plugin runs inside
the IDE and does not require a database connection or a network service to edit schemas.

Report vulnerabilities using this repository's
[private security advisory form](https://github.com/thirtyeighttwentysix/volan-idea/security/advisories/new).
Include the plugin/IDE version, a minimal reproduction and the impact. Do not publish
credentials or signing keys in an issue.

Release signing keys and Marketplace tokens are held in GitHub Actions Secrets.
Workflow dependencies are pinned to commits and tracked by Dependabot. Pull request
builds receive no release credentials.
