# Product Requirements Document (PRD)

## Anonymous Ephemeral Messaging App

**Document Version:** 1.0  
**Status:** Draft  
**Platform:** Android  
**Primary Language:** Kotlin  
**UI:** Jetpack Compose  
**Target:** Lightweight, privacy-focused, anonymous 1-to-1 messaging

---

# 1. Product Overview

## 1.1 Product Concept

The application is a lightweight, privacy-focused anonymous messaging application for Android.

Users do not need to provide:

- Phone numbers
- Email addresses
- Usernames
- Real names
- Profile photos
- Public profiles

Instead, every user receives a randomly generated **8-character Public ID** when they start using the application.

Users can share their Public ID with another person. The other person can enter the ID, send a chat request, and begin a conversation after the request is accepted.

The defining characteristic of the application is that identities and conversations are **ephemeral**.

Each identity has a fixed lifetime of 24 hours. Once the identity expires:

1. The identity becomes invalid.
2. The associated conversations become unavailable.
3. Conversation data is removed from the server.
4. Other participants receive a realtime notification.
5. Local copies of the affected conversation are removed.
6. A new identity can be generated for the user.

The application therefore provides temporary, pseudonymous communication rather than permanent social identities.

---

# 2. Product Vision

> **A lightweight anonymous messenger where conversations exist temporarily and identities disappear automatically.**

The product should feel:

- Simple
- Fast
- Minimal
- Private
- Anonymous
- Temporary
- Lightweight

The application should avoid becoming a social network.

---

# 3. Core Product Principles

## 3.1 No Traditional Account Identity

The application should not require:

- Email
- Phone number
- Password
- Username
- Real name

A user is identified publicly only through their current Public ID.

---

## 3.2 Ephemeral Identity

Every identity has a fixed lifetime.

Example:

```text
Identity Created
26 Sep 2026 22:00:00

Expires
27 Sep 2026 22:00:00
```

User activity does not extend the identity lifetime.

The 24-hour expiration period is fixed from identity creation.

---

## 3.3 Privacy by Design

The application should minimize the amount of information stored by the server.

Messages should be end-to-end encrypted.

The server should store ciphertext rather than plaintext messages.

The application should avoid unnecessary metadata such as:

- Online status
- Last seen
- Typing status
- Read receipts
- Profile information

---

## 3.4 Server as the Source of Truth

Expiration and security-sensitive operations must not depend on the device clock.

The server determines:

- Identity expiration
- Request validity
- Message validity
- Conversation validity
- Account/identity deletion

The Android client may display countdowns using server-provided timestamps, but it must not be authoritative.

---

# 4. Target Users

The application is intended for users who want to have temporary conversations without creating a permanent social identity.

Potential use cases include:

- Sharing a temporary contact identity
- Anonymous discussions
- Short-term conversations
- Privacy-sensitive communication
- Temporary communication between people who do not want to exchange phone numbers

The product does not attempt to replace permanent messaging platforms.

---

# 5. User Journey

## 5.1 First Launch

```text
Install Application
        ↓
Open Application
        ↓
Welcome Screen
        ↓
Start Messaging
        ↓
Generate Identity
        ↓
Display Public ID
```

---

# 6. Public ID

## 6.1 Format

Every user receives an 8-character Public ID.

Example:

```text
K7F2A91X
```

Allowed characters:

```text
A-Z
0-9
```

The ID should be generated using a cryptographically secure random generator.

---

## 6.2 Uniqueness

The backend must verify that the generated Public ID is not currently assigned.

Conceptual process:

```text
Generate ID
    ↓
Check database
    ↓
Already exists?
   /       \
 YES       NO
  ↓         ↓
Generate   Assign
again      identity
```

---

## 6.3 Public ID Characteristics

The Public ID:

- Does not contain personal information.
- Can be shared voluntarily.
- Is required to initiate a conversation.
- Cannot be used as an authentication credential.
- Is temporary.
- Becomes invalid when the identity expires.

---

# 7. Identity Lifecycle

## 7.1 Identity States

```text
ACTIVE
   ↓
EXPIRED
   ↓
REVOKED
   ↓
PURGED
```

### ACTIVE

