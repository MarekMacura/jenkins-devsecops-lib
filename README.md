# jenkins-devsecops-lib

In application repo (not the library repo), the Jenkinsfile becomes very simple:

@Library('devsecops-lib@main') _  // Load the shared library from Jenkins config  
  
devsecopsPipeline(  
    appName: 'orderservice',  
    gitUrl: 'https://github.com/myorg/orderservice.git',  
    gitBranch: 'main',  
    sonarServer: 'SonarQubeServer',  
    registry: 'quay.io/myorg',  
    testUrl: 'http://test.orderservice.example.com'  
)  



Configuring the Shared Library in Jenkins

    In Jenkins UI: Manage Jenkins → Configure System → Global Pipeline Libraries
    Add:
        Name: devsecops-lib
        Default version: main (or a Git tag/branch)
        Retrieval method: Modern SCM → Git
        Project Repository: https://github.com/yourorg/jenkins-devsecops-lib.git
    Save.

Tagging Versions in Git

# Commit your library changes  
git add .  
git commit -m "Add DAST stage improvements"  
  
# Create a new version tag  
git tag v1.0.0  
  
# Push the tag to remote  
git push origin v1.0.0  


Pinning Different Projects to Different Versions


Azure AI Foundry| gpt-5-chat-latest
AI-generated content may be incorrect

Got it ✅ — you want to version your Jenkins Shared Library so that you can control when pipeline changes roll out to your projects.

This is a best practice in production DevSecOps setups, because it:

    Prevents unexpected pipeline changes from breaking builds.
    Allows gradual rollout of new security checks.
    Lets you pin projects to a specific known-good pipeline version.
    Makes rollback simple if there’s a problem.

1️⃣ How Jenkins Shared Library Versioning Works

When you configure your shared library in Jenkins (Manage Jenkins → Configure System → Global Pipeline Libraries), you set:

    Default version — the Git branch, tag, or commit hash Jenkins uses if you don’t explicitly specify one.
    You can override this in your Jenkinsfile with @Library('library-name@version').

Example:

@Library('devsecops-lib@v1.0.0') _  
devsecopsPipeline(  
    appName: 'orderservice',  
    gitUrl: 'https://github.com/myorg/orderservice.git',  
    registry: 'quay.io/myorg'  
)  

Here:

    devsecops-lib is the library name from Jenkins config.
    @v1.0.0 is a Git tag in the library repo.

2️⃣ Tagging Versions in Git

Inside your shared library repo (jenkins-devsecops-lib):

# Commit your library changes  
git add .  
git commit -m "Add DAST stage improvements"  
  
# Create a new version tag  
git tag v1.0.0  
  
# Push the tag to remote  
git push origin v1.0.0  

You can also use semantic versioning:

    v1.0.0 — initial stable version
    v1.1.0 — new features (non-breaking)
    v2.0.0 — breaking changes

3️⃣ Pinning Different Projects to Different Versions

You can have:

    Production apps pinned to a stable version:

@Library('devsecops-lib@v1.0.0') _  
devsecopsPipeline(...)  

    Test apps pinned to the latest development branch:

@Library('devsecops-lib@develop') _  
devsecopsPipeline(...)  

    Canary apps pinned to a release candidate:

@Library('devsecops-lib@v1.1.0-rc1') _  
devsecopsPipeline(...)  



Recommended Versioning Strategy for DevSecOps Pipelines
Branch/Tag	Purpose
main	Latest stable code, only minor/patch changes
develop	In-progress features for testing
vX.Y.Z	Tagged stable releases for production
rc-X.Y.Z	Release candidates for limited rollout

Workflow:

    Develop new pipeline logic in develop branch.
    Test it in non-critical jobs.
    Merge into main when stable.
    Tag main with a new version (v1.1.0).
    Update production Jenkinsfiles to point to the new tag.
