#!groovy

properties([
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
                "JAVA_HOME=${tool 'jdk8'}",
                "PATH+JAVA=${tool 'jdk8'}/bin"
        ]) {
            sh 'mvn -e clean verify'
        }
    }

    stage('Archive Test Report') {
        archive 'target/surefire-reports/*-output.txt'
    }

    stage('Weekly Test Run') {
        withEnv([
                "JAVA_HOME=${tool 'jdk8'}",
                "PATH+JAVA=${tool 'jdk8'}/bin"
        ]) {
            sh 'java -jar target/update-center2-*-bin*/update-center2-*.jar' +
                    ' -id default -connectionCheckUrl http://www.google.com/' +
                    ' -no-experimental -skip-release-history' +
                    ' -www ./output/latest -download-fallback ./output/htaccess -cap 2.107.999 -capCore 2.999'
        }
    }

    stage('LTS Test Run') {
        withEnv([
                "JAVA_HOME=${tool 'jdk8'}",
                "PATH+JAVA=${tool 'jdk8'}/bin"
        ]) {
            sh 'java -jar target/update-center2-*-bin*/update-center2-*.jar' +
                    ' -id default -connectionCheckUrl http://www.google.com/' +
                    ' -no-experimental -skip-release-history' +
                    ' -www ./output/stable -cap 2.107.999 -capCore 2.999 -stableCore'
        }
    }

    stage('Archive Update Site') {
        archive 'output/**/*.json, output/htaccess'
    }
}