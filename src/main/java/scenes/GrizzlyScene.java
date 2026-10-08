package scenes;

import activities.KeyActivity;
import activities.LocalDbActivity;
import activities.MeetingActivity;
import activities.UserActivity;
import exceptions.CancelledUserCreationException;
import exceptions.ConnectToWorksheetException;
import helpers.AlertUtils;
import helpers.CommonUtils;
import helpers.Constants;
import helpers.LoggingUtils;
import java.io.File;
import java.net.NoRouteToHostException;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

public class GrizzlyScene {
  /** @author Dalton Smith GrizzlyScene Manages the main interface */

  // object that should be able to be modified by calling
  // this scene directly
  private static Label messageText = new Label("");
  private static Label meetingStatusText = new Label("No active meeting");

  private static TextField studentIDBox = new TextField();

  // define our scene objects
  private Button loginButton = new Button("Login/Logout");
  private Hyperlink forgotIdLink = new Hyperlink("Forgot ID?");
  private UserActivity userActivity = new UserActivity();
  private Text description = new Text(Constants.kUserTutorial);
  private Hyperlink creditsLink = new Hyperlink("Credits");
  private Text creditsText = new Text("v" + Constants.kVersion);
  private Hyperlink optionsLink = new Hyperlink("Full Screen");
  private BorderPane bottomPane = new BorderPane();

  private AlertUtils alertUtils = new AlertUtils();

  private GridPane subRoot = new GridPane();

  // boolean state variables
  private boolean handsFreeMode = LocalDbActivity.kHandsFreeMode;

  // our upper image
  private ImageView imageView;

  public GrizzlyScene() {
    Image splash;
    File file =
        new File(
            CommonUtils.getCurrentDir() + File.separator + "images" + File.separator + "error.png");

    // check for custom splash
    if (file.exists()) {
      splash = new Image(file.toURI().toString());

    } else {
      splash = new Image(Constants.kErrorImage);
    }

    imageView = new ImageView(splash);
  }

  public void updateInterface(GridPane root) {

    // create the upper image
    imageView.setPreserveRatio(true);
    imageView.setSmooth(true);
    imageView.setFitHeight(Constants.kCameraHeight);
    GridPane.setHalignment(imageView, HPos.CENTER);
    root.add(imageView, 0, 0);

    // update CSS IDS
    messageText.setId("messageText");
    meetingStatusText.setId("meetingStatus");
    studentIDBox.setId("textBox");
    loginButton.setId("confirmButton");
    forgotIdLink.setId("hyperlinkBottom");
    creditsLink.setId("hyperlinkBottom");
    optionsLink.setId("hyperlinkBottom");
    creditsText.setId("hyperlinkBottom");

    // create our panes
    GridPane options = new GridPane();
    GridPane title = new GridPane();

    subRoot.setId("bottomView");

    // confirm alignments
    subRoot.setAlignment(Pos.CENTER);
    options.setAlignment(Pos.CENTER);
    title.setAlignment(Pos.CENTER);
    messageText.setAlignment(Pos.CENTER);
    meetingStatusText.setAlignment(Pos.CENTER);
    description.setTextAlignment(TextAlignment.CENTER);
    description.setId("textDescription");

    // manually align message text because Gridpane is weird
    GridPane.setHalignment(messageText, HPos.CENTER);
    GridPane.setHalignment(meetingStatusText, HPos.CENTER);
    GridPane.setHalignment(description, HPos.CENTER);
    GridPane.setHalignment(subRoot, HPos.CENTER);

    // set bottom pane details
    bottomPane.setId("bottomPane");
    bottomPane.setLeft(optionsLink);
    bottomPane.setCenter(creditsText);
    bottomPane.setRight(creditsLink);
    bottomPane.setMinWidth(subRoot.getWidth());

    // add our various nodes to respective panes
    title.add(description, 0, 1);
    options.add(studentIDBox, 0, 0);
    options.add(loginButton, 1, 0);
    options.add(forgotIdLink, 0, 1);
    subRoot.add(title, 0, 0);
    subRoot.add(options, 0, 1);
    subRoot.add(meetingStatusText, 0, 2);
    subRoot.add(messageText, 0, 3);

    // sub root details
    subRoot.setVgap(10);

    // add to root pane
    root.add(subRoot, 0, 1);
    root.add(bottomPane, 0, 2);

    // handle our buttons
    setEventHandlers();
  }

