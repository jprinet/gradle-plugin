package hudson.plugins.gradle.injection

import com.gargoylesoftware.htmlunit.html.HtmlButton
import com.gargoylesoftware.htmlunit.html.HtmlForm
import hudson.plugins.gradle.config.GlobalConfig
import hudson.slaves.EnvironmentVariablesNodeProperty
import hudson.util.FormValidation
import hudson.util.XStream2
import spock.lang.Shared
import spock.lang.Subject
import spock.lang.Unroll

@Subject(GlobalConfig.class)
class InjectionConfigTest extends BaseGradleInjectionIntegrationTest {

    @Shared
    FilenameFilter injectionConfigXmlFilter = { _, name -> name == "hudson.plugins.gradle.config.GlobalConfig.xml" }

    @Unroll
    def "sets showLegacyConfigurationWarning to true if any of legacy env variables is set"() {
        given:
        def env = new EnvironmentVariablesNodeProperty()
        env.getEnvVars().put("TEST", "true")

        if (name != null) {
            env.getEnvVars().put(name, value)
        }

        j.jenkins.getGlobalNodeProperties().add(env)

        expect:
        GlobalConfig.get().isShowLegacyConfigurationWarning() == showWarning

        where:
        name                                                           | value               || showWarning
        null                                                           | null                || false
        "FOO"                                                          | "bar"               || false
        "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_INJECTION"              | "true"              || true
        "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_URL"                    | "https://localhost" || true
        "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_ALLOW_UNTRUSTED_SERVER" | "true"              || true
        "GRADLE_ENTERPRISE_ACCESS_KEY"                                 | "foo"               || true
        "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_PLUGIN_VERSION"         | "3.11.1"            || true
        "JENKINSGRADLEPLUGIN_CCUD_PLUGIN_VERSION"                      | "1.8.1"             || true
        "JENKINSGRADLEPLUGIN_GRADLE_PLUGIN_REPOSITORY_URL"             | "https://localhost" || true
        "JENKINSGRADLEPLUGIN_GRADLE_INJECTION_ENABLED_NODES"           | "foo,bar"           || true
        "JENKINSGRADLEPLUGIN_GRADLE_INJECTION_DISABLED_NODES"          | "foo,bar"           || true
        "JENKINSGRADLEPLUGIN_GRADLE_ENTERPRISE_EXTENSION_VERSION"      | "1.15.5"            || true
        "JENKINSGRADLEPLUGIN_CCUD_EXTENSION_VERSION"                   | "1.11.1"            || true
        "JENKINSGRADLEPLUGIN_MAVEN_INJECTION_ENABLED_NODES"            | "foo,bar"           || true
        "JENKINSGRADLEPLUGIN_MAVEN_INJECTION_DISABLED_NODES"           | "foo,bar"           || true
    }

    @Unroll
    def "validates server url"() {
        expect:
        with(GlobalConfig.get().doCheckServer(url)) {
            kind == expectedKind
            message == expectedMssage
        }

        where:
        url                      || expectedKind              | expectedMssage
        "http://gradle.com/test" || FormValidation.Kind.OK    | null
        "http://localhost"       || FormValidation.Kind.OK    | null
        "https://localhost"      || FormValidation.Kind.OK    | null
        "  https://localhost"    || FormValidation.Kind.OK    | null
        "https://localhost  "    || FormValidation.Kind.OK    | null
        "ftp://localhost"        || FormValidation.Kind.ERROR | "Not a valid URL."
        "localhost"              || FormValidation.Kind.ERROR | "Not a valid URL."
        ""                       || FormValidation.Kind.ERROR | "Required."
        null                     || FormValidation.Kind.ERROR | "Required."
    }

    @Unroll
    def "validates plugin repository url"() {
        expect:
        with(GlobalConfig.get().doCheckGradlePluginRepositoryUrl(url)) {
            kind == expectedKind
            message == expectedMssage
        }

        where:
        url                      || expectedKind              | expectedMssage
        "http://gradle.com/test" || FormValidation.Kind.OK    | null
        "http://localhost"       || FormValidation.Kind.OK    | null
        "https://localhost"      || FormValidation.Kind.OK    | null
        "  https://localhost"    || FormValidation.Kind.OK    | null
        "https://localhost  "    || FormValidation.Kind.OK    | null
        "ftp://localhost"        || FormValidation.Kind.ERROR | "Not a valid URL."
        "localhost"              || FormValidation.Kind.ERROR | "Not a valid URL."
        ""                       || FormValidation.Kind.OK    | null
        null                     || FormValidation.Kind.OK    | null
    }

