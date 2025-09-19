// vars/devsecopsSteps.groovy  
  
def checkoutSource(String gitUrl) {  
    git branch: env.GIT_BRANCH, url: gitUrl  
}  
  
def runSAST(String sonarServer) {  
    withSonarQubeEnv(sonarServer) {  
        sh """  
            sonar-scanner \  
              -Dsonar.projectKey=${env.APP_NAME} \  
              -Dsonar.sources=. \  
              -Dsonar.host.url=$SONAR_HOST_URL \  
              -Dsonar.login=$SONAR_AUTH_TOKEN  
        """  
    }  
}  
  
def runDependencyScan() {  
    withCredentials([string(credentialsId: 'snyk-api-token', variable: 'SNYK_TOKEN')]) {  
        sh """  
            snyk auth $SNYK_TOKEN  
            snyk test --severity-threshold=high || true  
        """  
    }  
}  
  
def buildContainerImage(String registry) {  
    withCredentials([usernamePassword(credentialsId: 'quay-creds', usernameVariable: 'QUAY_USER', passwordVariable: 'QUAY_PASS')]) {  
        def imageTag = "${registry}/${env.APP_NAME}:${env.BUILD_NUMBER}"  
        sh """  
            buildah bud -t $imageTag .  
            buildah push --creds $QUAY_USER:$QUAY_PASS $imageTag  
        """  
        env.QUAY_IMAGE = imageTag  
    }  
}  
  
def scanContainerImage() {  
    sh """  
        trivy image --exit-code 0 --severity HIGH,CRITICAL ${env.QUAY_IMAGE}  
        trivy image --exit-code 1 --severity CRITICAL ${env.QUAY_IMAGE} || exit 1  
    """  
}  
  
def deployToTest() {  
    withCredentials([usernamePassword(credentialsId: 'argocd-creds', usernameVariable: 'ARGOCD_USER', passwordVariable: 'ARGOCD_PASS')]) {  
        sh """  
            argocd login argocd.example.com --username $ARGOCD_USER --password $ARGOCD_PASS --insecure  
            argocd app sync ${env.APP_NAME}-test  
        """  
    }  
}  
  
def runDAST(String targetUrl) {  
    sh """  
        docker run --rm owasp/zap2docker-stable zap-baseline.py -t ${targetUrl} -r zap_report.html || true  
    """  
    archiveArtifacts artifacts: 'zap_report.html', fingerprint: true  
}  
  
def approvalGate() {  
    timeout(time: 1, unit: 'HOURS') {  
        input message: "Approve deployment to Production?"  
    }  
}  
  
def deployToProd() {  
    withCredentials([usernamePassword(credentialsId: 'argocd-creds', usernameVariable: 'ARGOCD_USER', passwordVariable: 'ARGOCD_PASS')]) {  
        sh """  
            argocd login argocd.example.com --username $ARGOCD_USER --password $ARGOCD_PASS --insecure  
            argocd app sync ${env.APP_NAME}-prod  
        """  
    }  
}  
