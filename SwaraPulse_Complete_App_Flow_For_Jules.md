# SwaraPulse — Complete App Behaviour & Flow Description
## Written for Jules: No code. Pure behaviour, screens, and user journeys.

---

## WHAT THIS APP IS

SwaraPulse is a patient management app for Swara Yoga practitioners — think of it
like a doctor's clinic management system but built around traditional Indian yogic
evaluation principles. A practitioner uses this app daily to record patient visits,
track yogic parameters like Nadi and Element readings, schedule appointments, and
view analytics about their practice over time.

Everything is stored locally on the device. There is no server, no login to a
backend, no internet required. The data lives in a SQLite database on the phone.

---

## THE FIVE CORE CONCEPTS

**1. Patient** — A person who visits the practitioner. Has basic details like name,
age, mobile number, gender, blood group. A patient can have many visits over time.

**2. Visit** — A single consultation session. Every time a patient comes in, the
practitioner records a visit. A visit captures the yogic evaluation (which Nadi and
Element the patient is in), the doctor's own assessment, the lunar calendar details
at the time of the visit (Paksha and Tithi), the patient's chief complaint, and the
prescription or treatment given.

**3. Appointment** — A scheduled future slot. A patient books an appointment for a
future date. On that date, the appointment becomes a visit when the practitioner
starts the consultation.

**4. Followup** — At the end of a visit, the practitioner can set a date for the
patient to return. This is stored as a followup date on the visit record. The app
reminds the practitioner when followups are approaching.

**5. Analytics** — The app analyses all visit data to show patterns: which Nadi is
most common among patients, which elements appear most, how well the practitioner's
own Nadi aligns with patients, and what the most common complaints are.

---

## THE NAVIGATION STRUCTURE

The app has five main sections accessible from a bottom navigation bar at the bottom
of the screen. The bar is always visible except on the auth screen and form screens.

The five tabs are:
- Home (Dashboard)
- Patients
- Schedule (Appointments)
- Analytics
- Settings

---

## SCREEN-BY-SCREEN BEHAVIOUR

---

### AUTH SCREEN
**This is the first screen the app shows every time it opens.**

On first launch ever, the screen is in setup mode. It shows a PIN creation form.
The user types a 4 to 6 digit PIN, confirms it, and taps save. The PIN is stored
securely. From that point on, every app launch requires the user to enter that PIN.

On subsequent launches, the screen shows a PIN entry keypad. The user taps digits
on a custom numpad displayed on screen. As digits are entered, dots fill up to show
progress. When the correct PIN is entered, the app unlocks and goes to the Dashboard.

If the device supports fingerprint or face recognition, a biometric prompt appears
automatically. The user can authenticate with biometric instead of typing the PIN.
If biometric fails or is cancelled, the PIN keypad is shown as fallback.

The screen has a visual identity — the SwaraPulse logo in the centre, a subtle
animated ring around it, and the app name below. The background is dark.

On successful authentication, the app navigates to the Dashboard and the auth screen
is removed from the back stack so pressing back does not return to it.

---

### DASHBOARD (Home Tab)
**The main overview screen. Shows today's situation at a glance.**

At the top is a greeting that changes based on time of day — "Good Morning",
"Good Afternoon", or "Good Evening" — followed by the practitioner's name. Below
the greeting is today's date. This entire top section sits on a gradient card.

Below the greeting are four stat counters displayed in a horizontal scrollable row:
- Total active patients (patients who are not provisional and are marked active)
- Total visits ever recorded
- Visits this month
- Number of followups due in the next 48 hours

Each counter animates by counting up from zero when the screen loads.

Below the stats is a section called "Today's Appointments". This shows all
appointments scheduled for today that have not been completed or cancelled. Each
appointment is shown as a small card with the patient name, the time, and the
purpose. There is a "Start Visit" button on each card. Tapping it opens the visit
form for that patient. If there are no appointments today, a friendly empty message
appears.

Below that is "Upcoming Followups" — a list of patients who have a followup date
set in the next 7 days. Each item shows the patient name, how many days until the
followup (colour coded: red for today, amber for tomorrow, green for later), and
a snippet of the complaint from that visit. Tapping an item goes to that patient's
detail screen.

