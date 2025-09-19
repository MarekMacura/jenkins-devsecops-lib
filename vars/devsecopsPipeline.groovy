// vars/devsecopsPipeline.groovy  
def call(Map config = [:]) {  
    pipeline {  
        agent any  
  
        environment {  
            APP_NAME   = config.appName ?: 'myapp'  
            GIT_BRANCH = config.gitBranch ?: 'main'  
        }  
  
        stages {  
            stage('Checkout') {  
                steps {  
                    script { checkoutSource(config.gitUrl) }  
                }  
            }  
  
            stage('SAST') {  
                steps {  
                    script { runSAST(config.sonarServer ?: 'SonarQubeServer') }  
                }  
            }  
  
            stage('Dependency Scan') {  
                steps {  
                    script { runDependencyScan() }  
                }  
            }  
  
            stage('Build Image') {  
                steps {  
                    script { buildContainerImage(config.registry ?: 'quay.io/myorg') }  
                }  
            }  
  
            stage('Image Scan') {  
                steps {  
                    script { scanContainerImage() }  
                }  
            }  
  
            stage('Deploy to Test') {  
                steps {  
                    script { deployToTest() }  
                }  
            }  
  
            stage('DAST') {  
                steps {  
                    script { runDAST(config.testUrl ?: "http://test.${APP_NAME}.example.com") }  
                }  
            }  
  
            stage('Approval Gate') {  
                steps {  
                    script { approvalGate() }  
                }  
            }  
  
            stage('Deploy to Production') {  
                steps {  
                    script { deployToProd() }  
                }  
            }  
        }  
  
        post {  
            always {  
                cleanWs()  
            }  
        }  
    }  
}  
