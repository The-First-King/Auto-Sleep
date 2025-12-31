# Auto Sleep

Auto Sleep is a root-based utility for people who want a truly quiet phone during sleep hours — at night or during daytime naps (e.g., shift work).

**Scheduled Sleep mode**
  - Automatically enters Sleep mode at your configured start time and exits at your configured end time.
  - Runs on selected weekdays.
  - Restores your previous settings on exit.
  
> **Root access is required**. The app uses `su` commands to toggle radios and enforce Doze.

## How it works

It can run on a schedule and also provides a **Quick Settings tile** for an instant, manual override (tap ON/OFF).

When Sleep mode turns **ON** (by schedule or tile), Auto Sleep:

1. Takes a **snapshot** of current states (Airplane / Wi‑Fi / Bluetooth).
2. Applies Sleep mode actions (root commands):
   - Airplane Mode **ON**
   - Wi‑Fi **OFF**
   - Bluetooth **OFF**
   - Force **Doze** idle
3. Shows a persistent notification (optional in Settings).

When Sleep mode turns **OFF** (scheduled end, tile tap, or “Stop now”), Auto Sleep:

1. Unforces **Doze**.
2. Restores the pre-sleep **snapshot** (always restores the original Airplane/Wi‑Fi/Bluetooth states).
3. Removes the persistent notification.
4. Keeps the schedule enabled (so it can re-enter Sleep mode next time).

> About Doze: Android defers network/background work during Doze and periodically allows brief maintenance windows.  
> https://developer.android.com/training/monitoring-device-state/doze-standby

## Screenshots

<div align="center">
  <img src="https://github.com/The-First-King/Auto-Sleep/blob/master/metadata/en-US/images/phoneScreenshots/01.png?raw=true" alt="App UI" width="405" />
  <img src="https://github.com/The-First-King/Auto-Sleep/blob/master/metadata/en-US/images/phoneScreenshots/02.png?raw=true" alt="App Settings" width="405" />
</div>

## Permissions & Privacy

Auto Sleep is designed to do one job (enter/exit Sleep mode) and not collect any personal data.

### Why permissions are used

- **Root access**  
  Required to execute system-level commands like toggling radios and forcing Doze.  
- **Exact alarm scheduling**  
  Used to trigger Sleep mode start/end at precise times (schedule reliability under idle modes).
- **Run at startup**  
  Ensures your schedule can be restored after reboot.
- **Notifications**  
  Used for the persistent “Sleep mode active” notification and the “Stop now” action.  

### Privacy

- **No Internet permission**: the app cannot send data to servers.
- **No personal data access**: it does not read contacts, location, messages, or files.
- **Open source**: you can review exactly what it does.

## Installation & license

<a href="https://github.com/The-First-King/Auto-Sleep/releases"><img src="images/GitHub.png" alt="Get it on GitHub" height="60"></a>
<a href="https://apt.izzysoft.de/packages/com.mine.autosleep"><img src="images/IzzyOnDroid.png" alt="Get it at IzzyOnDroid" height="60"></a>

---

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT.

---
