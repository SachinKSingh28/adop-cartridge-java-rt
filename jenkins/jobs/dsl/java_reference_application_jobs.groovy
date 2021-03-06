// Folders
def workspaceFolderName = "${WORKSPACE_NAME}"
def projectFolderName = "${PROJECT_NAME}"

// Variables
def projectNameKey = projectFolderName.toLowerCase().replace("/", "-")
def referenceAppgitRepo = "spring-petclinic"
def regressionTestGitRepo = "adop-cartridge-java-regression-tests-rapid"
def referenceAppGitUrl = "ssh://jenkins@gerrit:29418/${PROJECT_NAME}/" + referenceAppgitRepo
def regressionTestGitUrl = "ssh://jenkins@gerrit:29418/${PROJECT_NAME}/" + regressionTestGitRepo

// Jobs
def buildAppJob = freeStyleJob(projectFolderName + "/Reference_Application_Build")
def unitTestJob = freeStyleJob(projectFolderName + "/Reference_Application_Unit_Tests")
def codeAnalysisJob = freeStyleJob(projectFolderName + "/Reference_Application_Code_Analysis")
def buildDockerImage = freeStyleJob(projectFolderName + "/Reference_Application_Build_Docker_Image")
def regressionTestBatch1Job = freeStyleJob(projectFolderName + "/Reference_Application_Regression_Tests_Batch1")
def regressionTestBatch2Job = freeStyleJob(projectFolderName + "/Reference_Application_Regression_Tests_Batch2")
def regressionTestBatch3Job = freeStyleJob(projectFolderName + "/Reference_Application_Regression_Tests_Batch3")
def regressionTestBatch4Job = freeStyleJob(projectFolderName + "/Reference_Application_Regression_Tests_Batch4")
def regressionTestBatch5Job = freeStyleJob(projectFolderName + "/Reference_Application_Regression_Tests_Batch5")
def performanceTestJob = freeStyleJob(projectFolderName + "/Reference_Application_Performance_Tests")
def securityTestJob = freeStyleJob(projectFolderName + "/Reference_Application_Security_Tests")
def functionalTestJob = freeStyleJob(projectFolderName + "/Reference_Application_Functional_Tests")
def rapidTestsMultiJob = multiJob(projectFolderName + "/Reference_Application_Rapid_Tests")
def deployToDemoJob = freeStyleJob(projectFolderName + "/Reference_Application_Deploy_Demo")
def deployJobToProdA = freeStyleJob(projectFolderName + "/Reference_Application_Deploy_ProdA")
def deployJobToProdB = freeStyleJob(projectFolderName + "/Reference_Application_Deploy_ProdB")

// Views
def pipelineView = buildPipelineView(projectFolderName + "/Java_Reference_Application")

pipelineView.with {
    title('Reference Application Pipeline')
    displayedBuilds(2)
    selectedJob(projectFolderName + "/Reference_Application_Build")
    showPipelineParameters()
    showPipelineDefinitionHeader()
    refreshFrequency(5)
}

buildAppJob.with {
    description("This job builds Java Spring reference application")
    logRotator{
        numToKeep(3)
    }
    wrappers {
        preBuildCleanup()
        injectPasswords()
        maskPasswords()
        sshAgent("adop-jenkins-master")
    }
    scm {
        git {
            remote {
                url(referenceAppGitUrl)
                credentials("adop-jenkins-master")
            }
            branch("*/master")
        }
    }
    environmentVariables {
        env('WORKSPACE_NAME', workspaceFolderName)
        env('PROJECT_NAME', projectFolderName)
    }
    label("java8")
    triggers {
        gerrit {
            events {
                refUpdated()
            }
            project(projectFolderName + '/' + referenceAppgitRepo, 'plain:master')
            configure { node ->
                node / serverName("ADOP Gerrit")
            }
        }
    }
    steps {
        maven {
            goals('clean install -DskipTests')
            mavenInstallation("ADOP Maven")
        }
    }
    publishers {
        archiveArtifacts("**/*")
        downstreamParameterized {
            trigger(projectFolderName + "/Reference_Application_Unit_Tests") {
                condition("UNSTABLE_OR_BETTER")
                parameters {
                    predefinedProp("B", '${BUILD_NUMBER}')
                    predefinedProp("PARENT_BUILD", '${JOB_NAME}')
                }
            }
        }
    }
}

unitTestJob.with {
    description("This job runs unit tests on Java Spring reference application.")
    parameters {
        stringParam("B", '', "Parent build number")
        stringParam("PARENT_BUILD", "Reference_Application_Build", "Parent build name")
    }
    logRotator{
        numToKeep(3)
    }
    wrappers {
        preBuildCleanup()
        injectPasswords()
        maskPasswords()
        sshAgent("adop-jenkins-master")
    }
    environmentVariables {
        env('WORKSPACE_NAME', workspaceFolderName)
        env('PROJECT_NAME', projectFolderName)
    }
    label("java8")
    steps {
        copyArtifacts("Reference_Application_Build") {
            buildSelector {
                buildNumber('${B}')
            }
        }
        maven {
            goals('clean test')
            mavenInstallation("ADOP Maven")
        }
    }
    publishers {
        archiveArtifacts("**/*")
        downstreamParameterized {
            trigger(projectFolderName + "/Reference_Application_Code_Analysis") {
                condition("UNSTABLE_OR_BETTER")
                parameters {
                    predefinedProp("B", '${B}')
                    predefinedProp("PARENT_BUILD", '${PARENT_BUILD}')
                }
            }
        }
    }
}

codeAnalysisJob.with {
    description("This job runs code quality analysis for Java reference application using SonarQube.")
    parameters {
        stringParam("B", '', "Parent build number")
        stringParam("PARENT_BUILD", "Reference_Application_Build", "Parent build name")
    }
    logRotator{
        numToKeep(3)
    }
    environmentVariables {
        env('WORKSPACE_NAME', workspaceFolderName)
        env('PROJECT_NAME', projectFolderName)
        env('PROJECT_NAME_KEY', projectNameKey)
    }
    wrappers {
        preBuildCleanup()
        injectPasswords()
        maskPasswords()
        sshAgent("adop-jenkins-master")
    }
    label("java8")
    steps {
        copyArtifacts('Reference_Application_Unit_Tests') {
            buildSelector {
                buildNumber('${B}')
            }
        }
    }
    configure { myProject ->
        myProject / builders << 'hudson.plugins.sonar.SonarRunnerBuilder'(plugin: "sonar@2.2.1") {
            project('sonar-project.properties')
            properties('''sonar.projectKey=${PROJECT_NAME_KEY}
sonar.projectName=${PROJECT_NAME}
sonar.projectVersion=1.0.${B}
sonar.sources=src/main/java
sonar.language=java
sonar.sourceEncoding=UTF-8
sonar.scm.enabled=false''')
            javaOpts()
            jdk('(Inherit From Job)')
            task()
        }
    }
    publishers {
        downstreamParameterized {
            trigger(projectFolderName + "/Reference_Application_Build_Docker_Image") {
                condition("UNSTABLE_OR_BETTER")
                parameters {
                    predefinedProp("B", '${B}')
                    predefinedProp("PARENT_BUILD", '${PARENT_BUILD}')
                }
            }
        }
    }
}

buildDockerImage.with {
    description("This job creates a docker image with java foss petclinic application on tomcat.")
    parameters {
        stringParam("B", '', "Parent build number")
        stringParam("PARENT_BUILD", "Reference_Application_Build", "Parent build name")
    }
    logRotator{
        numToKeep(3)
    }
    wrappers {
        preBuildCleanup()
        injectPasswords()
        maskPasswords()
        sshAgent("adop-jenkins-master")
    }
    environmentVariables {
        env('WORKSPACE_NAME', workspaceFolderName)
        env('PROJECT_NAME', projectFolderName)
    }
    label("docker")
    steps {
        copyArtifacts("Reference_Application_Build") {
            buildSelector {
                buildNumber('${B}')
                includePatterns('target/petclinic.war')
            }
        }
        shell('''set +x
                |echo "
                |FROM tomcat:8.0
                |ADD target/petclinic.war /usr/local/tomcat/webapps/
                |" > ${WORKSPACE}/Dockerfile
                |export REPO_NAME="$(echo ${PROJECT_NAME} | tr '/' '_' | tr '[:upper:]' '[:lower:]')"
                |docker build -t ${REPO_NAME}/adop-foss-java:0.0.${B} .
                |echo "New image has been build - ${REPO_NAME}/adop-foss-java:0.0.${B}"
                |set -x'''.stripMargin()
        )
    }
    publishers {
        downstreamParameterized {
            trigger(projectFolderName + "/Reference_Application_Rapid_Tests") {
                condition("UNSTABLE_OR_BETTER")
                parameters {
                    predefinedProp("B", '${B}')
                    predefinedProp("PARENT_BUILD", '${PARENT_BUILD}')
                }
            }
        }
    }
}