Below that is "Recent Activity" — the last 5 visits recorded across all patients.
Each item shows the patient avatar (a circle with their initial), the patient name,
the Nadi and Element chips from that visit, and how long ago the visit was. Tapping
goes to the patient detail.

At the bottom right is a floating action button. Tapping it expands into three
smaller buttons stacked vertically: "New Patient", "New Visit", "New Appointment".
Tapping any of them navigates to the relevant form. Tapping the main FAB again
collapses the options.

---

### PATIENTS SCREEN (Patients Tab)
**A browsable list of all patients.**

At the top is a search bar that is always visible. As the user types, the list
filters in real time — searching across patient name, mobile number, and email.

Next to the search bar are two icon buttons:
- A toggle to switch between grid view (two columns of cards) and list view (single
  column rows)
- A filter icon that opens a bottom sheet with filter options

The filter bottom sheet has three sections:
- Status filter chips: All, Active, Inactive, Provisional
- Gender filter chips: Any, Male, Female, Other
- Sort chips: Name A to Z, Name Z to A, Most Recent, Oldest First
There are Reset and Done buttons at the bottom.

In grid view, each patient appears as a card showing their avatar circle, name, age
and gender chip, last visit time ago, mobile number, and a "New Visit" button at the
bottom of the card. Long pressing a card opens a context menu with View Details, New
Visit, and Delete options.

In list view, each patient is a single row with avatar, name, age and gender, mobile,
and last visit time. Swiping a row to the right triggers New Visit. Swiping left
triggers Delete (with a confirmation).

At the bottom right is a "New Patient" button that opens the visit form in new
patient mode.

If no patients match the search or filters, an empty state is shown with a large
icon, a message, and an "Add First Patient" button.

---

### PATIENT DETAIL SCREEN
**Everything about one specific patient.**

Reached by tapping a patient anywhere in the app.

The top bar shows the patient's name, a back button, an edit button, a "New Visit"
plus button, and a three-dot menu. The three-dot menu has Export as PDF, Export as
Excel, and Delete Patient.

When exporting, a small loading spinner appears in the top bar. When done, the
Android share sheet opens so the practitioner can send the file via email,
WhatsApp, save to Drive, etc.

Below the top bar is a collapsible header card. When expanded it shows the full
patient profile: avatar, name, age, gender, blood group, mobile (tap to call),
email (tap to compose), address (tap to open Maps), and any category tags. A
chevron button collapses it to save space.

Below the header is a quick stats strip showing: total visits, last visit time ago,
average visits per month, and followup rate as a percentage.

Below the stats are four tabs in a scrollable tab row:

**Tab 1 — Overview**
Shows a summary of the patient. At the top, if a followup is scheduled, an amber
alert card shows the date and says "Followup Due". Below that is a small card
showing the patient's dominant Nadi and Element (whichever appeared most across all
visits). Below that is the medical history text. Below that is a "Top Complaints"
list showing the three most frequent complaints extracted from visit notes. At the
bottom are the last three visit cards as a preview with a "See All Visits" button
that switches to the Visits tab.

**Tab 2 — Visits**
Shows all visits for this patient. At the top is a search bar to filter visits by
keyword, a Nadi filter dropdown, and a sort toggle between newest first and oldest
first.

Each visit is a card that can be tapped to expand. In collapsed state it shows the
visit date, a badge saying "Initial Visit" if it was the first ever visit for this
patient, and a short snippet of the chief complaint.

When expanded, the card shows:
- A row of chips: Mandala, Patient Nadi, Patient Element, Sitting Position
- Doctor's assessment row: Doctor's Nadi, Element before, Element after
- Temporal row: Paksha, Tithi number, Tithi Element
- Chief Complaint text (formatted, could have bold or lists from the rich text entry)
- Prescription text (same format)
- Disease category chips (e.g. "Respiratory", "Neurological")
- Any custom fields the practitioner added
- A row of image thumbnails if photos were attached — tapping opens full screen view
- Followup date if one was set
- Edit and Delete buttons at the bottom

