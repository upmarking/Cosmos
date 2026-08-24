/**
 * Google Calendar Service for Cosmos
 * 
 * Creates Calendar events with auto-generated Google Meet links
 * and manages attendees (participants) on those events.
 * 
 * Uses a Google Cloud service account. The service account key JSON
 * is loaded from:
 *   1. GOOGLE_CALENDAR_SERVICE_ACCOUNT_KEY environment variable (JSON string), or
 *   2. ./service-account.json file (for local development)
 * 
 * IMPORTANT: The service account must belong to a Google Workspace domain
 * for Meet link auto-generation to work. Set GOOGLE_CALENDAR_ID to the
 * calendar ID (e.g., "primary" or a shared calendar like "events@cosmos.app").
 */

const { google } = require('googleapis');

let _calendarClient = null;

/**
 * Initialize and return a Google Calendar API client using service account credentials.
 */
function getCalendarClient() {
  if (_calendarClient) return _calendarClient;

  let credentials;

  // Try environment variable first (Firebase secrets / runtime config)
  const envKey = process.env.GOOGLE_CALENDAR_SERVICE_ACCOUNT_KEY;
  if (envKey) {
    try {
      credentials = JSON.parse(envKey);
    } catch (e) {
      console.error('Failed to parse GOOGLE_CALENDAR_SERVICE_ACCOUNT_KEY:', e.message);
      throw new Error('Invalid service account key in environment variable');
    }
  } else {
    // Fall back to local file (development)
    try {
      credentials = require('./service-account.json');
    } catch (e) {
      throw new Error(
        'Google Calendar service account not configured. ' +
        'Set GOOGLE_CALENDAR_SERVICE_ACCOUNT_KEY env var or place service-account.json in functions/'
      );
    }
  }

  const auth = new google.auth.GoogleAuth({
    credentials,
    scopes: ['https://www.googleapis.com/auth/calendar'],
  });

  _calendarClient = google.calendar({ version: 'v3', auth });
  return _calendarClient;
}

/**
 * Get the target Calendar ID from environment, defaulting to "primary".
 */
function getCalendarId() {
  return process.env.GOOGLE_CALENDAR_ID || 'primary';
}

/**
 * Build an RFC3339 datetime string from a date string and time string.
 * 
 * @param {string} dateStr - e.g., "Aug 25, 2026" or "2026-08-25"
 * @param {string} timeStr - e.g., "2:00 PM" or "14:00"
 * @param {number} durationMinutes - event duration in minutes (default 60)
 * @returns {{ start: string, end: string }} RFC3339 datetime strings
 */