regressionTestBatch1Job.with {
    description("This job runs regression tests on deployed java application")
    parameters {
        stringParam("B", '', "Parent build number")
        stringParam("PARENT_BUILD", "Reference_Application_Build", "Parent build name")
    }
    logRotator{
        numToKeep(3)
    }
    scm {
        git {
            remote {
                url(regressionTestGitUrl)
                credentials("adop-jenkins-master")
            }
            branch("*/master")
        }
    }
    wrappers {
        preBuildCleanup()
        injectPasswords()
        maskPasswords()
        sshAgent("adop-jenkins-master")
    }
    environmentVariables {
        env('WORKSPACE_NAME', workspaceFolderName)
        env('PROJECT_NAME', projectFolderName)
    }
    label("java8")
    steps {
        shell('''
            |export SERVICE_NAME="ExampleWorkspace_RegressionTesting_Batch1"
            |echo "SERVICE_NAME=${SERVICE_NAME}" > env.properties
            |export REPO_NAME="$(echo ${PROJECT_NAME} | tr '/' '_' | tr '[:upper:]' '[:lower:]')"
            |echo "REPO_NAME=${REPO_NAME}" >> env.properties
            |docker run -d --net ${DOCKER_NETWORK_NAME} --name ${SERVICE_NAME} ${REPO_NAME}/adop-foss-java:0.0.${B}
            |APP_IP=$( docker inspect --format '{{ .NetworkSettings.Networks.'"$DOCKER_NETWORK_NAME"'.IPAddress }}' ${SERVICE_NAME} )
            |APP_URL=http://${APP_IP}:8080/petclinic
            |COUNT=1
            |while ! curl -q ${APP_URL} -o /dev/null
            |do
            |  if [ ${COUNT} -gt 10 ]; then
            |    echo "Docker build failed even after ${COUNT}. Please investigate."
            |    docker rm -f ${SERVICE_NAME}
            |    exit 1
            |  fi
            |  echo "Application is not up yet. Retrying ..Attempt (${COUNT})"
            |  sleep 5
            |  COUNT=$((COUNT+1))
            |done
            |
            |echo "Running automation tests"
            |echo "Setting values for container, project and app names"
            |CONTAINER_NAME="owasp_zap-"${SERVICE_NAME}${BUILD_NUMBER}-batch1
            |APP_IP=$( docker inspect --format '{{ .NetworkSettings.Networks.'"$DOCKER_NETWORK_NAME"'.IPAddress }}' ${SERVICE_NAME} )
            |APP_URL=http://${APP_IP}:8080/petclinic
            |ZAP_PORT="9090"
            |
            |echo CONTAINER_NAME=$CONTAINER_NAME >> env.properties
            |echo APP_URL=$APP_URL >> env.properties
            |echo ZAP_PORT=$ZAP_PORT >> env.properties
            |
            |echo "Starting OWASP ZAP Intercepting Proxy"
            |JOB_WORKSPACE_PATH="/var/lib/docker/volumes/jenkins_slave_home/_data/${PROJECT_NAME}/Reference_Application_Regression_Tests_Batch1"
            |#JOB_WORKSPACE_PATH="$(docker inspect --format '{{ .Mounts.Networks.'"$DOCKER_NETWORK_NAME"'.IPAddress }}' ${CONTAINER_NAME} )/${JOB_NAME}"
            |echo JOB_WORKSPACE_PATH=$JOB_WORKSPACE_PATH >> env.properties
            |mkdir -p ${JOB_WORKSPACE_PATH}/owasp_zap_proxy/test-results
            |docker run -it -d --net=$DOCKER_NETWORK_NAME -v ${JOB_WORKSPACE_PATH}/owasp_zap_proxy/test-results/:/opt/zaproxy/test-results/ --name ${CONTAINER_NAME} -P nhantd/owasp_zap start zap-test
            |
            |sleep 30s
            |ZAP_IP=$( docker inspect --format '{{ .NetworkSettings.Networks.'"$DOCKER_NETWORK_NAME"'.IPAddress }}' ${CONTAINER_NAME} )
            |echo "ZAP_IP =  $ZAP_IP"
            |echo ZAP_IP=$ZAP_IP >> env.properties
            |echo ZAP_ENABLED="true" >> env.properties
            |echo "Running Selenium tests through maven."
            |'''.stripMargin()
        )
        environmentVariables {
            propertiesFile('env.properties')
        }
        maven {
            goals('clean -B test -DPETCLINIC_URL=${APP_URL} -DZAP_IP=${ZAP_IP} -DZAP_PORT=${ZAP_PORT} -DZAP_ENABLED=${ZAP_ENABLED}  -Dcucumber.options="--tags @batch1"')
            mavenInstallation("ADOP Maven")
        }
        shell('''
            |echo "Stopping OWASP ZAP Proxy and generating report."
            |docker stop ${CONTAINER_NAME}
            |docker rm ${CONTAINER_NAME}
            |
            |docker run -i --net=$DOCKER_NETWORK_NAME -v ${JOB_WORKSPACE_PATH}/owasp_zap_proxy/test-results/:/opt/zaproxy/test-results/ --name ${CONTAINER_NAME} -P nhantd/owasp_zap stop zap-test
            |docker cp ${CONTAINER_NAME}:/opt/zaproxy/test-results/zap-test-report.html .
            |sleep 10s
            |docker rm ${CONTAINER_NAME}
            |
            |docker stop ${SERVICE_NAME}
            |docker rm ${SERVICE_NAME}
            |'''.stripMargin()
        )
    }
    configure { myProject ->
        myProject / 'publishers' << 'net.masterthought.jenkins.CucumberReportPublisher'(plugin: 'cucumber-reports@0.1.0') {
            jsonReportDirectory("")
            pluginUrlPath("")
            fileIncludePattern("")
            fileExcludePattern("")
            skippedFails("false")
            pendingFails("false")
            undefinedFails("false")
            missingFails("false")
            noFlashCharts("false")
            ignoreFailedTests("false")
            parallelTesting("false")
        }
    }
    publishers {
        publishHtml {
            report('$WORKSPACE') {
                reportName('ZAP security test report')
                reportFiles('zap-test-report.html')
            }
        }
    }
}