**Tab 3 — Trends**
Visual analytics for this patient only. Shows:
- A bar chart of visit count per month for the last 6 months
- A horizontal bar breakdown of which Elements appeared across visits
- A horizontal bar breakdown of which Nadis appeared
- A small stats row: followup rate percentage, average days between visits, total
  visits count

**Tab 4 — Media**
A grid of all photos attached to any visit for this patient. Each photo shows a
thumbnail with the date it was taken below it. Tapping a photo opens it full screen
where the user can pinch to zoom and share it.

---

### VISIT FORM SCREEN
**A 7-step form for recording a new visit or editing an existing one.**

This screen is full screen with no bottom navigation bar. At the top is a progress
bar showing 7 segments — the filled segments show how far through the form the user
is. Below the progress bar is a step title and "Step X of 7". There is a back arrow
on the left that goes back one step (or exits the form if on step 1). On the right
is a small "Saved" indicator that updates after autosave.

At the bottom of the screen are two buttons: Back (hidden on step 1) and Next.
On the last step the Next button is replaced by the Save button inside the step
content itself.

The form autosaves every 30 seconds. If the user closes the form accidentally and
reopens it, the draft is restored.

**Step 1 — Patient Information**
If opened from an existing patient's profile, this step is pre-filled with the
patient's stored information. The practitioner can update any field and the patient
record will be updated when the visit is saved.

If opened as a new patient, all fields are blank.

Fields: Full name (required), Age (required), Gender selector as chips (Male /
Female / Other, required), Mobile number (required), Email, Address (multiline),
Occupation, Blood Group dropdown, Category tags (the practitioner can type custom
tags like "Chronic" or "Pediatric" and press Enter to add them), Emergency contact
name and phone, Active toggle switch, Provisional toggle switch.

Tapping Next validates: name must not be empty, age must be a number between 0 and
150, gender must be selected, mobile must be at least 10 characters. If any fail,
red error text appears under the relevant field and the form stays on step 1.

**Step 2 — Medical History**
A large multiline text area for documenting the patient's ongoing medical history,
allergies, past treatments, and any relevant background. This is free text.

There is a microphone button — tapping it opens the system speech recognition. The
spoken words are appended to the existing text. The character count is shown below
the field.

This step has no required fields. Tapping Next always proceeds.

**Step 3 — Yogic Evaluation**
This is the core yogic data entry step.

Four selections, each shown as a row of chip buttons:

Mandala — which energy channel is dominant in the patient today:
- Ida (left, lunar)
- Pingala (right, solar)
- Other

Patient Nadi — the specific nadi state of the patient:
- Ida
- Pingala
- Sushumna
- Shifting from Ida to Pingala
- Shifting from Pingala to Ida
- Other

Patient Element — the dominant element in the patient:
- Air (shown with air emoji)
- Fire (shown with fire emoji)
- Space (shown with sparkle emoji)
- Earth (shown with earth emoji)
- Water (shown with water emoji)
- Other

Sitting Position — where the patient is sitting relative to the practitioner:
- Right
- Left
- Front

Each chip is colour coded: Ida chips are indigo/blue toned, Pingala chips are orange
toned, Sushumna is green/teal, elements each have their own colour.

At the bottom of the step is a live preview card showing the currently selected Nadi
chip and Element badge side by side, so the practitioner can see the reading at a
glance.

All four selections are required. Tapping Next without selecting all four shows
error messages under each missing field.

**Step 4 — Doctor Assessment**
The practitioner records their own state and the lunar factors.

Doctor's Nadi — a free text field where the practitioner types which Nadi they
themselves are in during this consultation. This is free text because practitioners
may use their own terminology.

Doctor's Element Before — free text, the element the practitioner observed in
themselves at the start of the session.

Doctor's Element After — optional free text, the element after treatment was given.

Paksha — chip selector: Shukla (waxing moon period) or Krishna (waning moon period).

Tithi — a scrollable row of numbered chips from 1 to 15, plus an "Other" chip.
The practitioner taps the current lunar day number.

Tithi Element — same chip selector as Patient Element in step 3.

