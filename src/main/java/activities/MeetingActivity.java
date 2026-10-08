package activities;

import databases.DatabaseProcess;
import helpers.Constants;
import helpers.LoggingUtils;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import scenes.GrizzlyScene;

/** Activates the meeting configured for today and closes it at its scheduled end time. */
public class MeetingActivity {
  private static MeetingActivity activeInstance;
  private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm");
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread thread = new Thread(r, "meeting-scheduler");
    thread.setDaemon(true);
    return thread;
  });
  private final DatabaseProcess database = new DatabaseProcess();
  private final UserActivity users = new UserActivity();
  private String endedMeetingKey;
  private LocalDateTime extendedEndTime;

  public MeetingActivity() {
    activeInstance = this;
  }

  public static MeetingActivity getActiveInstance() {
    return activeInstance;
  }

  public synchronized void endMeeting() {
    endedMeetingKey = "manual-" + LocalDate.now();
    users.logoutAllUsers();
    updateMeetingRow("ENDED", LocalTime.now());
    GrizzlyScene.setMessageBoxText("Meeting ended. Everyone has been logged out.");
  }

  public synchronized void extendMeeting(long hours, long minutes) {
    LocalDateTime base = extendedEndTime != null ? extendedEndTime : LocalDateTime.now();
    extendedEndTime = base.plus(Duration.ofHours(hours).plusMinutes(minutes));
    GrizzlyScene.setMessageBoxText("Meeting extended until " + extendedEndTime.toLocalTime());
  }

  public synchronized void startMeeting(String name, long hours, long minutes) {
    try {
      List<List<Object>> rows = database.returnWorksheetData(Constants.kMeetingsSheet);
      int row = rows == null ? 2 : rows.size() + 1;
      LocalDateTime start = LocalDateTime.now();
      LocalDateTime end = start.plus(Duration.ofHours(hours).plusMinutes(minutes));
      database.updateSpreadSheet(row, 1, String.valueOf(row - 1), Constants.kMeetingsSheet);
      database.updateSpreadSheet(row, 2, name, Constants.kMeetingsSheet);
      database.updateSpreadSheet(row, 3, start.toLocalDate().toString(), Constants.kMeetingsSheet);
      database.updateSpreadSheet(row, 4, start.toLocalTime().withSecond(0).withNano(0).toString(), Constants.kMeetingsSheet);
      database.updateSpreadSheet(row, 5, end.toLocalTime().withSecond(0).withNano(0).toString(), Constants.kMeetingsSheet);
      database.updateSpreadSheet(row, 6, "ACTIVE", Constants.kMeetingsSheet);
      extendedEndTime = end;
      endedMeetingKey = null;
      GrizzlyScene.setMessageBoxText("Meeting started: " + name);
    } catch (Exception e) {
      LoggingUtils.log(Level.WARNING, e);
      GrizzlyScene.setMessageBoxText("Unable to start meeting. Check Google Sheets access.");
    }
  }

  public void start() {
    scheduler.scheduleWithFixedDelay(this::checkMeeting, 0, 30, TimeUnit.SECONDS);
  }

  public boolean isActiveNow() {
    try {
      List<List<Object>> rows = database.returnWorksheetData(Constants.kMeetingsSheet);
      if (rows == null) return false;
      LocalDateTime now = LocalDateTime.now();
      for (int i = 1; i < rows.size(); i++) {
        List<Object> row = rows.get(i);
        if (row.size() < 5) continue;
        LocalDate date = parseDate(value(row, 2));
        LocalTime start = LocalTime.parse(value(row, 3), TIME);
        LocalTime end = LocalTime.parse(value(row, 4), TIME);
        if (row.size() > 6 && !value(row, 6).isEmpty()) {
          end = LocalTime.parse(value(row, 6), TIME);
        }
        if ("ACTIVE".equalsIgnoreCase(row.size() > 5 ? value(row, 5) : "")
            && !now.isBefore(LocalDateTime.of(date, start))
            && now.isBefore(LocalDateTime.of(date, end))) return true;
      }
    } catch (Exception e) {
      LoggingUtils.log(Level.WARNING, e);
    }
    return false;
  }

  private void checkMeeting() {
    try {
      List<List<Object>> rows = database.returnWorksheetData(Constants.kMeetingsSheet);
      if (rows == null) {
        GrizzlyScene.setMeetingStatus("No active meeting");
        return;
      }
      LocalDateTime now = LocalDateTime.now();
      boolean active = false;
      for (int i = 1; i < rows.size(); i++) {
        List<Object> row = rows.get(i);
        if (row.size() < 5) continue;
        LocalDate date = parseDate(value(row, 2));
        LocalTime start = LocalTime.parse(value(row, 3), TIME);
        LocalTime end = LocalTime.parse(value(row, 4), TIME);
        LocalDateTime endDateTime = LocalDateTime.of(date, end);
        String status = row.size() > 5 ? value(row, 5) : "";
        if (row.size() > 6 && !value(row, 6).isEmpty()) {
          endDateTime = LocalDateTime.of(date, LocalTime.parse(value(row, 6), TIME));
        }
        if (extendedEndTime != null && date.equals(now.toLocalDate())) {
          endDateTime = extendedEndTime;
        }
        if (!date.equals(now.toLocalDate())) continue;
        String key = date + "-" + end;

        if ((now.isEqual(endDateTime) || now.isAfter(endDateTime))
            && !key.equals(endedMeetingKey)
            && !"PROCESSED".equalsIgnoreCase(status)
            && !"ENDED".equalsIgnoreCase(status)) {
          endedMeetingKey = key;
          users.logoutAllUsers();
          updateMeetingRow("ENDED", endDateTime.toLocalTime());
          GrizzlyScene.setMeetingStatus("No active meeting");
          GrizzlyScene.setMessageBoxText("Meeting ended. Everyone has been logged out.");
          return;
        }
        if (!now.isBefore(LocalDateTime.of(date, start)) && now.isBefore(endDateTime)) {
          active = true;
          GrizzlyScene.setMeetingStatus("Active meeting: " + value(row, 1));
          return;
        }
      }
      if (!active) GrizzlyScene.setMeetingStatus("No active meeting");
    } catch (Exception e) {
      LoggingUtils.log(Level.WARNING, e);
    }
  }

  private String value(List<Object> row, int index) {
    return row.get(index).toString().trim();
  }

  private LocalDate parseDate(String text) {
    String[] parts = text.trim().split("-");
    return LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
  }

  private void updateMeetingRow(String status, LocalTime endTime) {
    try {
      List<List<Object>> rows = database.returnWorksheetData(Constants.kMeetingsSheet);
      if (rows == null) return;
      for (int i = 1; i < rows.size(); i++) {
        List<Object> row = rows.get(i);
        if (row.size() >= 3 && LocalDate.now().toString().equals(value(row, 2))) {
          database.updateSpreadSheet(i + 1, 6, status, Constants.kMeetingsSheet);
          database.updateSpreadSheet(i + 1, 7, endTime.toString(), Constants.kMeetingsSheet);
          return;
        }
      }
    } catch (Exception e) {
      LoggingUtils.log(Level.WARNING, e);
    }
  }
}
