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
  private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("h:mm a");
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread thread = new Thread(r, "meeting-scheduler");
    thread.setDaemon(true);
    return thread;
  });
  private final DatabaseProcess database = new DatabaseProcess();
  private final UserActivity users = new UserActivity();
  private String endedMeetingKey;
  private LocalDateTime extendedEndTime;
  private Integer activeMeetingRow;

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
      LocalDateTime start = LocalDateTime.now();
      int row = findUpcomingMeetingRow(rows, name, start);

      if (row > 0) {
        // Start the preset early, preserving its programmed end time.
        database.updateSpreadSheet(row, 3, start.toLocalDate().toString(), Constants.kMeetingsSheet);
        database.updateSpreadSheet(row, 4, start.toLocalTime().withSecond(0).withNano(0).toString(), Constants.kMeetingsSheet);
      } else {
        row = rows == null ? 2 : rows.size() + 1;
        LocalDateTime end = start.plus(Duration.ofHours(hours).plusMinutes(minutes));
        database.updateSpreadSheet(row, 1, String.valueOf(row - 1), Constants.kMeetingsSheet);
        database.updateSpreadSheet(row, 2, name, Constants.kMeetingsSheet);
        database.updateSpreadSheet(row, 3, start.toLocalDate().toString(), Constants.kMeetingsSheet);
        database.updateSpreadSheet(row, 4, start.toLocalTime().withSecond(0).withNano(0).toString(), Constants.kMeetingsSheet);
        database.updateSpreadSheet(row, 5, end.toLocalTime().withSecond(0).withNano(0).toString(), Constants.kMeetingsSheet);
        extendedEndTime = end;
      }
      database.updateSpreadSheet(row, 6, "ACTIVE", Constants.kMeetingsSheet);
      activeMeetingRow = row;
      endedMeetingKey = null;
      GrizzlyScene.setMessageBoxText("Meeting started: " + name);
    } catch (Exception e) {
      LoggingUtils.log(Level.WARNING, e);
      GrizzlyScene.setMessageBoxText("Unable to start meeting. Check Google Sheets access.");
    }
  }

  private int findUpcomingMeetingRow(List<List<Object>> rows, String name, LocalDateTime now) {
    if (rows == null) return -1;
    LocalDateTime nextStart = null;
    int nextRow = -1;

    for (int i = 1; i < rows.size(); i++) {
      List<Object> row = rows.get(i);
      if (row.size() < 6 || !name.equalsIgnoreCase(value(row, 1))) continue;
      String status = value(row, 5);
      if ("ENDED".equalsIgnoreCase(status) || "PROCESSED".equalsIgnoreCase(status)) continue;
      try {
        LocalDateTime start = LocalDateTime.of(parseDate(value(row, 2)), parseTime(value(row, 3)));
        if (start.isAfter(now) && (nextStart == null || start.isBefore(nextStart))) {
          nextStart = start;
          nextRow = i + 1;
        }
      } catch (Exception e) {
        LoggingUtils.log(Level.FINE, "Skipping invalid scheduled meeting row: " + e.getMessage());
      }
    }
    return nextRow;
  }

  public void start() {
    scheduler.scheduleWithFixedDelay(this::checkMeeting, 0, 30, TimeUnit.SECONDS);
  }

  public void refreshNow() {
    GrizzlyScene.setMessageBoxText("Refreshing meeting schedule...");
    scheduler.execute(
        () -> {
          checkMeeting();
          GrizzlyScene.setMessageBoxText("Meeting schedule refreshed.");
        });
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
        LocalTime start = parseTime(value(row, 3));
        LocalTime end = parseTime(value(row, 4));
        if (row.size() > 6 && !value(row, 6).isEmpty()) {
          end = parseTime(value(row, 6));
        }
        if (isScheduledStatus(row.size() > 5 ? value(row, 5) : "")
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
        LocalTime start = parseTime(value(row, 3));
        LocalTime end = parseTime(value(row, 4));
        LocalDateTime endDateTime = LocalDateTime.of(date, end);
        String status = row.size() > 5 ? value(row, 5) : "";
        if (row.size() > 6 && !value(row, 6).isEmpty()) {
          endDateTime = LocalDateTime.of(date, parseTime(value(row, 6)));
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
        if (isScheduledStatus(status)
            && !now.isBefore(LocalDateTime.of(date, start))
            && now.isBefore(endDateTime)) {
          active = true;
          activeMeetingRow = i + 1;
          GrizzlyScene.setMeetingStatus("Active meeting: " + value(row, 1));
          return;
        }
      }
      if (!active) GrizzlyScene.setMeetingStatus(getUpcomingMeetingStatus(rows, now));
    } catch (Exception e) {
      LoggingUtils.log(Level.WARNING, e);
    }
  }

  private String getUpcomingMeetingStatus(List<List<Object>> rows, LocalDateTime now) {
    LocalDateTime nextStart = null;
    String nextName = null;

    for (int i = 1; i < rows.size(); i++) {
      List<Object> row = rows.get(i);
      if (row.size() < 5) continue;

      try {
        String status = row.size() > 5 ? value(row, 5) : "";
        if ("ENDED".equalsIgnoreCase(status) || "PROCESSED".equalsIgnoreCase(status)) continue;

        LocalDateTime start =
            LocalDateTime.of(parseDate(value(row, 2)), parseTime(value(row, 3)));
        if (start.isAfter(now) && (nextStart == null || start.isBefore(nextStart))) {
          nextStart = start;
          nextName = value(row, 1);
        }
      } catch (Exception e) {
        LoggingUtils.log(Level.FINE, "Skipping invalid meeting row: " + e.getMessage());
      }
    }

    if (nextStart == null) return "No active meeting";
    return "Upcoming meeting: " + nextName + " at " + nextStart.toLocalTime().format(DISPLAY_TIME);
  }

  private String value(List<Object> row, int index) {
    return row.get(index).toString().trim();
  }

  private boolean isScheduledStatus(String status) {
    return status == null || status.isEmpty() || "ACTIVE".equalsIgnoreCase(status);
  }

  private LocalTime parseTime(String text) {
    try {
      return LocalTime.parse(text, TIME);
    } catch (Exception e) {
      return LocalTime.parse(text, DateTimeFormatter.ISO_LOCAL_TIME);
    }
  }

  private LocalDate parseDate(String text) {
    String[] parts = text.trim().split("-");
    return LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
  }

  private void updateMeetingRow(String status, LocalTime endTime) {
    try {
      List<List<Object>> rows = database.returnWorksheetData(Constants.kMeetingsSheet);
      if (rows == null) return;
      if (activeMeetingRow != null && activeMeetingRow > 1 && activeMeetingRow <= rows.size()) {
        database.updateSpreadSheet(activeMeetingRow, 6, status, Constants.kMeetingsSheet);
        database.updateSpreadSheet(activeMeetingRow, 7, endTime.toString(), Constants.kMeetingsSheet);
        activeMeetingRow = null;
        return;
      }
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
