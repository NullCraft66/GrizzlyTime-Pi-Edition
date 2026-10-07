package activities;

import databases.BatchUpdateData;
import databases.DatabaseUtils;
import exceptions.CancelledUserCreationException;
import helpers.AlertUtils;
import helpers.Constants;
import helpers.LoggingUtils;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.logging.Level;
import javafx.application.Platform;
import scenes.GrizzlyScene;

public class UserActivity {
  /**
   * @author Dalton Smith UserActivity class Contains the various methods for handling user
   *     login/logout
   */
  private DatabaseUtils dbUtils = new DatabaseUtils();

  private AlertUtils alertUtils = new AlertUtils();

  private LogoutActivity logoutActivity = new LogoutActivity(dbUtils);
  private LoginActivity loginActivity = new LoginActivity(dbUtils);

  private static DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;

  // check if user is logged in
  public boolean isUserLoggedIn(String userID) throws Exception {
    dbUtils.getUpdatedData();

    ArrayList<String> ids = dbUtils.getColumnData(0, Constants.kMainSheet);

    int state = doesIdExist(ids, userID);

    switch (state) {
      case Constants.kIdDoesNotExist:
        break;
      case Constants.kIdLoggedIn:
        return true;
      case Constants.kIdNotLoggedIn:
        return false;
      default:
        LoggingUtils.log(Level.SEVERE, "Uh oh, isUserLoggedIn received an unknown ID of " + state);
        break;
    }

    // request users first name and last name
    LoggingUtils.log(Level.INFO, "New User Detected");
    ArrayList<String> userData = alertUtils.getUserInfo();

    // throws CancelledUserException if registration was cancelled
    createNewUser(userData, userID);

    return false;
  }

  /** Returns the registered user's display name, or null when the ID is not registered. */
  public String getUserDisplayName(String userID) throws Exception {
    dbUtils.getUpdatedData();
    int userRow = dbUtils.getCellRowFromColumn(userID, Constants.kStudentIdColumn, Constants.kMainSheet);
    if (userRow < 0) {
      return null;
    }

    String firstName = dbUtils.getCellData(userRow, Constants.kFirstNameColumn, Constants.kMainSheet);
    String lastName = dbUtils.getCellData(userRow, Constants.kLastNameColumn, Constants.kMainSheet);
    return (firstName == null ? "" : firstName.trim()) + " "
        + (lastName == null ? "" : lastName.trim());
  }

  public String findUserIdByIdentity(String identity) throws Exception {
    dbUtils.getUpdatedData();
    String search = identity.trim().toLowerCase();
    ArrayList<String> ids = dbUtils.getColumnData(Constants.kStudentIdColumn, Constants.kMainSheet);
    ArrayList<String> first = dbUtils.getColumnData(Constants.kFirstNameColumn, Constants.kMainSheet);
    ArrayList<String> last = dbUtils.getColumnData(Constants.kLastNameColumn, Constants.kMainSheet);
    ArrayList<String> emails = dbUtils.getColumnData(Constants.kEmailColumn, Constants.kMainSheet);
    String match = null;
    for (int i = 1; i < ids.size(); i++) {
      String fullName = (first.get(i) + " " + last.get(i)).trim().toLowerCase();
      if (search.equals(fullName) || search.equals(emails.get(i).trim().toLowerCase())) {
        if (match != null) return null;
        match = ids.get(i).trim();
      }
    }
    return match;
  }

  public void createNewUser(ArrayList<String> userData, String userID)
      throws CancelledUserCreationException {
    // cancel if user cancelled or exited registration dialog
    if (("TRUE").equalsIgnoreCase(userData.get(0))) {
      // create user then login
      ArrayList<BatchUpdateData> data = new ArrayList<>();

      int blankRow = dbUtils.nextEmptyCellColumn(Constants.kMainSheet);
      addUserInfoBasic(userData, userID, data, blankRow);
      data.add(new BatchUpdateData(blankRow, Constants.kEmailColumn, userData.get(3)));
      data.add(new BatchUpdateData(blankRow, Constants.kRoleColumn, userData.get(5)));
      data.add(new BatchUpdateData(blankRow, Constants.kGenderColumn, userData.get(4)));

      dbUtils.setCellDataBatch(data, Constants.kMainSheet);
      dbUtils.getUpdatedData();

      ArrayList<String> columnLogged =
          dbUtils.getColumnData(Constants.kStudentIdColumn, Constants.kLogSheet);

      int i;
      for (i = 1; i < columnLogged.size(); i++) {
        if (columnLogged.get(i).equals("")) {
          break;
        }
      }

      data.clear();
      addUserInfoBasic(userData, userID, data, i);

      dbUtils.setCellDataBatch(data, Constants.kLogSheet);
      dbUtils.getUpdatedData();

      // ensure there is a date column
      logoutActivity.getCurrentDateColumn();

    } else {
      LoggingUtils.log(Level.INFO, "Account Creation Cancelled");
      throw new CancelledUserCreationException("Cancelled");
    }
  }

