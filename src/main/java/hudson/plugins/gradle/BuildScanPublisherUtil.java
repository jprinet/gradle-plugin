package hudson.plugins.gradle;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import org.jenkinsci.remoting.RoleChecker;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Objects;

public class BuildScanPublisherUtil {

    private static final String BUILD_SCAN_PLUGIN_GE_PLUGIN_VERSION = "BUILD_SCAN_PLUGIN_GE_PLUGIN_VERSION";
    private static final String RESOURCE_INIT_SCRIPT_GRADLE = "scripts/init-script.gradle";
    private static final String GRADLE_INIT_DIR = "/.gradle/init.d";
    private static final String GRADLE_INIT_FILE = "init-build-scan.gradle";
    private static final String TMP_FILE_PREFIX = "init";
    private static final String GRADLE_SUFFIX = ".gradle";

    public static boolean isForcePublishBuildScan(EnvVars env, FilePath workspace) {
        return env != null && env.containsKey(BUILD_SCAN_PLUGIN_GE_PLUGIN_VERSION) && workspace != null;
    }

    public static FilePath copyInitScriptToWorkspace(FilePath workspace, GradleLogger logger) {
        try {
            FilePath initScriptDir = workspace.createTempDir(TMP_FILE_PREFIX, GRADLE_SUFFIX);
            FilePath initScriptFile = initScriptDir.createTempFile(TMP_FILE_PREFIX, GRADLE_SUFFIX);

            initScriptFile.copyFrom(
                    Objects.requireNonNull(BuildScanPublisherUtil.class.getClassLoader().getResourceAsStream(RESOURCE_INIT_SCRIPT_GRADLE))
            );

            return initScriptFile;
        } catch (IOException | InterruptedException e) {
            logger.info("ERROR - " + e.getMessage());
        }

        return null;
    }

    //TODO clean init script after build?
    public static void copyInitScriptToUserHome(VirtualChannel channel, String home, PrintStream genericLogger) {
        try {
            if (home != null) {
                String gradleInitScriptDirectoryName = home + GRADLE_INIT_DIR;

                FilePath gradleInitScriptFile = new FilePath(channel, gradleInitScriptDirectoryName + "/" + GRADLE_INIT_FILE);
                if (!gradleInitScriptFile.exists()) {

                    FilePath gradleInitScriptDirectory = new FilePath(channel, gradleInitScriptDirectoryName);

                    if (!gradleInitScriptDirectory.exists()) {
                        genericLogger.println("creating init script dir " + gradleInitScriptDirectory.getRemote());
                        gradleInitScriptDirectory.mkdirs();
                    }

                    genericLogger.println("creating init script file " + gradleInitScriptFile.getRemote());
                    gradleInitScriptFile.copyFrom(
                            Objects.requireNonNull(BuildScanPublisherUtil.class.getClassLoader().getResourceAsStream(RESOURCE_INIT_SCRIPT_GRADLE))
                    );
                }
            } else {
                genericLogger.println("error - HOME is not set");
            }
        } catch (IOException | InterruptedException e) {
            genericLogger.println("ERROR - " + e.getMessage());
        }
    }

}