regressionTestBatch2Job.with {
    description("This job runs regression tests on deployed java application")
    parameters {
        stringParam("B", '', "Parent build number")
        stringParam("PARENT_BUILD", "Reference_Application_Build", "Parent build name")
    }
    logRotator{
        numToKeep(3)
    }
    scm {
        git {
            remote {
                url(regressionTestGitUrl)
                credentials("adop-jenkins-master")
            }
            branch("*/master")
        }
    }
    wrappers {
        preBuildCleanup()
        injectPasswords()
        maskPasswords()
        sshAgent("adop-jenkins-master")
    }
    environmentVariables {
        env('WORKSPACE_NAME', workspaceFolderName)
        env('PROJECT_NAME', projectFolderName)
    }
    label("java8")
    steps {
        shell('''
            |export SERVICE_NAME="ExampleWorkspace_RegressionTesting_Batch2"
            |echo "SERVICE_NAME=${SERVICE_NAME}" > env.properties
            |export REPO_NAME="$(echo ${PROJECT_NAME} | tr '/' '_' | tr '[:upper:]' '[:lower:]')"
            |echo "REPO_NAME=${REPO_NAME}" >> env.properties
            |docker run -d --net ${DOCKER_NETWORK_NAME} --name ${SERVICE_NAME} ${REPO_NAME}/adop-foss-java:0.0.${B}
            |APP_IP=$( docker inspect --format '{{ .NetworkSettings.Networks.'"$DOCKER_NETWORK_NAME"'.IPAddress }}' ${SERVICE_NAME} )
            |APP_URL=http://${APP_IP}:8080/petclinic
            |COUNT=1
            |while ! curl -q ${APP_URL} -o /dev/null
            |do
            |  if [ ${COUNT} -gt 10 ]; then
            |    echo "Docker build failed even after ${COUNT}. Please investigate."
            |    docker rm -f ${SERVICE_NAME}
            |    exit 1
            |  fi
            |  echo "Application is not up yet. Retrying ..Attempt (${COUNT})"
            |  sleep 5
            |  COUNT=$((COUNT+1))
            |done
            |
            |echo "Running automation tests"
            |echo "Setting values for container, project and app names"
            |CONTAINER_NAME="owasp_zap-"${SERVICE_NAME}${BUILD_NUMBER}-batch2
            |APP_IP=$( docker inspect --format '{{ .NetworkSettings.Networks.'"$DOCKER_NETWORK_NAME"'.IPAddress }}' ${SERVICE_NAME} )
            |APP_URL=http://${APP_IP}:8080/petclinic
            |ZAP_PORT="9090"
            |
            |echo CONTAINER_NAME=$CONTAINER_NAME >> env.properties
            |echo APP_URL=$APP_URL >> env.properties
            |echo ZAP_PORT=$ZAP_PORT >> env.properties
            |
            |echo "Starting OWASP ZAP Intercepting Proxy"
            |JOB_WORKSPACE_PATH="/var/lib/docker/volumes/jenkins_slave_home/_data/${PROJECT_NAME}/Reference_Application_Regression_Tests_Batch2"
            |#JOB_WORKSPACE_PATH="$(docker inspect --format '{{ .Mounts.Networks.'"$DOCKER_NETWORK_NAME"'.IPAddress }}' ${CONTAINER_NAME} )/${JOB_NAME}"
            |echo JOB_WORKSPACE_PATH=$JOB_WORKSPACE_PATH >> env.properties
            |mkdir -p ${JOB_WORKSPACE_PATH}/owasp_zap_proxy/test-results
            |docker run -it -d --net=$DOCKER_NETWORK_NAME -v ${JOB_WORKSPACE_PATH}/owasp_zap_proxy/test-results/:/opt/zaproxy/test-results/ --name ${CONTAINER_NAME} -P nhantd/owasp_zap start zap-test
            |
            |sleep 30s
            |ZAP_IP=$( docker inspect --format '{{ .NetworkSettings.Networks.'"$DOCKER_NETWORK_NAME"'.IPAddress }}' ${CONTAINER_NAME} )
            |echo "ZAP_IP =  $ZAP_IP"
            |echo ZAP_IP=$ZAP_IP >> env.properties
            |echo ZAP_ENABLED="true" >> env.properties
            |echo "Running Selenium tests through maven."
            |'''.stripMargin()
        )
        environmentVariables {
            propertiesFile('env.properties')
        }
        maven {
            goals('clean -B test -DPETCLINIC_URL=${APP_URL} -DZAP_IP=${ZAP_IP} -DZAP_PORT=${ZAP_PORT} -DZAP_ENABLED=${ZAP_ENABLED}  -Dcucumber.options="--tags @batch2"')
            mavenInstallation("ADOP Maven")
        }
        shell('''
            |echo "Stopping OWASP ZAP Proxy and generating report."
            |docker stop ${CONTAINER_NAME}
            |docker rm ${CONTAINER_NAME}
            |
            |docker run -i --net=$DOCKER_NETWORK_NAME -v ${JOB_WORKSPACE_PATH}/owasp_zap_proxy/test-results/:/opt/zaproxy/test-results/ --name ${CONTAINER_NAME} -P nhantd/owasp_zap stop zap-test
            |docker cp ${CONTAINER_NAME}:/opt/zaproxy/test-results/zap-test-report.html .
            |sleep 10s
            |docker rm ${CONTAINER_NAME}
            |
            |docker stop ${SERVICE_NAME}
            |docker rm ${SERVICE_NAME}
            |'''.stripMargin()
        )
    }
    configure { myProject ->
        myProject / 'publishers' << 'net.masterthought.jenkins.CucumberReportPublisher'(plugin: 'cucumber-reports@0.1.0') {
            jsonReportDirectory("")
            pluginUrlPath("")
            fileIncludePattern("")
            fileExcludePattern("")
            skippedFails("false")
            pendingFails("false")
            undefinedFails("false")
            missingFails("false")
            noFlashCharts("false")
            ignoreFailedTests("false")
            parallelTesting("false")
        }
    }
    publishers {
        publishHtml {
            report('$WORKSPACE') {
                reportName('ZAP security test report')
                reportFiles('zap-test-report.html')
            }
        }
    }
}

regressionTestBatch3Job.with {
    description("This job runs regression tests on deployed java application")
    parameters {
        stringParam("B", '', "Parent build number")
        stringParam("PARENT_BUILD", "Reference_Application_Build", "Parent build name")
    }
    logRotator{
        numToKeep(3)
    }
    scm {
        git {
            remote {
                url(regressionTestGitUrl)
                credentials("adop-jenkins-master")
            }
            branch("*/master")
        }
    }
    wrappers {
        preBuildCleanup()
        injectPasswords()
        maskPasswords()
        sshAgent("adop-jenkins-master")
    }
    environmentVariables {
        env('WORKSPACE_NAME', workspaceFolderName)
        env('PROJECT_NAME', projectFolderName)
    }
    label("java8")
    steps {
        shell('''
            |export SERVICE_NAME="ExampleWorkspace_RegressionTesting_Batch3"
            |echo "SERVICE_NAME=${SERVICE_NAME}" > env.properties
            |export REPO_NAME="$(echo ${PROJECT_NAME} | tr '/' '_' | tr '[:upper:]' '[:lower:]')"
            |echo "REPO_NAME=${REPO_NAME}" >> env.properties
            |docker run -d --net ${DOCKER_NETWORK_NAME} --name ${SERVICE_NAME} ${REPO_NAME}/adop-foss-java:0.0.${B}
            |APP_IP=$( docker inspect --format '{{ .NetworkSettings.Networks.'"$DOCKER_NETWORK_NAME"'.IPAddress }}' ${SERVICE_NAME} )
            |APP_URL=http://${APP_IP}:8080/petclinic
            |COUNT=1
            |while ! curl -q ${APP_URL} -o /dev/null
            |do
            |  if [ ${COUNT} -gt 10 ]; then
            |    echo "Docker build failed even after ${COUNT}. Please investigate."
            |    docker rm -f ${SERVICE_NAME}
            |    exit 1
            |  fi
            |  echo "Application is not up yet. Retrying ..Attempt (${COUNT})"
            |  sleep 5
            |  COUNT=$((COUNT+1))
            |done
            |
            |echo "Running automation tests"
            |echo "Setting values for container, project and app names"
            |CONTAINER_NAME="owasp_zap-"${SERVICE_NAME}${BUILD_NUMBER}-batch3
            |APP_IP=$( docker inspect --format '{{ .NetworkSettings.Networks.'"$DOCKER_NETWORK_NAME"'.IPAddress }}' ${SERVICE_NAME} )
            |APP_URL=http://${APP_IP}:8080/petclinic
            |ZAP_PORT="9090"
            |
            |echo CONTAINER_NAME=$CONTAINER_NAME >> env.properties
            |echo APP_URL=$APP_URL >> env.properties
            |echo ZAP_PORT=$ZAP_PORT >> env.properties
            |
            |echo "Starting OWASP ZAP Intercepting Proxy"
            |JOB_WORKSPACE_PATH="/var/lib/docker/volumes/jenkins_slave_home/_data/${PROJECT_NAME}/Reference_Application_Regression_Tests_Batch3"
            |#JOB_WORKSPACE_PATH="$(docker inspect --format '{{ .Mounts.Networks.'"$DOCKER_NETWORK_NAME"'.IPAddress }}' ${CONTAINER_NAME} )/${JOB_NAME}"
            |echo JOB_WORKSPACE_PATH=$JOB_WORKSPACE_PATH >> env.properties
            |mkdir -p ${JOB_WORKSPACE_PATH}/owasp_zap_proxy/test-results
            |docker run -it -d --net=$DOCKER_NETWORK_NAME -v ${JOB_WORKSPACE_PATH}/owasp_zap_proxy/test-results/:/opt/zaproxy/test-results/ --name ${CONTAINER_NAME} -P nhantd/owasp_zap start zap-test
            |
            |sleep 30s
            |ZAP_IP=$( docker inspect --format '{{ .NetworkSettings.Networks.'"$DOCKER_NETWORK_NAME"'.IPAddress }}' ${CONTAINER_NAME} )
            |echo "ZAP_IP =  $ZAP_IP"
            |echo ZAP_IP=$ZAP_IP >> env.properties
            |echo ZAP_ENABLED="true" >> env.properties
            |echo "Running Selenium tests through maven."
            |'''.stripMargin()
        )
        environmentVariables {
            propertiesFile('env.properties')
        }
        maven {
            goals('clean -B test -DPETCLINIC_URL=${APP_URL} -DZAP_IP=${ZAP_IP} -DZAP_PORT=${ZAP_PORT} -DZAP_ENABLED=${ZAP_ENABLED}  -Dcucumber.options="--tags @batch3"')
            mavenInstallation("ADOP Maven")
        }
        shell('''
            |echo "Stopping OWASP ZAP Proxy and generating report."
            |docker stop ${CONTAINER_NAME}
            |docker rm ${CONTAINER_NAME}
            |
            |docker run -i --net=$DOCKER_NETWORK_NAME -v ${JOB_WORKSPACE_PATH}/owasp_zap_proxy/test-results/:/opt/zaproxy/test-results/ --name ${CONTAINER_NAME} -P nhantd/owasp_zap stop zap-test
            |docker cp ${CONTAINER_NAME}:/opt/zaproxy/test-results/zap-test-report.html .
            |sleep 10s
            |docker rm ${CONTAINER_NAME}
            |
            |docker stop ${SERVICE_NAME}
            |docker rm ${SERVICE_NAME}
            |'''.stripMargin()
        )
    }
    configure { myProject ->
        myProject / 'publishers' << 'net.masterthought.jenkins.CucumberReportPublisher'(plugin: 'cucumber-reports@0.1.0') {
            jsonReportDirectory("")
            pluginUrlPath("")
            fileIncludePattern("")
            fileExcludePattern("")
            skippedFails("false")
            pendingFails("false")
            undefinedFails("false")
            missingFails("false")
            noFlashCharts("false")
            ignoreFailedTests("false")
            parallelTesting("false")
        }
    }
    publishers {
        publishHtml {
            report('$WORKSPACE') {
                reportName('ZAP security test report')
                reportFiles('zap-test-report.html')
            }
        }
    }
}