The identity can:

- Receive chat requests
- Send chat requests
- Send messages
- Receive messages

### EXPIRED

The identity can no longer:

- Receive requests
- Send requests
- Send messages
- Receive messages

### REVOKED

The identity has been invalidated and should no longer be publicly resolvable.

### PURGED

Identity-related server data has been permanently removed according to the deletion policy.

---

# 8. Automatic Identity Rotation

Users can enable automatic identity rotation.

Default behavior:

```text
Automatic Identity Rotation: ON
```

An identity expires exactly 24 hours after creation.

Example:

```text
Created:
22:00:00

Expires:
22:00:00 next day
```

Chat activity does not reset or extend the timer.

---

# 9. Manual Identity Rotation

The application may provide:

```text
Settings
    ↓
Identity
    ↓
Rotate Identity
```

When the user manually rotates their identity:

1. Current identity is revoked.
2. Current conversations become unavailable.
3. Conversation data is deleted according to the deletion policy.
4. Participants are notified.
5. A new Public ID is generated.

Example:

```text
Old ID:
K7F2A91X

        ↓ Rotate

New ID:
Q8M4X2KP
```

The system must not retain a permanent mapping such as:

```text
K7F2A91X → Q8M4X2KP
```

unless required for a specific operational or security purpose.

---

# 10. Chat Request System

## 10.1 Request Flow

```text
User A
  ↓
Enter User B's Public ID
  ↓
Validate ID
  ↓
Send Request
  ↓
User B Inbox
  ↓
Accept / Reject
```

---

## 10.2 ID Validation

When a user enters a Public ID:

### Valid ID

The server confirms that the ID currently exists and is active.

The user can send a request.

### Invalid ID

The application displays:

```text
ID does not match
```

---

## 10.3 Self-ID Validation

Users must not be able to search for their own Public ID.

If the entered ID belongs to the current identity:

```text
You cannot message yourself.
```

---

# 11. Request States

Chat requests use the following states:

```text
PENDING
   ├── ACCEPTED
   ├── REJECTED
   └── CANCELLED
```

---

## 11.1 Pending

The request has been created but not processed.

---

## 11.2 Accepted

The receiver accepts the request.

The system creates or activates a conversation.

The UI displays:

```text
Request accepted

[ Open Chat ]
```

---

## 11.3 Rejected

The receiver rejects the request.

The sender sees:

```text
Request rejected

[ Remove ]
```

---

## 11.4 Expired Request

If either identity expires before the request is accepted, the request becomes invalid.

The request should not create a conversation.

---

# 12. Inbox

The Inbox contains incoming chat requests.

Example:

```text
Inbox

┌────────────────────────────┐
│ A8K29D3F                   │
│ wants to chat with you     │
│                            │
│ [ Accept ]   [ Reject ]    │
└────────────────────────────┘
```

The Inbox should remain lightweight and should not contain a permanent contact list.

---

# 13. Conversation

A conversation is created after a request is accepted.

The conversation is strictly 1-to-1 in the MVP.

---

## 13.1 Conversation UI

Example:

```text
< K7F2A91X

              Hello        21:31

How are you?               21:32

──────────────────────────────

[ Type a message... ] [ Send ]
```

The conversation should display:

- Message bubbles
- Timestamp
- Text input
- Send button

---

# 14. Conversation Lifecycle

A conversation exists only while both participating identities remain valid.

Conceptually:

```text
Conversation Expiration =
MIN(
    User A Identity Expiration,
    User B Identity Expiration
)
```

Example:

```text
User A expires: 22:00
User B expires: 23:00

Conversation expires: 22:00
```

Therefore, expiration of either participant invalidates the conversation.

---

# 15. Message Expiration Boundary

The server is responsible for determining whether a message is valid.

If:

```text
message_timestamp < identity_expires_at
```

the message may be accepted.

If:

```text
message_timestamp >= identity_expires_at
```

the message must be rejected.

Example:

```text
21:59:59.999
Message accepted

22:00:00.000
Identity expired
Message rejected
```

This prevents ambiguity at the exact expiration boundary.

---

# 16. Realtime Expiration

Identity expiration and conversation invalidation should be delivered through a realtime channel where possible.