    @Unroll
    def "validates gradle plugin and ccud plugin version"() {
        expect:
        with(GlobalConfig.get().doCheckGradlePluginVersion(version)) {
            kind == expectedKind
            message == expectedMssage
        }
        with(GlobalConfig.get().doCheckCcudPluginVersion(version)) {
            kind == expectedKind
            message == expectedMssage
        }

        where:
        version                   || expectedKind              | expectedMssage
        "1"                       || FormValidation.Kind.ERROR | "Not a valid version."
        "1.0"                     || FormValidation.Kind.OK    | null
        "1.1.1"                   || FormValidation.Kind.OK    | null
        "   1.1.1"                || FormValidation.Kind.OK    | null
        "1.0   "                  || FormValidation.Kind.OK    | null
        "2.0.0-SNAPSHOT"          || FormValidation.Kind.OK    | null
        "2.0.0-my-branch_42-test" || FormValidation.Kind.OK    | null
        "first"                   || FormValidation.Kind.ERROR | "Not a valid version."
    }

    def "saves injection configuration"() {
        given:
        def webClient = j.createWebClient()
        def page = webClient.goTo("configure")
        def form = page.getFormByName("config")

        when:
        form.getInputByName("_.injectionEnabled").click()
        form.getInputByName("_.server").setValueAttribute("https://localhost")
        form.getInputByName("_.allowUntrusted").click()
        form.getInputByName("_.accessKey").setValueAttribute("ACCESS_KEY")

        form.getInputByName("_.gradlePluginVersion").setValueAttribute("3.11.1")
        form.getInputByName("_.ccudPluginVersion").setValueAttribute("1.8")
        form.getInputByName("_.gradlePluginRepositoryUrl").setValueAttribute("https://localhost/repostiry")

        getAddButton(form, "Gradle auto-injection enabled nodes").click()
        form.getInputsByName("_.label").last().setValueAttribute("gradle1")
        getAddButton(form, "Gradle auto-injection disabled nodes").click()
        form.getInputsByName("_.label").last().setValueAttribute("gradle2")

        form.getInputByName("_.injectMavenExtension").click()
        form.getInputByName("_.injectCcudExtension").click()

        getAddButton(form, "Maven auto-injection enabled nodes").click()
        form.getInputsByName("_.label").last().setValueAttribute("maven1")
        getAddButton(form, "Maven auto-injection disabled nodes").click()
        form.getInputsByName("_.label").last().setValueAttribute("maven2")

        j.submit(form)

        then:
        def files = j.jenkins.root.listFiles(injectionConfigXmlFilter)
        files.length == 1
        with(fromXml(files.first().text)) {
            injectionEnabled
            server == "https://localhost"
            allowUntrusted
            accessKey.plainText == "ACCESS_KEY"

            gradlePluginVersion == "3.11.1"
            ccudPluginVersion == "1.8"
            gradlePluginRepositoryUrl == "https://localhost/repostiry"
            gradleInjectionEnabledNodes*.label == ['gradle1']
            gradleInjectionDisabledNodes*.label == ['gradle2']

            injectMavenExtension
            injectCcudExtension
            mavenInjectionEnabledNodes*.label == ['maven1']
            mavenInjectionDisabledNodes*.label == ['maven2']
        }
    }

    @Unroll
    def "ignores empty access key"() {
        given:
        def webClient = j.createWebClient()
        def page = webClient.goTo("configure")
        def form = page.getFormByName("config")

        when:
        form.getInputByName("_.injectionEnabled").click()
        form.getInputByName("_.server").setValueAttribute("https://localhost")
        form.getInputByName("_.allowUntrusted").click()
        form.getInputByName("_.accessKey").setValueAttribute(accessKey)

        j.submit(form)

        then:
        def config = GlobalConfig.get()
        config.injectionEnabled
        config.server == "https://localhost"
        config.accessKey == null

        where:
        accessKey << ["", "   "]
    }

    private static GlobalConfig fromXml(String xml) {
        return (GlobalConfig) new XStream2().fromXML(xml)
    }

    private static HtmlButton getAddButton(HtmlForm form, String label) {
        def xpath = "//td[@class = 'setting-name' and text() = '$label']/following-sibling::td[@class = 'setting-main']//span[contains(@class, 'repeatable-add')]//button[text() = 'Add']"
        return form.getFirstByXPath(xpath)
    }
}
