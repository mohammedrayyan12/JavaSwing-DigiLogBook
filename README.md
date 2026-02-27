# 📚 DigiLogBook

<div align="center">

![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Swing](https://img.shields.io/badge/Swing-GUI-blue?style=for-the-badge)
![SQLite](https://img.shields.io/badge/SQLite-07405E?style=for-the-badge&logo=sqlite&logoColor=white)
![Supabase](https://img.shields.io/badge/Supabase-3ECF8E?style=for-the-badge&logo=supabase&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-02303A?style=for-the-badge&logo=gradle&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)

**A modern desktop application for managing lab session logbooks with cloud synchronization**

[Features](#-features) • [Installation](#-installation) • [Cloud Setup](#-cloud-setup) • [Usage](#-usage) • [Screenshots](#-screenshots) • [Contributing](#-contributing)

</div>

---

## 🔗 Project Structure

DigiLogBook is a distributed system consisting of two main components:

* **Server - DigiLogBook (This Repo):** Developed by [@mohammedrayyan12](https://github.com/mohammedrayyan12). Manages cloud integration via Supabase Edge Functions, data persistence, and fail-safe synchronization logic.
* **Client - Session Tracker:** Developed by [@CodingMirage](https://github.com/CodingMirage). Provides the desktop user interface for student attendance and local log entry.

**Client Repository:** [Session-Tracker-Client](https://github.com/CodingMirage/JavaSwing-SessionTracker)

---

## 🎯 Overview

**DigiLogBook** is a comprehensive Java Swing desktop application designed for educational institutions to manage student lab session records efficiently. It provides an intuitive interface for importing, viewing, filtering, and exporting lab attendance data with automatic cloud backup via **Supabase Edge Functions**.

### 🎓 Perfect For:

* University computer labs
* Educational institutions
* Training centers
* Any organization tracking session-based attendance

---

## 🎉 What's New in v2.0

The application has undergone a major architectural transformation:

### 🗄️ **Database Architecture Overhaul**

**Previously (v1.x):**
- Used flat-file storage (`optionsData.csv`) for configuration
- Fixed database schema with hardcoded columns
- Manual configuration file editing required

**Now (v2.0):**
- **Dynamic SQL Configuration**: All categories (Subject, Department, Batch, etc.) stored in `configuration_options` table
- **JSON-based Session Storage**: Session details stored in flexible `details` TEXT (JSON) column
- **UI-Driven Management**: Add/remove categories directly from Settings without code changes
- **Auto-Sync**: Configuration changes propagate automatically to cloud

### ☁️ **Cloud Integration Revolution**

**v2.0 uses Supabase Edge Functions instead of direct JDBC connections:**

- **Serverless Architecture**: No database credentials stored locally
- **Enhanced Security**: Admin key authentication for all cloud operations
- **Auto-Table Creation**: Tables created automatically on first verification
- **Better Error Handling**: Comprehensive feedback for cloud operations

### 🎨 **Enhanced User Experience**

- New "Configuration Details" tab in Settings
- Dynamic dropdown filters based on your configuration
- Real-time UI updates when categories change
- Improved visual feedback during cloud operations

---

## ✨ Features

### 📊 **Dynamic Data Management**

* **Configuration Manager**: A dedicated interface to manage the organizational structure of your logs
  - Add/remove categories (Subject, Department, Batch, etc.) on the fly
  - Manage items within each category (e.g., add new subject codes)
  - Changes instantly reflect in all dropdowns and filters
* **Smart Grouping**: Sessions dynamically grouped by your defined categories
* **Advanced Filtering**: Filter records based on custom-defined attributes
* **In-Memory Processing**: Fast temporary data storage for quick operations

### ☁️ **Cloud Synchronization via Edge Functions**

* **Supabase Integration**: Serverless cloud database with Edge Functions
* **Secure Communication**: Admin key authentication for all cloud requests
* **Dual Database Support**: Local SQLite + Remote Supabase PostgreSQL
* **Automatic Sync**: Seamlessly synchronize data between local and cloud
* **Auto-Table Creation**: Tables and schema created automatically on verification
* **Connection Verification**: Test cloud connection before saving credentials

### 📥 **CSV Import & Processing**

* **Bulk Import**: Import multiple CSV files simultaneously
* **Flexible Format**: Handles various CSV structures
* **Validation**: Input validation during import
* **Preview**: Review data before committing to database

### 💾 **Export Capabilities**

* **Multiple Formats**: Export to CSV or professionally formatted PDF
* **Selective Export**: Choose specific session groups to export
* **Custom PDF Layout**: Beautifully formatted PDF reports with:
  - Two-tier headers (Date/Subject/Dept | Slot/Lab/Sem/Batch)
  - Clean table layout with borders
  - Professional styling with backgrounds

### 🔄 **Automation Features**

* **Auto-Save**: Automatically backup records before scheduled deletion
* **Scheduled Cleanup**: Configurable auto-delete based on data age
  - **Local Database**: Monthly cleanup (1-12 months)
  - **Cloud Database**: Weekly cleanup (1-4 weeks)
* **Smart Scheduling**: Background task runs daily to check cleanup conditions
* **Testing Mode**: Disable deletions during development with `testing.skip.delete` flag

### 🎨 **User Interface**

* **Modern Design**: Clean, intuitive Nimbus Look & Feel
* **Interactive Calendar**: Custom date picker with month/year navigation
* **Expandable Groups**: Collapsible session groups for better organization
* **Responsive Layout**: Adapts to different screen sizes
* **Settings Dialog**: Tabbed interface for all configuration needs
* **Real-time Updates**: UI refreshes automatically when configuration changes

### 🔒 **Security & Safety**

* **Testing Mode**: Disable deletions during development/testing
* **Secure Storage**: Configuration stored in `~/.DigiLogBook/`
* **Admin Key Authentication**: All cloud requests authenticated
* **Input Validation**: Comprehensive validation throughout
* **Error Handling**: Graceful error handling with user-friendly messages

---

## 🚀 Installation

### Prerequisites

* **Java Runtime Environment (JRE) 11 or higher**
* **Supabase Account** (free tier) - for cloud sync
* **Gradle** (included via Gradle Wrapper)

### Quick Start

#### Option 1: Run from JAR (Recommended)

```bash
# Download the latest release from GitHub Releases
# Double-click DigiLogBook-v2.0.0.jar or run:
java -jar DigiLogBook-v2.0.0.jar
```

#### Option 2: Build from Source

```bash
# Clone the repository
git clone https://github.com/mohammedrayyan12/JavaSwing-DigiLogBook.git
cd JavaSwing-DigiLogBook

# Compile and run
./gradle runApp

# Build using Gradle Wrapper (no Gradle installation needed!)
./gradlew shadowJar

# Run the application
java -jar build/libs/JavaSwing-DigiLogBook-all.jar
```

**Windows users:**
```bash
# Use gradlew.bat instead
gradlew.bat shadowJar
java -jar build/libs/JavaSwing-DigiLogBook-all.jar
```

### 📦 Dependencies

All dependencies are managed by Gradle automatically:

| Library | Version | Purpose |
|---------|---------|---------|
| SQLite JDBC | 3.50.3.0 | Local database operations |
| Apache PDFBox | 3.0.5 | PDF generation |
| Gson | 2.10.1 | JSON parsing for cloud API |

---

## ☁️ Cloud Setup

**DigiLogBook v2.0 requires Supabase for cloud synchronization.**

### 📖 Complete Setup Guide

**👉 Follow the detailed guide: [SETUP_DATABASE.md](SETUP_DATABASE.md)**

The setup guide covers:
1. Creating a Supabase project
2. Running SQL permission scripts
3. Deploying Edge Functions
4. Configuring 3 required secrets (`RECORDS_TABLE_NAME`, `CONFIG_TABLE_NAME`, `ADMIN_KEY`)
5. Connecting DigiLogBook to your Supabase project

### Quick Setup Summary

1. **Create Supabase Project** at [supabase.com](https://supabase.com)
2. **Run SQL Script**: Execute `Edge Functions/database-schema.sql` in SQL Editor
3. **Add 3 Secrets** in Edge Functions settings:
   - `RECORDS_TABLE_NAME` (e.g., "sessions")
   - `CONFIG_TABLE_NAME` (e.g., "configuration_options")
   - `ADMIN_KEY` (your secure random key)
4. **Configure DigiLogBook**: Enter Project URL, Anon Key, and Admin Key
5. **Verify**: Tables auto-created on successful verification

> **⚠️ Important**: You MUST complete cloud setup before using cloud sync features. Local-only mode works without Supabase.

---

## 📖 Usage

### 1️⃣ **First Launch**

On first run, the application creates:

* Configuration directory: `~/.DigiLogBook/`
* Config file: `config.properties`
* Local database: `data.db`

### 2️⃣ **Import Session Data**

1. Click **"+ Add"** button on the main screen
2. Select one or multiple CSV file(s) containing session records
3. Data is loaded into temporary in-memory database
4. Review and filter the imported data

**CSV Format Example:**
```csv
Login Time,USN,Name,Sem,Dept,Subject,Batch,Logout Time,Session ID
2024-11-20T08:30:15,1VI21CS001,John Doe,3,CSE,Data Structures,I,2024-11-20T10:10:45,session_001
2024-11-20T08:30:20,1VI21CS002,Jane Smith,3,CSE,Data Structures,I,2024-11-20T10:10:50,session_002
```

**Field Requirements:**
- **Login/Logout Time**: ISO-8601 format (`yyyy-MM-dd'T'HH:mm:ss`)
- **USN**: University Seat Number (format: `1VI\d{2}[A-Z]{2}\d{3}`)
- **Sem**: Semester (1-8)
- **Dept**: Department (CSE, ISE, AIML, DS, ECE, MECH, CIVIL, etc.)
- **Batch**: Batch identifier (I, II, etc.)
- **Session ID**: Unique identifier per session

### 3️⃣ **View & Filter Data**

1. Click **"→ View"** to access existing records in local database
2. Use **dynamic dropdown filters** in the toolbar:
   - Filters are automatically generated based on your `configuration_options` table
   - Any new category you add will appear as a filter!
3. Click **"Select Date/Time"** for advanced filtering:
   - **All Records**: Check "All" checkbox
   - **Specific Date**: Select from calendar picker
   - **Date Range**: Uncheck "Match Date" to select range
   - **Time Slot**: Choose start and end times
   - **Match Slot**: Enable to match exact time slot

**Special Features:**
- Click **"⟳"** (Refresh) to sync with cloud database
- Expand/collapse session groups to view detailed records
- Groups show count of entries and session details

### 4️⃣ **Export Records**

1. Click **"Export"** button in the toolbar
2. A dialog shows all session groups with checkboxes
3. Select groups you want to export:
   - Use **"Select All"** to choose all groups
   - Use **"Cancel"** to deselect all
4. Choose format: **PDF** or **CSV** from dropdown
5. Select save location in file chooser
6. Click **"Export"** to generate the file

**Export Features:**
- Counter shows number of selected groups
- PDF includes professional formatting with headers
- CSV exports raw data for further processing

### 5️⃣ **Manage Configuration (NEW in v2.0)**

**This is the killer feature of v2.0!** Customize your logbook structure without touching code.

#### Access Configuration Manager

1. Click ⚙️ **Settings** icon (top-right)
2. Go to **"Configuration Details"** tab

#### Add New Category

1. Click **"+ Add / Remove Categories"** button
2. Enter new category name (e.g., "Lab Assistant", "Room No", "System ID")
3. Click **"Add Category"**
4. Category immediately appears in UI with dropdown filter!

#### Manage Items in Category

1. Select a category from the list
2. Click **"Manage [Category]"** button
3. In the dialog:
   - **Search**: Filter existing items
   - **Add New**: Enter value and click "Add"
   - **Delete**: Select item and click "Delete"
4. Changes sync to cloud automatically (if configured)

**Example Use Cases:**
- Add new subject codes for the semester
- Add new batch identifiers
- Create custom categories for your institution
- Remove outdated values

### 6️⃣ **Configure Cloud Sync**

> **📖 First Time?** Follow [SETUP_DATABASE.md](SETUP_DATABASE.md) first!

1. Click ⚙️ **Settings** icon
2. Go to **"Cloud Database"** tab
3. Click **"Add Cloud Database Info"**
4. Enter **3 required fields**:
   - **Project URL**: Your Supabase project URL
     - Format: `https://xxxxx.supabase.co`
   - **Publishable/Anon Key**: From Supabase Settings → API
   - **Admin Key**: The `ADMIN_KEY` you set in Supabase secrets
5. Click **"Verify and Save"**
6. Wait for verification:
   - Status changes to "Verifying & Setting up Cloud..." (orange)
   - Tables are auto-created in Supabase
   - Status changes to "Verified" (green) ✅

**Cloud Sync Features:**
- Click **"⟳"** (Refresh) in View mode to sync from cloud
- Configuration changes auto-sync to cloud
- Auto-save exports data before cloud cleanup

### 7️⃣ **Setup Auto-Save/Delete**

1. Go to Settings → **"Auto Save/Delete"** tab
2. Click **"Enable Auto Save"** toggle button
3. Click on directory path to select auto-save location
4. Configure cleanup duration:
   - **Local DB**: 1, 3, 6, 9, or 12 months
   - **Cloud DB**: 1, 2, 3, or 4 weeks
5. Settings saved automatically

**Features:**
- View last cleanup dates for both databases
- Testing mode warning if deletions disabled
- Auto-save exports data to CSV before deletion
- Background task runs daily

---

## ⚙️ Configuration

### config.properties

Located at: `~/.DigiLogBook/config.properties`

```properties
# Local Database
LOCAL_TABLE=student_log
JDBC_URL_local=data.db

# Cloud Configuration (Supabase)
project.url=https://xxxxx.supabase.co
anon.key=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
server.header=your_admin_key_here
CLOUD_DB_VERIFIED=true

# Auto-Save/Delete Settings
auto.save=true
auto.save.records.directory=/home/user/autoSaved_Session_Records
local.auto.delete.duration=3
cloud.auto.delete.duration=2
local.auto.delete.last.run.date=2025-02-27
cloud.auto.delete.last.run.date=2025-02-27

# Testing/Development
testing.skip.delete=false
```

### 📂 Database Schema (v2.0)

Both the **Local SQLite** cache and the **Supabase Cloud** instance share a unified schema to ensure seamless data synchronization and offline-first reliability.
```sql
-- Shared schema for Local (student_log) and Cloud (sessions)
CREATE TABLE sessions (
    session_id  TEXT PRIMARY KEY, -- Unique session identifier
    login_time  TEXT NOT NULL,    -- ISO8601 timestamp
    logout_time TEXT,             -- Nullable until session completion
    usn         TEXT NOT NULL,    -- Student registration number
    name        TEXT NOT NULL,    -- Student name
    details     TEXT              -- Flexible JSON storage for dynamic metadata
);
```
| Environment | Table Name | Storage Engine | Path / Host |
| :--- | :--- | :--- | :--- |
| **Local** | `student_log` | SQLite | `~/.DigiLogBook/data.db` |
| **Cloud** | Configurable* | PostgreSQL | Supabase Instance (auto-created on verification) |

**Table: configuration_options** (name configurable via `CONFIG_TABLE_NAME`)
```sql
CREATE TABLE configuration_options (
    id SERIAL PRIMARY KEY,
    category TEXT NOT NULL,      -- e.g., 'Subject', 'Department' (Case Sensitive)
    item_value TEXT NOT NULL     -- e.g., 'Data Structures', 'CSE' (Case Insensitive)
);
```

**The `details` field stores:**
```json
{
  "Sem": "3",
  "Department": "CSE",
  "Subject": "Data Structures",
  "Batch": "I",
  "labName": "314",
  "SysNo": "M-12",
  "custom_field": "custom_value"
}
```

---

## 📸 Screenshots

### Main Interface
<div align="center">
<img src="images/screenshot-main.png" alt="Main Interface" width="700"/>
<p><i>Clean main interface with Add and View options</i></p>
</div>

### Cloud Database Configuration
<div align="center">
<img src="images/screenshot-cloud-config.png" alt="Cloud Configuration" width="700"/>
<p><i>Cloud database setup with Project URL, Anon Key, and Admin Key</i></p>
</div>

### Configuration Management (NEW!)
<div align="center">
<img src="images/screenshot-settings-configurations.png" alt="Configuration Manager" width="700"/>
<p><i>Dynamic schema & category management interface</i></p>
</div>

### Settings - Auto Save/Delete
<div align="center">
<img src="images/screenshot-settings-AD_disable.png" alt="Settings - Disabled" width="700"/>
<p><i>Auto Save/Delete disabled state</i></p>

<img src="images/screenshot-settings-AD_enable.png" alt="Settings - Enabled" width="700"/>
<p><i>Auto Save/Delete enabled with testing mode indicator</i></p>
</div>

### Data View with Filters
<div align="center">
<img src="images/screenshot-view.png" alt="Data View" width="700"/>
<p><i>Session groups with dynamic filtering and export options</i></p>
</div>

---

## 🏗️ Project Structure

```
DigiLogBook/
├── src/main/java/
│   ├── logBookData.java          # Main application entry point
│   ├── DataPlace.java            # UI coordination and data flow
│   ├── AddLogbookManager.java    # In-memory database management
│   ├── DataGrouper.java          # Session grouping logic
│   ├── UIGroupDisplay.java       # UI components for data display
│   ├── Utils.java                # Utility classes
│   │   ├── ConfigLoader          # Configuration management
│   │   ├── OptionsManager        # Configuration options sync
│   │   ├── CloudAPI              # Edge Functions communication
│   │   ├── CloudDataBaseInfo     # Cloud verification & setup
│   │   ├── ExportCsvPdf          # Export functionality
│   │   └── DatePicker            # Custom calendar widget
│   └── autoDelete.java           # Scheduled cleanup tasks
├── Edge Functions/
│   ├── database-schema.sql       # SQL permission grants
│   ├── server-api/               # Supabase Edge Function (for Server Application)
│   │   └── index.ts              # Main Edge Function handler
│   └── client-api/               # Supabase Edge Function (for Client Application)
│       └── index.ts
├── Sample DataSet/               # Example CSV files
├── images/                       # Screenshots for documentation
├── gradle/                       # Gradle wrapper files
├── build.gradle                  # Gradle build configuration
├── gradlew / gradlew.bat         # Gradle wrapper scripts
├── README.md                     # This file
├── SETUP_DATABASE.md             # Supabase setup guide
├── databaseStructure.txt         # Database documentation
└── LICENSE                       # MIT License
```

---

## 🛠️ Development

This project uses **Gradle** for dependency management and build automation.

### Building the Project

**Using Gradle Wrapper (Recommended):**

```bash
# Clean and build
./gradlew clean shadowJar

# Run the application
java -jar build/libs/JavaSwing-DigiLogBook-all.jar
```

**Windows:**
```bash
gradlew.bat clean shadowJar
java -jar build/libs/JavaSwing-DigiLogBook-all.jar
```

### Testing Mode

Enable testing mode to prevent data deletion during development:

```properties
# In config.properties
testing.skip.delete=true
```

Console output will show:
```
⚠️ TESTING MODE: Skipping delete operation
   (Set testing.skip.delete=false to enable deletion)
```

### Development Guidelines

* **Code Style**: Follow existing Java conventions
* **Comments**: Add JavaDoc for public methods
* **Testing**: Test thoroughly with `testing.skip.delete=true`
* **Commits**: Use conventional commit messages:
  - `feat:` New feature
  - `fix:` Bug fix
  - `docs:` Documentation
  - `refactor:` Code refactoring
  - `test:` Tests

---

## 🤝 Contributing

Contributions are welcome! Here's how you can help:

### How to Contribute

1. **Fork** the repository
2. **Create** a feature branch
   ```bash
   git checkout -b feature/AmazingFeature
   ```
3. **Commit** your changes
   ```bash
   git commit -m 'feat: add some AmazingFeature'
   ```
4. **Push** to the branch
   ```bash
   git push origin feature/AmazingFeature
   ```
5. **Open** a Pull Request

### Areas for Contribution

* 🐛 Bug fixes
* ✨ New features
* 📝 Documentation improvements
* 🎨 UI/UX enhancements
* 🧪 Test coverage
* 🌐 Internationalization (i18n)

---

## 🐛 Known Issues & Roadmap

### Current Limitations

* ⚠️ Admin Key stored in plaintext in config file (encryption planned)
* ⚠️ No multi-user concurrent access handling
* ⚠️ Limited to single-window interface
* ⚠️ No undo functionality for deletions

### Planned Features (v2.1)

**High Priority:**
* 🔐 Encrypted storage for Admin Key
* 📊 Dashboard with analytics and charts
* 🔍 Advanced search with query builder
* 📧 Email notifications for scheduled tasks
* 🔄 Real-time cloud sync (WebSocket)

**Medium Priority:**
* 🌐 Multi-language support (i18n)
* 👥 User authentication and role-based access
* 📱 Mobile companion app
* 🎨 Dark mode theme

**Future Considerations:**
* 📈 Statistical reports and graphs
* 🔔 Desktop notifications
* 🗄️ Connection pooling for better performance
* 🧪 Comprehensive unit test coverage
* 🔌 Plugin system for extensions

---

## 📄 License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

```
MIT License

Copyright (c) 2025 mohammedrayyan12

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:
...
```

---

## 👨‍💻 Author

**Mohammed Rayyan**

* GitHub: [@mohammedrayyan12](https://github.com/mohammedrayyan12)
* Project Link: [https://github.com/mohammedrayyan12/JavaSwing-DigiLogBook](https://github.com/mohammedrayyan12/JavaSwing-DigiLogBook)

---

## 🙏 Acknowledgments

* **Apache PDFBox** - Excellent PDF generation library
* **SQLite** - Lightweight, reliable local database
* **Supabase** - Modern, open-source Firebase alternative with Edge Functions
* **Gradle** - Powerful build automation tool
* **Gson** - JSON parsing library
* **Java Swing** - Rich GUI toolkit for desktop applications

---

## 📞 Support & Troubleshooting

### Common Issues

**Issue: "Connection to cloud database failed"**
* Solution: Follow [SETUP_DATABASE.md](SETUP_DATABASE.md) completely
* Verify all 3 secrets are set in Supabase
* Check Admin Key matches exactly

**Issue: "CSV import failed"**
* Verify CSV format matches template
* Check for special characters in data
* Ensure all required columns present

**Issue: "Configuration changes not syncing"**
* Verify cloud connection is active
* Check console for sync errors
* Try clicking "⟳" Refresh manually

**Issue: "Testing mode warning in settings"**
* Set `testing.skip.delete=false` in config.properties
* Restart application

### Getting Help

If you encounter issues:

1. Check the [Issues](https://github.com/mohammedrayyan12/JavaSwing-DigiLogBook/issues) page
2. Search existing issues for solutions
3. Create a new issue with:
   * Detailed description
   * Steps to reproduce
   * System information (OS, Java version)
   * Error logs from console
   * Screenshots (if applicable)

---

<div align="center">

**⭐ If you find this project useful, please consider giving it a star! ⭐**

Made with ❤️ for educational institutions

[Report Bug](https://github.com/mohammedrayyan12/JavaSwing-DigiLogBook/issues) · [Request Feature](https://github.com/mohammedrayyan12/JavaSwing-DigiLogBook/issues) · [View Demo](https://github.com/mohammedrayyan12/JavaSwing-DigiLogBook/tree/v2/Sample%20DataSet)

</div>