function buildDateTimeRange(dateStr, timeStr, durationMinutes = 60) {
  // Parse the date
  let parsedDate;
  
  // Try common formats
  const cleanDate = dateStr
    .replace(/^(Tomorrow|Today|Next\s+\w+),?\s*/i, '')
    .replace(/^(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday),?\s*/i, '')
    .trim();

  parsedDate = new Date(cleanDate);
  
  if (isNaN(parsedDate.getTime())) {
    // Fallback: try ISO format
    parsedDate = new Date(dateStr);
  }
  
  if (isNaN(parsedDate.getTime())) {
    // Last resort: use tomorrow
    parsedDate = new Date();
    parsedDate.setDate(parsedDate.getDate() + 1);
  }

  // Parse the time
  let hours = 10, minutes = 0; // default 10:00 AM
  
  if (timeStr) {
    const timeMatch = timeStr.match(/(\d{1,2}):?(\d{2})?\s*(AM|PM)?/i);
    if (timeMatch) {
      hours = parseInt(timeMatch[1], 10);
      minutes = parseInt(timeMatch[2] || '0', 10);
      const period = (timeMatch[3] || '').toUpperCase();
      if (period === 'PM' && hours < 12) hours += 12;
      if (period === 'AM' && hours === 12) hours = 0;
    }
  }

  const startDate = new Date(parsedDate);
  startDate.setHours(hours, minutes, 0, 0);

  const endDate = new Date(startDate);
  endDate.setMinutes(endDate.getMinutes() + durationMinutes);

  // Format as RFC3339 with timezone offset
  const formatRFC3339 = (d) => {
    const pad = (n) => String(n).padStart(2, '0');
    const offset = -d.getTimezoneOffset();
    const sign = offset >= 0 ? '+' : '-';
    const absOffset = Math.abs(offset);
    const tzHours = pad(Math.floor(absOffset / 60));
    const tzMinutes = pad(absOffset % 60);
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}${sign}${tzHours}:${tzMinutes}`;
  };

  return {
    start: formatRFC3339(startDate),
    end: formatRFC3339(endDate),
  };
}

/**
 * Create a Google Calendar event with an auto-generated Google Meet link.
 * 
 * @param {Object} params
 * @param {string} params.title - Event title
 * @param {string} params.description - Event description
 * @param {string} params.dateStr - Human-readable date (e.g., "Aug 25, 2026")
 * @param {string} params.timeStr - Human-readable time (e.g., "2:00 PM")
 * @param {number} [params.durationMinutes=60] - Event duration
 * @param {string[]} [params.attendeeEmails=[]] - Initial attendee emails
 * @param {string} [params.timezone='Asia/Kolkata'] - Timezone for the event
 * @returns {Promise<{ calendarEventId: string, meetLink: string, htmlLink: string }>}
 */
async function createCalendarEvent({
  title,
  description = '',
  dateStr,
  timeStr,
  durationMinutes = 60,
  attendeeEmails = [],
  timezone = 'Asia/Kolkata',
}) {
  const calendar = getCalendarClient();
  const calendarId = getCalendarId();
  const { start, end } = buildDateTimeRange(dateStr, timeStr, durationMinutes);

  const event = {
    summary: title,
    description: description || `Cosmos Event: ${title}`,
    start: {
      dateTime: start,
      timeZone: timezone,
    },
    end: {
      dateTime: end,
      timeZone: timezone,
    },
    attendees: attendeeEmails.map((email) => ({ email })),
    conferenceData: {
      createRequest: {
        requestId: `cosmos-${Date.now()}-${Math.random().toString(36).substring(2, 8)}`,
        conferenceSolutionKey: {
          type: 'hangoutsMeet',
        },
      },
    },
    reminders: {
      useDefault: false,
      overrides: [
        { method: 'email', minutes: 60 },
        { method: 'popup', minutes: 15 },
      ],
    },
    guestsCanInviteOthers: false,
    guestsCanModify: false,
    guestsCanSeeOtherGuests: true,
  };

  const response = await calendar.events.insert({
    calendarId,
    resource: event,
    conferenceDataVersion: 1, // Required to generate Meet link
    sendUpdates: 'all', // Send invite emails to attendees
  });

  const createdEvent = response.data;
  const meetLink = createdEvent.hangoutLink || createdEvent.conferenceData?.entryPoints?.[0]?.uri || '';

  console.log(`Created Calendar event: ${createdEvent.id}, Meet: ${meetLink}`);

  return {
    calendarEventId: createdEvent.id,
    meetLink,
    htmlLink: createdEvent.htmlLink || '',
  };
}

/**
 * Add an attendee to an existing Calendar event.
 * 
 * @param {string} calendarEventId - The Calendar event ID
 * @param {string} email - Attendee email to add
 * @returns {Promise<void>}
 */
async function addAttendeeToEvent(calendarEventId, email) {
  if (!calendarEventId || !email) {
    console.warn('addAttendeeToEvent: missing calendarEventId or email');
    return;
  }

  const calendar = getCalendarClient();
  const calendarId = getCalendarId();

  // Get current event to read existing attendees
  const existing = await calendar.events.get({
    calendarId,
    eventId: calendarEventId,
  });

  const attendees = existing.data.attendees || [];
  
  // Check if already an attendee
  if (attendees.some((a) => a.email.toLowerCase() === email.toLowerCase())) {
    console.log(`${email} is already an attendee of event ${calendarEventId}`);
    return;
  }

  attendees.push({ email });

  await calendar.events.patch({
    calendarId,
    eventId: calendarEventId,
    resource: { attendees },
    sendUpdates: 'all', // Send invite to new attendee
  });

  console.log(`Added ${email} to Calendar event ${calendarEventId}`);
}

/**
 * Remove an attendee from an existing Calendar event.
 * 
 * @param {string} calendarEventId - The Calendar event ID
 * @param {string} email - Attendee email to remove
 * @returns {Promise<void>}
 */
async function removeAttendeeFromEvent(calendarEventId, email) {
  if (!calendarEventId || !email) {
    console.warn('removeAttendeeFromEvent: missing calendarEventId or email');
    return;
  }

  const calendar = getCalendarClient();
  const calendarId = getCalendarId();

  const existing = await calendar.events.get({
    calendarId,
    eventId: calendarEventId,
  });

  const attendees = (existing.data.attendees || []).filter(
    (a) => a.email.toLowerCase() !== email.toLowerCase()
  );

  await calendar.events.patch({
    calendarId,
    eventId: calendarEventId,
    resource: { attendees },
    sendUpdates: 'all', // Notify removed attendee
  });

  console.log(`Removed ${email} from Calendar event ${calendarEventId}`);
}

/**
 * Delete a Calendar event entirely.
 * 
 * @param {string} calendarEventId - The Calendar event ID
 * @returns {Promise<void>}
 */
async function deleteCalendarEvent(calendarEventId) {
  if (!calendarEventId) return;

  const calendar = getCalendarClient();
  const calendarId = getCalendarId();

  try {
    await calendar.events.delete({
      calendarId,
      eventId: calendarEventId,
      sendUpdates: 'all',
    });
    console.log(`Deleted Calendar event ${calendarEventId}`);
  } catch (e) {
    console.warn(`Failed to delete Calendar event ${calendarEventId}:`, e.message);
  }
}

module.exports = {
  createCalendarEvent,
  addAttendeeToEvent,
  removeAttendeeFromEvent,
  deleteCalendarEvent,
};
