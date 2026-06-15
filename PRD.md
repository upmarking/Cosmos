# Product Requirement Document (PRD): Cosmos

Build a complete mobile and web app for curated professional networking and founder matchmaking. The product should help users discover the right people, meet in a structured way, keep conversations organized, and grow valuable professional relationships over time.

This is not a job board, not a sales platform, and not a generic social network. It is a high-trust relationship building platform for founders, operators, professionals, investors, students, mentors, service providers, and other career oriented users.

The experience should feel premium, simple, intentional, and highly organized.

---

## 1. Primary Product Goal
Create a platform where users can:
1. Discover and match with relevant people
2. Join curated events and networking sessions
3. Maintain structured conversations and follow-ups
4. Join or create communities
5. Track relationship progress and networking outcomes
6. Build trust through profiles, mutual connections, LinkedIn based import, and peer endorsements
7. Use AI to summarize meetings, surface next steps, and improve follow-up quality

---

## 2. Core Product Principles
1. **Quality over quantity**
   Limit users to a maximum of 10 meaningful new connections per month to keep networking intentional and valuable.
2. **Trust first**
   Profiles should feel credible, professional, and verifiable. LinkedIn connection should be used as a trust signal and profile enrichment source.
3. **Purpose driven connections**
   Every match should be based on goals, tags, interests, and mutual relevance, not random discovery.
4. **Structured communication**
   Conversations should behave like a relationship CRM, where people can organize chats, goals, labels, notes, meeting summaries, and next steps.
5. **Event driven networking**
   Networking events should be scheduled, structured, and timed, with clear rounds and feedback loops.
6. **Community led growth**
   Communities should be curated, admin created, and led by designated managers.
7. **AI assisted productivity**
   AI should summarize meetings, create action points, help with follow ups, and surface useful patterns from interactions.

---

## 3. App Structure
The app contains these top-level sections:
1. **Connect** (Swipe matchmaking)
2. **Events** (Speed networking & structured rounds)
3. **Communities** (Curated manager-led groups)
4. **Conversations** (CRM-like chat & meeting follow-ups)
5. **Profile** (Milestones & progress tracking)
6. **Notifications**

---

## 4. User Types
Support the following user types (selectable during onboarding):
* Founder / Co-founder
* Startup operator
* Investor
* Student
* Mentor
* Professional (Tech, Marketing, Finance, Legal, Healthcare, Business)
* Creator / Freelancer
* Service provider
* Community member

*Users can choose multiple tags, but the app should still identify their primary category.*

---

## 5. User Flows & Feature Requirements

### Onboarding Flow
Collects setup details:
1. **Basic Identity**: Full name, profile photo, headline, location, current role, company, years of experience.
2. **Professional Intent**: What they are looking for (e.g., Co-founders, mentors, collaborators, investors, clients, strategic intros, industry peers).
3. **Interest Tags**: e.g., startup, AI, marketing, product, design, finance, legal, SaaS, web3, sales, operations, etc.
4. **Goal Statement & Long-Term Vision**: What they want to achieve and what they are building/seeking.
5. **Availability Preferences**: Meeting formats, preferred windows, duration preferences.
6. **LinkedIn Integration**: Connect account to import roles, experience, and show a "LinkedIn Linked" trust indicator (not a verified badge).
7. **Profile Completion**: Progress indicator highlighting remaining fields to unlock full discovery.

### Connect Section (Matchmaking)
Swipe-based discovery engine:
* **Profile Card**: Displays name, photo, headline, role, tags, goal statement, bio, mutual connections, LinkedIn status, endorsed skills, and trust badges.
* **Matchmaking Rules**:
  * Prioritize relevance based on shared tags, goals, and mutual connections.
  * Rank LinkedIn connected profiles higher.
  * Limit to 10 meaningful matches (mutual swipe/connect) per month.
* **Swiping**: Swipe right/yes to show interest; swipe left/no to skip. Mutual interest starts a conversation.

### Events Section (Structured Networking)
* **Event Types**: Open networking, curated founder meetups, industry rounds, invite-only, and themed sessions.
* **Flow**: Registration -> Scheduled rounds (e.g., 15 mins) -> Live scheduling/countdown -> Post-meeting feedback and ratings.
* **Paid Events**: Commit payment upfront. Attendance & higher-rated feedback can influence refunds.
* **Post-Event**: AI summaries generated from meetings and stored in the conversation thread.

### Conversations Section (Relationship CRM)
Each connection gets a workspace:
* 1-to-1 Chat & transcript storage.
* **AI Summaries**: Key discussion points, decisions, next steps, follow-up reminders.
* **CRM Elements**: Private notes and labels (e.g., *Potential partner*, *Follow up needed*, *Warm intro requested*).
* **Private Goals**: Set private targets for each connection (unseen by the counterparty) to track milestone progression.
* **Warm Intro Flow**: Request introductions through mutual connections and track status.

### Profiles & Endorsements
* **Progress Tracker**: Visual metrics on meaningful connections, events attended, follow-ups, goals achieved, and monthly activity.
* **Peer Endorsements**: Quick-tap endorsements for specific professional skills (e.g., communication, product thinking, fundraising, tech ability) displaying star/counts and endorsers.

### Communities Section
* Curated, admin/manager-created spaces.
* Features: Feed, Announcements, Member list, Join requests, Discussion boards, and Admin controls.

### Notifications Center
Configurable notifications for new matches, messages, event reminders, AI summaries, introduction status, endorsements, and community updates.

---

## 6. Match Quality & Recommendation Logic
Relevance logic leverages:
1. Shared tags & complementary goals
2. Industry & role alignment
3. LinkedIn connection & mutual connections
4. Previous feedback & event participation history

---

## 7. Platform Culture & Rules
* Strict anti-spam policy. No pitching/selling.
* Repeated reports for outreach/sales behavior will trigger warnings, restrictions, or visibility limits.
* Onboarding explains the relationship-building ethos.

---

## 8. AI Features
1. Auto-generate meeting summaries and action items.
2. Suggest next steps and follow-up messaging.
3. Highlight collaboration opportunities and match recommendations.
4. Organize chat threads and alert users to overdue follow-ups.
5. Guide users to refine profiles/goals.