  private void addUserInfoBasic(
      ArrayList<String> userData, String userID, ArrayList<BatchUpdateData> data, int i) {
    data.add(new BatchUpdateData(i, Constants.kStudentIdColumn, userID));
    data.add(new BatchUpdateData(i, Constants.kFirstNameColumn, userData.get(1)));
    data.add(new BatchUpdateData(i, Constants.kLastNameColumn, userData.get(2)));
  }

  public int doesIdExist(ArrayList<String> ids, String userID) {
    // check if the user ID exists
    for (int i = 0; i < ids.size(); i++) {
      // if the user exists, check if logged in or logged out and return state
      if (ids.get(i).equals(userID)) {
        String cellData = dbUtils.getCellData(i, Constants.kLoggedInColumn, Constants.kMainSheet);
        try {
          cellData = cellData.replaceAll("\\s+", "");

        } catch (NullPointerException e) {
          continue;
          // do nothing because the cell doesn't exist?
        }

        if (cellData.equals("TRUE")) {
          return Constants.kIdLoggedIn;

        } else {
          return Constants.kIdNotLoggedIn;
        }
      }
    }

    return Constants.kIdDoesNotExist;
  }

  // login our user
  public void loginUser(String userID) {
    Platform.runLater(() -> GrizzlyScene.setMessageBoxText("Logging in user: " + userID));

    // grab the current time from system and format it into string
    LocalDateTime loginTime = LocalDateTime.now();
    String formattedLoginTime = loginTime.format(formatter);

    int userRow =
        dbUtils.getCellRowFromColumn(userID, Constants.kStudentIdColumn, Constants.kMainSheet);

    // log the user in
    if (userRow != -1) {
      loginActivity.loginUser(userRow, formattedLoginTime);

      Platform.runLater(
          () -> {
            String displayName;
            try {
              displayName = getUserDisplayName(userID);
            } catch (Exception e) {
              LoggingUtils.log(Level.WARNING, e);
              displayName = userID;
            }
            GrizzlyScene.setMessageBoxText("Welcome " + displayName.trim() + "!");
            GrizzlyScene.clearInput();
          });
    }
  }

  // logout the user
  public void logoutUser(String userID) {
    Platform.runLater(() -> GrizzlyScene.setMessageBoxText("Logging out user: " + userID));

    // grab the row the user is on
    int userRow =
        dbUtils.getCellRowFromColumn(userID, Constants.kStudentIdColumn, Constants.kMainSheet);

    // grab last logged in time
    LocalDateTime logoutTime = LocalDateTime.now();
    LocalDateTime loginTime =
        LocalDateTime.parse(
            dbUtils.getCellData(userRow, Constants.kLastLoginColumn, Constants.kMainSheet),
            formatter);

    String formattedLogoutTime = logoutTime.format(formatter);

    // assuming userRow isn't invalid, calculate difference in time and log hours
    if (userRow != -1) {
      // update the logout time
      dbUtils.setCellData(
          userRow, Constants.kLastLogoutColumn, formattedLogoutTime, Constants.kMainSheet);

      Duration elapsed = Duration.between(loginTime, logoutTime);
      if (elapsed.isNegative()) {
        LoggingUtils.log(Level.SEVERE, "Logout time occurred before login time for " + userID);
      } else {
        long totalSeconds = elapsed.getSeconds();
        long diffHours = totalSeconds / 3600;
        long diffMinutes = (totalSeconds % 3600) / 60;
        long diffSeconds = totalSeconds % 60;
        String totalTimeFromDifference =
            String.format("%02d:%02d:%02d", diffHours, diffMinutes, diffSeconds);
        LocalTime totalHoursTime = LocalTime.of((int) (diffHours % 24), (int) diffMinutes, (int) diffSeconds);
        logoutActivity.logoutUserWithHours(userID, userRow, totalHoursTime, totalTimeFromDifference);
      }

      // logout the user
      dbUtils.setCellData(userRow, Constants.kLoggedInColumn, "FALSE", Constants.kMainSheet);

    }
  }

  // logout all currently logged-in users
  public void logoutAllUsers() {
    dbUtils.getUpdatedData();

    ArrayList<String> ids = dbUtils.getColumnData(Constants.kStudentIdColumn, Constants.kMainSheet);

    for (int i = 1; i < ids.size(); i++) {
      String userID = ids.get(i);

      if (userID == null || userID.trim().isEmpty()) {
        continue;
      }

      String loggedIn = dbUtils.getCellData(i, Constants.kLoggedInColumn, Constants.kMainSheet);

      if ("TRUE".equalsIgnoreCase(loggedIn != null ? loggedIn.trim() : "")) {
        logoutUser(userID);
      }
    }

    Platform.runLater(() -> GrizzlyScene.setMessageBoxText("All users have been logged out."));
  }

  // checks if ID is valid long and x digit number (x based on config file)
  public boolean isValidID(String userID) {
    int idLength = LocalDbActivity.kIdLength;
    int mentorIdLength = LocalDbActivity.kIdLengthFallback;

    try {
      Long.parseLong(userID);

      if (Constants.kMentorFallback) {
        return userID.length() == idLength || userID.length() == mentorIdLength;

      } else {
        return userID.length() == idLength;
      }

    } catch (NumberFormatException e) {
      // not a valid ID
      return false;
    }
  }
}
