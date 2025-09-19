📦 Stack Components

    Jenkins Shared Library → runs CI/CD pipeline, executes security scans, calculates all 4 DORA metrics, pushes metrics to Prometheus Pushgateway
    Prometheus → scrapes Pushgateway and stores historical metrics
    Grafana → visualizes DORA + DevSecOps metrics, with color thresholds and alert rules
    Pushgateway → temporary metrics storage for Jenkins jobs
    Example App Jenkinsfile → demo application pipeline
    Grafana Dashboard JSON → preconfigured panels with thresholds
    Grafana Alert Rules JSON → Slack/Email alerts for breaches
    Docker Compose → runs everything locally for testing
