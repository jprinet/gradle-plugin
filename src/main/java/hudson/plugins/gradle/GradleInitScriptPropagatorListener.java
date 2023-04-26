package hudson.plugins.gradle;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.slaves.ComputerListener;

import java.io.IOException;
import java.util.Objects;
import java.util.logging.Logger;

@Extension
public class GradleInitScriptPropagatorListener extends ComputerListener {

  private static final Logger LOGGER = Logger.getLogger(GradleInitScriptPropagatorListener.class.getName());

  private static final String BUILD_SCAN_DELETE_INIT_SCRIPT = "BUILD_SCAN_DELETE_INIT_SCRIPT";
  private static final String BUILD_SCAN_OVERRIDE_HOME = "BUILD_SCAN_OVERRIDE_HOME";
  private static final String BUILD_SCAN_PLUGIN_GE_PLUGIN_VERSION = "BUILD_SCAN_PLUGIN_GE_PLUGIN_VERSION";
  private static final String RESOURCE_INIT_SCRIPT_GRADLE = "scripts/init-script.gradle";
  private static final String INIT_DIR = "init.d";
  private static final String GRADLE_DIR = ".gradle";
  private static final String GRADLE_INIT_FILE = "init-build-scan.gradle";

  @Override
  public void onOnline(Computer c, TaskListener listener) throws IOException, InterruptedException {
    try {
      LOGGER.info("onOnline " + c.getName());

      EnvVars env = c.buildEnvironment(listener);

      String destination = getHomeDestination(env);

      removeInitScript(c.getChannel(), destination, env);

      copyInitScriptToDestination(c.getChannel(), destination, env);
    } catch(IllegalStateException e) {
      LOGGER.warning("Error: " + e.getMessage());
    }
  }

  private String getHomeDestination(EnvVars env) {
    String homeOverride = env.get(BUILD_SCAN_OVERRIDE_HOME);
    if (homeOverride != null) {
      return homeOverride + "/" + GRADLE_DIR + "/" + INIT_DIR;
    } else {
      return env.get("HOME") + "/" + GRADLE_DIR + "/" + INIT_DIR;
    }
  }

  private void copyInitScriptToDestination(VirtualChannel channel, String destination, EnvVars env) {
    if (isForcePublishBuildScanEnabled(env)) {
      try {
        assertDestinationNotNull(destination);
        FilePath gradleInitScriptFile = new FilePath(channel, destination + "/" + GRADLE_INIT_FILE);

        LOGGER.info("check init script file exists");
        if (!gradleInitScriptFile.exists()) {
          if (!gradleInitScriptFile.exists()) {
            FilePath gradleInitScriptDirectory = new FilePath(channel, destination);
            if (!gradleInitScriptDirectory.exists()) {
              LOGGER.info("create init script directory");
              gradleInitScriptDirectory.mkdirs();
            }

            LOGGER.info("copy init script file");
            gradleInitScriptFile.copyFrom(
                    Objects.requireNonNull(GradleInitScriptPropagatorListener.class.getClassLoader().getResourceAsStream(RESOURCE_INIT_SCRIPT_GRADLE))
            );
          }
        }
      } catch (IOException | InterruptedException e) {
        throw new IllegalStateException(e);
      }
    } else {
      LOGGER.info("force build scan disabled");
    }
  }

  private boolean isForcePublishBuildScanEnabled(EnvVars env) {
    return env.containsKey(BUILD_SCAN_PLUGIN_GE_PLUGIN_VERSION);
  }

  private void removeInitScript(VirtualChannel channel, String destination, EnvVars env) {
    if (Boolean.parseBoolean(env.get(BUILD_SCAN_DELETE_INIT_SCRIPT))) {
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

  private void assertDestinationNotNull(String destination) {
    if (destination == null) {
      throw new IllegalStateException("Destination for init script is null");
    }
  }

}