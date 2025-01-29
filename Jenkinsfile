#!groovy

properties([
        disableConcurrentBuilds(),
        buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '2')),
])

node('linux') {
    stage('Prepare') {
        deleteDir()
        checkout scm
    }

    stage('Generate') {
        withEnv([
                "PATH+MVN=${tool 'mvn'}/bin",
                "JAVA_HOME=${tool 'jdk11'}",
                "PATH+JAVA=${tool 'jdk11'}/bin"
        ]) {
            sh 'mvn -e clean verify'
        }
    }

    stage('Archive Test Report') {
        archive 'target/surefire-reports/*-output.txt'
    }
}