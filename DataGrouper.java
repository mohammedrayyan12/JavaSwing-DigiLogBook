
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class SessionRecord {
    String usn, name, sessionId;
    LocalDateTime loginTime, logoutTime;
    Map<String, String> attributes;

    public SessionRecord(String loginTime, String logoutTime, String usn, String name, Map<String, String> attributes, String sessionId) {
        this.loginTime = LocalDateTime.parse(loginTime);
        if (logoutTime == null || logoutTime.equals("null"))
            this.logoutTime = null;
        else
            this.logoutTime = LocalDateTime.parse(logoutTime);
        this.usn = usn;
        this.name = name;
        this.attributes = attributes;
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

    public String getSessionId() {
        return this.sessionId;
    }
}

class SessionGroup {
    public Map<String,String> attributes;
    public String slot;
    public String date;
    public List<SessionRecord> records = new ArrayList<>();

    public SessionGroup(String date, String slot, Map<String, String> attributes) {
        this.date = date;
        this.slot = slot;
        this.attributes = attributes;
    }
}

class SessionGrouper {

    public static List<SessionGroup> groupSessions(List<SessionRecord> allRecords) {
        // Group by date+slot+dept+sem+batch
        Map<String, List<SessionRecord>> grouped = new HashMap<>();

        for (SessionRecord r : allRecords) {
            StringBuilder attrKey = new StringBuilder();
            r.attributes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> attrKey.append(e.getValue()).append("_"));

            String key = attrKey.toString() + getSlot(r.getLoginTime(), r.getLogoutTime()) + r.getLoginTime().toLocalDate();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }

        List<SessionGroup> result = new ArrayList<>();

        for (Map.Entry<String, List<SessionRecord>> entry : grouped.entrySet()) {
            List<SessionRecord> groupRecords = entry.getValue();

            // Sort by login time
            groupRecords.sort(Comparator.comparing(r -> r.usn));
            SessionRecord first_ = groupRecords.get(0);

            // Date Comparision
			DateTimeFormatter formatter1 = DateTimeFormatter.ofPattern("yyyy-MM-dd");
			DateTimeFormatter formatter2 = DateTimeFormatter.ofPattern("yyyy-MM-dd, EE");
			LocalDate selectedFromDate = LocalDate.parse(DataPlace.dateFrom.getText(), formatter2);
			LocalDate selectedToDate = (DataPlace.dateTo.isVisible())
					? LocalDate.parse(DataPlace.dateTo.getText(), formatter2)
					: selectedFromDate;
			String recorDate = first_.getLoginTime().toLocalDate().toString();
			LocalDate entryDate = LocalDate.parse(recorDate, formatter1);

			// slot comparision
			String slot = getSlot(first_.getLoginTime(), first_.getLogoutTime());
			String[] from_to_time = slot.split("\\s*-\\s*");
			LocalTime selectedFromTime = LocalTime.parse(DataPlace.startTime.getSelectedItem().toString());
			String selectedToTime = DataPlace.endTime.getSelectedItem().toString();
			boolean conn;
			if (selectedToTime.equals("Ongoing") || from_to_time[1].equals("Ongoing"))
				if (from_to_time[1].equals("Ongoing"))
					conn = true;
				else
					conn = false;
			else
				conn = !LocalTime.parse(from_to_time[1]).isAfter(LocalTime.parse(selectedToTime));

			boolean slotCondition = (DataPlace.matchSlotConditon)
					? LocalTime.parse(from_to_time[0]).equals(selectedFromTime)
							&& (from_to_time[1].equals("Ongoing") || (from_to_time[1]).equals(selectedToTime))
					: !LocalTime.parse(from_to_time[0]).isBefore(selectedFromTime)
							&& conn;

			// Grouping
			if ((DataPlace.everything.isSelected()
					|| (!entryDate.isBefore(selectedFromDate) && !entryDate.isAfter(selectedToDate)))) {
				if (slotCondition) {

					SessionGroup sg_ = new SessionGroup(recorDate, slot, first_.attributes);
					sg_.records.addAll(groupRecords);
					result.add(sg_);
				}
			}
        }
        Comparator<SessionGroup> timeComparator = Comparator.comparing((SessionGroup r) -> r.date + r.slot);
        result.sort(timeComparator.reversed());

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
