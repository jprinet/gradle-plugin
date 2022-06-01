package hudson.plugins.gradle

import hudson.EnvVars
import hudson.model.FreeStyleProject
import hudson.model.Label
import hudson.slaves.EnvironmentVariablesNodeProperty
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jvnet.hudson.test.CreateFileBuilder
import org.jvnet.hudson.test.JenkinsRule
import spock.lang.Unroll

@Unroll
class ForceBuildScanIntegrationTest extends AbstractIntegrationTest {

  private static final String MSG_PUBLISH_BUILD_SCAN = "Publishing build scan..."

  def 'build scan is published without GE plugin with Gradle manual step #gradleVersion'() {
        given:
        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()
        FreeStyleProject p = j.createFreeStyleProject()
        EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars env = prop.getEnvVars();
        env.put('BUILD_SCAN_PLUGIN_GE_PLUGIN_VERSION', '3.10.1')
        env.put('BUILD_SCAN_PLUGIN_CCUD_PLUGIN_VERSION', '1.7')
        env.put('BUILD_SCAN_PLUGIN_GE_URL', 'http://foo.com')

        def slave = j.createOnlineSlave(Label.get("foo"), env)
        p.setAssignedNode(slave)

        p.buildersList.add(buildScriptBuilder())
        p.buildersList.add(new Gradle(tasks: 'hello', gradleName: gradleVersion, switches: "--no-daemon"))

        when:
        def build = j.buildAndAssertSuccess(p)

        then:
        println JenkinsRule.getLog(build)
        j.assertLogContains(MSG_PUBLISH_BUILD_SCAN, build)

        where:
        gradleVersion << ['4.10.3', '5.6.4', '6.9.2', '7.4.2']
    }

    def 'build scan is not published without BUILD_SCAN_PLUGIN_GE_PLUGIN_VERSION on manual step'() {
        given:
        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()
        FreeStyleProject p = j.createFreeStyleProject()
        EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars env = prop.getEnvVars();

        def slave = j.createOnlineSlave(Label.get("foo"), env)
        p.setAssignedNode(slave)

        p.buildersList.add(buildScriptBuilder())
        p.buildersList.add(new Gradle(tasks: 'hello', gradleName: gradleVersion, switches: "--no-daemon"))

        when:
        def build = j.buildAndAssertSuccess(p)

        then:
        println JenkinsRule.getLog(build)
        j.assertLogNotContains(MSG_PUBLISH_BUILD_SCAN, build)

        where:
        gradleVersion << ['7.4.2']
    }

    def 'build scan is published without GE plugin with Gradle pipeline #gradleVersion'() {
        given:
        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()
        def pipelineJob = j.createProject(WorkflowJob)

        pipelineJob.setDefinition(new CpsFlowDefinition("""
    stage('Build') {
      node {
        withEnv(['BUILD_SCAN_PLUGIN_GE_PLUGIN_VERSION=3.10.1','BUILD_SCAN_PLUGIN_CCUD_PLUGIN_VERSION=1.7','BUILD_SCAN_PLUGIN_GE_URL=http://foo.com']){
          withGradle {
            def gradleHome = tool name: '${gradleInstallationRule.gradleVersion}', type: 'gradle'
            writeFile file: 'settings.gradle', text: ''
            writeFile file: 'build.gradle', text: ""
            if (isUnix()) {
              sh "'\${gradleHome}/bin/gradle' help --no-daemon --console=plain"
            } else {
              bat(/"\${gradleHome}\\bin\\gradle.bat" help --no-daemon --console=plain/)
            }
          }
        }
      }
    }
""", false))

        when:
        def build = j.buildAndAssertSuccess(pipelineJob)

        then:
        println JenkinsRule.getLog(build)
        j.assertLogContains(MSG_PUBLISH_BUILD_SCAN, build)

        where:
        gradleVersion << ['4.10.3', '5.6.4', '6.9.2', '7.4.2']
    }