regressionTestBatch4Job.with {
    description("This job runs regression tests on deployed java application")
    parameters {
        stringParam("B", '', "Parent build number")
        stringParam("PARENT_BUILD", "Reference_Application_Build", "Parent build name")
    }
    logRotator{
        numToKeep(3)
    }
    scm {
        git {
            remote {
                url(regressionTestGitUrl)
                credentials("adop-jenkins-master")
            }
            branch("*/master")
        }
    }
    wrappers {
        preBuildCleanup()
        injectPasswords()
        maskPasswords()
        sshAgent("adop-jenkins-master")
    }
    environmentVariables {
        env('WORKSPACE_NAME', workspaceFolderName)
        env('PROJECT_NAME', projectFolderName)
    }
    label("java8")
    steps {
        shell('''
            |export SERVICE_NAME="ExampleWorkspace_RegressionTesting_Batch4"
            |echo "SERVICE_NAME=${SERVICE_NAME}" > env.properties
            |export REPO_NAME="$(echo ${PROJECT_NAME} | tr '/' '_' | tr '[:upper:]' '[:lower:]')"
            |echo "REPO_NAME=${REPO_NAME}" >> env.properties
            |docker run -d --net ${DOCKER_NETWORK_NAME} --name ${SERVICE_NAME} ${REPO_NAME}/adop-foss-java:0.0.${B}
            |APP_IP=$( docker inspect --format '{{ .NetworkSettings.Networks.'"$DOCKER_NETWORK_NAME"'.IPAddress }}' ${SERVICE_NAME} )
            |APP_URL=http://${APP_IP}:8080/petclinic
            |COUNT=1
            |while ! curl -q ${APP_URL} -o /dev/null
            |do
            |  if [ ${COUNT} -gt 10 ]; then
            |    echo "Docker build failed even after ${COUNT}. Please investigate."
            |    docker rm -f ${SERVICE_NAME}
            |    exit 1
            |  fi
            |  echo "Application is not up yet. Retrying ..Attempt (${COUNT})"
            |  sleep 5
            |  COUNT=$((COUNT+1))
            |done
            |
            |echo "Running automation tests"
            |echo "Setting values for container, project and app names"
            |CONTAINER_NAME="owasp_zap-"${SERVICE_NAME}${BUILD_NUMBER}-batch4
            |APP_IP=$( docker inspect --format '{{ .NetworkSettings.Networks.'"$DOCKER_NETWORK_NAME"'.IPAddress }}' ${SERVICE_NAME} )
            |APP_URL=http://${APP_IP}:8080/petclinic
            |ZAP_PORT="9090"
            |
            |echo CONTAINER_NAME=$CONTAINER_NAME >> env.properties
            |echo APP_URL=$APP_URL >> env.properties
            |echo ZAP_PORT=$ZAP_PORT >> env.properties
            |
            |echo "Starting OWASP ZAP Intercepting Proxy"
            |JOB_WORKSPACE_PATH="/var/lib/docker/volumes/jenkins_slave_home/_data/${PROJECT_NAME}/Reference_Application_Regression_Tests_Batch4"
            |#JOB_WORKSPACE_PATH="$(docker inspect --format '{{ .Mounts.Networks.'"$DOCKER_NETWORK_NAME"'.IPAddress }}' ${CONTAINER_NAME} )/${JOB_NAME}"
            |echo JOB_WORKSPACE_PATH=$JOB_WORKSPACE_PATH >> env.properties
            |mkdir -p ${JOB_WORKSPACE_PATH}/owasp_zap_proxy/test-results
            |docker run -it -d --net=$DOCKER_NETWORK_NAME -v ${JOB_WORKSPACE_PATH}/owasp_zap_proxy/test-results/:/opt/zaproxy/test-results/ --name ${CONTAINER_NAME} -P nhantd/owasp_zap start zap-test
            |
            |sleep 30s
            |ZAP_IP=$( docker inspect --format '{{ .NetworkSettings.Networks.'"$DOCKER_NETWORK_NAME"'.IPAddress }}' ${CONTAINER_NAME} )
            |echo "ZAP_IP =  $ZAP_IP"
            |echo ZAP_IP=$ZAP_IP >> env.properties
            |echo ZAP_ENABLED="true" >> env.properties
            |echo "Running Selenium tests through maven."
            |'''.stripMargin()
        )
        environmentVariables {
            propertiesFile('env.properties')
        }
        maven {
            goals('clean -B test -DPETCLINIC_URL=${APP_URL} -DZAP_IP=${ZAP_IP} -DZAP_PORT=${ZAP_PORT} -DZAP_ENABLED=${ZAP_ENABLED}  -Dcucumber.options="--tags @batch4"')
            mavenInstallation("ADOP Maven")
        }
        shell('''
            |echo "Stopping OWASP ZAP Proxy and generating report."
            |docker stop ${CONTAINER_NAME}
            |docker rm ${CONTAINER_NAME}
            |
            |docker run -i --net=$DOCKER_NETWORK_NAME -v ${JOB_WORKSPACE_PATH}/owasp_zap_proxy/test-results/:/opt/zaproxy/test-results/ --name ${CONTAINER_NAME} -P nhantd/owasp_zap stop zap-test
            |docker cp ${CONTAINER_NAME}:/opt/zaproxy/test-results/zap-test-report.html .
            |sleep 10s
            |docker rm ${CONTAINER_NAME}
            |
            |docker stop ${SERVICE_NAME}
            |docker rm ${SERVICE_NAME}
            |'''.stripMargin()
        )
    }
    configure { myProject ->
        myProject / 'publishers' << 'net.masterthought.jenkins.CucumberReportPublisher'(plugin: 'cucumber-reports@0.1.0') {
            jsonReportDirectory("")
            pluginUrlPath("")
            fileIncludePattern("")
            fileExcludePattern("")
            skippedFails("false")
            pendingFails("false")
            undefinedFails("false")
            missingFails("false")
            noFlashCharts("false")
            ignoreFailedTests("false")
            parallelTesting("false")
        }
    }
    publishers {
        publishHtml {
            report('$WORKSPACE') {
                reportName('ZAP security test report')
                reportFiles('zap-test-report.html')
            }
        }
    }
}