At the bottom of this step is an "Alignment Check" card. It automatically compares:
- Does the patient's Nadi name match the doctor's Nadi text entry? Green check if
  yes, amber warning if no.
- Does the patient's element match the doctor's element before? Green check if yes,
  amber warning if no.

This is informational only and does not block proceeding.

Doctor's Nadi, Doctor's Element Before, Paksha, Tithi, and Tithi Element are all
required.

**Step 5 — Clinical Notes**
Two large text areas with microphone buttons, same speech-to-text behaviour as step 2:

Chief Complaint — what the patient is presenting with today. Required. If left empty,
Next shows an error.

Prescription — the treatment or advice given. Optional.

Below the text areas is a tag input for Disease Categories. The practitioner can type
category names and press Enter to add them as chips. Suggested categories appear as
small chip buttons when typing: Respiratory, Digestive, Neurological, Musculoskeletal,
Cardiovascular, Dermatological, Endocrine, Urological, Gynecological, Psychiatric,
Ophthalmic, ENT, Pediatric, Autoimmune, Oncological.

**Step 6 — Media and Custom Fields**
Two sections:

Media Attachments:
Two buttons side by side — Camera and Gallery. Camera opens the device camera and
lets the practitioner take a photo directly. Gallery opens the image picker to choose
an existing photo. Selected photos appear as a horizontal scrollable row of square
thumbnails. Each thumbnail has a small red X button in the top right corner to remove it.

Custom Fields:
A list of key-value fields the practitioner can define for this specific visit. Each
field has a label, a type (Text, Number, or Date), and a value input. An "Add Custom
Field" button opens a small dialog to name the field and choose its type. Each
existing field has a trash icon to remove it. This is for any data the practitioner
wants to record that the standard form doesn't cover.

**Step 7 — Timing and Save**
Two date-time pickers:

Visit Date and Time — defaults to right now if not changed. Tapping opens a date
picker then a time picker in sequence. The selected date and time are shown in the
field.

Next Followup — optional. Same date-time picker behaviour. There is a clear button
to remove a set followup date.

Below the date pickers is a Review Summary card — a compact table showing all the
key data entered across all steps: patient name, age, gender, Nadi, Element,
Mandala, Doctor Nadi, Paksha, Tithi, complaint snippet, categories. This is a
final review before saving.

At the bottom is the Save Record button. Tapping it:
1. Validates all 7 steps. If any required field across any step is missing, the
   form jumps back to the first failing step and shows the error.
2. If all valid, saves the patient record (creating new or updating existing) and
   saves the visit record linked to that patient.
3. Clears the autosave draft.
4. Updates the "last patient" app shortcut so long-pressing the app icon shows
   "New Visit for [this patient]".
5. Navigates back to the patient detail screen showing the newly added visit.

While saving, the button shows a spinner and "Saving…" text. All inputs are
disabled.

---

### APPOINTMENTS SCREEN (Schedule Tab)
**Manage all upcoming and past appointments.**

The screen has three tabs at the top: Today, Upcoming, Past.

The floating action button in the bottom right opens a "Schedule Appointment" bottom
sheet panel that slides up from the bottom.

**Today tab** — Shows all appointments for today that are scheduled. Each appointment
card shows a date block on the left (gradient indigo for today), the patient name,
the time, the purpose, and any notes. Below that are action buttons:
- Start Visit — navigates to the visit form pre-filled for this patient
- Green checkmark — marks the appointment as Completed
- Amber X — marks as Cancelled
- Red trash — deletes the appointment entirely

**Upcoming tab** — All future appointments beyond today, sorted by date ascending.
Same card layout but the date block is grey instead of gradient.

**Past tab** — Completed and cancelled appointments. Same cards but slightly dimmed
to indicate they are historical.

**Schedule Appointment bottom sheet:**
A form panel that slides up. Contains:
- Patient search field — as the practitioner types a name or mobile number, a list
  of matching patients appears below the field. Tapping a patient selects them and
  the search field shows their name.
- Date and Time picker — same picker behaviour as the visit form
- Purpose — a short text field for what the appointment is for
- Notes — a text area for any additional notes
- Set Reminder toggle — when on, the app will send a notification 30 minutes before
  this appointment