    def 'build scan is not published without BUILD_SCAN_PLUGIN_GE_PLUGIN_VERSION on pipeline'() {
        given:
        gradleInstallationRule.gradleVersion = gradleVersion
        gradleInstallationRule.addInstallation()
        def pipelineJob = j.createProject(WorkflowJob)

        pipelineJob.setDefinition(new CpsFlowDefinition("""
    stage('Build') {
      node {
        withGradle {
          def gradleHome = tool name: '${gradleInstallationRule.gradleVersion}', type: 'gradle'
          writeFile file: 'settings.gradle', text: ''
          writeFile file: 'build.gradle', text: ""
          if (isUnix()) {
            sh "'\${gradleHome}/bin/gradle' help --no-daemon --console=plain"
          } else {
            bat(/"\${gradleHome}\\bin\\gradle.bat" help --no-daemon --console=plain/)
          }
        }
      }
    }
""", false))

        when:
        def build = j.buildAndAssertSuccess(pipelineJob)

        then:
        println JenkinsRule.getLog(build)
        j.assertLogNotContains(MSG_PUBLISH_BUILD_SCAN, build)

        where:
        gradleVersion << ['7.4.2']
    }

  def 'init script is copied in a custom gradle home'() {
    given:
    gradleInstallationRule.gradleVersion = gradleVersion
    gradleInstallationRule.addInstallation()
    def pipelineJob = j.createProject(WorkflowJob)

    pipelineJob.setDefinition(new CpsFlowDefinition("""
    stage('Build') {
      node {
        withEnv(['BUILD_SCAN_PLUGIN_GE_PLUGIN_VERSION=3.10.1','BUILD_SCAN_PLUGIN_CCUD_PLUGIN_VERSION=1.7','BUILD_SCAN_PLUGIN_GE_URL=http://foo.com','BUILD_SCAN_OVERRIDE_HOME=/tmp']){
          withGradle {
            def gradleHome = tool name: '${gradleInstallationRule.gradleVersion}', type: 'gradle'
            writeFile file: 'settings.gradle', text: ''
            writeFile file: 'build.gradle', text: ""
            if (isUnix()) {
              sh "'\${gradleHome}/bin/gradle' help --no-daemon --console=plain"
            } else {
              bat(/"\${gradleHome}\\bin\\gradle.bat" help --no-daemon --console=plain/)
            }
          }
          
          def exists = fileExists '/tmp/.gradle/init.d/init-build-scan.gradle'
          if (!exists) {
            error "Gradle init script not found"
          }
        }
      }
    }
""", false))

    when:
    def build = j.buildAndAssertSuccess(pipelineJob)

    then:
    println JenkinsRule.getLog(build)

    where:
    gradleVersion << ['7.4.2']
  }

  def 'init script is deleted after run'() {
    given:
    gradleInstallationRule.gradleVersion = gradleVersion
    gradleInstallationRule.addInstallation()
    def pipelineJob = j.createProject(WorkflowJob)

    pipelineJob.setDefinition(new CpsFlowDefinition("""
    stage('Build') {
      node {
        withEnv(['BUILD_SCAN_PLUGIN_GE_PLUGIN_VERSION=3.10.1','BUILD_SCAN_PLUGIN_CCUD_PLUGIN_VERSION=1.7','BUILD_SCAN_PLUGIN_GE_URL=http://foo.com','BUILD_SCAN_OVERRIDE_HOME=/tmp','BUILD_SCAN_DELETE_INIT_SCRIPT_AFTER_RUN=true']){
          withGradle {
            def gradleHome = tool name: '${gradleInstallationRule.gradleVersion}', type: 'gradle'
            writeFile file: 'settings.gradle', text: ''
            writeFile file: 'build.gradle', text: ""
            if (isUnix()) {
              sh "'\${gradleHome}/bin/gradle' help --no-daemon --console=plain"
            } else {
              bat(/"\${gradleHome}\\bin\\gradle.bat" help --no-daemon --console=plain/)
            }
          }
          
          def exists = fileExists '/tmp/.gradle/init.d/init-build-scan.gradle'
          if (exists) {
            error "Gradle init script not deleted"
          }
        }
      }
    }
""", false))

    when:
    def build = j.buildAndAssertSuccess(pipelineJob)

    then:
    println JenkinsRule.getLog(build)

    where:
    gradleVersion << ['7.4.2']
  }

  private static CreateFileBuilder buildScriptBuilder() {
        return new CreateFileBuilder('build.gradle', """
task hello { doLast { println 'Hello' } }""")
    }

    private static boolean isUnix() {
        return File.pathSeparatorChar == ':' as char
    }
}
