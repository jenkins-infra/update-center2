#!groovy

properties([
        disableConcurrentBuilds(),
        buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '2')),
])

node('maven-21') {
    stage('Prepare') {
        deleteDir()
        checkout scm
    }

    stage('Generate') {
        sh 'mvn -e clean verify'
    }

    stage('Archive Test Report') {
        archive 'target/surefire-reports/*-output.txt'
    }
}