regressionTestBatch5Job.with {
    description("This job runs regression tests on deployed java application")
    parameters {
        stringParam("B", '', "Parent build number")
        stringParam("PARENT_BUILD", "Reference_Application_Build", "Parent build name")
    }
    logRotator{
        numToKeep(3)
    }
    scm {
        git {
            remote {
                url(regressionTestGitUrl)
                credentials("adop-jenkins-master")
            }
            branch("*/master")
        }
    }
    wrappers {
        preBuildCleanup()
        injectPasswords()
        maskPasswords()
        sshAgent("adop-jenkins-master")
    }
    environmentVariables {
        env('WORKSPACE_NAME', workspaceFolderName)
        env('PROJECT_NAME', projectFolderName)
    }
    label("java8")
    steps {
        shell('''
            |export SERVICE_NAME="ExampleWorkspace_RegressionTesting_Batch5"
            |echo "SERVICE_NAME=${SERVICE_NAME}" > env.properties
            |export REPO_NAME="$(echo ${PROJECT_NAME} | tr '/' '_' | tr '[:upper:]' '[:lower:]')"
            |echo "REPO_NAME=${REPO_NAME}" >> env.properties
            |docker run -d --net ${DOCKER_NETWORK_NAME} --name ${SERVICE_NAME} ${REPO_NAME}/adop-foss-java:0.0.${B}
            |APP_IP=$( docker inspect --format '{{ .NetworkSettings.Networks.'"$DOCKER_NETWORK_NAME"'.IPAddress }}' ${SERVICE_NAME} )
            |APP_URL=http://${APP_IP}:8080/petclinic
            |COUNT=1
            |while ! curl -q ${APP_URL} -o /dev/null
            |do
            |  if [ ${COUNT} -gt 10 ]; then
            |    echo "Docker build failed even after ${COUNT}. Please investigate."
            |    docker rm -f ${SERVICE_NAME}
            |    exit 1
            |  fi
            |  echo "Application is not up yet. Retrying ..Attempt (${COUNT})"
            |  sleep 5
            |  COUNT=$((COUNT+1))
            |done
            |
            |echo "Running automation tests"
            |echo "Setting values for container, project and app names"
            |CONTAINER_NAME="owasp_zap-"${SERVICE_NAME}${BUILD_NUMBER}-batch5
            |APP_IP=$( docker inspect --format '{{ .NetworkSettings.Networks.'"$DOCKER_NETWORK_NAME"'.IPAddress }}' ${SERVICE_NAME} )
            |APP_URL=http://${APP_IP}:8080/petclinic
            |ZAP_PORT="9090"
            |
            |echo CONTAINER_NAME=$CONTAINER_NAME >> env.properties
            |echo APP_URL=$APP_URL >> env.properties
            |echo ZAP_PORT=$ZAP_PORT >> env.properties
            |
            |echo "Starting OWASP ZAP Intercepting Proxy"
            |JOB_WORKSPACE_PATH="/var/lib/docker/volumes/jenkins_slave_home/_data/${PROJECT_NAME}/Reference_Application_Regression_Tests_Batch5"
            |#JOB_WORKSPACE_PATH="$(docker inspect --format '{{ .Mounts.Networks.'"$DOCKER_NETWORK_NAME"'.IPAddress }}' ${CONTAINER_NAME} )/${JOB_NAME}"
            |echo JOB_WORKSPACE_PATH=$JOB_WORKSPACE_PATH >> env.properties
            |mkdir -p ${JOB_WORKSPACE_PATH}/owasp_zap_proxy/test-results
            |docker run -it -d --net=$DOCKER_NETWORK_NAME -v ${JOB_WORKSPACE_PATH}/owasp_zap_proxy/test-results/:/opt/zaproxy/test-results/ --name ${CONTAINER_NAME} -P nhantd/owasp_zap start zap-test
            |
            |sleep 30s
            |ZAP_IP=$( docker inspect --format '{{ .NetworkSettings.Networks.'"$DOCKER_NETWORK_NAME"'.IPAddress }}' ${CONTAINER_NAME} )
            |echo "ZAP_IP =  $ZAP_IP"
            |echo ZAP_IP=$ZAP_IP >> env.properties
            |echo ZAP_ENABLED="true" >> env.properties
            |echo "Running Selenium tests through maven."
            |'''.stripMargin()
        )
        environmentVariables {
            propertiesFile('env.properties')
        }
        maven {
            goals('clean -B test -DPETCLINIC_URL=${APP_URL} -DZAP_IP=${ZAP_IP} -DZAP_PORT=${ZAP_PORT} -DZAP_ENABLED=${ZAP_ENABLED}  -Dcucumber.options="--tags @batch5"')
            mavenInstallation("ADOP Maven")
        }
        shell('''
            |echo "Stopping OWASP ZAP Proxy and generating report."
            |docker stop ${CONTAINER_NAME}
            |docker rm ${CONTAINER_NAME}
            |
            |docker run -i --net=$DOCKER_NETWORK_NAME -v ${JOB_WORKSPACE_PATH}/owasp_zap_proxy/test-results/:/opt/zaproxy/test-results/ --name ${CONTAINER_NAME} -P nhantd/owasp_zap stop zap-test
            |docker cp ${CONTAINER_NAME}:/opt/zaproxy/test-results/zap-test-report.html .
            |sleep 10s
            |docker rm ${CONTAINER_NAME}
            |
            |docker stop ${SERVICE_NAME}
            |docker rm ${SERVICE_NAME}
            |'''.stripMargin()
        )
    }
    configure { myProject ->
        myProject / 'publishers' << 'net.masterthought.jenkins.CucumberReportPublisher'(plugin: 'cucumber-reports@0.1.0') {
            jsonReportDirectory("")
            pluginUrlPath("")
            fileIncludePattern("")
            fileExcludePattern("")
            skippedFails("false")
            pendingFails("false")
            undefinedFails("false")
            missingFails("false")
            noFlashCharts("false")
            ignoreFailedTests("false")
            parallelTesting("false")
        }
    }
    publishers {
        publishHtml {
            report('$WORKSPACE') {
                reportName('ZAP security test report')
                reportFiles('zap-test-report.html')
            }
        }
    }
}

