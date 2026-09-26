pipeline {
    agent any

    environment {
        DB_URL = 'jdbc:postgresql://localhost:15432/payguard'
        DB_CREDENTIALS = credentials('payguard-db-credentials')
        FAILURE_SIMULATION_ENABLED = 'false'
        IMAGE_NAME = 'payguard-payment-service'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Test') {
            steps {
                withEnv([
                    "DB_USERNAME=${DB_CREDENTIALS_USR}",
                    "DB_PASSWORD=${DB_CREDENTIALS_PSW}"
                ]) {
                    dir('payment-service') {
                        bat 'mvnw.cmd --batch-mode clean test'
                    }
                }
            }
        }

        stage('Package') {
            steps {
                withEnv([
                    "DB_USERNAME=${DB_CREDENTIALS_USR}",
                    "DB_PASSWORD=${DB_CREDENTIALS_PSW}"
                ]) {
                    dir('payment-service') {
                        bat 'mvnw.cmd --batch-mode package -DskipTests'
                    }
                }
            }
        }

        stage('Docker Build') {
            steps {
                bat 'docker build -t %IMAGE_NAME%:%BUILD_NUMBER% payment-service'
            }
        }

        stage('Kubernetes Validation') {
            steps {
                bat 'kubectl apply --dry-run=client -f kubernetes/namespace.yaml'
                bat 'kubectl apply --dry-run=client -f kubernetes/configmap.yaml'
                bat 'kubectl apply --dry-run=client -f kubernetes/secret.example.yaml'
                bat 'kubectl apply --dry-run=client -f kubernetes/postgres/pvc.yaml'
                bat 'kubectl apply --dry-run=client -f kubernetes/postgres/deployment.yaml'
                bat 'kubectl apply --dry-run=client -f kubernetes/postgres/service.yaml'
                bat 'kubectl apply --dry-run=client -f kubernetes/payment-service/deployment.yaml'
                bat 'kubectl apply --dry-run=client -f kubernetes/payment-service/service.yaml'
            }
        }
    }

    post {
        success {
            echo 'PayGuard pipeline completed successfully.'
        }

        failure {
            echo 'PayGuard pipeline failed. Check the failed stage.'
        }

        always {
            junit allowEmptyResults: true,
                  testResults: 'payment-service/target/surefire-reports/*.xml'

            archiveArtifacts allowEmptyArchive: true,
                             artifacts: 'payment-service/target/*.jar'
        }
    }
}
