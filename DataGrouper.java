import java.time.LocalDateTime;

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