functionalTestJob.with {
    description("This job runs regression tests on deployed java application")
    parameters {
        stringParam("B", '', "Parent build number")
        stringParam("PARENT_BUILD", "Reference_Application_Build", "Parent build name")
    }
    logRotator{
        numToKeep(3)
    }
    scm {
        git {
            remote {
                url(regressionTestGitUrl)
                credentials("adop-jenkins-master")
            }
            branch("*/master")
        }
    }
    wrappers {
        preBuildCleanup()
        injectPasswords()
        maskPasswords()
        sshAgent("adop-jenkins-master")
    }
    environmentVariables {
        env('WORKSPACE_NAME', workspaceFolderName)
        env('PROJECT_NAME', projectFolderName)
    }
    label("java8")
    steps {
        shell('''
            |export SERVICE_NAME="ExampleWorkspace_Functional_Tests"
            |echo "SERVICE_NAME=${SERVICE_NAME}" > env.properties
            |export REPO_NAME="$(echo ${PROJECT_NAME} | tr '/' '_' | tr '[:upper:]' '[:lower:]')"
            |echo "REPO_NAME=${REPO_NAME}" >> env.properties
            |docker run -d --net ${DOCKER_NETWORK_NAME} --name ${SERVICE_NAME} ${REPO_NAME}/adop-foss-java:0.0.${B}
            |APP_IP=$( docker inspect --format '{{ .NetworkSettings.Networks.'"$DOCKER_NETWORK_NAME"'.IPAddress }}' ${SERVICE_NAME} )
            |APP_URL=http://${APP_IP}:8080/petclinic
            |COUNT=1
            |while ! curl -q ${APP_URL} -o /dev/null
            |do
            |  if [ ${COUNT} -gt 10 ]; then
            |    echo "Docker build failed even after ${COUNT}. Please investigate."
            |    docker rm -f ${SERVICE_NAME}
            |    exit 1
            |  fi
            |  echo "Application is not up yet. Retrying ..Attempt (${COUNT})"
            |  sleep 5
            |  COUNT=$((COUNT+1))
            |done
            |
            |echo "Running automation tests"
            |echo "Setting values for container, project and app names"
            |CONTAINER_NAME="owasp_zap-"${SERVICE_NAME}${BUILD_NUMBER}-FT
            |APP_IP=$( docker inspect --format '{{ .NetworkSettings.Networks.'"$DOCKER_NETWORK_NAME"'.IPAddress }}' ${SERVICE_NAME} )
            |APP_URL=http://${APP_IP}:8080/petclinic
            |ZAP_PORT="9090"
            |
            |echo CONTAINER_NAME=$CONTAINER_NAME >> env.properties
            |echo APP_URL=$APP_URL >> env.properties
            |echo ZAP_PORT=$ZAP_PORT >> env.properties
            |
            |echo "Starting OWASP ZAP Intercepting Proxy"
            |JOB_WORKSPACE_PATH="/var/lib/docker/volumes/jenkins_slave_home/_data/${PROJECT_NAME}/Reference_Application_Functional_Tests"
            |#JOB_WORKSPACE_PATH="$(docker inspect --format '{{ .Mounts.Networks.'"$DOCKER_NETWORK_NAME"'.IPAddress }}' ${CONTAINER_NAME} )/${JOB_NAME}"
            |echo JOB_WORKSPACE_PATH=$JOB_WORKSPACE_PATH >> env.properties
            |mkdir -p ${JOB_WORKSPACE_PATH}/owasp_zap_proxy/test-results
            |docker run -it -d --net=$DOCKER_NETWORK_NAME -v ${JOB_WORKSPACE_PATH}/owasp_zap_proxy/test-results/:/opt/zaproxy/test-results/ --name ${CONTAINER_NAME} -P nhantd/owasp_zap start zap-test
            |
            |sleep 30s
            |ZAP_IP=$( docker inspect --format '{{ .NetworkSettings.Networks.'"$DOCKER_NETWORK_NAME"'.IPAddress }}' ${CONTAINER_NAME} )
            |echo "ZAP_IP =  $ZAP_IP"
            |echo ZAP_IP=$ZAP_IP >> env.properties
            |echo ZAP_ENABLED="true" >> env.properties
            |echo "Running Selenium tests through maven."
            |'''.stripMargin()
        )
        environmentVariables {
            propertiesFile('env.properties')
        }
        maven {
            goals('clean -B test -DPETCLINIC_URL=${APP_URL} -DZAP_IP=${ZAP_IP} -DZAP_PORT=${ZAP_PORT} -DZAP_ENABLED=${ZAP_ENABLED}  -Dcucumber.options="--tags @batch1"')
            mavenInstallation("ADOP Maven")
        }
        shell('''
            |echo "Stopping OWASP ZAP Proxy and generating report."
            |docker stop ${CONTAINER_NAME}
            |docker rm ${CONTAINER_NAME}
            |
            |docker run -i --net=$DOCKER_NETWORK_NAME -v ${JOB_WORKSPACE_PATH}/owasp_zap_proxy/test-results/:/opt/zaproxy/test-results/ --name ${CONTAINER_NAME} -P nhantd/owasp_zap stop zap-test
            |docker cp ${CONTAINER_NAME}:/opt/zaproxy/test-results/zap-test-report.html .
            |sleep 10s
            |docker rm ${CONTAINER_NAME}
            |
            |docker stop ${SERVICE_NAME}
            |docker rm ${SERVICE_NAME}
            |'''.stripMargin()
        )
    }
    configure { myProject ->
        myProject / 'publishers' << 'net.masterthought.jenkins.CucumberReportPublisher'(plugin: 'cucumber-reports@0.1.0') {
            jsonReportDirectory("")
            pluginUrlPath("")
            fileIncludePattern("")
            fileExcludePattern("")
            skippedFails("false")
            pendingFails("false")
            undefinedFails("false")
            missingFails("false")
            noFlashCharts("false")
            ignoreFailedTests("false")
            parallelTesting("false")
        }
    }
    publishers {
        publishHtml {
            report('$WORKSPACE') {
                reportName('ZAP security test report')
                reportFiles('zap-test-report.html')
            }
        }
    }
}

securityTestJob.with {
    description("This job runs regression tests on deployed java application")
    parameters {
        stringParam("B", '', "Parent build number")
        stringParam("PARENT_BUILD", "Reference_Application_Build", "Parent build name")
    }
    logRotator{
        numToKeep(3)
    }
    scm {
        git {
            remote {
                url(regressionTestGitUrl)
                credentials("adop-jenkins-master")
            }
            branch("*/master")
        }
    }
    wrappers {
        preBuildCleanup()
        injectPasswords()
        maskPasswords()
        sshAgent("adop-jenkins-master")
    }
    environmentVariables {
        env('WORKSPACE_NAME', workspaceFolderName)
        env('PROJECT_NAME', projectFolderName)
    }
    label("java8")
    steps {
        shell('''
            |export SERVICE_NAME="ExampleWorkspace_Security_Tests"
            |echo "SERVICE_NAME=${SERVICE_NAME}" > env.properties
            |export REPO_NAME="$(echo ${PROJECT_NAME} | tr '/' '_' | tr '[:upper:]' '[:lower:]')"
            |echo "REPO_NAME=${REPO_NAME}" >> env.properties
            |docker run -d --net ${DOCKER_NETWORK_NAME} --name ${SERVICE_NAME} ${REPO_NAME}/adop-foss-java:0.0.${B}
            |APP_IP=$( docker inspect --format '{{ .NetworkSettings.Networks.'"$DOCKER_NETWORK_NAME"'.IPAddress }}' ${SERVICE_NAME} )
            |APP_URL=http://${APP_IP}:8080/petclinic
            |COUNT=1
            |while ! curl -q ${APP_URL} -o /dev/null
            |do
            |  if [ ${COUNT} -gt 10 ]; then
            |    echo "Docker build failed even after ${COUNT}. Please investigate."
            |    docker rm -f ${SERVICE_NAME}
            |    exit 1
            |  fi
            |  echo "Application is not up yet. Retrying ..Attempt (${COUNT})"
            |  sleep 5
            |  COUNT=$((COUNT+1))
            |done
            |
            |echo "Running automation tests"
            |echo "Setting values for container, project and app names"
            |CONTAINER_NAME="owasp_zap-"${SERVICE_NAME}${BUILD_NUMBER}-FT
            |APP_IP=$( docker inspect --format '{{ .NetworkSettings.Networks.'"$DOCKER_NETWORK_NAME"'.IPAddress }}' ${SERVICE_NAME} )
            |APP_URL=http://${APP_IP}:8080/petclinic
            |ZAP_PORT="9090"
            |
            |echo CONTAINER_NAME=$CONTAINER_NAME >> env.properties
            |echo APP_URL=$APP_URL >> env.properties
            |echo ZAP_PORT=$ZAP_PORT >> env.properties
            |
            |echo "Starting OWASP ZAP Intercepting Proxy"
            |JOB_WORKSPACE_PATH="/var/lib/docker/volumes/jenkins_slave_home/_data/${PROJECT_NAME}/Reference_Application_Security_Tests"
            |#JOB_WORKSPACE_PATH="$(docker inspect --format '{{ .Mounts.Networks.'"$DOCKER_NETWORK_NAME"'.IPAddress }}' ${CONTAINER_NAME} )/${JOB_NAME}"
            |echo JOB_WORKSPACE_PATH=$JOB_WORKSPACE_PATH >> env.properties
            |mkdir -p ${JOB_WORKSPACE_PATH}/owasp_zap_proxy/test-results
            |docker run -it -d --net=$DOCKER_NETWORK_NAME -v ${JOB_WORKSPACE_PATH}/owasp_zap_proxy/test-results/:/opt/zaproxy/test-results/ --name ${CONTAINER_NAME} -P nhantd/owasp_zap start zap-test
            |
            |sleep 30s
            |ZAP_IP=$( docker inspect --format '{{ .NetworkSettings.Networks.'"$DOCKER_NETWORK_NAME"'.IPAddress }}' ${CONTAINER_NAME} )
            |echo "ZAP_IP =  $ZAP_IP"
            |echo ZAP_IP=$ZAP_IP >> env.properties
            |echo ZAP_ENABLED="true" >> env.properties
            |echo "Running Selenium tests through maven."
            |'''.stripMargin()
        )
        environmentVariables {
            propertiesFile('env.properties')
        }
        maven {
            goals('clean -B test -DPETCLINIC_URL=${APP_URL} -DZAP_IP=${ZAP_IP} -DZAP_PORT=${ZAP_PORT} -DZAP_ENABLED=${ZAP_ENABLED}  -Dcucumber.options="--tags @batch1"')
            mavenInstallation("ADOP Maven")
        }
        shell('''
            |echo "Stopping OWASP ZAP Proxy and generating report."
            |docker stop ${CONTAINER_NAME}
            |docker rm ${CONTAINER_NAME}
            |
            |docker run -i --net=$DOCKER_NETWORK_NAME -v ${JOB_WORKSPACE_PATH}/owasp_zap_proxy/test-results/:/opt/zaproxy/test-results/ --name ${CONTAINER_NAME} -P nhantd/owasp_zap stop zap-test
            |docker cp ${CONTAINER_NAME}:/opt/zaproxy/test-results/zap-test-report.html .
            |sleep 10s
            |docker rm ${CONTAINER_NAME}
            |
            |docker stop ${SERVICE_NAME}
            |docker rm ${SERVICE_NAME}
            |'''.stripMargin()
        )
    }
    configure { myProject ->
        myProject / 'publishers' << 'net.masterthought.jenkins.CucumberReportPublisher'(plugin: 'cucumber-reports@0.1.0') {
            jsonReportDirectory("")
            pluginUrlPath("")
            fileIncludePattern("")
            fileExcludePattern("")
            skippedFails("false")
            pendingFails("false")
            undefinedFails("false")
            missingFails("false")
            noFlashCharts("false")
            ignoreFailedTests("false")
            parallelTesting("false")
        }
    }
    publishers {
        publishHtml {
            report('$WORKSPACE') {
                reportName('ZAP security test report')
                reportFiles('zap-test-report.html')
            }
        }
    }
}