Recommended mechanism:

```text
WebSocket
```

Example:

```text
Server
   │
   │ IDENTITY_EXPIRED
   ↓
Connected Client
```

The client immediately receives the event.

---

# 17. Conversation Unavailable Alert

If a conversation expires while the user is viewing it, the application should display a modal alert.

Example:

```text
┌─────────────────────────────────┐
│                                 │
│    Conversation unavailable     │
│                                 │
│    The other user is no longer  │
│    available.                   │
│                                 │
│      [ Delete conversation ]    │
│                                 │
└─────────────────────────────────┘
```

The button should:

1. Delete the local conversation.
2. Confirm server-side deletion if necessary.
3. Navigate the user to Home.

---

# 18. Realtime Is Not the Only Synchronization Mechanism

WebSocket connections cannot be assumed to remain active indefinitely.

The application must support fallback synchronization.

When the application:

- Reconnects
- Returns from background
- Starts after being closed
- Re-establishes network connectivity

it should synchronize state with the server.

Conceptually:

```text
App reconnects
      ↓
Sync
      ↓
Server state
      ↓
Conversation expired?
      ↓
Delete local conversation
```

This prevents stale conversations from remaining indefinitely on devices.

---

# 19. Offline Behavior

The MVP should support limited offline behavior.

Recommended rules:

### Sending messages offline

Messages should remain unsent until connectivity is restored.

The application should not claim that a message was successfully delivered until the server acknowledges it.

Example state:

```text
Sending...
```

then:

```text
Sent
```

or:

```text
Failed
```

---

### Receiving messages offline

Messages received while the application is offline should be synchronized when the client reconnects, provided the conversation and identity are still valid.

---

# 20. End-to-End Encryption

Messages should use end-to-end encryption.

Architecture:

```text
User A
  │
  │ Plaintext
  ↓
Encrypt
  │
  │ Ciphertext
  ↓
Server
  │
  │ Ciphertext
  ↓
User B
  │
  ↓
Decrypt
  │
  ↓
Plaintext
```

The server should not receive plaintext message content.

---

# 21. Cryptography Requirements

The application must not implement custom cryptographic algorithms.

The implementation should use established cryptographic primitives and audited libraries.

Potential architecture:

```text
X25519
   +
HKDF
   +
AES-GCM
```

or an equivalent modern authenticated-encryption construction.

The final cryptographic protocol must be formally specified before implementation.

---

# 22. Key Management

Private cryptographic material must never be stored as ordinary plaintext.

Keys should be protected using Android's secure storage facilities where appropriate.

The Public ID must never function as a cryptographic secret.

Conceptually:

```text
Public ID
    ≠
Authentication Credential
    ≠
Encryption Private Key
```

---

# 23. Server Data

The server should minimize stored information.

Potential server-side data:

```text
Identity
- internal_id
- public_id
- created_at
- expires_at
- status
```

```text
Chat Request
- request_id
- sender_identity
- receiver_identity
- status
- created_at
```

```text
Conversation
- conversation_id
- participant references
- created_at
- expires_at
```

```text
Message
- message_id
- conversation_id
- sender reference
- ciphertext
- created_at
```

The server should not store plaintext messages.

---

# 24. Local Storage

The Android application may maintain a local encrypted database for:

- Current identity
- Cryptographic keys
- Active conversations
- Encrypted message cache
- Request state

Local message storage should be encrypted at rest.

When a conversation becomes invalid:

```text
Server invalidates
       ↓
Client receives event
       ↓
Local encrypted messages deleted
```

---

# 25. Account Deletion Model

The application cannot rely on Android's uninstall event to notify the server.

Therefore, server-side deletion must be based on identity expiration and explicit deletion/revocation.

The system should not assume:

```text
Uninstall = immediate server notification
```

Instead:

```text
No active identity
        ↓
Expiration policy
        ↓
Server invalidation
        ↓
Data purge
```

---

# 26. Deletion Requirements

When an identity expires:

1. Public ID becomes invalid.
2. New requests to the identity are rejected.
3. Pending requests become invalid.
4. Associated conversations become unavailable.
5. Realtime deletion events are emitted.
6. Server-side conversation data is purged according to the deletion policy.
7. Local conversation data is deleted when the client receives the event or next synchronizes.
8. A new identity may be generated.