- Schedule Appointment button — saves the appointment and closes the sheet

---

### ANALYTICS SCREEN (Analytics Tab)
**Practice-wide patterns and insights.**

At the top is a row of filter chips to change the time range:
All Time, 30 Days, 90 Days, 6 Months, 1 Year.
Selecting a chip immediately recalculates all data below for that period.

The screen scrolls vertically through sections:

**Key Metrics** — Four stat cards in a horizontal scrollable row:
- Total patients who had at least one visit in the selected period
- Total visits in the period
- Average visits per patient (one decimal place)
- Count of unique chief complaints recorded

**Correlation Insights** — A gradient indigo card with three percentage bars:
- Nadi Alignment: what percentage of visits had the patient's Nadi matching the
  doctor's Nadi entry
- Element Alignment: what percentage had patient element matching doctor element
- Tithi-Element Correlation: what percentage had the Tithi Element matching the
  patient's element
- Below the bars: the most common patient-to-doctor Nadi pairing found in the data
  (e.g. "Ida → Pingala appeared 34 times")

**Patient Characteristics** — Four horizontal bar charts in a 2-column grid:
- Patient Nadi distribution (how many visits had each Nadi)
- Patient Element distribution
- Mandala distribution
- Sitting Position distribution
Each bar shows the label, a coloured progress bar, and the count with percentage.
The bars animate in from zero when the section loads.

**Doctor and Temporal Factors** — Four more bar charts:
- Doctor Nadi (free text entries grouped by value)
- Doctor Element Before
- Tithi Distribution (which Tithi numbers appear most)
- Tithi Element distribution

**Demographics** — Two bar charts:
- Gender breakdown of patients seen
- Age group breakdown: Infant (0–2), Child (3–12), Teen (13–18), Young Adult
  (19–35), Adult (36–50), Middle Age (51–65), Senior (65+)

**Visit Timeline** — A bar chart showing visit count per month for the last 6 months.
Month abbreviations on the X axis, count on the Y axis. Indigo coloured bars.

**Top Chief Complaints** — A ranked list of the 10 most frequent complaints extracted
from all chief complaint text. Each entry shows:
- A rank badge (gold for 1st, silver for 2nd, bronze for 3rd, plain for the rest)
- The complaint text
- A coloured progress bar
- Count and percentage

**Visit Records Table** — A horizontally scrollable table showing individual visit
rows. Columns: Date, Patient, Patient Nadi, Patient Element, Mandala, Doctor Nadi,
Doctor Element, Tithi, and a Match column. The Match column shows a green checkmark
if either the Nadi or Element matched between patient and doctor. Matching values in
the Nadi and Element columns are highlighted green. Shows up to 50 most recent
visits. If there are more, a note says "Showing 50 of X records".

---

### SETTINGS SCREEN (Settings Tab)
**App configuration and data management.**

The screen is a vertically scrolling list of cards, each representing a category.

**Profile Card**
Three text fields: Display Name, Professional Title, Clinic Name. These are shown
in the Dashboard greeting. A "Save Profile" button saves them.

**Security Card**
- Biometric Authentication toggle — turn fingerprint/face auth on or off
- Dark Mode toggle — switches the entire app between light and dark theme
- Change PIN row — tapping opens a dialog with three fields: Current PIN, New PIN,
  Confirm New PIN. The dialog validates that the current PIN is correct and the new
  PIN entries match before saving.

**Backup and Restore Card**
Explanatory text: "All data is stored locally on this device."
- Export as JSON button — packages the entire database into a JSON file and opens
  the Android share sheet so the practitioner can save it to Google Drive, email it,
  or send it to themselves any way they like.
- Export as Excel button — creates a multi-sheet Excel file (patient info sheet,
  all visits sheet, analytics summary sheet) and shares it the same way.
- Restore from JSON button — tapping first shows a confirmation dialog warning that
  this will overwrite all current data. If confirmed, a file picker opens for the
  user to choose a previously exported JSON backup. The app reads the file, clears
  the database, and imports all the data from the backup.

**About Card**
Static information: app version, database technology, theme information.