  public void reShowUI(GridPane root) {
    root.setId("main");

    // add to root pane
    root.add(imageView, 0, 0);
    root.add(subRoot, 0, 1);
    root.add(bottomPane, 0, 2);
  }

  // our event handlers for interactivity
  private void setEventHandlers() {
    // login on enter key press
    studentIDBox.setOnAction(event -> confirmLogin());

    // login button event handler
    loginButton.setOnAction(event -> confirmLogin());

    forgotIdLink.setOnAction(
        event -> {
          String replacementId = findIdFromIdentity();
          studentIDBox.clear();
          if (replacementId != null && !replacementId.trim().isEmpty()) {
            studentIDBox.setText(replacementId.trim());
          }
          studentIDBox.requestFocus();
        });

    creditsLink.setOnAction(event -> showCredits());

    optionsLink.setOnAction(
        event -> {
          Stage stage = (Stage) optionsLink.getScene().getWindow();
          if (KeyActivity.isFullscreen) {
            stage.setFullScreen(false);
            KeyActivity.isFullscreen = false;
          } else {
            stage.setFullScreen(true);
            KeyActivity.isFullscreen = true;
          }
        });
  }

  private void showCredits() {
    SceneManager.updateScene(Constants.kCreditsSceneState);
  }

  // helper login method
  private void confirmLogin() {
    // RFID readers and barcode scanners commonly add trailing whitespace before Enter.
    String normalizedId = studentIDBox.getText().trim();
    studentIDBox.setText(normalizedId);
    setMessageBoxText("Processing...");

    // Admin LogOut ALL
    if (normalizedId.equals("0000000")) {
      handleMeetingControls();
      return;
    }

    MeetingActivity meeting = MeetingActivity.getActiveInstance();
    if (meeting != null && !meeting.isActiveNow()) {
      setMessageBoxText("No active meeting. An administrator must enter 0000000 to start one.");
      return;
    }

    // confirm the ID is vslid
    if (!userActivity.isValidID(studentIDBox.getText())) {
      setMessageBoxText("ID " + studentIDBox.getText() + " is invalid.");

      Task<Void> wait =
          new Task<Void>() {
            @Override
            protected Void call() throws Exception {
              Thread.sleep(5000);
              return null;
            }
          };

      wait.setOnSucceeded(e -> setMessageBoxText(""));

      // no need to set as daemon as will end after x seconds.
      new Thread(wait).start();
      return;
    }

    if (!handsFreeMode) {
      // confirm that the user wants to login/logout
      if (alertUtils.confirmInput("Confirm login/logout of user: " + studentIDBox.getText())) {
        loginUser();
      } else {
        setMessageBoxText("");
      }

      // show no prompts
    } else {
      loginUser();
    }
  }

  private void handleMeetingControls() {
    String action = alertUtils.getMeetingAdminAction();
    MeetingActivity meeting = MeetingActivity.getActiveInstance();
    if (meeting == null || action == null) return;
    if (action.equals("START")) {
      String[] meetingInfo = alertUtils.getNewMeeting();
      if (meetingInfo != null) {
        try {
          String[] duration = meetingInfo[1].split(":");
          long hours = Long.parseLong(duration[0]);
          long minutes = duration.length > 1 ? Long.parseLong(duration[1]) : 0;
          if (hours >= 0 && minutes >= 0 && minutes < 60) {
            meeting.startMeeting(meetingInfo[0], hours, minutes);
          }
        } catch (Exception e) {
          setMessageBoxText("Use a duration like 1:30.");
        }
      }
    } else if (action.equals("END")) {
      meeting.endMeeting();
    } else {
      long[] extension = alertUtils.getMeetingExtension();
      if (extension != null) meeting.extendMeeting(extension[0], extension[1]);
    }
    studentIDBox.clear();
  }

