/**  
 * Convert a log file into an HTML file, line by line.  
 *  
 * @param logFilePath path to the source log file  
 * @param htmlFilePath path to output HTML file  
 */  
def logToHtml(String logFilePath, String htmlFilePath) {  
    def logFile = new File(logFilePath)  
    def htmlFile = new File(htmlFilePath)  
  
    htmlFile.withWriter('UTF-8') { writer ->  
        writer.println("<!DOCTYPE html>")  
        writer.println("<html>")  
        writer.println("<head>")  
        writer.println("<meta charset='UTF-8'>")  
        writer.println("<title>Log File</title>")  
        writer.println("<style>")  
        writer.println("body { font-family: monospace; background-color: #f9f9f9; }")  
        writer.println(".logline { margin: 0; white-space: pre; }")  
        writer.println("</style>")  
        writer.println("</head>")  
        writer.println("<body>")  
  
        logFile.eachLine { line ->  
            // Escape HTML special characters  
            def safeLine = line.replaceAll('&', '&amp;')  
                               .replaceAll('<', '&lt;')  
                               .replaceAll('>', '&gt;')  
            writer.println("<div class='logline'>${safeLine}</div>")  
        }  
  
        writer.println("</body>")  
        writer.println("</html>")  
    }  
}  
  
// Example usage:  
logToHtml("/path/to/my.log", "/path/to/my.html")  


pipeline {  
    agent any  
  
    stages {  
        stage('Generate HTML') {  
            steps {  
                script {  
                    // Example: Generate HTML from log file  
                    logToHtml("my.log", "report.html")  
                }  
            }  
        }  
    }  
  
    post {  
        always {  
            // Publish the HTML report using HTML Publisher plugin  
            publishHTML([  
                reportDir: '.',              // Directory containing the HTML file  
                reportFiles: 'report.html',  // HTML file name  
                reportName: 'Log Report',    // Display name in Jenkins UI  
                keepAll: true,               // Keep past reports  
                allowMissing: false,  
                alwaysLinkToLastBuild: true  
            ])  
        }  
    }  
}  
  
// Groovy method to convert log to HTML  
def logToHtml(String logFilePath, String htmlFilePath) {  
    def logFile = new File(logFilePath)  
    def htmlFile = new File(htmlFilePath)  
  
    htmlFile.withWriter('UTF-8') { writer ->  
        writer.println("<!DOCTYPE html>")  
        writer.println("<html><head><meta charset='UTF-8'><title>Log File</title></head><body>")  
        logFile.eachLine { line ->  
            def safeLine = line.replaceAll('&', '&amp;')  
                               .replaceAll('<', '&lt;')  
                               .replaceAll('>', '&gt;')  
            writer.println("<div>${safeLine}</div>")  
        }  
        writer.println("</body></html>")  
    }  
}  
