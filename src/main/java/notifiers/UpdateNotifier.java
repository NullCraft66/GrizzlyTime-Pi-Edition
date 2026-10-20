package notifiers;

import activities.LocalDbActivity;
import helpers.AlertUtils;
import helpers.CommonUtils;
import helpers.Constants;
import helpers.LoggingUtils;
import java.io.*;
import java.net.URL;
import java.util.Scanner;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

/** @author Dalton Smith UpdateNotifier Displays popup if version is outdated */
public class UpdateNotifier {

  private static final String UPDATE_VERSION_FILE = ".update-version";
  private AlertUtils alertUtils = new AlertUtils();

  private void downloadVersion() throws IOException {

    File file = new File(CommonUtils.getCurrentDir(), UPDATE_VERSION_FILE);

    if (file.exists()) {
      LoggingUtils.log(Level.WARNING, UPDATE_VERSION_FILE + " already exists");

      if (file.delete()) {
        LoggingUtils.log(Level.INFO, "Delete was successful");
      } else {
        LoggingUtils.log(Level.WARNING, "Delete was unsuccessful");
      }
    }

    BufferedInputStream inputStream =
        new BufferedInputStream(new URL(Constants.kUpdateUrl).openStream());
    FileOutputStream fileOS = new FileOutputStream(file);

    byte[] data = new byte[1024];
    int byteContent;
    while ((byteContent = inputStream.read(data, 0, 1024)) != -1) {
      fileOS.write(data, 0, byteContent);
    }
  }

  public void checkUpdates() {
    if (!LocalDbActivity.kUpdateNotifier) {
      return;
    }

    try {
      downloadVersion();
      LoggingUtils.log(Level.INFO, "Successfully grabbed version");

    } catch (IOException e) {
      LoggingUtils.log(Level.INFO, "Error checking for updates");
      LoggingUtils.log(Level.WARNING, e);
    }

    Scanner scanner;
    try {
      scanner = new Scanner(new File(CommonUtils.getCurrentDir(), UPDATE_VERSION_FILE));

    } catch (FileNotFoundException e) {
      LoggingUtils.log(Level.SEVERE, e);
      return;
    }

    String version;

    if (scanner.hasNext()) {
      version = scanner.next();
      LoggingUtils.log(Level.INFO, "Updated version is: " + version);
      LoggingUtils.log(Level.INFO, "Current version is: " + Constants.kVersion);

    } else {
      LoggingUtils.log(Level.WARNING, "No version detected in version.txt");
      return;
    }

    if (!Constants.kVersion.equals(version)) {
      boolean confirm =
          alertUtils.createAlert(
              "New Version Available",
              "An update is available!",
              "Version " + version + " is available!\n" + Constants.kReleaseUrl);

      if (confirm) {
        startAutomaticUpdate();
      }
    }
  }

  private void startAutomaticUpdate() {
    try {
      File projectDir = new File(CommonUtils.getCurrentDir());
      Dialog<Void> progressDialog = new Dialog<>();
      progressDialog.setTitle("Updating GrizzlyTime");
      progressDialog.setHeaderText("Downloading and installing the update...");
      Label status = new Label("Starting update...");
      TextArea output = new TextArea();
      output.setEditable(false);
      output.setWrapText(false);
      output.setPrefRowCount(16);
      output.setPrefColumnCount(70);
      VBox content = new VBox(8, status, output);
      content.setPadding(new Insets(10));
      progressDialog.getDialogPane().setContent(content);
      progressDialog.getDialogPane().getStylesheets().add(Constants.kRootStylesheet);
      progressDialog.getDialogPane().getButtonTypes().clear();
      progressDialog.show();

      Process updater =
          new ProcessBuilder("bash", "./scripts/update.sh")
              .directory(projectDir)
              .redirectErrorStream(true)
              .start();
      LoggingUtils.log(Level.INFO, "Automatic update started");

      Thread outputReader =
          new Thread(
              () -> {
                try (BufferedReader reader = updater.inputReader()) {
                  String line;
                  while ((line = reader.readLine()) != null) {
                    String updateLine = line;
                    Platform.runLater(
                        () -> {
                          output.appendText(updateLine + System.lineSeparator());
                          status.setText(updateLine);
                        });
                  }
                  int exitCode = updater.waitFor();
                  Platform.runLater(
                      () -> {
                        status.setText(
                            exitCode == 0
                                ? "Update complete. GrizzlyTime is restarting."
                                : "Update failed. See the output above.");
                        if (exitCode == 0) progressDialog.setResult(null);
                      });
                } catch (Exception e) {
                  LoggingUtils.log(Level.SEVERE, e);
                  Platform.runLater(() -> status.setText("Update failed. See the log output."));
                }
              },
              "update-output-reader");
      outputReader.setDaemon(true);
      outputReader.start();
    } catch (IOException e) {
      LoggingUtils.log(Level.SEVERE, e);
      CommonUtils.application.getHostServices().showDocument(Constants.kReleaseUrl);
    }
  }
}
