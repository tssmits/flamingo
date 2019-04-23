timestamps {
    node {

        properties([
            [$class: 'jenkins.model.BuildDiscarderProperty', strategy: [$class: 'LogRotator',
                artifactDaysToKeepStr: '5',
                artifactNumToKeepStr: '5',
                daysToKeepStr: '15',
                numToKeepStr: '5']
            ]]);

        final def jdks = ['OpenJDK11','JDK8']

        stage("Prepare") {
             checkout scm
        }

        jdks.eachWithIndex { jdk, indexOfJdk ->
            final String jdkTestName = jdk.toString()

            withEnv(["JAVA_HOME=${ tool jdkTestName }", "PATH+MAVEN=${tool 'Maven 3.6.0'}/bin:${env.JAVA_HOME}/bin"]) {

                echo "Using JDK: ${jdkTestName}"

                stage("Build: ${jdkTestName}") {
                    echo "Building branch: ${env.BRANCH_NAME}"
                    sh "mvn clean install -U -DskipTests -Dtest.skip.integrationtests=true -B -V -fae -q"
                }

                stage("Test: ${jdkTestName}") {
                    echo "Running unit tests"
                    sh "mvn -e clean test -B"
                }

                try {
                    lock('flamingo-oracle') {
                      timeout(90) {
                        stage("Prepare Oracle: ${indexOfJdk}") {
                             sh "sqlplus -l -S C##JENKINS_FLAMINGO/jenkins_flamingo@192.168.1.11:1521/orcl < ./.jenkins/clear-oracle-schema.sql"
                        }

                        stage("IntegrationTest: ${jdkTestName}") {
                            echo "Running integration tests on all modules except viewer-admin"
                            sh "mvn -e verify -B -Pjenkins -pl '!viewer-admin'"

                            echo "Running integration tests on viewer-admin module only"
                            sh "mvn -e verify -B -Pjenkins -pl 'viewer-admin'"
                        }
                      }
                    }
                } finally {

                }
            }
        }

        stage('Publish Test Results') {
            junit allowEmptyResults: true, testResults: '**/target/surefire-reports/TEST-*.xml, **/target/failsafe-reports/TEST-*.xml'
        }
        stage('Publish Test Coverage results') {
            jacoco exclusionPattern: '**/*Test.class', execPattern: '**/target/**.exec'
        }

        withEnv(["JAVA_HOME=${ tool 'JDK8' }", "PATH+MAVEN=${tool 'Maven 3.6.0'}/bin:${env.JAVA_HOME}/bin"]) {
            stage('Check Javadocs') {
                sh "mvn javadoc:javadoc"
            }

            stage('Check Test Javadocs') {
                sh "mvn javadoc:test-javadoc"
            }

            stage('OWASP Dependency Check') {
                sh "mvn org.owasp:dependency-check-maven:aggregate -DskipSystemScope=true -DnodeAuditAnalyzerEnabled=false -DnodeAnalyzerEnabled=false -Dformat=XML -DsuppressionFile=./.mvn/owasp-suppression.xml"

                dependencyCheckPublisher canComputeNew: false, defaultEncoding: '', healthy: '85', pattern: '**/dependency-check-report.xml', shouldDetectModules: true, unHealthy: ''
            }
        }
    }
}
