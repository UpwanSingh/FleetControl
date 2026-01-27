# 🚛 Fleet Control Details: Owner's Master Manual

Welcome to **Fleet Control**, your premium offline fleet management system. This guide covers every single feature, secret, and workflow in the app.

---

## 1. Getting Started: The "Device Lock"
Your app is protected by a military-grade **Device Lock**. It will not open on *any* phone without a specific License Key generated for that exact device.

### 🔑 How to Unlock Your Phone (Owner Mode)
1.  Open the App. You will see the **"Device Locked"** screen.
2.  Tap the **Device ID** code to copy it.
3.  Open the `FleetKey.html` tool on your computer/browser.
4.  Paste the Device ID and click **GENERATE**.
5.  Copy the 8-character Key.
6.  Paste it into the app and click **ACTIVATE**.
7.  **Set your Owner PIN.** (Documentation: *Keep this PIN secret. It locks your financial data.*)

---

## 2. Adding & Onboarding Drivers
You have two ways to add drivers. Use the "Magic Link" for the fastest experience.

### Method A: The Magic Link (Recommended) 🪄
*Best for remote drivers or quick setup.*
1.  Go to **Owner Dashboard > Drivers**.
2.  Click the **+ (Add)** button. Enter the Driver's Name, Phone, and a 4-digit PIN (give this PIN to them).
3.  Once added, find them in the list and click the **Share Icon** <img src="share_icon.png" height="15"/>.
4.  Send the file via **WhatsApp** to the driver.
5.  **Instruction for Driver:** "Install the app, then tap this file and select 'Open in Fleet Control'."
    *   *Result:* Their app will unlock instantly in **Driver Mode Only**. They cannot access your Owner Dashboard.

### Method B: Manual Activation
*Use this if you don't use WhatsApp.*
1.  Install the app on the Driver's phone.
2.  Open it. Copy their Device ID.
3.  Generate a Key using `FleetKey.html`.
4.  Enter the Key on their phone.
5.  **Important:** Do NOT enter your Owner PIN on their phone. Just click "Driver" and select their name (you must have added them first).

---

## 3. The Money Engine: Managing Finances 💰

### Setting Rates (Critical)
The app calculates profits based on "Slabs". You must configure this for accurate math.
*   **Path:** Owner Dashboard > Settings (Gear Icon) > Rate Configuration.
*   **Concepts:**
    *   **Base Rate:** What you charge per km (e.g., ₹20/km).
    *   **Driver Commission:** Percentage the driver keeps (e.g., 10%).
    *   **Fuel Logic:** The app automatically deducts fuel costs logged by drivers from the *Gross Profit*.

### Viewing Profits
*   **Path:** Owner Dashboard > Profits.
*   **Real-Time Data:** This screen updates instantly as drivers finish trips.
*   **Net Profit:** = (Total Trip Value) - (Fuel Costs) - (Driver Commission/Salary) - (Maintenance).

---

## 4. Daily Operations

### The Driver's Workflow
1.  Driver logs in with their 4-digit PIN.
2.  **Start Trip:** They enter "Opening KM" and "Customer Name".
3.  **End Trip:** They enter "Closing KM" and "Payment Collected".
4.  **Fuel:** They click "Add Fuel" and upload a photo of the receipt/meter.

### The Owner's Workflow (Validation)
1.  Go to **Owner Dashboard > Audit Logs**.
2.  You will see a feed of every action: "Ramesh started trip", "Suresh added fuel".
3.  **To Approve Expenses:** Go to **Reports > Expenses**. Check the fuel receipts. If one looks fake, you can mark it (Delete/Edit).

---

## 5. Security & Data Safety 🛡️

### "Offline First" Architecture
*   **Where is my data?** It is 100% on **YOUR phone**. Not in the cloud. Not on our servers.
*   **Benefit:** Zero monthly fees, perfect privacy.
*   **Risk:** If you lose your phone, you lose your data.

### ⚡ CRITICAL: How to Backup
1.  Go to **Settings > Backup & Restore**.
2.  Click **CREATE BACKUP**.
3.  This creates a strictly encrypted file.
4.  **Send this file to your Email or Google Drive immediately.**
5.  *Tip:* Do this weekly.

### What if a Driver quits?
1.  Go to **Drivers List**.
2.  Swipe left on their name (or click details > Edit).
3.  Set **Status** to **Inactive**.
4.  Their login PIN will stop working immediately.

---

## 6. Advanced Features
*   **PDF Exports:** Go to Reports > Export Icon. You can generate a "Monthly Performance Report" to share with investors or partners.
*   **Dark Mode:** The app looks stunning in low light. Toggle it in Settings.

---

*Fleet Control v1.0 - Built for the Modern Transporter.*
