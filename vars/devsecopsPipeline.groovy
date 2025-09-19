#!/usr/bin/env groovy  
import groovy.json.JsonSlurper  
import groovy.json.JsonOutput  
import groovy.xml.XmlSlurper  
  
// ================= CORE PIPELINE STEPS =================  
  
def checkoutCode() {  
    checkout scm  
}  
  
def buildApp() {  
    sh '''  
        echo "🔨 Building application..."  
        mvn clean install -DskipTests  
    '''  
}  
  
def runUnitTests() {  
    sh '''  
        echo "🧪 Running unit tests..."  
        mvn test  
    '''  
}  
  
def runSAST() {  
    sh '''  
        echo "🔍 Running static code analysis (SonarQube)..."  
        mvn sonar:sonar \  
            -Dsonar.projectKey=${APP_NAME} \  
            -Dsonar.host.url=${SONAR_HOST_URL} \  
            -Dsonar.login=${SONARQUBE_TOKEN}  
    '''  
    sleep 10 // Wait for SonarQube to process  
  
    def authHeader = "Authorization: Basic " + "${SONARQUBE_TOKEN}:".bytes.encodeBase64().toString()  
    def apiUrl = "${SONAR_HOST_URL}/api/issues/search?componentKeys=${APP_NAME}&types=VULNERABILITY"  
    def jsonText = sh(script: "curl -s -H '${authHeader}' '${apiUrl}'", returnStdout: true).trim()  
    def json = new JsonSlurper().parseText(jsonText)  
    return json.total ?: 0  
}  
  
def runSCA() {  
    sh '''  
        echo "📦 Checking for vulnerable dependencies..."  
        mvn org.owasp:dependency-check-maven:check -Dformat=JSON  
    '''  
    def reportFile = "target/dependency-check-report.json"  
    def report = new JsonSlurper().parse(new File(reportFile))  
    return report.dependencies.count { it.vulnerabilities && it.vulnerabilities.size() > 0 }  
}  
  
def buildDockerImage() {  
    sh '''  
        echo "🐳 Building Docker image..."  
        docker build -t ${REGISTRY_URL}/${APP_NAME}:${IMAGE_TAG} .  
    '''  
}  
  
def scanContainerImage() {  
    sh '''  
        echo "🛡 Scanning Docker image with Trivy..."  
        trivy image --exit-code 0 --severity HIGH,CRITICAL --format json \  
            ${REGISTRY_URL}/${APP_NAME}:${IMAGE_TAG} > trivy-report.json || true  
    '''  
    def report = new JsonSlurper().parse(new File("trivy-report.json"))  
    def vulns = 0  
    report.Results.each { result ->  
        if (result.Vulnerabilities) {  
            vulns += result.Vulnerabilities.size()  
        }  
    }  
    return vulns  
}  
  
def deployToDev() {  
    sh '''  
        echo "🚀 Deploying to Dev..."  
        docker push ${REGISTRY_URL}/${APP_NAME}:${IMAGE_TAG}  
        kubectl apply -f k8s/deployment.yaml  
    '''  
}  
  
def runDAST() {  
    sh '''  
        echo "🌐 Running DAST (OWASP ZAP)..."  
        zap-cli --zap-url http://zap:8080 --api-key $ZAP_API_KEY \  
            report -o zap-report.json -f json || true  
    '''  
    def report = new JsonSlurper().parse(new File("zap-report.json"))  
    def vulns = 0  
    report.site.each { site ->  
        if (site.alerts) {  
            vulns += site.alerts.size()  
        }  
    }  
    return vulns  
}  
  
// ================= METRICS HELPERS =================  
  
// Get commit timestamp  
def getCommitTimestamp() {  
    return sh(script: "git log -1 --format=%ct", returnStdout: true).trim().toLong() * 1000  
}  
  
// Get Deployment Frequency (last N days)  
def getDeploymentFrequency(int days = 7) {  
    def job = Jenkins.instance.getItemByFullName(env.JOB_NAME)  
    def now = System.currentTimeMillis()  
    def fromTime = now - (days * 24 * 60 * 60 * 1000L)  
    def count = 0  
    job.builds.each { build ->  
        if (build.getResult()?.toString() == 'SUCCESS' && build.getTimeInMillis() >= fromTime) {  
            count++  
        }  
    }  
    return count  
}  
  
