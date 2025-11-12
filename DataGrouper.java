
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class SessionRecord {
    String usn, name, sem, dept, sub, batch, sessionId;
    LocalDateTime loginTime, logoutTime;

    public SessionRecord(String loginTime, String logoutTime, String usn, String name, String sem, String dept,
            String sub, String batch, String sessionId) {
        this.loginTime = LocalDateTime.parse(loginTime);
        if (logoutTime == null || logoutTime.equals("null"))
            this.logoutTime = null;
        else
            this.logoutTime = LocalDateTime.parse(logoutTime);
        this.usn = usn;
        this.name = name;
        this.sem = sem;
        this.dept = dept;
        this.sub = sub;
        this.batch = batch;
        this.sessionId = sessionId;
    }

    public LocalDateTime getLoginTime() {
        return this.loginTime;
    }

    public LocalDateTime getLogoutTime() {
        return this.logoutTime;
    }

    public String getUsn() {
        return this.usn;
    }

    public String getName() {
        return this.name;
    }

    public String getSem() {
        return this.sem;
    }

    public String getDept() {
        return this.dept;
    }

    public String getSub() {
        return this.sub;
    }

    public String getBatch() {
        return this.batch;
    }

    public String getSessionId() {
        return this.sessionId;
    }
}

class SessionGroup {
    public String sub;
    public String dept;
    public String sem;
    public String batch;
    public String slot;
    public String date;
    public List<SessionRecord> records = new ArrayList<>();

    public SessionGroup(String date, String slot, String sub, String dept, String sem, String batch) {
        this.date = date;
        this.slot = slot;
        this.sub = sub;
        this.dept = dept;
        this.sem = sem;
        this.batch = batch;
    }
}

class SessionGrouper {

    public static List<SessionGroup> groupSessions(List<SessionRecord> allRecords) {
        // Group by date+slot+dept+sem+batch
        Map<String, List<SessionRecord>> grouped = new HashMap<>();

        for (SessionRecord r : allRecords) {
            String key = r.getSub() + "_" + r.getDept() + "_" + r.getSem() + "_" + r.getBatch() + "_"
                    + getSlot(r.getLoginTime(), r.getLogoutTime())
                    + r.getLoginTime().toLocalDate();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }

        List<SessionGroup> result = new ArrayList<>();

        for (Map.Entry<String, List<SessionRecord>> entry : grouped.entrySet()) {
            List<SessionRecord> groupRecords = entry.getValue();

            // Sort by login time
            groupRecords.sort(Comparator.comparing(r -> r.loginTime));
            SessionRecord first_ = groupRecords.get(0);

            String recorDate = first_.getLoginTime().toLocalDate().toString();

            // slot comparision
            String slot = getSlot(first_.getLoginTime(), first_.getLogoutTime());

            SessionGroup sg_ = new SessionGroup(recorDate, slot, first_.sub, first_.dept, first_.sem,
                    first_.batch);
            sg_.records.addAll(groupRecords);
            result.add(sg_);
        }

        return result;
    }

    private static String getSlot(LocalDateTime starts, LocalDateTime ends) {
        LocalTime start = starts.toLocalTime();
        LocalTime end = (ends == null) ? null : ends.toLocalTime();

        String from = start.toString() + " - ";
        String to = "16:00";
        if (!start.isBefore(LocalTime.of(13, 30, 0)) && !start.isAfter(LocalTime.of(14, 20, 0)))
            from = "13:30 - ";
        else if (!start.isBefore(LocalTime.of(12, 05, 0)) && !start.isAfter(LocalTime.of(12, 55, 0)))
            from = "12:05 - ";
        else if (!start.isBefore(LocalTime.of(11, 15, 0)) && !start.isAfter(LocalTime.of(12, 05, 0)))
            from = "11:15 - ";
        else if (!start.isBefore(LocalTime.of(10, 10, 0)) && !start.isAfter(LocalTime.of(11, 0, 0)))
            from = "10:00 - ";
        else if (!start.isBefore(LocalTime.of(9, 20, 0)) && !start.isAfter(LocalTime.of(10, 10, 0)))
            from = "09:20 - ";
        else if (!start.isBefore(LocalTime.of(8, 30, 0)) && !start.isAfter(LocalTime.of(9, 20, 0)))
            from = "08:30 - ";

        if (end == null)
            to = "Ongoing";
        else if (!end.isBefore(LocalTime.of(14, 20, 0)))
            to = "14:20";
        else if (!end.isBefore(LocalTime.of(12, 05, 0)) && !end.isAfter(LocalTime.of(12, 55, 0)))
            to = "12:55";
        else if (!end.isBefore(LocalTime.of(11, 15, 0)) && !end.isAfter(LocalTime.of(12, 05, 0)))
            to = "12:05";
        else if (!end.isBefore(LocalTime.of(10, 10, 0)) && !end.isAfter(LocalTime.of(11, 0, 0)))
            to = "11:00";
        else if (!end.isBefore(LocalTime.of(9, 20, 0)) && !end.isAfter(LocalTime.of(10, 10, 0)))
            to = "10:10";
        else if (!end.isBefore(LocalTime.of(8, 30, 0)) && !end.isAfter(LocalTime.of(9, 20, 0)))
            to = "09:20";

        return from + to;
    }
}
