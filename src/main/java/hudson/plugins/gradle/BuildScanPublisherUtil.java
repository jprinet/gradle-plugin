package hudson.plugins.gradle;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.remoting.VirtualChannel;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

public class BuildScanPublisherUtil {

    private static final String BUILD_SCAN_DELETE_INIT_SCRIPT_AFTER_RUN = "BUILD_SCAN_DELETE_INIT_SCRIPT_AFTER_RUN";
    private static final String BUILD_SCAN_PLUGIN_GE_PLUGIN_VERSION = "BUILD_SCAN_PLUGIN_GE_PLUGIN_VERSION";
    private static final String BUILD_SCAN_OVERRIDE_HOME = "BUILD_SCAN_OVERRIDE_HOME";
    private static final String RESOURCE_INIT_SCRIPT_GRADLE = "scripts/init-script.gradle";
    private static final String INIT_DIR = "init.d";
    private static final String GRADLE_DIR = ".gradle";
    private static final String GRADLE_INIT_FILE = "init-build-scan.gradle";

    private static final ReentrantLock lock = new ReentrantLock();

    public static boolean isForcePublishBuildScan(EnvVars env, FilePath workspace) {
        return env != null && workspace != null && env.containsKey(BUILD_SCAN_PLUGIN_GE_PLUGIN_VERSION);
    }

    public static String getWorkspaceDestination(FilePath workspace){
        return workspace.getRemote() + "/" + GRADLE_DIR;
    }

    public static String getHomeDestination(EnvVars env){
        String homeOverride = env.get(BUILD_SCAN_OVERRIDE_HOME);
        if(homeOverride != null){
            return homeOverride;
        } else {
            return env.get("HOME") + "/" + GRADLE_DIR + "/" + INIT_DIR;
        }
    }

    public static FilePath copyInitScriptToDestination(VirtualChannel channel, String destination) {
        try {
            assertDestinationNotNull(destination);
            FilePath gradleInitScriptFile = new FilePath(channel, destination + "/" + GRADLE_INIT_FILE);

            if (!gradleInitScriptFile.exists()) {
                lock.lock();
                try {
                    if (!gradleInitScriptFile.exists()) {
                        FilePath gradleInitScriptDirectory = new FilePath(channel, destination);
                        if (!gradleInitScriptDirectory.exists()) {
                            gradleInitScriptDirectory.mkdirs();
                        }

                        gradleInitScriptFile.copyFrom(
                            Objects.requireNonNull(BuildScanPublisherUtil.class.getClassLoader().getResourceAsStream(RESOURCE_INIT_SCRIPT_GRADLE))
                        );
                    }
                } finally {
                    lock.unlock();
                }
            }

            return gradleInitScriptFile;
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void removeInitScript(VirtualChannel channel, String destination, EnvVars env) {
        if(Boolean.parseBoolean(env.get(BUILD_SCAN_DELETE_INIT_SCRIPT_AFTER_RUN))) {
            try {
                assertDestinationNotNull(destination);

                FilePath gradleInitScriptFile = new FilePath(channel, destination + "/" + GRADLE_INIT_FILE);
                if (gradleInitScriptFile.exists()) {
                    if (!gradleInitScriptFile.delete()) {
                        throw new IllegalStateException("Error while deleting init script");
                    }
                }
            } catch (IOException | InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static void assertDestinationNotNull(String destination){
        if(destination == null) {
            throw new IllegalStateException("Destination for init script is null");
        }
    }
}