performanceTestJob.with {
    description("This job run the Jmeter test for the java reference application")
    parameters {
        stringParam("B", '', "Parent build number")
        stringParam("PARENT_BUILD", "Reference_Application_Regression_Tests", "Parent build name")
    }
    logRotator{
        numToKeep(3)
    }
    wrappers {
        preBuildCleanup()
        injectPasswords()
        maskPasswords()
        sshAgent("adop-jenkins-master")
    }
    environmentVariables {
        env('WORKSPACE_NAME', workspaceFolderName)
        env('PROJECT_NAME', projectFolderName)
        env('JMETER_TESTDIR', 'jmeter-test')
    }
    label("docker")
    steps {
        copyArtifacts("Reference_Application_Build") {
            buildSelector {
                buildNumber('${B}')
            }
            targetDirectory('${JMETER_TESTDIR}')
        }
        shell('''export SERVICE_NAME="ExampleWorkspace_Performance_Tests"
            |echo "SERVICE_NAME=${SERVICE_NAME}" > env.properties
            |export REPO_NAME="$(echo ${PROJECT_NAME} | tr '/' '_' | tr '[:upper:]' '[:lower:]')"
            |echo "REPO_NAME=${REPO_NAME}" >> env.properties
            |
            |docker run -d --net ${DOCKER_NETWORK_NAME} --name ${SERVICE_NAME} ${REPO_NAME}/adop-foss-java:0.0.${B}
            |APP_IP=$( docker inspect --format '{{ .NetworkSettings.Networks.'"$DOCKER_NETWORK_NAME"'.IPAddress }}' ${SERVICE_NAME} )
            |APP_URL=http://${APP_IP}:8080/petclinic
            |COUNT=1
            |while ! curl -q ${APP_URL} -o /dev/null
            |do
            |  if [ ${COUNT} -gt 10 ]; then
            |    echo "Docker build failed even after ${COUNT}. Please investigate."
            |    docker rm -f ${SERVICE_NAME}
            |    exit 1
            |  fi
            |  echo "Application is not up yet. Retrying ..Attempt (${COUNT})"
            |  sleep 5
            |  COUNT=$((COUNT+1))
            |done
            |
            |if [ -e ../apache-jmeter-2.13.tgz ]; then
            |	cp ../apache-jmeter-2.13.tgz $JMETER_TESTDIR
            |else
            |	wget http://www.apache.org/dist/jmeter/binaries/apache-jmeter-2.13.tgz
            |    cp apache-jmeter-2.13.tgz ../
            |    mv apache-jmeter-2.13.tgz $JMETER_TESTDIR
            |fi
            |cd $JMETER_TESTDIR
            |tar -xf apache-jmeter-2.13.tgz
            |echo 'Changing user defined parameters for jmx file'
            |sed -i 's/PETCLINIC_HOST_VALUE/'"${SERVICE_NAME}"'/g' src/test/jmeter/petclinic_test_plan.jmx
            |sed -i 's/PETCLINIC_PORT_VALUE/8080/g' src/test/jmeter/petclinic_test_plan.jmx
            |sed -i 's/CONTEXT_WEB_VALUE/petclinic/g' src/test/jmeter/petclinic_test_plan.jmx
            |sed -i 's/HTTPSampler.path"></HTTPSampler.path">petclinic</g' src/test/jmeter/petclinic_test_plan.jmx
            |'''.stripMargin()
        )
        environmentVariables {
            propertiesFile('env.properties')
        }
        ant {
            props('testpath': '$WORKSPACE/$JMETER_TESTDIR/src/test/jmeter', 'test': 'petclinic_test_plan')
            buildFile('${WORKSPACE}/$JMETER_TESTDIR/apache-jmeter-2.13/extras/build.xml')
            antInstallation('ADOP Ant')
        }
        shell('''mv $JMETER_TESTDIR/src/test/gatling/* .
            |export SERVICE_NAME="ExampleWorkspace_Performance_Tests"
            |CONTAINER_IP=$(docker inspect --format '{{ .NetworkSettings.Networks.'"$DOCKER_NETWORK_NAME"'.IPAddress }}' ${SERVICE_NAME})
            |sed -i "s/###TOKEN_VALID_URL###/http:\\/\\/${CONTAINER_IP}:8080/g" ${WORKSPACE}/src/test/scala/default/RecordedSimulation.scala
            |sed -i "s/###TOKEN_RESPONSE_TIME###/10000/g" ${WORKSPACE}/src/test/scala/default/RecordedSimulation.scala
            |'''.stripMargin()
        )
        maven {
            goals('gatling:execute')
            mavenInstallation('ADOP Maven')
        }
        shell('''
            |echo "Stopping Performance Test container and generating report."
            |docker stop ${SERVICE_NAME}
            |docker rm ${SERVICE_NAME}
            |'''.stripMargin()
        )
    }
    publishers {
        publishHtml {
            report('$WORKSPACE/$JMETER_TESTDIR/src/test/jmeter') {
                reportName('Jmeter Report')
                reportFiles('petclinic_test_plan.html')
            }
        }
    }
    configure { project ->
        project / publishers << 'io.gatling.jenkins.GatlingPublisher' {
            enabled true
        }
    }
}