// Get Change Failure Rate (last N days)  
def getChangeFailureRate(int days = 7) {  
    def job = Jenkins.instance.getItemByFullName(env.JOB_NAME)  
    def now = System.currentTimeMillis()  
    def fromTime = now - (days * 24 * 60 * 60 * 1000L)  
    def totalDeploys = 0  
    def failedDeploys = 0  
    job.builds.each { build ->  
        if (build.getTimeInMillis() >= fromTime) {  
            totalDeploys++  
            if (build.getResult()?.toString() != 'SUCCESS') {  
                failedDeploys++  
            }  
        }  
    }  
    return totalDeploys > 0 ? failedDeploys / totalDeploys : 0.0  
}  
  
// Get MTTR (Mean Time To Restore) in millis  
def getMTTR(int days = 7) {  
    def job = Jenkins.instance.getItemByFullName(env.JOB_NAME)  
    def now = System.currentTimeMillis()  
    def fromTime = now - (days * 24 * 60 * 60 * 1000L)  
  
    def failureTimes = []  
    def restoreTimes = []  
    def lastFailureTime = null  
  
    job.builds.reverseEach { build ->  
        if (build.getTimeInMillis() >= fromTime) {  
            if (build.getResult()?.toString() != 'SUCCESS') {  
                lastFailureTime = build.getTimeInMillis()  
            } else if (lastFailureTime != null) {  
                failureTimes << lastFailureTime  
                restoreTimes << build.getTimeInMillis()  
                lastFailureTime = null  
            }  
        }  
    }  
  
    if (failureTimes.size() == 0) return 0  
    def totalRestoreTime = 0  
    for (int i = 0; i < failureTimes.size(); i++) {  
        totalRestoreTime += (restoreTimes[i] - failureTimes[i])  
    }  
    return (totalRestoreTime / failureTimes.size())  
}  
  
// Push metrics to Prometheus Pushgateway & optional API  
def recordMetrics(Map args = [:]) {  
    def leadTimeHours = args.leadTimeMillis / 1000 / 60 / 60  
    def failureRate = args.failureRate ?: 0.0  
    def mttrHours = args.mttrMillis ? args.mttrMillis / 1000 / 60 / 60 : 0  
  
    echo "📊 Lead Time to Deploy: ${leadTimeHours} hours"  
    echo "📊 Deployment Frequency: ${args.deploymentFrequency} / week"  
    echo "📊 Change Failure Rate: ${failureRate * 100}%"  
    echo "📊 Mean Time to Restore: ${mttrHours} hours"  
    echo "📊 Vulnerabilities: SAST=${args.sastVulns}, SCA=${args.scaVulns}, Container=${args.containerVulns}, DAST=${args.dastVulns}"  
  
    // Push to Prometheus  
    def jobName = "devsecops_metrics"  
    def instance = "jenkins"  
    sh """  
        cat <<EOF | curl --data-binary @- http://prometheus-pushgateway:9091/metrics/job/${jobName}/instance/${instance}  
        lead_time_to_deploy_hours ${leadTimeHours}  
        deployment_frequency_per_week ${args.deploymentFrequency}  
        change_failure_rate ${failureRate}  
        mean_time_to_restore_hours ${mttrHours}  
        sast_vulnerabilities ${args.sastVulns}  
        sca_vulnerabilities ${args.scaVulns}  
        container_vulnerabilities ${args.containerVulns}  
        dast_vulnerabilities ${args.dastVulns}  
        EOF  
    """  
  
    // Optional API push  
    if (args.metricsApiUrl) {  
        def payload = JsonOutput.toJson([  
            leadTimeHours       : leadTimeHours,  
            deploymentFrequency : args.deploymentFrequency,  
            changeFailureRate   : failureRate,  
            meanTimeToRestoreH  : mttrHours,  
            vulnerabilities     : [  
                SAST      : args.sastVulns,  
                SCA       : args.scaVulns,  
                Container : args.containerVulns,  
                DAST      : args.dastVulns  
            ],  
            buildResult         : args.result,  
            timestamp           : System.currentTimeMillis()  
        ])  
        sh """  
            curl -X POST -H "Content-Type: application/json" \  
                -d '${payload}' ${args.metricsApiUrl}  
        """  
    }  
}  
  
return this  