**Account Card**
- Logout button — shows a confirmation dialog. On confirm, the session is cleared
  and the app navigates back to the auth screen. The PIN remains set so the
  practitioner must enter it again to get back in.

---

## NOTIFICATIONS

The app sends two types of notifications:

**Followup Reminders** — Every morning at 7 AM, the app checks if any patient has
a followup date set within the next 48 hours. For each one found, a notification is
posted showing the patient name and when the followup is due. Tapping the
notification opens the patient's detail screen.

**Appointment Reminders** — 30 minutes before each scheduled appointment, a
notification fires with the patient name and appointment purpose. The notification
has a "Start Visit" action button that opens the visit form directly.

---

## HOME SCREEN WIDGET

The app provides a home screen widget the user can add to their Android home screen.
It shows two coloured boxes side by side:
- Left box (indigo gradient): today's appointment count with "Today's Appointments"
  label
- Right box (amber gradient): followups due within 48 hours with "Followups Due"
  label
The widget updates every 30 minutes. Tapping it opens the Appointments screen.

---

## APP SHORTCUTS

Long pressing the SwaraPulse icon on the home screen shows three shortcuts:
- "Add Patient" — opens the app directly to the new patient form
- "Today's Schedule" — opens the app directly to the Appointments screen
- "New Visit for [Name]" — dynamically updated to show the last patient seen;
  tapping opens the visit form pre-filled for that patient

---

## KEY DATA RULES

- A visit cannot exist without a patient. Every visit is always linked to one patient.
- Deleting a patient deletes all their visits and appointments automatically.
- A patient marked as Provisional appears in the list but is not counted in the
  "Active Patients" stat on the dashboard.
- The "Initial Visit" badge on a visit card only appears on the chronologically
  first visit for that patient — determined by the visit date, not the record
  creation date.
- The followup date on a visit is separate from appointments. A followup is a note
  on a past visit saying "patient should return by this date". An appointment is a
  confirmed future booking.
- The autosave draft stores only one draft at a time. If the user starts a new visit
  form without submitting the previous one, the previous draft is overwritten.
- Media photos are stored as files in the app's private storage. The file path is
  saved in the visit record. Photos are never uploaded anywhere.
- The PIN is hashed before storage. The app never stores the raw PIN digits.
- All dates and times are stored in the device's local timezone.

---

## SCREEN TRANSITION BEHAVIOUR

- Tapping a bottom nav tab: slides the new screen in from the right
- Pressing back: slides the current screen out to the right (reverse)
- Opening the visit form: slides up from bottom
- Dismissing the visit form: slides down
- Opening a bottom sheet (appointments schedule, filter panel): slides up from bottom
- All transitions take 280 milliseconds

---

## EMPTY STATES

Every list in the app has an empty state:
- Patient list with no patients: icon of a person, "No patients yet", "Add First Patient" button
- Visits tab with no visits: icon of a clipboard, "No visits recorded yet", "Record First Visit" button
- Appointments today with none: calendar icon, "No appointments today"
- Analytics with no data: bar chart icon, "No visit data for this time range"
- Media tab with no photos: photo icon, "No media attached to any visits"

All empty state icons float gently up and down with a slow animation.

---

## WHAT JULES MUST UNDERSTAND ABOUT THE CURRENT STATE

The entire backend — database, ViewModels, repositories, exporters, workers — is
already implemented and compiles successfully. The APK installs on a real Android
device and launches without crashing.

The only problem is the app shows a blank white screen with the text
"SwaraPulse App Scaffold" instead of the auth screen.

This means exactly one thing: the `setContent {}` block in `MainActivity.kt` is
rendering a placeholder `Text` composable instead of calling `SwaraPulseNavHost()`.

The fix is to open `MainActivity.kt`, delete whatever is inside `setContent {}`,
and replace it with a call to `SwaraPulseNavHost()` wrapped in `SwaraPulseTheme {}`.

After that single change, the app should show the Auth screen on launch, allow
PIN setup, then show the Dashboard with the bottom navigation bar, and all five
tabs should be navigable.

No other file needs to change for the app to start working visually.