deployToDemoJob.with {
    description("This job deploys the java reference application to the CI environment")
    parameters {
        stringParam("B", '', "Parent build number")
        stringParam("PARENT_BUILD", "Reference_Application_Build", "Parent build name")
        stringParam("ENVIRONMENT_NAME", "CI", "Name of the environment.")
    }
    logRotator{
        numToKeep(3)
    }
    wrappers {
        preBuildCleanup()
        injectPasswords()
        maskPasswords()
        sshAgent("adop-jenkins-master")
    }
    environmentVariables {
        env('WORKSPACE_NAME', workspaceFolderName)
        env('PROJECT_NAME', projectFolderName)
    }
    label("docker")
    steps {
        copyArtifacts("Reference_Application_Build") {
            buildSelector {
                buildNumber('${B}')
                includePatterns('target/petclinic.war')
            }
        }
        shell('''set +x
            |export SERVICE_NAME="$(echo ${PROJECT_NAME} | tr '/' '_')_${ENVIRONMENT_NAME}"
            |docker cp ${WORKSPACE}/target/petclinic.war  ${SERVICE_NAME}:/usr/local/tomcat/webapps/
            |docker restart ${SERVICE_NAME}
            |COUNT=1
            |while ! curl -q http://${SERVICE_NAME}:8080/petclinic -o /dev/null
            |do
            |  if [ ${COUNT} -gt 10 ]; then
            |    echo "Docker build failed even after ${COUNT}. Please investigate."
            |    exit 1
            |  fi
            |  echo "Application is not up yet. Retrying ..Attempt (${COUNT})"
            |  sleep 5
            |  COUNT=$((COUNT+1))
            |done
            |echo "=.=.=.=.=.=.=.=.=.=.=.=."
            |echo "=.=.=.=.=.=.=.=.=.=.=.=."
            |echo "Environment URL (replace PUBLIC_IP with your public ip address where you access jenkins from) : http://${SERVICE_NAME}.PUBLIC_IP.xip.io/petclinic"
            |echo "=.=.=.=.=.=.=.=.=.=.=.=."
            |echo "=.=.=.=.=.=.=.=.=.=.=.=."
            |set -x'''.stripMargin()
        )
    }
    publishers {
        downstreamParameterized {
            trigger(projectFolderName + "/Reference_Application_Deploy_ProdA") {
                condition("UNSTABLE_OR_BETTER")
                parameters {
                    predefinedProp("B", '${B}')
                    predefinedProp("PARENT_BUILD", '${PARENT_BUILD}')
                    predefinedProp("ENVIRONMENT_NAME", '${ENVIRONMENT_NAME}')
                }
            }
        }
    }
}

rapidTestsMultiJob.with {
    description("This job deploys the java reference application to the CI environment")
    parameters {
        stringParam("B", '', "Parent build number")
        stringParam("PARENT_BUILD", "Reference_Application_Build", "Parent build name")
    }
    logRotator{
        numToKeep(3)
    }
    wrappers {
        preBuildCleanup()
        injectPasswords()
        maskPasswords()
        sshAgent("adop-jenkins-master")
    }
    environmentVariables {
        env('WORKSPACE_NAME', workspaceFolderName)
        env('PROJECT_NAME', projectFolderName)
    }
    steps {
        phase('Run_Test_Batches') {
            phaseJob(projectFolderName + "/Reference_Application_Regression_Tests_Batch1")
            phaseJob(projectFolderName + "/Reference_Application_Regression_Tests_Batch2")
            phaseJob(projectFolderName + "/Reference_Application_Regression_Tests_Batch3")
            phaseJob(projectFolderName + "/Reference_Application_Regression_Tests_Batch4")
            phaseJob(projectFolderName + "/Reference_Application_Regression_Tests_Batch5")
            phaseJob(projectFolderName + "/Reference_Application_Performance_Tests")
            phaseJob(projectFolderName + "/Reference_Application_Security_Tests")
            phaseJob(projectFolderName + "/Reference_Application_Functional_Tests") 
        }
        phase('Deploy_To_Demo') {
            phaseJob(projectFolderName + "/Reference_Application_Deploy_Demo")
        }
    }  
}

deployJobToProdA.with {
    description("This job deploys the java reference application to the ProdA environment")
    parameters {
        stringParam("B", '', "Parent build number")
        stringParam("PARENT_BUILD", "Reference_Application_Build", "Parent build name")
        stringParam("ENVIRONMENT_NAME", "PRODA", "Name of the environment.")
    }
    logRotator{
        numToKeep(3)
    }
    wrappers {
        preBuildCleanup()
        injectPasswords()
        maskPasswords()
        sshAgent("adop-jenkins-master")
    }
    environmentVariables {
        env('WORKSPACE_NAME', workspaceFolderName)
        env('PROJECT_NAME', projectFolderName)
    }
    label("docker")
    steps {
        copyArtifacts("Reference_Application_Build") {
            buildSelector {
                buildNumber('${B}')
                includePatterns('target/petclinic.war')
            }
        }
        shell('''
            |export SERVICE_NAME="$(echo ${PROJECT_NAME} | tr '/' '_')_${ENVIRONMENT_NAME}"
            |docker cp ${WORKSPACE}/target/petclinic.war  ${SERVICE_NAME}:/usr/local/tomcat/webapps/
            |docker restart ${SERVICE_NAME}
            |COUNT=1
            |while ! curl -q http://${SERVICE_NAME}:8080/petclinic -o /dev/null
            |do
            |  if [ ${COUNT} -gt 10 ]; then
            |    echo "Docker build failed even after ${COUNT}. Please investigate."
            |    exit 1
            |  fi
            |  echo "Application is not up yet. Retrying ..Attempt (${COUNT})"
            |  sleep 5
            |  COUNT=$((COUNT+1))
            |done
            |echo "=.=.=.=.=.=.=.=.=.=.=.=."
            |echo "=.=.=.=.=.=.=.=.=.=.=.=."
            |echo "Environment URL (replace PUBLIC_IP with your public ip address where you access jenkins from) : http://${SERVICE_NAME}.PUBLIC_IP.xip.io/petclinic"
            |echo "=.=.=.=.=.=.=.=.=.=.=.=."
            |echo "=.=.=.=.=.=.=.=.=.=.=.=."
            |set -x'''.stripMargin())
    }
    publishers {
        buildPipelineTrigger(projectFolderName + "/Reference_Application_Deploy_ProdB") {
            parameters {
                predefinedProp("B", '${B}')
                predefinedProp("PARENT_BUILD", '${PARENT_BUILD}')
                predefinedProp("ENVIRONMENT_PREVNODE", '${ENVIRONMENT_NAME}')
            }
        }
    }
}

deployJobToProdB.with {
    description("This job deploys the java reference application to the ProdA environment")
    parameters {
        stringParam("B", '', "Parent build number")
        stringParam("PARENT_BUILD", "Reference_Application_Build", "Parent build name")
        stringParam("ENVIRONMENT_NAME", "PRODB", "Name of the environment.")
    }
    logRotator{
        numToKeep(3)
    }
    wrappers {
        preBuildCleanup()
        injectPasswords()
        maskPasswords()
        sshAgent("adop-jenkins-master")
    }
    environmentVariables {
        env('WORKSPACE_NAME', workspaceFolderName)
        env('PROJECT_NAME', projectFolderName)
    }
    label("docker")
    steps {
        copyArtifacts("Reference_Application_Build") {
            buildSelector {
                buildNumber('${B}')
                includePatterns('target/petclinic.war')
            }
        }
        shell('''|export SERVICE_NAME="$(echo ${PROJECT_NAME} | tr '/' '_')_${ENVIRONMENT_NAME}"
            |docker cp ${WORKSPACE}/target/petclinic.war  ${SERVICE_NAME}:/usr/local/tomcat/webapps/
            |docker restart ${SERVICE_NAME}
            |COUNT=1
            |while ! curl -q http://${SERVICE_NAME}:8080/petclinic -o /dev/null
            |do
            |  if [ ${COUNT} -gt 10 ]; then
            |    echo "Docker build failed even after ${COUNT}. Please investigate."
            |    exit 1
            |  fi
            |  echo "Application is not up yet. Retrying ..Attempt (${COUNT})"
            |  sleep 5
            |  COUNT=$((COUNT+1))
            |done
            |echo "=.=.=.=.=.=.=.=.=.=.=.=."
            |echo "=.=.=.=.=.=.=.=.=.=.=.=."
            |echo "Environment URL (replace PUBLIC_IP with your public ip address where you access jenkins from) : http://${SERVICE_NAME}.PUBLIC_IP.xip.io/petclinic"
            |echo "=.=.=.=.=.=.=.=.=.=.=.=."
            |echo "=.=.=.=.=.=.=.=.=.=.=.=."
            |set -x'''.stripMargin()
        )
    }
}