  private String findIdFromIdentity() {
    String identity = alertUtils.getIdentityLookup();
    if (identity == null || identity.trim().isEmpty()) return null;
    try {
      String id = userActivity.findUserIdByIdentity(identity);
      if (id == null) setMessageBoxText("No unique matching user was found.");
      return id;
    } catch (Exception e) {
      LoggingUtils.log(Level.WARNING, e);
      setMessageBoxText("Unable to search for your ID. Please try again.");
      return null;
    }
  }

  // login the user, check if hands free or not
  private void loginUser() {
    // separate login process on different thread to ensure
    // main application does not freeze
    // also allows in for multiple users login simultaneously
    Runnable loginUser =
        () -> {
          // ensure that the user typed something in
          if (studentIDBox.getText().isEmpty()) {
            setMessageBoxText("Nothing was entered!");
            return;
          }

          // attempt login/logout and or account creation
          // do nothing if account creation was cancelled
          try {
            // Admin LogOut ALL
            if (studentIDBox.getText().equals("0000000")) {
              userActivity.logoutAllUsers();
              return;
            }
            String userID = studentIDBox.getText();
            String displayName = userActivity.getUserDisplayName(userID);

            // Unknown IDs use the existing account-creation flow.
            if (displayName == null) {
              if (!(userActivity.isUserLoggedIn(userID))) {
                LoggingUtils.log(Level.INFO, "Logging in: " + userID);
                userActivity.loginUser(userID);
              }
            } else if (alertUtils.confirmUserNameFromBackground(displayName)) {
              if (!userActivity.isUserLoggedIn(userID)) {
                LoggingUtils.log(Level.INFO, "Logging in: " + userID);
                userActivity.loginUser(userID);
              } else {
                LoggingUtils.log(Level.INFO, "Logging out: " + userID);
                userActivity.logoutUser(userID);
              }
            } else {
              String replacementId = findIdFromIdentity();
              studentIDBox.clear();
              if (replacementId != null && !replacementId.trim().isEmpty()) {
                studentIDBox.setText(replacementId.trim());
                confirmLogin();
              } else {
                setMessageBoxText("Enter a new ID number to continue.");
              }
            }

          } catch (CancelledUserCreationException e) {
            setMessageBoxText("Cancelled account creation");

          } catch (ConnectToWorksheetException e) {
            setMessageBoxText("There was an error connecting to the database. Please retry.");

          } catch (NoRouteToHostException e) {
            setMessageBoxText("Unable to connect to database. Check internet and retry.");

          } catch (Exception e) {
            LoggingUtils.log(Level.SEVERE, e);
            setMessageBoxText("An unknown error has occurred, see log file.");
          }

          // refocus the textbox
          Platform.runLater(() -> studentIDBox.requestFocus());
        };

    // start our thread
    Thread t = new Thread(loginUser);
    t.setDaemon(true);
    t.start();
  }

  // helper methods for setting and clearing text box
  public static void setMessageBoxText(String text) {
    if (Platform.isFxApplicationThread()) {
      messageText.setText(text);
    } else {
      Platform.runLater(() -> messageText.setText(text));
    }
  }

  public static void setMeetingStatus(String text) {
    if (Platform.isFxApplicationThread()) {
      meetingStatusText.setText(text);
    } else {
      Platform.runLater(() -> meetingStatusText.setText(text));
    }
  }

  public static void clearInput() {
    studentIDBox.clear();
  }
}