---

# 27. Security Requirements

## 27.1 Transport Security

All client-server communication must use secure transport.

```text
HTTPS / TLS
```

Plain HTTP must not be used for production communication.

---

## 27.2 Authentication

Public IDs must not be used as authentication credentials.

A private authentication mechanism must be established separately.

The authentication credential must be securely stored on the Android device.

---

## 27.3 Rate Limiting

The backend must implement rate limiting for:

- Public ID lookup
- Chat requests
- Authentication attempts
- Message sending
- WebSocket connections
- Synchronization endpoints

This reduces abuse and Public ID enumeration.

---

## 27.4 Public ID Enumeration Protection

Because Public IDs are short, attackers could attempt to guess IDs.

The backend should use:

- Rate limiting
- Request throttling
- Abuse detection
- Request limits
- Appropriate response handling

The system should not expose unnecessary information about users.

---

# 28. Blocking

Users must be able to block another identity.

Example:

```text
Conversation
     ↓
Menu
     ↓
Block User
```

A blocked identity cannot send new requests to the blocker while the block remains active.

Block records may contain:

```text
blocker_id
blocked_id
created_at
```

Block behavior must be carefully aligned with identity rotation.

Because identities are ephemeral, a newly generated identity should not automatically inherit a previous identity's block relationships unless the product later introduces a persistent account-level identity.

---

# 29. Privacy Model

The product should be described as **pseudonymous**, rather than guaranteeing absolute anonymity.

The application removes traditional identity requirements, but network-level metadata may still exist.

Potential infrastructure-level metadata can include:

- IP addresses
- Connection timestamps
- Network information
- Operational logs

The production system should minimize retention of such information where practical.

---

# 30. UI / UX Requirements

The UI should be intentionally minimal.

## Required screens

### 30.1 Welcome

```text
Anonymous Chat

Temporary conversations.
No phone number required.

[ Start Messaging ]
```

---

### 30.2 Home

```text
Your Public ID

K7F2A91X

[ Copy ] [ Share ]

Find someone

[ Enter Public ID ]

[ Send Request ]

────────────────

Inbox
```

---

### 30.3 Inbox

```text
Inbox

Incoming Requests

A8K29D3F

[ Accept ] [ Reject ]
```

---

### 30.4 Conversation

```text
< A8K29D3F

        Hello       21:31

How are you?        21:32

[ Message... ] [Send]
```

---

### 30.5 Settings

Potential settings:

```text
Settings

Identity
K7F2A91X

Expires in:
11h 32m

Automatic rotation
ON

[ Rotate Identity ]

Privacy
[ Blocked identities ]

About
```

---

# 31. Navigation

Recommended navigation:

```text
              HOME
             /    \
            /      \
         INBOX    SEARCH
           \        /
            \      /
          CONVERSATION
```

The MVP should avoid complex navigation.

---

# 32. Android Technology Stack

Recommended initial stack:

```text
Language:
Kotlin

UI:
Jetpack Compose

Architecture:
MVVM / Clean Architecture

Local Database:
Room

Networking:
Ktor Client or equivalent

Realtime:
WebSocket

Backend:
Kotlin + Ktor

Database:
PostgreSQL

Cryptography:
Established cryptographic libraries

Secure Storage:
Android Keystore / appropriate secure storage
```

---

# 33. Android Architecture

Recommended structure:

```text
app/
│
├── data/
│   ├── local/
│   ├── remote/
│   └── repository/
│
├── domain/
│   ├── model/
│   ├── repository/
│   └── usecase/
│
├── presentation/
│   ├── welcome/
│   ├── home/
│   ├── inbox/
│   ├── search/
│   ├── conversation/
│   └── settings/
│
├── crypto/
│
└── core/
```

Architecture:

```text
Presentation
      ↓
   Domain
      ↓
     Data
   ↙      ↘
Local     Remote
```

---

# 34. Backend Architecture

Conceptual backend:

```text
Android Client
      │
      │ HTTPS
      ▼
┌────────────────────┐
│     Ktor API       │
├────────────────────┤
│ Identity Service   │
│ Request Service    │
│ Chat Service       │
│ Realtime Service   │
│ Deletion Service   │
└─────────┬──────────┘
          │
          ▼
     PostgreSQL
```

---

# 35. Core Backend Services

## Identity Service

Responsibilities:

- Generate Public IDs
- Validate IDs
- Manage expiration
- Rotate identities
- Revoke identities

---

## Request Service

Responsibilities:

- Create requests
- Validate sender/receiver
- Accept requests
- Reject requests
- Expire invalid requests

---

## Conversation Service

Responsibilities:

- Create conversations
- Validate participants
- Store message ciphertext
- Retrieve encrypted messages
- Invalidate conversations

---

## Realtime Service

Responsibilities:

- WebSocket connections
- Identity expiration events
- Conversation invalidation events
- New message notifications
- Request notifications

---

## Deletion Service

Responsibilities:

- Process expired identities
- Invalidate conversations
- Purge expired data
- Trigger realtime events

---

# 36. Important Server Event Types

Potential event types:

```text
IDENTITY_EXPIRED

IDENTITY_ROTATED

CHAT_REQUEST_RECEIVED

CHAT_REQUEST_ACCEPTED

CHAT_REQUEST_REJECTED

CONVERSATION_UNAVAILABLE

MESSAGE_RECEIVED
```

---

# 37. Example Realtime Flow

```text
User A identity expires
          ↓
Identity Service
          ↓
Conversation Service
          ↓
Conversation invalidated
          ↓
Deletion Service
          ↓
Realtime Service
          ↓
WebSocket
          ↓
User B
          ↓
Show "Conversation unavailable"
          ↓
Delete local conversation
          ↓
Navigate Home
```

---

# 38. Database Design Principles

The database should prioritize:

- Small records
- Efficient indexes
- Minimal metadata
- Automatic expiration
- Efficient deletion
- No plaintext message content

Important indexes may include:

```text
public_id
expires_at
conversation_id
participant_id
request_receiver
request_status
created_at
```

---

# 39. Performance Requirements

The application should be lightweight.

Goals:

- Fast startup
- Minimal background processing
- Minimal network traffic
- No unnecessary polling
- Efficient database queries
- No media processing in MVP
- Realtime connection only when appropriate

---

# 40. Polling vs Realtime

The application should avoid aggressive polling such as:

```text
Every 1 second:
GET /messages
```

Instead:

```text
WebSocket
    +
reconnect synchronization
```

This reduces:

- Battery usage
- Network traffic
- Server load

---

# 41. Message Delivery

Messages should have explicit delivery states.

Example:

```text
LOCAL
  ↓
SENDING
  ↓
SENT
  ↓
DELIVERED
```

The MVP may simplify this to:

```text
SENDING
   ↓
SENT
```

Read receipts are intentionally excluded from the MVP.

---

# 42. Error States

The application must handle:

### Invalid Public ID

```text
ID does not match
```

### Own Public ID

```text
You cannot message yourself.
```

### Identity expired

```text
Your identity has expired.
A new identity has been generated.
```

### Conversation expired

```text
Conversation unavailable.
```

### Network unavailable

```text
Unable to connect.
Check your internet connection.
```

### Request already exists

```text
Request already sent.
```

### User unavailable

```text
This identity is no longer available.
```

---

# 43. MVP Scope

## Included

### Identity

- Generate Public ID
- 8-character ID
- Secure random generation
- 24-hour expiration
- Automatic rotation
- Manual rotation
- Identity invalidation

### Messaging

- 1-to-1 chat
- Text messages
- Timestamps
- E2EE
- Encrypted local storage

### Requests

- Public ID lookup
- Request creation
- Accept
- Reject
- Request expiration

### Realtime

- WebSocket connection
- Message events
- Request events
- Conversation expiration events

### Privacy

- No phone number
- No email
- No username
- No profile
- No online status
- No last seen
- No typing indicator
- No read receipts

### Safety

- Block
- Rate limiting
- Abuse protection

---

# 44. Explicitly Out of Scope for MVP

The following should not be implemented initially:

- Group chat
- Voice calls
- Video calls
- Image messages
- Video messages
- File sharing
- Voice messages
- Stories
- Status updates
- Profile photos
- Permanent usernames
- Phone-number authentication
- Email authentication
- Public user directory
- Message reactions
- Stickers
- GIFs
- Message forwarding
- Permanent conversation history

---

# 45. Future Roadmap

## Version 1.1

Potential improvements:

- Better offline synchronization
- Message retry
- Improved abuse prevention
- More detailed connection status
- Improved deletion synchronization

---

## Version 1.2

Potential additions:

- Message expiration options
- Temporary conversation settings
- More advanced block controls

---

## Future

Potentially consider:

- Group conversations
- Media messages
- Voice messages
- Advanced cryptographic protocol
- Multi-device support

These should only be considered after the security model is mature.

---

# 46. Non-Functional Requirements

## Security

The application must:

- Use TLS.
- Protect authentication credentials.
- Use established cryptographic libraries.
- Never implement custom encryption.
- Encrypt messages end-to-end.
- Avoid storing plaintext messages.
- Validate authorization server-side.
- Rate-limit sensitive endpoints.

---

## Privacy

The application should:

- Minimize personal data.
- Minimize server metadata retention.
- Avoid permanent identity history.
- Delete expired conversations.
- Delete local expired conversations.
- Avoid unnecessary tracking.

---

## Performance

The application should:

- Start quickly.
- Use minimal memory.
- Minimize network requests.
- Avoid unnecessary background services.
- Avoid aggressive polling.
- Efficiently process realtime events.

---

# 47. Critical Security Rule

The server must never trust the client.

For example, the client must not be allowed to claim:

```text
identity_expires_at = tomorrow
```

The server determines:

```text
created_at
expires_at
identity_status
```

Similarly, the client cannot simply claim:

```text
"I am user K7F2A91X."
```

The server must verify the private authentication credential associated with the identity.

---

# 48. Important Edge Cases

The implementation must explicitly define behavior for:

### Case 1 — Message sent exactly at expiration

Server timestamp determines validity.

---

### Case 2 — Request sent immediately before expiration

The server accepts the request only if the sender and receiver are valid at the transaction time.

---

### Case 3 — Request accepted after sender expiration

The request must be rejected or invalidated.

---

### Case 4 — User offline during expiration

When the user reconnects, synchronization must detect the expired conversation.

---

### Case 5 — WebSocket disconnects during expiration

The client must recover through synchronization after reconnecting.

---

### Case 6 — User manually rotates identity

Old identity and associated conversations become invalid according to the deletion policy.

---

### Case 7 — Android app is uninstalled

The system must not depend on an uninstall callback.

The server relies on identity expiration and lifecycle policies.

---

### Case 8 — User reinstalls the application

A new installation must not automatically recover the previous identity.

A new identity should be created unless a future version introduces a secure account recovery mechanism.

---

# 49. Success Criteria

The MVP is considered successful when a user can:

```text
Install
   ↓
Start Messaging
   ↓
Receive Public ID
   ↓
Share ID
   ↓
Another user enters ID
   ↓
Send request
   ↓
Receiver accepts
   ↓
Conversation created
   ↓
Exchange encrypted messages
   ↓
Identity reaches 24 hours
   ↓
Conversation becomes unavailable
   ↓
Other participant receives realtime event
   ↓
Conversation is deleted
   ↓
New identity is generated
```

All of this should work without requiring:

- Phone number
- Email
- Username
- Profile
- Permanent account identity

---

# 50. Product Definition

The application can be summarized as:

> **A lightweight Android messenger built around temporary pseudonymous identities. Users communicate through randomly generated Public IDs, establish conversations through explicit requests, exchange end-to-end encrypted messages, and automatically lose their identity and conversations after 24 hours.**

The core product loop is:

```text
GENERATE ID
     ↓
SHARE ID
     ↓
REQUEST
     ↓
ACCEPT
     ↓
CHAT
     ↓
24 HOURS
     ↓
DELETE
     ↓
NEW ID
     ↓
REPEAT
```

The central design philosophy is:

> **Temporary identity. Temporary conversation. Minimal data.**