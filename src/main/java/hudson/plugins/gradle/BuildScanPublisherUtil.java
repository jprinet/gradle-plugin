package hudson.plugins.gradle;

import hudson.EnvVars;
import hudson.FilePath;

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
    private static final String USER_HOME = "user.home";

    public static boolean isForcePublishBuildScan(EnvVars env, FilePath workspace) {
        return env != null && env.containsKey(BUILD_SCAN_PLUGIN_GE_PLUGIN_VERSION) && workspace != null;
    }

    public static FilePath copyInitScriptToWorkspace(FilePath workspace) throws IOException, InterruptedException {
        return copyInitScriptToWorkspaceInternal(workspace);
    }

    private static FilePath copyInitScriptToWorkspaceInternal(FilePath workspace) throws IOException, InterruptedException {
        FilePath initScriptDir = workspace.createTempDir(TMP_FILE_PREFIX, GRADLE_SUFFIX);
        FilePath initScriptFile = initScriptDir.createTempFile(TMP_FILE_PREFIX, GRADLE_SUFFIX);

        initScriptFile.copyFrom(
            Objects.requireNonNull(BuildScanPublisherUtil.class.getClassLoader().getResourceAsStream(RESOURCE_INIT_SCRIPT_GRADLE))
        );

        return initScriptFile;
    }

    public static FilePath copyInitScriptToUserHome(FilePath workspace, PrintStream logger) throws IOException, InterruptedException {
        FilePath initScriptFile = null;

        String userHome = System.getProperty(USER_HOME);
        if(userHome != null) {
            String gradleInitScriptDirectoryName = userHome + GRADLE_INIT_DIR;

            File gradleInitScriptFile = new File(gradleInitScriptDirectoryName + "/" + GRADLE_INIT_FILE);
            if (!gradleInitScriptFile.exists()){

                File gradleInitScriptDirectory = new File(gradleInitScriptDirectoryName);
                boolean isGradleInitScriptDirectory = true;
                if (!gradleInitScriptDirectory.exists()) {
                    if(!gradleInitScriptDirectory.mkdirs()) {
                        isGradleInitScriptDirectory = false;
                        logger.println("error - Gradle init script directory can't be created");
                    }
                }

                if(isGradleInitScriptDirectory) {
                    initScriptFile = copyInitScriptToWorkspaceInternal(workspace);
                    initScriptFile.renameTo(new FilePath(gradleInitScriptFile));
                }
            }
        } else {
            logger.println("error - " + USER_HOME + " is not set");
        }

        return initScriptFile;
    }

}
